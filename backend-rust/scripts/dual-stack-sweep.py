#!/usr/bin/env python3
"""Authenticated Java/Rust dual-stack sweep.

Sends the same requests to a Java baseline and the Rust service, each backed by
its own isolated, synthetic-only MySQL schema, and compares status, business
code, message, description and data. It covers what the anonymous shared fixture
(phase9-contract-diff.sh) cannot: every inventory endpoint with a valid admin
session, ordinary-user authorization, and "probe" bodies whose id fields are
`999999` as a string and as a number (not-found paths, error precedence, Jackson
scalar coercion).

Safety: the two databases are TRUNCATEd between runs, so their names must match
`rust_(parity|flow|sweep)_...`; anything else is refused. Credentials come from
the environment only (MYSQL_PWD); tokens are generated per run and only their
SHA-256 digests are stored. Requests run sequentially on purpose: concurrent
logout/reset-password calls invalidate the fixture session mid-run.

Differences are classified (see CATEGORIES) and resolved against a registry
(dual-stack-sweep-registry.json). Exit status is 1 when any difference resolves
to FAIL, so a regression cannot hide in "different but accepted" noise.

Setup (all synthetic, nothing touches production):
  1. Create two schemas from the structure of the dev database, no data:
       docker exec -e MYSQL_PWD mysql-project006 mysqldump -uroot --no-data --skip-triggers \
         --set-gtid-purged=OFF --no-tablespaces project006 > schema.sql
       CREATE DATABASE rust_parity_java_<id> / rust_parity_rust_<id> CHARACTER SET utf8mb4
         COLLATE utf8mb4_0900_ai_ci, then load schema.sql into both.
  2. Java baseline (JDK 21, fast-jar built from backend/):
       DB_USER=... DB_PASSWORD=... QUARKUS_HTTP_PORT=18121 QUARKUS_HTTP_HOST=127.0.0.1 \
       QUARKUS_DATASOURCE_JDBC_URL='jdbc:mysql://127.0.0.1:3306/rust_parity_java_<id>?useSSL=false&allowPublicKeyRetrieval=true' \
       java -Duser.timezone=UTC -jar backend/target/quarkus-app/quarkus-run.jar
  3. Rust service with every migration route flag on:
       RUST_BACKEND_ADDR=127.0.0.1:18122 \
       RUST_BACKEND_DATABASE_URL=mysql://USER:PASSWORD@127.0.0.1:3306/rust_parity_rust_<id> \
       RUST_PHASE*_ENABLED=true (every flag listed in crates/app/src/platform/flags.rs) \
       backend-rust/target/debug/rust-backend
  4. MYSQL_PWD=... python3 backend-rust/scripts/dual-stack-sweep.py \
       --java-db rust_parity_java_<id> --rust-db rust_parity_rust_<id> --out report.json
"""
import argparse
import base64
import collections
import copy
import datetime
import hashlib
import json
import os
import pathlib
import re
import secrets
import socket
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

SAFE_DB = re.compile(r"^rust_(parity|flow|sweep)_[a-z0-9_]+$")
ROOT = pathlib.Path(__file__).resolve().parents[2]
SESSION_ENDPOINTS = {"POST /api/user/userOut", "GET /api/user/resetPassword", "POST /api/user/changePassword"}
ID_KEYS = ["id", "trainId", "userId", "roomId", "examId", "knowledgeId", "knowledgeSwfId", "swfId", "levelId",
           "cableId", "deviceId", "deviceTypeId", "classifyId", "testPaperId", "questionId", "floorId",
           "equipmentId", "ruleId", "paperId", "testId", "recordId", "groupId", "teacherId", "studentId"]

# Category -> default disposition when the registry has no override.
CATEGORIES = {
    "TEXT_ONLY": "ACCEPTED",                  # same status/code/data; message or description wording differs
    "RUST_FAILS_WHERE_JAVA_OK": "FAIL",       # Rust 5xx/code 500 while Java answers normally
    "RUST_OTHER_CODE": "FAIL",                # different business code on the same input (e.g. error precedence)
    "JAVA_UNHANDLED_500": "ACCEPTED",         # Java NPE/unhandled -> HTTP 500; Rust returns a defined envelope
    "JAVA_RAW_HTTP_4XX": "ACCEPTED",          # Java raw 4xx without envelope; Rust answers with an envelope
    "RUST_RAW_HTTP_4XX": "OPEN",              # Rust raw 4xx (serde 422 / multipart 400) where Java coerces or answers
    "RUST_STRICTER": "OPEN",                  # Java 200 where Rust 202 (validation Java does not perform)
    "RUST_PERMISSIVE": "OPEN",                # Java 202 where Rust 200
    "DATA": "OPEN",                           # same code, different data (often downstream of the above)
}


def sha256(text):
    return hashlib.sha256(text.encode()).hexdigest()


