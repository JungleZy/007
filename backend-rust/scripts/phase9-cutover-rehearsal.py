#!/usr/bin/env python3
"""Real, local single-domain HTTP cutover. No builds, pulls, retries or source DB writes."""
import argparse
import base64
import hashlib
from datetime import datetime
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
MYSQL_CONTAINER = 'mysql-project006'
SOURCE_DB = 'project006'
SAFE_DB = re.compile(r'rust_cutover_global_rule_[0-9a-f]{16}\Z')


class Failure(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise Failure(message)


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def command(args, *, input=None, timeout=30):
    result = subprocess.run(args, input=input, text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        raise Failure(f'{args[0]} exited {result.returncode}: {result.stderr.strip()[:1500]}')
    return result.stdout.strip()


def check_ports(ports):
    require(len(set(ports)) == len(ports), 'Java, Rust and gateway ports must be distinct')
    for port in ports:
        require(1024 <= port <= 65535 and port not in (18001, 18002, 18121, 18122),
                f'unsafe rehearsal port: {port}')
        with socket.socket() as listener:
            listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                listener.bind(('127.0.0.1', port))
                listener.listen(1)
            except OSError as error:
                raise Failure(f'loopback port {port} is occupied; no process was stopped') from error


def nginx_config(domain, owner, generation, ports, work):
    require(re.fullmatch(r'[A-Za-z][A-Za-z0-9]*', domain), 'unsafe route domain')
    require(owner in ('java', 'rust', 'frozen'), 'unknown route owner')
    require(re.fullmatch(r'[a-z0-9-]+', generation), 'unsafe generation')
    proxy = lambda port: f'proxy_pass http://127.0.0.1:{port};'
    selected = 'return 503;' if owner == 'frozen' else proxy(ports[owner])
    path = lambda name: json.dumps(str(work / name))
    selected = (f'set $cutover_owner {owner}; '
                f'if (-f {path("frozen")}) {{ set $cutover_owner frozen; return 503; }} ' + selected)
    return f'''worker_processes 1;
pid {path('nginx.pid')};
error_log {path('gateway-error.log')} error;
events {{ worker_connections 128; }}
http {{
    log_format evidence escape=json '{{"id":"$http_x_cutover_id","method":"$request_method","uri":"$uri","status":$status,"upstream":"$upstream_addr","upstream_status":"$upstream_status","generation":"{generation}","worker":"$pid"}}';
    access_log {path('evidence/access.jsonl')} evidence;
    client_max_body_size 11m;
    client_body_temp_path {path('client-body')};
    proxy_temp_path {path('proxy-temp')};
    fastcgi_temp_path {path('fastcgi-temp')};
    uwsgi_temp_path {path('uwsgi-temp')};
    scgi_temp_path {path('scgi-temp')};
    map $http_upgrade $connection_upgrade {{ default upgrade; '' close; }}
    server {{
        listen 127.0.0.1:{ports['gateway']};
        set $cutover_owner java;
        add_header X-Cutover-Owner $cutover_owner always;
        add_header X-Cutover-Generation {generation} always;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_next_upstream off;
        proxy_connect_timeout 5s;
        proxy_read_timeout 120s;
        proxy_send_timeout 120s;
        location = /api/{domain} {{ {selected} }}
        location ^~ /api/{domain}/ {{ {selected} }}
        location / {{ {proxy(ports['java'])} }}
    }}
}}
'''


def validate_access(requests, records, ports):
    by_id = {}
    for record in records:
        by_id.setdefault(record['id'], []).append(record)
    evidence = []
    for request in requests:
        matches = by_id.get(request['id'], [])
        require(len(matches) == 1, f"request {request['id']} needs exactly one access record, got {len(matches)}")
        record = matches[0]
        frozen = request['owner'] == 'frozen'
        upstream = f"127.0.0.1:{ports[request['owner']]}" if not frozen else None
        require(record['upstream'] in ('', '-') if frozen else record['upstream'] == upstream,
                f"wrong or retried upstream for {request['id']}: {record['upstream']}")
        for key in ('method', 'uri', 'status', 'generation'):
            require(record[key] == request[key], f"access {key} differs for {request['id']}")
        require(record['upstream_status'] in ('', '-') if frozen else record['upstream_status'] == str(request['status']),
                f"upstream status differs for {request['id']}")
        evidence.append(record)
    require(set(by_id) == {request['id'] for request in requests}, 'unexpected gateway requests in access log')
    return evidence


class Action:
    """One real operation, without a retry or an executor that can hang process exit."""
    def __init__(self, operation):
        self.done = threading.Event()
        self.value = self.error = None
        def run():
            try:
                self.value = operation()
            except BaseException as error:
                self.error = error
            finally:
                self.done.set()
        self.thread = threading.Thread(target=run, daemon=True)
        self.thread.start()

    def result(self, timeout):
        require(self.done.wait(timeout), 'operation did not finish before deadline')
        if self.error:
            raise self.error
        return self.value


class Mysql:
    def __init__(self, schema):
        require(SAFE_DB.fullmatch(schema), 'refusing unsafe rehearsal schema')
        self.schema = schema
        self.created = False

    def args(self, database=True):
        return ['docker', 'exec', '-i', '-e', 'MYSQL_PWD', MYSQL_CONTAINER, 'mysql',
                '-uroot', '--default-character-set=utf8mb4', '-N', '-B', '--raw',
                *([self.schema] if database else [])]

    def sql(self, statement, database=True):
        return command(self.args(database), input=statement, timeout=30)

    def create(self):
        dump = command(['docker', 'exec', '-e', 'MYSQL_PWD', MYSQL_CONTAINER, 'mysqldump',
                        '-uroot', '--no-data', '--skip-triggers', '--set-gtid-purged=OFF',
                        '--no-tablespaces', SOURCE_DB], timeout=60)
        require(dump and 'CREATE TABLE' in dump, 'source schema dump is empty')
        self.sql(f'CREATE DATABASE `{self.schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;', False)
        self.created = True
        self.sql(dump)
        tables = self.sql('SHOW TABLES;').splitlines()
        require(tables, 'target schema has no tables')
        for table in tables:
            require(re.fullmatch(r'[A-Za-z0-9_]+', table), 'unsafe source table identifier')
        counts = self.sql(' UNION ALL '.join(f'SELECT COUNT(*) FROM `{table}`' for table in tables))
        require(all(count == '0' for count in counts.splitlines()), 'fresh schema unexpectedly contains data')
        return {'source': SOURCE_DB, 'target': self.schema, 'tables': len(tables),
                'dump_sha256': hashlib.sha256(dump.encode()).hexdigest(), 'copied_data_rows': 0}

    def structure(self):
        return self.sql("SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION,COLUMN_TYPE,IS_NULLABLE,"
                        "IFNULL(COLUMN_DEFAULT,'<NULL>'),EXTRA FROM information_schema.COLUMNS "
                        "WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION;") + '\n' + self.sql(
                        "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,INDEX_TYPE "
                        "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
                        "ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;")

    def drop(self):
        if self.created:
            self.sql(f'DROP DATABASE `{self.schema}`;', False)
            self.created = False
            return 'dropped run-created schema'
        return 'not created; untouched'


class RowLock:
    def __init__(self, database):
        self.database, self.connection_id = database, None
        self.process = subprocess.Popen(database.args() + ['--unbuffered'], stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    def acquire(self, row_id):
        require(type(row_id) is int and row_id > 0, 'unsafe lock row id')
        self.process.stdin.write('SELECT CONNECTION_ID();\n')
        self.process.stdin.flush()
        self.connection_id = int(Action(lambda: self.process.stdout.readline().strip()).result(10))
        reader = Action(lambda: self.process.stdout.readline().strip())
        self.process.stdin.write(f'START TRANSACTION; SELECT id FROM t_post_train_global_rule WHERE id={row_id} FOR UPDATE;\n')
        self.process.stdin.flush()
        require(reader.result(10) == str(row_id), 'row lock did not acquire selected row')

    def release(self):
        if self.process.poll() is None:
            try:
                self.process.stdin.write('ROLLBACK;\n')
                self.process.stdin.flush()
                self.process.communicate(timeout=10)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                if self.connection_id is not None:
                    self.database.sql(f'KILL CONNECTION {self.connection_id};')
                self.process.kill()
                self.process.communicate(timeout=10)
                raise Failure('lock client required forced cleanup (not a graceful writer drain)')
            require(self.process.returncode == 0, 'row lock session did not close cleanly')
        return 'independent row-lock transaction rolled back and session closed'


class Rehearsal:
    def __init__(self, args):
        self.args, self.domain = args, 'postTrainGlobalRule'
        self.run_id = secrets.token_hex(8)
        self.db = Mysql(f'rust_cutover_global_rule_{self.run_id}')
        self.work = Path(tempfile.mkdtemp(prefix='rust-cutover-'))
        self.config_dir, self.evidence_dir = self.work / 'config', self.work / 'evidence'
        self.config_dir.mkdir()
        self.evidence_dir.mkdir()
        self.ports = {'java': args.java_port, 'rust': args.rust_port, 'gateway': args.gateway_port}
        self.base = f"http://127.0.0.1:{args.gateway_port}"
        self.processes, self.log_handles, self.actions = {}, [], []
        self.lock = None
        self.stop = threading.Event()
        self.generations, self.requests = {}, []
        self.generation = self.owner = None
        password = os.environ.get('MYSQL_PWD', '')
        self.secrets = [password, urllib.parse.quote(password, safe='')]
        self.report = {'status': 'FAIL', 'run_id': self.run_id, 'domain': f'/api/{self.domain}',
                       'gateway_url': self.base, 'stages': [], 'requests': self.requests,
                       'transitions': [], 'cleanup': {}, 'limitations': [
                           'Local synthetic HTTP through real Nginx, not browser/Electron/production proof.',
                           'No production traffic, service restart recovery, or cross-host network failure proof.',
                           'Draining whole Nginx generations is conservative: unrelated long-lived Java WS can block it; timeout stays frozen.',
                           'WS fallback is checked and closed separately, not seamless cutover with active production WS.',
                           'No automatic retry or fallback; timeouts abort before admitting a second owner.']}

    def cancelled(self):
        require(not self.stop.is_set(), 'rehearsal interrupted; refusing further ownership changes')

    def redact(self, text):
        for secret in sorted(filter(None, self.secrets), key=len, reverse=True):
            text = text.replace(secret, '[REDACTED]')
        return text

    def prerequisites(self):
        require(sys.platform.startswith('linux'), 'Linux process identity inspection is required')
        require(os.geteuid() != 0, 'run the native gateway as an unprivileged user')
        require(shutil.which('docker'), 'docker is required')
        require(os.environ.get('MYSQL_PWD'), 'MYSQL_PWD is required; never pass credentials as arguments')
        check_ports(list(self.ports.values()))
        for path in (self.args.rust_binary, self.args.java_home / 'bin/java', self.args.java_jar, self.args.nginx_binary):
            require(path.is_file(), f'required verified artifact is absent: {path}')
        require(os.access(self.args.rust_binary, os.X_OK), 'Rust artifact is not executable')
        require(os.access(self.args.nginx_binary, os.X_OK), 'Nginx artifact is not executable')
        version = subprocess.run([str(self.args.nginx_binary), '-v'], text=True, capture_output=True, timeout=10)
        require(version.returncode == 0, 'Nginx version check failed')
        self.report['nginx_version'] = version.stderr.strip()
        self.report['source_revision'] = command(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'])
        app_root = self.args.java_jar.parent
        java_files = [{'path': str(path.relative_to(app_root)), 'sha256': sha256_file(path)}
                      for path in sorted(app_root.rglob('*')) if path.is_file()]
        require(java_files, 'Java fast-jar runtime directory is empty')
        self.report['artifacts'] = {
            'nginx': {'path': str(self.args.nginx_binary), 'sha256': sha256_file(self.args.nginx_binary)},
            'rust': {'path': str(self.args.rust_binary), 'sha256': sha256_file(self.args.rust_binary)},
            'java_fast_jar': {'path': str(app_root), 'files': java_files, 'manifest_sha256': fingerprint(java_files)},
            'java_launcher_sha256': sha256_file(self.args.java_home / 'bin/java'),
            'runner_sha256': sha256_file(__file__),
            'wrapper_sha256': sha256_file(Path(__file__).with_suffix('.sh'))}
        self.cancelled()

    def start_process(self, owner, argv, environment):
        handle = (self.work / f'{owner}.log').open('w')
        self.log_handles.append(handle)
        process = subprocess.Popen(argv, cwd=self.work, env=environment, stdout=handle, stderr=subprocess.STDOUT,
                                   start_new_session=(owner == 'gateway'))
        self.processes[owner] = process
        self.report.setdefault('processes', {})[owner] = {'pid': process.pid, 'port': self.ports[owner],
                                                        'bind': '127.0.0.1'}
        return process

    def start_services(self):
        password = os.environ['MYSQL_PWD']
        environment = {key: value for key, value in os.environ.items()
                       if not key.startswith(('QUARKUS_', 'RUST_', 'DB_', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'))}
        environment.update(DB_USER='root', DB_PASSWORD=password, QUARKUS_PROFILE='prod',
                           QUARKUS_HTTP_PORT=str(self.ports['java']), QUARKUS_HTTP_HOST='127.0.0.1',
                           QUARKUS_DATASOURCE_JDBC_URL=f'jdbc:mysql://127.0.0.1:3306/{self.db.schema}?useSSL=false&allowPublicKeyRetrieval=true',
                           QUARKUS_HIBERNATE_ORM_DATABASE_GENERATION='validate', QUARKUS_LOG_FILE_ENABLE='false')
        java = self.start_process('java', [str(self.args.java_home / 'bin/java'), '-Duser.timezone=UTC',
                                           '-jar', str(self.args.java_jar)], environment)
        self.wait_service('java', java)
        environment = {key: value for key, value in os.environ.items() if not key.startswith(('RUST_', 'DATABASE_URL'))}
        environment.update(RUST_BACKEND_ADDR=f"127.0.0.1:{self.ports['rust']}",
                           RUST_BACKEND_DATABASE_URL=f'mysql://root:{urllib.parse.quote(password, safe="")}@127.0.0.1:3306/{self.db.schema}',
                           RUST_BACKEND_DB_TIMEOUT_SECS='30')
        environment.update(self.rust_features())
        rust = self.start_process('rust', [str(self.args.rust_binary)], environment)
        self.wait_service('rust', rust)

    def raw_http(self, port, path, headers, body, method='POST', timeout=90):
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
        request = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=data, method=method,
                                         headers={**headers, 'Content-Type': 'application/json', 'Connection': 'close'})
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        try:
            response = opener.open(request, timeout=timeout)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            try:
                payload = json.loads(raw)
            except ValueError:
                payload = None
            return response.status, payload, response.headers

    def wait_service(self, owner, process):
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            self.cancelled()
            require(process.poll() is None, f'{owner} exited before readiness; see redacted runtime log')
            try:
                if owner == 'rust':
                    status, _, _ = self.raw_http(self.ports[owner], '/ready', {}, None, 'GET', timeout=2)
                    if status == 200:
                        return
                else:
                    status, body, _ = self.raw_http(self.ports[owner], '/api/user/getUsersByToken', {}, None, timeout=2)
                    if status == 200 and type(body) is dict and body.get('code') == 203:
                        return
            except (OSError, urllib.error.URLError):
                pass
            self.stop.wait(0.2)
        raise Failure(f'{owner} readiness timed out')

    def config(self, owner, generation):
        contents = nginx_config(self.domain, owner, generation, self.ports, self.work)
        target = self.config_dir / 'nginx.conf'
        temporary = target.with_suffix('.next')
        temporary.write_text(contents, encoding='utf-8')
        temporary.replace(target)
        self.generations[generation] = owner
        self.report.setdefault('configurations', []).append(
            {'generation': generation, 'owner': owner, 'sha256': hashlib.sha256(contents.encode()).hexdigest()})

    def nginx_command(self, *arguments):
        return [str(self.args.nginx_binary), '-p', str(self.work) + '/',
                '-c', str(self.config_dir / 'nginx.conf'), '-e', str(self.work / 'gateway-error.log'), *arguments]

    def start_gateway(self):
        self.config('java', 'baseline-java')
        command(self.nginx_command('-t'))
        gateway = self.start_process('gateway', self.nginx_command('-g', 'daemon off;'), os.environ.copy())
        self.await_generation('baseline-java')
        self.owner, self.generation = 'java', 'baseline-java'
        self.report['gateway'] = {'pid': gateway.pid, 'workers': sorted(self.workers())}


    def workers(self):
        gateway = self.processes.get('gateway')
        require(gateway is not None and gateway.poll() is None, 'Nginx master is not running')
        identities = set()
        for stat in Path('/proc').glob('[0-9]*/stat'):
            try:
                prefix, separator, suffix = stat.read_text().rpartition(') ')
            except (FileNotFoundError, ProcessLookupError, PermissionError):
                continue
            if separator and prefix.endswith('(nginx'):
                fields = suffix.split()
                if fields[1] == str(gateway.pid):
                    identities.add(f"{prefix.split()[0]}:{fields[19]}")
        return identities


    def reload(self, owner, generation):
        self.cancelled()
        self.config(owner, generation)
        command(self.nginx_command('-t'))
        command(self.nginx_command('-s', 'reload'))
        self.await_generation(generation)

    def http(self, path, body=None, who='admin', owner=None, label='', method='POST', expected_status=200, timeout=90):
        correlation = f'{self.run_id}-{secrets.token_hex(6)}'
        headers = {**self.headers[who], 'X-Cutover-Id': correlation}
        status, payload, response_headers = self.raw_http(self.ports['gateway'], path, headers, body, method, timeout=timeout)
        generation = response_headers.get('X-Cutover-Generation', '')
        actual_owner = self.generations.get(generation)
        require(actual_owner is not None, 'response lacks a known gateway generation')
        route_owner = response_headers.get('X-Cutover-Owner', '')
        selected = path == f'/api/{self.domain}' or path.startswith(f'/api/{self.domain}/')
        require(route_owner in ('java', 'rust', 'frozen'), 'response lacks gateway admission owner')
        if route_owner == 'frozen':
            require(selected and status == 503, 'frozen admission must reject only the selected domain')
        else:
            require(route_owner == (actual_owner if selected else 'java'), 'admission owner differs from route generation')
        self.requests.append({'id': correlation, 'method': method, 'uri': path.split('?')[0], 'status': status,
                              'generation': generation, 'owner': route_owner, 'label': label})
        if owner is not None:
            require(route_owner == owner, f'{label}: expected {owner}, got {route_owner}')
        if expected_status is not None:
            require(status == expected_status, f'{label}: expected HTTP {expected_status}, got {status}')
        return payload, generation

    def await_generation(self, generation):
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            self.cancelled()
            try:
                _, observed = self.http(self.read_path(), self.read_body(), who='ordinary',
                                        label='generation-observation', expected_status=None, timeout=2)
                if observed == generation:
                    return
            except (OSError, urllib.error.URLError):
                pass
            self.stop.wait(0.05)
        raise Failure(f'gateway did not serve generation {generation}')

    def drain(self, workers, transition):
        deadline = time.monotonic() + self.args.drain_timeout
        while workers & self.workers():
            self.cancelled()
            if time.monotonic() >= deadline:
                transition['status'] = 'FAIL_CLOSED_DRAIN_TIMEOUT'
                raise Failure('old Nginx workers did not drain; frozen gateway retained, next owner NOT published')
            self.stop.wait(0.05)

    def switch(self, new_owner, label, frozen):
        old_workers = self.workers()
        require(old_workers, 'cannot identify old Nginx worker generation')
        transition = {'from': self.owner, 'to': new_owner, 'label': label,
                      'old_workers': sorted(old_workers), 'status': 'FREEZING'}
        self.report['transitions'].append(transition)
        # A shared gate also fences old workers still accepting during reload.
        # Observing one response from a new generation is not an admission barrier.
        (self.work / 'frozen').touch()
        self.reload('frozen', f'{label}-frozen')
        transition['freeze_observed_ns'] = time.monotonic_ns()
        transition['status'] = 'DRAINING'
        frozen.set()
        self.drain(old_workers, transition)
        transition['drained_ns'] = time.monotonic_ns()
        self.cancelled()
        frozen_workers = self.workers()
        self.reload(new_owner, f'{label}-{new_owner}')
        self.drain(frozen_workers, transition)
        self.cancelled()
        (self.work / 'frozen').unlink()
        self.owner, self.generation = new_owner, f'{label}-{new_owner}'
        transition.update(status='COMPLETED', admitted_ns=time.monotonic_ns(), new_workers=sorted(self.workers()))
        return transition

    def action(self, operation):
        action = Action(operation)
        self.actions.append(action)
        return action

    def rust_features(self):
        return {'RUST_PHASE6_RADIOTELEPHONE_ENABLED': 'true'}

    def read_path(self):
        return f'/api/{self.domain}/findByType'

    def read_body(self):
        return {'type': 2}

    def seed_auth(self):
        admin, ordinary, device, invalid = (secrets.token_hex(24) for _ in range(4))
        self.secrets.extend((admin, ordinary, device, invalid))
        digest = lambda value: hashlib.sha256(value.encode()).hexdigest()
        self.db.sql("INSERT INTO t_user(id,user_account,user_name,status,device_id,token) VALUES "
                    f"('cutover-admin','cutover-admin','Cutover Admin',0,'{device}','{digest(admin)}'),"
                    f"('cutover-ordinary','cutover-ordinary','Cutover Ordinary',0,'{device}','{digest(ordinary)}');"
                    "INSERT INTO t_role(id,title,is_admin,is_default) VALUES "
                    "('cutover-admin-role','Cutover Admin',0,1),('cutover-ordinary-role','Cutover Ordinary',1,1);"
                    "INSERT INTO t_user_role(id,user_id,role_id) VALUES "
                    "('cutover-admin-m','cutover-admin','cutover-admin-role'),"
                    "('cutover-ordinary-m','cutover-ordinary','cutover-ordinary-role');")
        self.headers = {'admin': {'token': admin, 'deviceId': device},
                        'ordinary': {'token': ordinary, 'deviceId': device}, 'anonymous': {},
                        'missing-device': {'token': admin}, 'invalid': {'token': invalid, 'deviceId': device}}
        self.expected = {}
        self.auth_references = self.db.sql(
            "SELECT u.id,u.status,r.is_admin,ur.user_id,ur.role_id FROM t_user u "
            "JOIN t_user_role ur ON u.id=ur.user_id JOIN t_role r ON r.id=ur.role_id ORDER BY u.id;")
        require(self.auth_references ==
                'cutover-admin\t0\t0\tcutover-admin\tcutover-admin-role\n'
                'cutover-ordinary\t0\t1\tcutover-ordinary\tcutover-ordinary-role', 'auth fixture references differ')
        self.report['auth_fixture'] = {'admin_is_admin': 0, 'ordinary_is_admin': 1,
                                       'reference_rows': self.auth_references.splitlines(),
                                       'credentials': 'ephemeral header tokens; only SHA256 digests stored in DB'}

    def envelope(self, payload, code=200):
        messages = {200: 'ok', 203: 'token不能为空', 204: '设备标识不能为空', 206: '账号登录凭证异常', 207: '无权限'}
        require(type(payload) is dict and set(payload) == {'code', 'data', 'message', 'description'},
                'unexpected response envelope shape')
        require(type(payload['code']) is int and payload['code'] == code and
                payload['message'] == messages[code] and payload['description'] == '',
                f'expected unchanged business code/message {code}; got {payload}')
        if code != 200:
            require(payload['data'] is None, f'auth code {code} must carry null data')
        return payload['data']

    def rows(self):
        result = self.db.sql("SELECT JSON_OBJECT('id',id,'type',type,'level',level,'accuracy',accuracy,"
                             "'description',description,'createTime',DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s')) "
                             "FROM t_post_train_global_rule ORDER BY id;")
        return [json.loads(line) for line in result.splitlines()]

    def inventory(self, label):
        rows = self.rows()
        expected = sorted(self.expected.values(), key=lambda row: row['id'])
        self.report.setdefault('sql_observations', []).append({'label': label, 'rows': rows,
                                                               'expected': expected, 'inventory_sha256': fingerprint(rows)})
        require(rows == expected, f'{label}: exact SQL row inventory differs')
        if hasattr(self, 'sentinel'):
            require(self.expected.get(self.sentinel['id']) == self.sentinel, f'{label}: sentinel was modified')
        references = self.db.sql("SELECT u.id,u.status,r.is_admin,ur.user_id,ur.role_id FROM t_user u "
                                 "JOIN t_user_role ur ON u.id=ur.user_id JOIN t_role r ON r.id=ur.role_id ORDER BY u.id;")
        require(references == self.auth_references, f'{label}: auth fixture references changed')
        return rows

    def accept_saved(self, inputs, payload, owner):
        data = self.envelope(payload)
        require(type(data) is list and len(data) == len(inputs), 'save returned wrong number of rows')
        accepted = []
        for supplied, row in zip(inputs, data):
            require(type(row) is dict and set(row) == {'id', 'type', 'level', 'accuracy', 'description', 'createTime'},
                    'save row fields differ from frontend contract')
            require(type(row['id']) is int and row['id'] > 0, 'save returned invalid primary key')
            require(type(row['createTime']) is str and re.fullmatch(r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}', row['createTime']),
                    'save returned invalid createTime')
            for key in ('type', 'level', 'accuracy', 'description'):
                require(row[key] == supplied[key] and type(row[key]) is type(supplied[key]), f'save did not preserve {key}')
            if 'id' in supplied:
                require(row['id'] == supplied['id'], 'update changed primary key')
                require(row['createTime'] == self.expected[row['id']]['createTime'], 'update changed creation time')
            else:
                require(row['id'] not in self.expected, 'create reused an existing primary key')
                # Java formats the pre-flush LocalDateTime to whole seconds, while MySQL
                # rounds its fractional part into this DATETIME(0) column. Later reads
                # and updates must preserve the exact persisted value, not the wire floor.
                persisted = next((saved for saved in self.rows() if saved['id'] == row['id']), None)
                require(persisted is not None, 'created primary key was not persisted')
                require({key: value for key, value in persisted.items() if key != 'createTime'} ==
                        {key: value for key, value in row.items() if key != 'createTime'},
                        'created row differs from saved response')
                delta = (datetime.fromisoformat(persisted['createTime']) -
                         datetime.fromisoformat(row['createTime'])).total_seconds()
                require(delta in ((0, 1) if owner == 'java' else (0,)),
                        'created timestamp exceeds the observed DATETIME(0) rounding boundary')
                self.report.setdefault('creation_clocks', []).append(
                    {'id': row['id'], 'owner': owner, 'response': row['createTime'],
                     'persisted': persisted['createTime'], 'rounding_seconds': delta})
                row = persisted
            self.expected[row['id']] = row
            accepted.append(row)
        return accepted

    def save(self, inputs, label):
        payload, _ = self.http(f'/api/{self.domain}/addRule', inputs, owner=self.owner, label=label)
        rows = self.accept_saved(inputs, payload, self.owner)
        self.inventory(label)
        return rows

    def read_inventory(self, label, shadow=False):
        for kind in sorted({row['type'] for row in self.expected.values()} | {2}):
            if shadow:
                status, payload, _ = self.raw_http(self.ports['rust'], self.read_path(), self.headers['ordinary'], {'type': kind})
                require(status == 200, 'Rust shadow read HTTP status differs')
            else:
                payload, _ = self.http(self.read_path(), {'type': kind}, who='ordinary', owner=self.owner, label=label)
            data = self.envelope(payload)
            require(type(data) is list, f'{label}: read data is not an array')
            expected = sorted((row for row in self.expected.values() if row['type'] == kind), key=lambda row: row['id'])
            require(sorted(data, key=lambda row: row['id']) == expected, f'{label}: read lost or changed rows for type {kind}')
        self.inventory(label)
        if shadow:
            self.report['shadow_read'] = {'direct_loopback': f"http://127.0.0.1:{self.ports['rust']}",
                                          'writes': 0, 'rows': sorted(self.expected.values(), key=lambda row: row['id'])}

    def fallback_probe(self, label):
        payload, _ = self.http('/api/user/getUsersByToken', who='anonymous', owner='java', label=label)
        self.envelope(payload, 203)
        self.websocket_probe(label)

    def websocket_probe(self, label):
        # The existing public Java /status endpoint has no auth or domain writes.
        correlation = f'{self.run_id}-{secrets.token_hex(6)}'
        key = base64.b64encode(secrets.token_bytes(16)).decode()
        with socket.create_connection(('127.0.0.1', self.ports['gateway']), timeout=5) as connection:
            connection.sendall((f'GET /status HTTP/1.1\r\nHost: 127.0.0.1:{self.ports["gateway"]}\r\n'
                                'Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\n'
                                f'Sec-WebSocket-Key: {key}\r\nX-Cutover-Id: {correlation}\r\n\r\n').encode())
            with connection.makefile('rb') as stream:
                require(stream.readline().split()[1:2] == [b'101'], 'Java WS upgrade was not passed through')
                headers = {}
                for _ in range(40):
                    line = stream.readline()
                    if line == b'\r\n':
                        break
                    require(line and b':' in line, 'invalid WS response header')
                    name, value = line.decode().split(':', 1)
                    headers[name.lower()] = value.strip()
                expected_accept = base64.b64encode(hashlib.sha1((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
                require(headers.get('sec-websocket-accept') == expected_accept, 'WS handshake accept differs')
                generation = headers.get('x-cutover-generation')
                require(generation in self.generations, 'WS response lacks gateway generation')
                self.requests.append({'id': correlation, 'method': 'GET', 'uri': '/status', 'status': 101,
                                      'generation': generation, 'owner': 'java', 'label': f'{label}-ws'})
                def send_frame(opcode, payload):
                    mask = secrets.token_bytes(4)
                    connection.sendall(bytes([0x80 | opcode, 0x80 | len(payload)]) + mask +
                                       bytes(byte ^ mask[index % 4] for index, byte in enumerate(payload)))
                def receive_frame():
                    header = stream.read(2)
                    require(len(header) == 2 and header[1] < 126, 'invalid short WS response frame')
                    payload = stream.read(header[1])
                    require(len(payload) == header[1], 'truncated WS frame')
                    return header[0] & 15, payload
                send_frame(1, b'hello')
                require(receive_frame() == (1, b'pong'), 'Java WS text echo did not survive gateway')
                send_frame(8, b'\x03\xe8')
                require(receive_frame()[0] == 8, 'Java WS did not acknowledge close')
        self.report.setdefault('websocket_fallback', []).append({'label': label, 'path': '/status',
                                                               'upgrade_status': 101, 'reply': 'pong', 'closed': True})

    def stage(self, label):
        self.cancelled()
        before = self.inventory(f'{label}-before-auth')
        rejected = [{'id': self.selected, 'type': 2, 'level': '拒绝更新', 'accuracy': '0~0', 'description': 'must-not-persist'}]
        cases = [('anonymous', 203), ('missing-device', 204), ('invalid', 206), ('ordinary', 207)]
        for who, code in cases:
            for route, body in [('addRule', rejected), ('deleteById', {'id': self.selected, 'type': 2})]:
                payload, _ = self.http(f'/api/{self.domain}/{route}', body, who=who, owner=self.owner,
                                       label=f'{label}-{route}-{code}')
                self.envelope(payload, code)
                require(self.rows() == before, f'{label}: rejected {route}/{code} mutated DB')
        self.read_inventory(label)
        self.fallback_probe(f'{label}-java-fallback')
        self.report['stages'].append({'label': label, 'owner': self.owner, 'auth_codes': [203, 204, 206, 207],
                                       'ordinary_read': 'allowed', 'rejected_writes': 'unchanged SQL inventory',
                                       'rows': before})

    def waiting_writer(self):
        return self.db.sql(
            "SELECT JSON_OBJECT('blocking_connection',bt.PROCESSLIST_ID,'waiting_connection',rt.PROCESSLIST_ID,"
            "'waiting_thread',w.REQUESTING_THREAD_ID,'table',l.OBJECT_NAME,'index',l.INDEX_NAME,"
            "'lock_type',l.LOCK_TYPE,'lock_mode',l.LOCK_MODE,'lock_data',l.LOCK_DATA) "
            "FROM performance_schema.data_lock_waits w "
            "JOIN performance_schema.threads bt ON bt.THREAD_ID=w.BLOCKING_THREAD_ID "
            "JOIN performance_schema.threads rt ON rt.THREAD_ID=w.REQUESTING_THREAD_ID "
            "JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND l.ENGINE=w.ENGINE "
            f"WHERE bt.PROCESSLIST_ID={self.lock.connection_id} AND l.OBJECT_SCHEMA=DATABASE() "
            "AND l.OBJECT_NAME='t_post_train_global_rule';")

    def locked_switch(self, new_owner, label):
        old_owner = self.owner
        before = self.inventory(f'{label}-before-lock')
        updated = {key: value for key, value in self.expected[self.selected].items() if key != 'createTime'}
        updated['description'] = f'{label}-old-writer-committed'
        marker = {'type': 0, 'level': f'排空-{label}', 'accuracy': '90~100', 'description': f'{label}-exactly-once'}
        inputs = [updated, marker]
        self.lock = RowLock(self.db)
        self.lock.acquire(self.selected)
        writer = self.action(lambda: self.http(f'/api/{self.domain}/addRule', inputs, owner=old_owner,
                                               label=f'{label}-in-flight-write')[0])
        deadline = time.monotonic() + 10
        waiting = ''
        while time.monotonic() < deadline:
            self.cancelled()
            require(not writer.done.is_set(), 'owner write completed instead of waiting on independent row lock')
            waiting = self.waiting_writer()
            if waiting:
                break
            self.stop.wait(0.05)
        require(waiting, 'no performance_schema evidence of the in-flight row-lock wait')
        observations = [json.loads(line) for line in waiting.splitlines()]
        require(all(row['lock_data'] == str(self.selected) and row['index'] == 'PRIMARY' for row in observations),
                'in-flight write is not waiting for selected primary-key record')
        frozen = threading.Event()
        switching = self.action(lambda: self.switch(new_owner, label, frozen))
        deadline = time.monotonic() + 15
        while not frozen.wait(0.05):
            self.cancelled()
            if switching.done.is_set():
                switching.result(0)
            require(time.monotonic() < deadline, 'freeze was not observed before deadline')
        for route, body, method in [('addRule', inputs, 'POST'), ('deleteById', {'id': self.selected, 'type': 2}, 'POST'),
                                    ('findByType', {'type': 2}, 'POST'), ('', None, 'GET')]:
            path = f'/api/{self.domain}' + (f'/{route}' if route else '')
            self.http(path, body, owner='frozen', label=f'{label}-frozen-{route or "root"}', method=method, expected_status=503)
            require(self.rows() == before, 'frozen request or blocked old writer changed SQL inventory')
        self.fallback_probe(f'{label}-frozen-java-fallback')
        require(not switching.done.wait(0.5), 'cutover finished while the old writer was still locked')
        require(not writer.done.is_set() and self.waiting_writer(), 'old write no longer blocked before explicit release')
        transition = self.report['transitions'][-1]
        require(transition['status'] == 'DRAINING', 'next owner admitted before lock release')
        transition['blocked_proof'] = {'mysql_waits': observations, 'inventory_unchanged': True,
                                       'switch_incomplete_while_locked': True, 'old_request_incomplete': True,
                                       'observed_ns': time.monotonic_ns()}
        transition['release_ns'] = time.monotonic_ns()
        self.lock.release()
        self.lock = None
        saved = self.accept_saved(inputs, writer.result(self.args.drain_timeout + 15), old_owner)
        transition = switching.result(self.args.drain_timeout + 15)
        require(transition['release_ns'] <= transition['drained_ns'] <= transition['admitted_ns'],
                'next owner publication occurred before old writer drained')
        self.inventory(f'{label}-after-drain')
        require(sum(row['description'] == marker['description'] for row in self.rows()) == 1,
                'old batch write did not commit exactly one marker row')
        transition['committed_marker'] = saved[1]
        transition['single_commit_evidence'] = 'one upstream request, retries disabled, exactly one inserted batch marker'
        self.read_inventory(f'{label}-new-owner-reads-old-commit')

    def run(self):
        self.prerequisites()
        self.report['schema'] = self.db.create()
        structure = self.db.structure()
        self.report['schema']['structure_sha256'] = hashlib.sha256(structure.encode()).hexdigest()
        self.seed_auth()
        self.start_services()
        self.start_gateway()
        selected = {'type': 2, 'level': '合成主规则', 'accuracy': '90~100', 'description': 'java-baseline'}
        sentinel = {'type': 3, 'level': '无关哨兵', 'accuracy': '80~89', 'description': 'must-remain-unchanged'}
        self.selected = self.save([selected], 'java-baseline-create')[0]['id']
        self.sentinel = self.save([sentinel], 'java-sentinel-create')[0].copy()
        self.stage('java-baseline')
        self.read_inventory('rust-shadow-read', shadow=True)
        self.locked_switch('rust', 'cutover')
        selected.update(id=self.selected, level='Rust主规则', accuracy='91~100', description='rust-primary')
        self.save([selected, {'type': 4, 'level': 'Rust新增', 'accuracy': '70~79', 'description': 'created-by-rust'}],
                  'rust-update-and-create')
        self.stage('rust-primary')
        self.locked_switch('java', 'rollback')
        selected.update(level='Java回退规则', accuracy='92~100', description='java-rollback')
        self.save([selected, {'type': 1, 'level': 'Java回退新增', 'accuracy': '60~69', 'description': 'created-by-java-rollback'}],
                  'java-rollback-update-and-create')
        self.stage('java-rollback')
        self.locked_switch('rust', 'recutover')
        self.stage('rust-recutover')
        payload, _ = self.http(f'/api/{self.domain}/deleteById', {'id': self.selected, 'type': 2},
                               owner='rust', label='rust-final-delete')
        require(self.envelope(payload) is None, 'delete must return null data')
        del self.expected[self.selected]
        self.read_inventory('rust-final-deleted')
        require(self.db.structure() == structure, 'schema/index definitions changed during rehearsal')
        self.report['schema']['structure_unchanged'] = True
        self.report['selected_primary_key'] = self.selected
        self.report['sentinel'] = self.sentinel
        self.report['final_rows'] = self.rows()
        self.access_evidence()
        self.cancelled()
        self.report['status'] = 'PASS_REAL_LOCAL_GATEWAY'

    def access_evidence(self):
        deadline = time.monotonic() + 5
        while True:
            records = [json.loads(line) for line in (self.evidence_dir / 'access.jsonl').read_text().splitlines()]
            if len(records) >= len(self.requests) or time.monotonic() >= deadline:
                break
            self.stop.wait(0.05)
        self.report['access_evidence'] = validate_access(self.requests, records, self.ports)
        self.report['access_sha256'] = sha256_file(self.evidence_dir / 'access.jsonl')

    def cleanup(self):
        self.stop.set()
        cleanup = self.report['cleanup']
        def attempt(name, operation):
            try:
                cleanup[name] = operation()
            except Exception as error:
                cleanup[name] = {'error': self.redact(str(error))}
        # Stop admission before freeing a held DB lock, then reap only handles owned by this run.
        if 'gateway' not in self.processes:
            cleanup['gateway'] = 'not created; untouched'
        for owner in sorted(self.processes, key=lambda owner: owner != 'gateway'):
            if owner != 'gateway' and self.lock:
                attempt('row_lock', self.lock.release)
                self.lock = None
            process = self.processes[owner]
            def stop_process(process=process):
                forced = False
                if process.poll() is None:
                    if owner == 'gateway':
                        os.killpg(process.pid, signal.SIGTERM)
                    else:
                        process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        forced = True
                        if owner == 'gateway':
                            os.killpg(process.pid, signal.SIGKILL)
                        else:
                            process.kill()
                        process.wait(timeout=10)
                return {'pid': process.pid, 'returncode': process.returncode, 'forced_cleanup': forced}
            attempt(owner, stop_process)
        if self.lock:
            attempt('row_lock', self.lock.release)
        unfinished = sum(not action.done.wait(5) for action in self.actions)
        cleanup['actions'] = {'error': f'{unfinished} operations failed to stop'} if unfinished else 'all operations stopped'
        def capture_access():
            access = self.evidence_dir / 'access.jsonl'
            if access.exists():
                self.report['raw_access_log'] = access.read_text(errors='replace').splitlines()
                self.report['access_sha256'] = sha256_file(access)
            return 'captured available run-owned access log'
        attempt('access_evidence', capture_access)
        attempt('schema', self.db.drop)
        for index, handle in enumerate(self.log_handles):
            attempt(f'log_handle_{index}', lambda handle=handle: (handle.close(), 'closed')[1])
        def capture_runtime_logs():
            gateway_log = self.work / 'gateway-error.log'
            if gateway_log.exists():
                self.report['gateway_log'] = self.redact(gateway_log.read_text(errors='replace'))
            self.report['runtime_logs'] = {}
            for owner in self.processes:
                path = self.work / f'{owner}.log'
                text = self.redact(path.read_text(errors='replace'))
                self.report['runtime_logs'][owner] = {'redacted_sha256': hashlib.sha256(text.encode()).hexdigest(),
                                                     'last_lines': text.splitlines()[-35:]}
            return 'captured redacted run-owned logs'
        attempt('runtime_logs', capture_runtime_logs)
        attempt('workspace', lambda: (shutil.rmtree(self.work), 'removed run-created workspace')[1])
        if any(isinstance(value, dict) and 'error' in value for value in cleanup.values()):
            self.report['status'] = 'FAIL'


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        return None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('rust_binary', nargs='?', type=Path, default=ROOT / 'backend-rust/target/debug/rust-backend')
    parser.add_argument('--java-jar', type=Path, default=ROOT / 'backend/target/quarkus-app/quarkus-run.jar')
    parser.add_argument('--java-home', type=Path, default=Path.home() / '.local/opt/jdk21')
    parser.add_argument('--nginx-binary', type=Path, default=Path(shutil.which('nginx') or '/usr/sbin/nginx'),
                        help='verified native Nginx executable, sharing the Java/Rust network namespace')
    parser.add_argument('--java-port', type=int, default=18141)
    parser.add_argument('--rust-port', type=int, default=18142)
    parser.add_argument('--gateway-port', type=int, default=18140)
    parser.add_argument('--drain-timeout', type=float, default=20,
                        help='fail closed after this many seconds; must be between 1 and 30')
    parser.add_argument('--out', type=Path, help='JSON report (default: unique file under ~/.cache/rust-cutover)')
    args = parser.parse_args(argv)
    if not 1 <= args.drain_timeout <= 30:
        parser.error('--drain-timeout must be between 1 and 30 seconds')
    for name in ('rust_binary', 'java_jar', 'java_home', 'nginx_binary'):
        setattr(args, name, getattr(args, name).expanduser().resolve())
    rehearsal = Rehearsal(args)
    output = (args.out or Path.home() / '.cache/rust-cutover' / f'{rehearsal.run_id}.json').expanduser().resolve()
    # Signals request cooperative cancellation; creation calls finish before their owned handles are recorded.
    previous_handlers = {}
    for signum in (signal.SIGINT, signal.SIGTERM):
        previous_handlers[signum] = signal.signal(signum, lambda *_: rehearsal.stop.set())
    try:
        output.parent.mkdir(parents=True, exist_ok=True)
        rehearsal.run()
    except (Exception, KeyboardInterrupt) as error:
        rehearsal.report['error'] = rehearsal.redact(f'{type(error).__name__}: {error}')
    finally:
        try:
            rehearsal.cleanup()
        except Exception as error:
            rehearsal.report['status'] = 'FAIL'
            rehearsal.report['cleanup']['unexpected_error'] = rehearsal.redact(str(error))
        for signum, handler in previous_handlers.items():
            signal.signal(signum, handler)
    payload = rehearsal.redact(json.dumps(rehearsal.report, ensure_ascii=False, indent=2)) + '\n'
    try:
        # Restrict report permissions even if a caller reused an existing output file.
        descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
            os.fchmod(stream.fileno(), 0o600)
            stream.write(payload)
    except OSError as error:
        print(f'FAIL: could not persist report: {error}', file=sys.stderr)
        return 1
    passed = rehearsal.report['status'] == 'PASS_REAL_LOCAL_GATEWAY'
    print(f'{rehearsal.report["status"]}: Java -> Rust -> Java -> Rust /api/postTrainGlobalRule; report={output}')
    if not passed:
        print(rehearsal.report.get('error', 'cleanup failed; inspect report'), file=sys.stderr)
    return 0 if passed else 1


if __name__ == '__main__':
    sys.exit(main())