class Mysql:
    def __init__(self, container, java_db, rust_db):
        for name in (java_db, rust_db):
            if not SAFE_DB.match(name):
                sys.exit(f"refusing database {name!r}: must match {SAFE_DB.pattern}")
        if "MYSQL_PWD" not in os.environ:
            sys.exit("MYSQL_PWD must be set (credentials are never read from arguments or files)")
        self.container, self.dbs = container, (java_db, rust_db)

    def run(self, database, statement):
        assert database in self.dbs, "only the two isolated databases may be touched"
        cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", self.container, "mysql", "-uroot",
               "--default-character-set=utf8mb4", "-N", "-B", database]
        result = subprocess.run(cmd, input=statement, text=True, capture_output=True)
        if result.returncode:
            raise RuntimeError(result.stderr.strip())
        return result.stdout.strip()

    def reset(self, tokens):
        """Empty every table in both schemas, then seed one admin and one ordinary user.

        t_role.is_admin == 0 means administrator (see AGENTS.md red line 7).
        """
        names = [t for t in self.run(self.dbs[0], "SELECT table_name FROM information_schema.tables "
                                                  "WHERE table_schema=DATABASE()").split("\n") if t]
        seed = (
            "INSERT INTO t_user(id,user_account,user_name,status,device_id,token) VALUES "
            f"('sweep-admin','sweep-admin','Sweep Admin',0,'{tokens['device']}','{sha256(tokens['admin'])}'),"
            f"('sweep-student','sweep-student','Sweep Student',0,'{tokens['device']}','{sha256(tokens['student'])}');"
            "INSERT INTO t_role(id,title,is_admin,is_default) VALUES ('sweep-admin-role','Sweep Admin',0,1),"
            "('sweep-student-role','Sweep Student',1,1);"
            "INSERT INTO t_user_role(id,user_id,role_id) VALUES ('sweep-admin-m','sweep-admin','sweep-admin-role'),"
            "('sweep-student-m','sweep-student','sweep-student-role');"
        )
        statement = "SET FOREIGN_KEY_CHECKS=0;" + "".join(f"TRUNCATE TABLE `{t}`;" for t in names) + \
                    "SET FOREIGN_KEY_CHECKS=1;" + seed
        for database in self.dbs:
            self.run(database, statement)


def http_case(base, method, path, body, headers, timeout):
    data, hs = None, dict(headers)
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
        hs["Content-Type"] = "application/json"
    request = urllib.request.Request(base + path, data=data, headers=hs, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=timeout)
    except urllib.error.HTTPError as error:
        response = error
    except Exception as error:  # transport failure (e.g. connection dropped by a panic)
        return {"status": None, "error": f"{type(error).__name__}: {error}"[:200]}
    with response:
        raw = response.read()
    try:
        return {"status": response.status, "json": json.loads(raw.decode("utf-8"))}
    except ValueError:
        return {"status": response.status, "json": None, "raw": raw[:160].decode("utf-8", "replace")}


WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


class WsConnection:
    """Minimal RFC 6455 client (stdlib only): masked text frames out, fragmented text in, ping -> pong.

    Every received text frame is kept in arrival order; nothing is filtered, so heartbeats and
    rejection frames stay visible to the comparison.
    """

    def __init__(self, base, path, timeout):
        parts = urllib.parse.urlsplit(base)
        self.sock = socket.create_connection((parts.hostname, parts.port or 80), timeout=timeout)
        key = base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall((f"GET {path} HTTP/1.1\r\nHost: {parts.hostname}:{parts.port or 80}\r\n"
                           "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                           f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
        self.buffer = b""
        while b"\r\n\r\n" not in self.buffer:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise ConnectionError("connection closed during the WebSocket handshake")
            self.buffer += chunk
        head, self.buffer = self.buffer.split(b"\r\n\r\n", 1)
        lines = head.decode("latin-1").split("\r\n")
        self.status = int(lines[0].split()[1])
        fields = {name.strip().lower(): value.strip() for name, value in
                  (line.split(":", 1) for line in lines[1:] if ":" in line)}
        expected = base64.b64encode(hashlib.sha1((key + WS_GUID).encode()).digest()).decode()
        if self.status == 101 and fields.get("sec-websocket-accept") != expected:
            raise ConnectionError("invalid Sec-WebSocket-Accept")
        self.closed = self.status != 101
        self.close_code = None
        self.fragments = []

    def _read(self, count, deadline):
        while len(self.buffer) < count:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError
            self.sock.settimeout(remaining)
            try:
                chunk = self.sock.recv(65536)
            except socket.timeout:
                raise TimeoutError from None
            except OSError:
                chunk = b""
            if not chunk:
                self.closed = True
                raise ConnectionError("connection closed")
            self.buffer += chunk
        data, self.buffer = self.buffer[:count], self.buffer[count:]
        return data

    def _write(self, opcode, payload):
        header = bytearray([0x80 | opcode])
        size = len(payload)
        if size < 126:
            header.append(0x80 | size)
        elif size < 1 << 16:
            header += bytes([0x80 | 126]) + struct.pack("!H", size)
        else:
            header += bytes([0x80 | 127]) + struct.pack("!Q", size)
        mask = os.urandom(4)
        self.sock.sendall(bytes(header) + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload)))

    def send_text(self, text):
        self._write(0x1, text.encode())

    def recv(self, count, timeout):
        """Up to `count` text frames within `timeout` seconds; stops early if the server closes."""
        frames, deadline = [], time.monotonic() + timeout
        while len(frames) < count and not self.closed:
            try:
                first, second = self._read(2, deadline)
                size = second & 0x7F
                if size == 126:
                    size = struct.unpack("!H", self._read(2, deadline))[0]
                elif size == 127:
                    size = struct.unpack("!Q", self._read(8, deadline))[0]
                mask = self._read(4, deadline) if second & 0x80 else None
                payload = self._read(size, deadline)
            except (TimeoutError, ConnectionError):
                break
            if mask:
                payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
            opcode = first & 0x0F
            if opcode == 0x9:
                self._write(0xA, payload)
            elif opcode == 0x8:
                self.close_code = struct.unpack("!H", payload[:2])[0] if len(payload) >= 2 else None
                self.closed = True
                try:
                    self._write(0x8, payload[:2])
                except OSError:
                    pass
            elif opcode in (0x0, 0x1, 0x2):
                self.fragments.append(payload)
                if first & 0x80:
                    frames.append(b"".join(self.fragments).decode("utf-8", "replace"))
                    self.fragments = []
        return frames

    def close(self):
        if not self.closed:
            try:
                self._write(0x8, struct.pack("!H", 1000))
                self.recv(1 << 30, 1)
            except OSError:
                pass
        self.closed = True
        self.sock.close()


def ws_frame(text):
    """Frames are compared as JSON when they are JSON documents, otherwise as their literal text."""
    try:
        return json.loads(text)
    except ValueError:
        return text


def ws_frame_data(frame):
    """Opt-in (`parse_data`): compare a JSON-object `data` string as JSON, so key order and spacing inside the
    embedded document (Java keeps insertion order, Rust sorts keys) are not differences."""
    if isinstance(frame, dict) and isinstance(frame.get("data"), str):
        try:
            inner = json.loads(frame["data"])
        except ValueError:
            return frame
        if isinstance(inner, (dict, list)):
            return {**frame, "data": inner}
    return frame


def ws_observation(status, frames, connection, parse_data=False):
    """A WS step as a 200 envelope so the journey comparator, registry and identity map apply unchanged."""
    parse = ws_frame_data if parse_data else (lambda frame: frame)
    data = {"handshake": status, "frames": [parse(ws_frame(frame)) for frame in frames],
            "closed": bool(connection and connection.closed),
            "closeCode": connection.close_code if connection else None}
    return {"status": 200, "json": {"code": 200, "message": "ws", "description": "", "data": data}}


def ws_step(step, connections, base, auth, variables, timeout):
    """open/send/recv/close one connection named step["conn"] on one stack.

    open authenticates through the query (token, deviceId) exactly like the frontend; every step
    then waits `settle`/`timeout` seconds collecting up to `count` frames (default: all that arrive).
    """
    action, name = step["ws"], step["conn"]
    count = step.get("count", 1 << 30)
    if action == "open":
        path = substitute(step["path"], variables)
        if auth:
            path += ("&" if "?" in path else "?") + urllib.parse.urlencode(auth)
        try:
            connection = WsConnection(base, path, timeout)
        except (OSError, ConnectionError) as error:
            return {"status": None, "error": f"{type(error).__name__}: {error}"[:200]}
        connections[name] = connection
        frames = connection.recv(count, step.get("settle", 0.3)) if connection.status == 101 else []
        return ws_observation(connection.status, frames, connection, step.get("parse_data", False))
    connection = connections.get(name)
    if connection is None:
        return {"status": None, "error": f"connection {name!r} was never opened"}
    if action == "send":
        text = (substitute(step["text"], variables) if "text" in step
                else json.dumps(substitute(step["json"], variables), ensure_ascii=False))
        try:
            connection.send_text(text)
        except OSError:
            connection.closed = True
        frames = connection.recv(count, step.get("settle", 0.3))
    elif action == "recv":
        frames = connection.recv(count, step.get("timeout", 3))
    elif action == "close":
        connection.close()
        frames = []
    else:
        raise ValueError(f"unknown ws action {action!r}")
    return ws_observation(connection.status, frames, connection, step.get("parse_data", False))


def query_pairs(row):
    pairs = {}
    for binding in json.loads(row["request_bindings"]):
        if binding["annotation"] == "RestQuery":
            key = binding.get("key") or ""
            pairs[key if re.fullmatch(r"[A-Za-z_]\w*", key) else binding["type_and_name"].split()[-1]] = "1"
    return pairs


def probe_body(kind):
    value = "999999" if kind == "str" else 999999
    body = {key: value for key in ID_KEYS}
    body.update({"pageNumber": 1, "rows": 10, "type": 1, "status": 1, "name": "x", "title": "x"})
    return body


def build_jobs(inventory, headers, with_probes):
    main, session, probes = [], [], []
    for row in inventory:
        endpoint, method = row["endpoint_key"], row["method"]
        path = re.sub(r"\{[^}]+\}", "999999", row["path"])
        body = {} if method == "POST" else None
        cases = [("anon", "empty", path, body, {}), ("admin", "empty", path, body, headers["admin"])]
        pairs = query_pairs(row)
        if pairs:
            cases.append(("admin", "filled", path + "?" + urllib.parse.urlencode(pairs), body, headers["admin"]))
        if row["require_admin"] == "true":
            cases.append(("student", "empty", path, body, headers["student"]))
        target = session if endpoint in SESSION_ENDPOINTS else main
        for who, variant, p, b, h in cases:
            target.append({"endpoint": endpoint, "who": who, "variant": variant, "method": method, "path": p, "body": b, "headers": h})
        if with_probes and method == "POST" and endpoint not in SESSION_ENDPOINTS:
            for kind in ("str", "num"):
                probes.append({"endpoint": endpoint, "who": "admin", "variant": f"probe-{kind}", "method": method,
                               "path": path, "body": probe_body(kind), "headers": headers["admin"]})
    return main, session, probes


UUID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
DATETIME = re.compile(r"^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(\.\d+)?$")
EPOCH_MS = re.compile(r"^\d{13}$")


def normalize(value):
    """Identity-agnostic canonical form; production comparisons also check IdentityMap.

    Clocks retain shape masking. Integral floats equal their integer (as in JS).
    """
    if isinstance(value, dict):
        return {key: normalize(item) for key, item in value.items()}
    if isinstance(value, list):
        return [normalize(item) for item in value]
    if isinstance(value, str):
        if UUID.match(value):
            return "<uuid>"
        if EPOCH_MS.match(value):
            return "<epoch-ms>"
        if DATETIME.match(value):
            return "<datetime>"
        return value
    if isinstance(value, bool) or value is None:
        return value
    if isinstance(value, float) and value.is_integer():
        value = int(value)
    if isinstance(value, int) and 10**12 <= value < 10**13:
        return "<epoch-ms>"
    return value


class Unordered(list):
    """Opted-in multiset whose raw identities remain available for pair matching."""


NUMERIC_ID_DOMAINS = {"device", "deviceType", "deviceDescription", "deviceScoringRule", "groupNetTrain", "room"}


def identity_domain(path):
    endpoint = path.split("?")[0].split("/")
    if "addDeviceDescription" in endpoint:
        return "deviceDescription"
    return "room" if "router" in endpoint else next((part for part in endpoint if part in NUMERIC_ID_DOMAINS), None)


def child_identity_domain(field, domain):
    return "deviceDescription" if field == "descriptions" and domain == "device" else domain


class IdentityMap:
    """One journey (or standalone response) owns a bijection, numeric PKs per entity domain."""

    def __init__(self):
        self.java_to_rust, self.rust_to_java = {}, {}

    def clone(self):
        other = IdentityMap()
        other.java_to_rust = self.java_to_rust.copy()
        other.rust_to_java = self.rust_to_java.copy()
        return other

    def key(self, value, field, domain):
        if isinstance(value, str) and UUID.fullmatch(value):
            return ("uuid", value)
        numeric_domain = domain if field == "id" else field[:-2] if field.endswith("Id") else None
        if field == "deviceType":
            numeric_domain = "deviceType"
        elif field == "trainId" and domain == "groupNetTrain":
            numeric_domain = domain
        if numeric_domain in NUMERIC_ID_DOMAINS and type(value) is int and value > 0:
            return (numeric_domain, value)
        return None

    def canonical(self, value, side, field="", domain=None, agnostic=False):
        key = self.key(value, field, domain)
        if key is not None and key[0] != "uuid":
            if agnostic:
                return "<numeric-id>"
            if side == "rust" and key in self.rust_to_java:
                return self.rust_to_java[key][1]
        if isinstance(value, dict):
            return {name: self.canonical(item, side, name, child_identity_domain(name, domain), agnostic)
                    for name, item in value.items()}
        if isinstance(value, list):
            items = [self.canonical(item, side, field, domain, agnostic) for item in value]
            return unordered_array(items) if isinstance(value, Unordered) else items
        return value

    def bind(self, java, rust, path, field="", domain=None):
        left, right = self.key(java, field, domain), self.key(rust, field, domain)
        if left is None or right is None:
            return []
        expected = self.java_to_rust.get(left)
        inverse = self.rust_to_java.get(right)
        if (expected is not None and expected != right) or (inverse is not None and inverse != left):
            return [[path, java, rust, {"reason": "identity mismatch",
                                      "java_mapped_to": expected[1] if expected else None,
                                      "rust_mapped_to": inverse[1] if inverse else None}]]
        self.java_to_rust[left], self.rust_to_java[right] = right, left
        return []

    def compare(self, java, rust, path="$", field="", domain=None):
        pending, differences = [], []

        def walk(left, right, at, name, entity_domain):
            differences.extend(self.bind(left, right, at, name, entity_domain))
            if isinstance(left, dict) and isinstance(right, dict):
                for key in sorted(left.keys() & right.keys()):
                    walk(left[key], right[key], f"{at}.{key}", key, child_identity_domain(key, entity_domain))
            elif isinstance(left, Unordered) and isinstance(right, Unordered):
                pending.append((left, right, at, entity_domain))
            elif isinstance(left, list) and isinstance(right, list):
                for index, (x, y) in enumerate(zip(left, right)):
                    walk(x, y, f"{at}[{index}]", name, entity_domain)

        walk(java, rust, path, field, domain)
        for left, right, at, entity_domain in pending:
            differences.extend(self.match_unordered(left, right, at, entity_domain))
        return differences

    def match_unordered(self, java, rust, path, domain):
        """Match canonical forms first; existing identities must select a unique bijection."""
        groups = collections.defaultdict(lambda: [[], []])
        for side, values in enumerate((java, rust)):
            for index, value in enumerate(values):
                canonical = json.dumps(normalize(self.canonical(value, "java" if side == 0 else "rust", domain=domain, agnostic=True)),
                                       ensure_ascii=False, sort_keys=True)
                groups[canonical][side].append((index, value))
        differences = []
        for left, right in groups.values():
            if len(left) != len(right):
                # Ordinary data_diffs reports the full unmatched inventory; do not invent identities.
                continue
            solutions, failed = {}, []

            def search(index, remaining, mapping):
                if len(solutions) > 1:
                    return
                if index == len(left):
                    signature = frozenset(mapping.java_to_rust.items())
                    solutions[signature] = mapping
                    return
                java_index, item = left[index]
                seen = set()
                for candidate, (_, value) in enumerate(remaining):
                    spelling = json.dumps(value, ensure_ascii=False, sort_keys=True)
                    if spelling in seen:
                        continue
                    seen.add(spelling)
                    trial = mapping.clone()
                    errors = trial.compare(item, value, f"{path}[{java_index}]", domain=domain)
                    if errors:
                        failed.extend(errors)
                    else:
                        search(index + 1, remaining[:candidate] + remaining[candidate + 1:], trial)

            search(0, right, self.clone())
            if len(solutions) == 1:
                match = next(iter(solutions.values()))
                self.java_to_rust, self.rust_to_java = match.java_to_rust, match.rust_to_java
            elif solutions:
                differences.append([path, normalize([item for _, item in left]), normalize([item for _, item in right]),
                                    {"reason": "ambiguous identity correspondence"}])
            elif failed:
                differences.extend(failed)
        return differences


def code(resp):
    j = resp.get("json")
    return j.get("code") if isinstance(j, dict) else None


def comparable(resp):
    if resp.get("status") is None:
        return ("TRANSPORT", resp.get("error"))
    if resp.get("json") is None:
        return (resp["status"], "NONJSON", resp.get("raw"))
    return (resp["status"], json.dumps(normalize(resp["json"]), ensure_ascii=False, sort_keys=True))


def category(java, rust):
    js, rs, jc, rc = java.get("status"), rust.get("status"), code(java), code(rust)
    if rs is None or rs == 500 or rc == 500:
        if not (js == 500 or jc == 500):
            return "RUST_FAILS_WHERE_JAVA_OK"
    if js == 500 or jc == 500:
        return "JAVA_UNHANDLED_500"
    if java.get("json") is None and js in (400, 404, 405, 415):
        return "JAVA_RAW_HTTP_4XX"
    if rust.get("json") is None and rs in (400, 404, 405, 415, 422):
        return "RUST_RAW_HTTP_4XX"
    if js == rs and jc == rc:
        return "DATA" if envelope_data_diffs(java["json"], rust["json"]) else "TEXT_ONLY"
    if jc == 200 and rc == 202:
        return "RUST_STRICTER"
    if jc == 202 and rc == 200:
        return "RUST_PERMISSIVE"
    return "RUST_OTHER_CODE"


def data_diffs(a, b, path="$", out=None):
    """All differing paths, including presence and unmatched array contents."""
    out = [] if out is None else out
    if type(a) != type(b):
        out.append([path, a, b])
    elif isinstance(a, dict):
        for key in sorted(set(a) | set(b)):
            if key not in a or key not in b:
                out.append([f"{path}.{key}", a.get(key), b.get(key),
                            {"java_present": key in a, "rust_present": key in b}])
            else:
                data_diffs(a[key], b[key], f"{path}.{key}", out)
    elif isinstance(a, list):
        if len(a) != len(b):
            paired = min(len(a), len(b))
            out.append([f"{path}[len]", len(a), len(b),
                        {"java_extra": a[paired:], "rust_extra": b[paired:]}])
        for index, (x, y) in enumerate(zip(a, b)):
            data_diffs(x, y, f"{path}[{index}]", out)
    elif a != b:
        out.append([path, a, b])
    return out


def envelope_data_diffs(java, rust):
    """Preserve envelope membership as well as typed, normalized data values."""
    return data_diffs({"data": normalize(java["data"])} if "data" in java else {},
                      {"data": normalize(rust["data"])} if "data" in rust else {})


def brief(resp):
    j = resp.get("json")
    if resp.get("status") is None:
        return {"status": None, "error": resp.get("error")}
    if not isinstance(j, dict):
        return {"status": resp["status"], "raw": resp.get("raw")}
    return {"status": resp["status"], "code": j.get("code"), "message": j.get("message"), "description": j.get("description")}


JOURNEY_VAR = re.compile(r"\$\{(\w+)\}")


def substitute(value, variables):
    """Replace ${name} in strings; a string that is exactly one placeholder keeps the captured JSON type."""
    if isinstance(value, dict):
        return {key: substitute(item, variables) for key, item in value.items()}
    if isinstance(value, list):
        return [substitute(item, variables) for item in value]
    if isinstance(value, str):
        whole = JOURNEY_VAR.fullmatch(value)
        if whole:
            return variables.get(whole.group(1))
        return JOURNEY_VAR.sub(lambda match: str(variables.get(match.group(1))), value)
    return value


def extract(document, expression):
    """Tiny JSONPath for captures: $.data.id, $.data[0].id"""
    current = document
    for part in re.findall(r"[^.\[\]$]+|\[\d+\]", expression):
        current = current[int(part[1:-1])] if part.startswith("[") else current[part]
    return current


def transform_path(document, expression, transform):
    """Transform selected JSONPath-lite values ($.a.b, [0], [*]); missing paths stay missing."""
    parts = re.findall(r"\[\*\]|\[\d+\]|[^.\[\]$]+", expression)

    def walk(node, rest):
        part, tail = rest[0], rest[1:]
        if part == "[*]":
            keys = range(len(node)) if isinstance(node, list) else []
        elif part.startswith("["):
            index = int(part[1:-1])
            keys = [index] if isinstance(node, list) and index < len(node) else []
        else:
            keys = [part] if isinstance(node, dict) and part in node else []
        for key in keys:
            if tail:
                walk(node[key], tail)
            else:
                node[key] = transform(node[key])

    if parts:
        walk(document, parts)


def parsed_json(value):
    """Parse only opted-in string values; keep the wire string type distinguishable."""
    if not isinstance(value, str):
        return value
    return {"<parsed-json-string>": json.loads(value)}


def unordered_array(value):
    """Canonical multiset ordering retains duplicates and every row's fields."""
    if not isinstance(value, list):
        return value
    return Unordered(sorted(value, key=lambda item: json.dumps(normalize(item), ensure_ascii=False, sort_keys=True)))


STUDY_ENTITY_KEY = re.compile(
    r"^TheoryKnowledgeEntity\(id=(.*?), type=(.*?), status=(.*?), cover=(.*?), title=(.*?), "
    r"createUserId=(.*?), createTime=(.*?), credit=(.*?), difficultyId=(.*?), specialtyId=(.*?)\)$"
)
STUDY_ENTITY_FIELDS = ("id", "type", "status", "cover", "title", "createUserId", "createTime", "credit", "difficultyId", "specialtyId")


def normalized_entity_keys(value):
    """Normalize generated identities/clocks in selected Lombok study keys without merging entries."""
    if not isinstance(value, dict):
        return value
    entries, matched = [], False
    for key, item in value.items():
        match = STUDY_ENTITY_KEY.fullmatch(key)
        if match:
            matched = True
            fields = dict(zip(STUDY_ENTITY_FIELDS, match.groups()))
            if EPOCH_MS.fullmatch(fields["createTime"]) or DATETIME.fullmatch(fields["createTime"]):
                fields["createTime"] = "<clock>"
            key = {"<entity-key>": fields}
        entries.append([key, item])
    if not matched:
        return value
    # A multiset, not a dict: two generated keys can normalize to the same spelling.
    return {"<normalized-entity-key-entries>": unordered_array(entries)}


def captured_floor_content(document, floor_id):
    """Bind the sole floor key and every row reference before multiset comparison."""
    if not isinstance(floor_id, str) or not floor_id:
        return ["floor-content capture is missing"]
    data = document.get("data") if isinstance(document, dict) else None
    if not isinstance(data, dict) or set(data) != {floor_id}:
        return ["floor-content keys do not equal the captured requested floor"]
    rows = data[floor_id]
    if not isinstance(rows, list) or any(not isinstance(row, dict) or row.get("floorId") != floor_id for row in rows):
        return ["floor-content rows do not reference the captured requested floor"]
    document["data"] = {"<requested-floor>": unordered_array(rows)}
    return []


def post_ticker_source_ids(document, train_id, page_number, source_rows):
    """Validate the legacy UUID-to-Integer projection, then compare actual source IDs.

    This is opt-in for controlled findPage fixtures. Only canonical UUID source
    rows are supported; other historical String-to-Integer conversions stay outside
    this comparison proof. No response field is blindly masked.
    """
    data = document.get("data") if isinstance(document, dict) else None
    rows = data.get("messageBody") if isinstance(data, dict) else None
    if not isinstance(document, dict) or document.get("code") != 200 or not isinstance(rows, list):
        return ["post-ticker source proof requires a successful page response"]
    sources = {}
    for sort, source_id in source_rows:
        if type(sort) is not int or sort in sources or not isinstance(source_id, str) or not UUID.fullmatch(source_id):
            return ["post-ticker source inventory must have unique ordinals and UUID identities"]
        sources[sort] = source_id
    if len(rows) != len(sources):
        return ["post-ticker response inventory differs from persisted page"]
    replacements, seen = [], set()
    for row in rows:
        if not isinstance(row, dict) or type(row.get("sort")) is not int:
            return ["post-ticker response row has no integer ordinal"]
        sort = row["sort"]
        if sort in seen or sort not in sources or row.get("trainId") != train_id or type(row.get("pageNumber")) not in (int, float) or row.get("pageNumber") != page_number:
            return ["post-ticker response does not reference its captured train/page inventory"]
        seen.add(sort)
        source_id = sources[sort]
        # Hutool NumberUtil.parseInt rejects e/E before trying NumberFormat's
        # decimal prefix. A UUID has at most eight leading decimal digits.
        prefix = re.match(r"[0-9]+", source_id)
        expected = int(prefix.group()) if prefix and "e" not in source_id.lower() else None
        actual = row.get("id")
        if "id" not in row or (actual is not None if expected is None else type(actual) not in (int, float) or actual != expected):
            return [f"post-ticker source projection differs at ordinal {sort}"]
        replacements.append((row, source_id))
    for row, source_id in replacements:
        row["id"] = {"<verified-source-uuid>": source_id}
    return []


def verify_post_ticker_sources(document, selector, db, database):
    train_id, page = selector.get("trainId"), selector.get("pageNumber")
    if not isinstance(train_id, str) or not UUID.fullmatch(train_id) or type(page) is not int or page < 1:
        return ["post-ticker source proof needs a captured UUID train and positive page"]
    # Mysql.run itself restricts access to the two isolated parity schemas.
    inventory = db.run(database, "SELECT sort,id FROM t_post_ticker_tape_train_page "
                       f"WHERE train_id='{train_id}' AND page_number={page} ORDER BY sort,id")
    source_rows = []
    for line in inventory.splitlines():
        ordinal, source_id = line.split("\t", 1)
        source_rows.append((int(ordinal), source_id))
    return post_ticker_source_ids(document, train_id, page, source_rows)


def retained_settings(document, expected):
    if not isinstance(document, dict) or document.get("code") != 200 or not isinstance(expected, list):
        return ["retained settings proof requires a successful response and captured set"]
    if document.get("data") != expected:
        return ["retained settings differ from original IDs, values or order"]
    return []


def prepare_journey_response(document, step, variables=None):
    for expression in step.get("mask", []):
        transform_path(document, expression, lambda _: "<masked>")
    for expression in step.get("presence", []):
        # Clock-derived values (e.g. an elapsed time of a running room) are compared as present/absent only.
        transform_path(document, expression, lambda value: "<null>" if value is None else "<present>")
    for expression in step.get("parse_json", []):
        transform_path(document, expression, parsed_json)
    for expression in step.get("unordered", []):
        transform_path(document, expression, unordered_array)
    for expression in step.get("normalize_keys", []):
        transform_path(document, expression, normalized_entity_keys)
    if "floor_content" in step:
        return captured_floor_content(document, substitute(step["floor_content"], variables or {}))
    return []


def step_headers(step, headers, who, variables):
    """Fixture identity headers, or per-stack session headers captured earlier in the journey (e.g. a login token).

    A placeholder whose capture failed is omitted instead of sent as a literal, so the missing session stays visible.
    """
    if "headers" not in step:
        return headers[who]
    if who != "session":
        raise ValueError(f"step {step.get('name')!r}: explicit headers require who='session'")
    resolved = substitute(step["headers"], variables)
    return {name: str(value) for name, value in resolved.items() if value is not None}


def run_journeys(spec, args, headers, db):
    """Run scripted write-then-read flows on both stacks.

    Generated ids differ per stack, so each stack keeps its own captured variables;
    every step is then compared like a sweep request. These flows use realistic
    payloads (the ones the frontend sends), which empty/probe bodies cannot reach.
    Optional seed_sql fixtures execute through Mysql's isolated-schema guard on
    both databases before a journey or step (fixed hash keys or pagination clocks).
    """
    results = []
    for journey in spec.get("journeys", []):
        if "seed_sql" in journey:
            for database in db.dbs:
                db.run(database, journey["seed_sql"])
        variables = {"java": {}, "rust": {}}
        connections = {"java": {}, "rust": {}}
        identities = IdentityMap()
        try:
            results.extend(run_journey_steps(journey, args, headers, db, variables, connections, identities))
        finally:
            for side in connections.values():
                for connection in side.values():
                    connection.close()
    return results


def run_journey_steps(journey, args, headers, db, variables, connections, identities):
    results = []
    for step in journey["steps"]:
        if "seed_sql" in step:
            for database in db.dbs:
                db.run(database, step["seed_sql"])
        # send/recv/close reuse the identity the connection was opened with.
        reuses_connection = step.get("ws", "open") != "open"
        who = step.get("who", "session" if "headers" in step else "connection" if reuses_connection else "admin")
        outcome = {}
        for side, base in (("java", args.java_url), ("rust", args.rust_url)):
            if "ws" in step:
                auth = None if reuses_connection else step_headers(step, headers, who, variables[side])
                outcome[side] = ws_step(step, connections[side], base, auth, variables[side], args.timeout)
            else:
                path = substitute(step["path"], variables[side])
                body = substitute(step["body"], variables[side]) if "body" in step else ({} if step["method"] == "POST" else None)
                outcome[side] = http_case(base, step["method"], path, body,
                                          step_headers(step, headers, who, variables[side]), args.timeout)
            for name, expression in step.get("capture", {}).items():
                try:
                    variables[side][name] = copy.deepcopy(extract(outcome[side]["json"], expression))
                except (KeyError, IndexError, TypeError):
                    variables[side][name] = None
            errors = []
            if side == "rust" and "retained_settings" in step:
                expected = substitute(step["retained_settings"], variables[side])
                errors.extend(retained_settings(outcome[side].get("json"), expected))
            if "post_ticker_source_ids" in step:
                selector = substitute(step["post_ticker_source_ids"], variables[side])
                database = db.dbs[0 if side == "java" else 1]
                errors.extend(verify_post_ticker_sources(outcome[side].get("json"), selector, db, database))
            errors.extend(prepare_journey_response(outcome[side].get("json"), step, variables[side]))
            if errors:
                outcome[side]["comparison_errors"] = errors
        identity_diffs = []
        path = step.get("path", f"ws:{step.get('conn')}")
        domain = identity_domain(path)
        for name, expression in step.get("capture", {}).items():
            capture_domain = child_identity_domain("descriptions", domain) if ".descriptions" in expression else domain
            identity_diffs.extend(identities.compare(variables["java"][name], variables["rust"][name],
                                                     expression, expression.rsplit(".", 1)[-1], capture_domain))
        identity_diffs.extend(identities.compare(outcome["java"].get("json"), outcome["rust"].get("json"), domain=domain))
        outcome["java"]["identity_diffs"] = identity_diffs
        for side in ("java", "rust"):
            outcome[side]["comparison_json"] = identities.canonical(outcome[side].get("json"), side, domain=domain)
        job = {"endpoint": f"journey:{journey['name']}/{step['name']}", "variant": "journey", "who": who,
               "path": path, "method": step.get("method", f"WS-{step.get('ws', '').upper()}")}
        results.append((job, outcome["java"], outcome["rust"]))
        if step.get("sleep"):
            time.sleep(step["sleep"])
    return results


def expected_value(value, predicate):
    """Typed exact values or explicit JSON types; object fields are exhaustive."""
    if "value" in predicate:
        return json.dumps(value, sort_keys=True) == json.dumps(predicate["value"], sort_keys=True)
    types = {"null": type(None), "string": str, "integer": int, "number": (int, float),
             "boolean": bool, "array": list, "object": dict}
    if "type" not in predicate or type(value) not in (types[predicate["type"]] if isinstance(types[predicate["type"]], tuple) else (types[predicate["type"]],)):
        return False
    if "pattern" in predicate and re.fullmatch(predicate["pattern"], value) is None:
        return False
    if isinstance(value, dict) and "fields" in predicate:
        fields = predicate["fields"]
        return set(value) == set(fields) and all(expected_value(value[key], spec) for key, spec in fields.items())
    if isinstance(value, list) and "items" in predicate:
        return all(expected_value(item, predicate["items"]) for item in value)
    return True


def difference_matches(difference, predicate):
    evidence = difference[3] if len(difference) > 3 else {}
    for side, value in zip(("java", "rust"), difference[1:3]):
        spec = predicate.get(side)
        if spec is None:
            return False
        present = evidence.get(f"{side}_present", True)
        if present != spec.get("present", True):
            return False
        if present and not expected_value(value, spec):
            return False
    if difference[0].endswith("[len]"):
        for side in ("java", "rust"):
            extra = evidence.get(f"{side}_extra")
            specs = predicate.get(f"{side}_extra")
            if extra is None or specs is None or len(extra) != len(specs):
                return False
            if predicate.get("extra_order") == "unordered":
                remaining = list(specs)
                for row in extra:
                    match = next((index for index, spec in enumerate(remaining) if expected_value(row, spec)), None)
                    if match is None:
                        return False
                    remaining.pop(match)
            elif not all(expected_value(row, spec) for row, spec in zip(extra, specs)):
                return False
    return True


def allowed_data_paths(entry, rule):
    """Require complete evidence and a value/presence predicate for every path.

    Paths are exact by default. Only explicit subtree_paths authorize descendants.
    A [len] predicate also declares every extra row, never just its location.
    """
    if "allowed_paths" not in rule:
        return entry.get("category") != "DATA"
    differences = entry.get("data_diffs")
    if differences is None:
        return False
    for difference in differences:
        matched = False
        for path in rule["allowed_paths"]:
            suffix = r"(?:$|[.\[])" if path in rule.get("subtree_paths", []) else "$"
            pattern = re.escape(path).replace(r"\[\*\]", r"\[\d+\]") + suffix
            if re.match(pattern, difference[0]) and difference_matches(difference, rule.get("path_predicates", {}).get(path, {})):
                matched = True
                break
        if not matched:
            return False
    return True


def disposition(entry, registry):
    if entry.get("comparison_errors"):
        return "FAIL", "Journey response identity or contract validation failed"
    if entry["category"] == "TEXT_ONLY" and entry.get("data_diffs") != []:
        return "FAIL", "TEXT_ONLY requires complete evidence of zero data differences"
    for rule in registry.get("overrides", []):
        if rule["endpoint"] == entry["endpoint"] and rule.get("variant", entry["variant"]) == entry["variant"] \
                and rule.get("category", entry["category"]) == entry["category"] \
                and allowed_data_paths(entry, rule):
            return rule["disposition"], rule.get("note", "")
    default = registry.get("category_defaults", {}).get(entry["category"], CATEGORIES[entry["category"]])
    if entry["variant"] == "journey" and entry["category"] != "TEXT_ONLY":
        default = "FAIL"  # curated realistic flows: any behavioural difference is a finding
    return default, ""


def comparison_entry(job, java, rust, registry):
    """One production classification/disposition path, also exercised by regressions."""
    errors = {side: response["comparison_errors"] for side, response in (("java", java), ("rust", rust))
              if response.get("comparison_errors")}
    identity_diffs = java.get("identity_diffs")
    if identity_diffs is None:
        identities, domain = IdentityMap(), identity_domain(job["path"])
        identity_diffs = identities.compare(java.get("json"), rust.get("json"), domain=domain)
        java = {**java, "json": identities.canonical(java.get("json"), "java", domain=domain)}
        rust = {**rust, "json": identities.canonical(rust.get("json"), "rust", domain=domain)}
    else:
        java = {**java, "json": java.get("comparison_json", java.get("json"))}
        rust = {**rust, "json": rust.get("comparison_json", rust.get("json"))}
    if not errors and not identity_diffs and comparable(java) == comparable(rust):
        return None
    entry = {"endpoint": job["endpoint"], "variant": job["variant"], "who": job["who"], "path": job["path"],
             "category": "DATA" if errors or identity_diffs else category(java, rust), "java": brief(java), "rust": brief(rust)}
    if errors:
        entry["comparison_errors"] = errors
    if isinstance(java.get("json"), dict) and isinstance(rust.get("json"), dict):
        entry["data_diffs"] = envelope_data_diffs(java["json"], rust["json"]) + identity_diffs
        entry["first_data_diffs"] = entry["data_diffs"][:4]
    entry["disposition"], entry["note"] = disposition(entry, registry)
    return entry


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--java-url", default="http://127.0.0.1:18121")
    parser.add_argument("--rust-url", default="http://127.0.0.1:18122")
    parser.add_argument("--java-db", required=True)
    parser.add_argument("--rust-db", required=True)
    parser.add_argument("--mysql-container", default="mysql-project006")
    parser.add_argument("--inventory", default=str(ROOT / "docs/reviews/rust-migration/current-coverage.json"))
    parser.add_argument("--registry", default=str(pathlib.Path(__file__).with_name("dual-stack-sweep-registry.json")))
    parser.add_argument("--out", help="write the full difference report here")
    parser.add_argument("--no-probes", action="store_true", help="skip the id-probe (str/num) variants")
    parser.add_argument("--journeys", default=str(pathlib.Path(__file__).with_name("dual-stack-journeys.json")),
                        help="scripted write-then-read flows (default: dual-stack-journeys.json next to this script)")
    parser.add_argument("--no-journeys", action="store_true", help="skip the scripted journeys")
    parser.add_argument("--only-journeys", action="store_true", help="run only the scripted journeys (fast)")
    parser.add_argument("--timeout", type=float, default=25)
    args = parser.parse_args()

    registry = json.loads(pathlib.Path(args.registry).read_text()) if pathlib.Path(args.registry).exists() else {}
    inventory = json.loads(pathlib.Path(args.inventory).read_text())["endpoint_inventory"]
    db = Mysql(args.mysql_container, args.java_db, args.rust_db)
    tokens = {"admin": secrets.token_hex(24), "student": secrets.token_hex(24), "device": "sweep-device-" + secrets.token_hex(4)}
    headers = {"anon": {}, "admin": {"token": tokens["admin"], "deviceId": tokens["device"]},
               "student": {"token": tokens["student"], "deviceId": tokens["device"]}}
    main_jobs, session_jobs, probe_jobs = build_jobs(inventory, headers, not args.no_probes)

    def pair(job):
        java = http_case(args.java_url, job["method"], job["path"], job["body"], job["headers"], args.timeout)
        rust = http_case(args.rust_url, job["method"], job["path"], job["body"], job["headers"], args.timeout)
        return job, java, rust

    results = []
    journeys_path = pathlib.Path(args.journeys)
    if not args.no_journeys and journeys_path.exists():
        db.reset(tokens)
        results += run_journeys(json.loads(journeys_path.read_text()), args, headers, db)
    if not args.only_journeys:
        db.reset(tokens)
        results += [pair(job) for job in main_jobs]
        for job in session_jobs:  # each one invalidates the fixture session, so it gets a fresh state
            db.reset(tokens)
            results.append(pair(job))
        if probe_jobs:
            db.reset(tokens)
            results += [pair(job) for job in probe_jobs]
    db.reset(tokens)

    differences, counts = [], collections.Counter()
    for job, java, rust in results:
        entry = comparison_entry(job, java, rust, registry)
        if entry is None:
            counts["identical"] += 1
            continue
        differences.append(entry)
        counts[f"{entry['disposition']}:{entry['category']}"] += 1

    summary = {"requests": len(results), "identical": counts["identical"], "different": len(differences),
               "by_disposition": dict(collections.Counter(d["disposition"] for d in differences)),
               "by_category": dict(collections.Counter(d["category"] for d in differences))}
    report = {"schema": "rust-migration-authenticated-sweep/v1",
              "generated_at_utc": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
              "summary": summary, "differences": differences}
    if args.out:
        pathlib.Path(args.out).write_text(json.dumps(report, ensure_ascii=False, indent=1) + "\n")
    print(json.dumps(summary, ensure_ascii=False, indent=1))
    failures = [d for d in differences if d["disposition"] == "FAIL"]
    for d in failures:
        print(f"FAIL {d['category']} {d['endpoint']} [{d['who']}/{d['variant']}] java={d['java']} rust={d['rust']}", file=sys.stderr)
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
