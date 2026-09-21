#!/usr/bin/env python3
"""Generate the Phase 0 WebSocket inventory without contacting the application.

The output is deliberately source-oriented: it records the endpoint declarations,
callback families, browser consumers, and SHA-256s of every source file used by the
inventory. Protocol samples are emitted by WebSocketContractSnapshotRehearsal, not
invented here.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shlex
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ENDPOINTS = [
    {
        "family": "status",
        "class": "StatusWebSocket",
        "path": "/status",
        "path_params": "",
        "handshake": "none",
        "identity": "none",
        "role": "none",
        "messages": "@OnMessage(String): arbitrary text; reserved heartbeat ping -> pong; otherwise pong",
        "heartbeat": "__nip_heartbeat_ping__ -> __nip_heartbeat_pong__ (async)",
        "lifecycle": "open logs; close logs; error logs",
        "consumers": "none located",
        "source": "backend/src/main/java/com/nip/ws/StatusWebSocket.java",
    },
    {
        "family": "classic-hand",
        "class": "WebSocketService",
        "path": "/websocket/{sid}",
        "path_params": "sid(route-only)",
        "handshake": "query token + deviceId; invalid or missing -> code 1 error frame then close",
        "identity": "authenticated query user id; path sid ignored for identity",
        "role": "none",
        "messages": "JSON floor capture payload; malformed/forbidden -> ResponseModel error; success -> ResponseModel",
        "heartbeat": "shared reserved ping/pong",
        "lifecycle": "same authenticated id replaces old session; stale close cannot remove current mapping; close is local cleanup",
        "consumers": "bw-frontend/frontend/src/common/ws/SocketConnection.js; classic hand consumers not uniquely isolated",
        "source": "backend/src/main/java/com/nip/ws/WebSocketService.java",
    },
    {
        "family": "union",
        "class": "WebSocketUnionService",
        "path": "/websocketUnion/{sid}",
        "path_params": "sid(route-only)",
        "handshake": "query token + deviceId; invalid or missing -> code 1 error frame then close",
        "identity": "authenticated query user id; path sid ignored for identity",
        "role": "none",
        "messages": "RequestModel {code,sendUser,receiveUser,data}; unknown/malformed -> code -1; union code dispatch",
        "heartbeat": "shared reserved ping/pong",
        "lifecycle": "same authenticated id sends old code 1, closes old session, replaces mapping; stale old close ignored; room/user cleanup on current close",
        "consumers": "bw-frontend/frontend/src/views/manage/unionJob/js/UnionWs.js",
        "source": "backend/src/main/java/com/nip/ws/WebSocketUnionService.java",
    },
    {
        "family": "simulation",
        "class": "WebSocketSimulationService",
        "path": "/simulation/{id}/{roomId}",
        "path_params": "id(route-only),roomId(route-only parse)",
        "handshake": "query token + deviceId; invalid -> structured error then close; room membership/creator checked",
        "identity": "authenticated query user id; path id ignored",
        "role": "room membership userType/channel; creator may receive synthetic channel -1 member",
        "messages": "room-type-specific JSON topic/body or type payload; malformed/unauthorized -> code -1 error and connection retained",
        "heartbeat": "shared reserved ping/pong",
        "lifecycle": "replace same user+room holder; stale close is identity/session guarded; onError delegates onClose; close broadcasts offline",
        "consumers": "bw-frontend/frontend/src/views/manage/unionJob/disturbCode/js/train.js; BroadcastTeachTrain consumers",
        "source": "backend/src/main/java/com/nip/ws/WebSocketSimulationService.java",
    },
    {
        "family": "general-key",
        "class": "WebSocketGeneralKeyPatService",
        "path": "/generalKeyPatTrain/{uid}/{trainId}",
        "path_params": "uid(route-only),trainId(route-only parse)",
        "handshake": "query token + deviceId; invalid -> SocketResponseModel error then close",
        "identity": "authenticated query user id; path uid ignored",
        "role": "training user role from service (role 1 control, otherwise participant)",
        "messages": "JSON {topic,...}; begin/end control; ready/pat/finish participant; malformed/unknown/role mismatch -> code -1",
        "heartbeat": "shared reserved ping/pong",
        "lifecycle": "same user replacement; stale close guarded by session identity; close broadcasts offline and removes empty room",
        "consumers": "bw-frontend/frontend/src/views/manage/organization/electronKeyZuXun/train/student/js/handKeyTrain.js; handkeyZuXun counterpart",
        "source": "backend/src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java",
    },
    {
        "family": "general-telex",
        "class": "WebSocketGeneralTelexPatService",
        "path": "/generalTelexPatTrain/{uid}/{trainId}",
        "path_params": "uid(route-only),trainId(opaque string)",
        "handshake": "query token + deviceId; invalid -> SocketResponseModel error then close",
        "identity": "authenticated query user id; path uid ignored",
        "role": "training user role from service (role 1 control, otherwise participant)",
        "messages": "JSON {topic,...}; begin/end control; ready/pat/finish participant; malformed/unknown/role mismatch -> code -1",
        "heartbeat": "shared reserved ping/pong",
        "lifecycle": "same user replacement; stale close guarded by session identity; close broadcasts offline and removes empty room",
        "consumers": "bw-frontend/frontend/src/views/manage/organization/datagramZuXun/train/student/js/datagramTrain.js; telexZuXun counterpart",
        "source": "backend/src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java",
    },
    {
        "family": "general-ticker",
        "class": "WebSocketGeneralTickerPatService",
        "path": "/generalTickerPat/{uid}/{trainId}/{role}",
        "path_params": "uid(route-only),trainId(route-only parse),role(parse and must match service role)",
        "handshake": "query token + deviceId; invalid -> SocketResponseModel error then close",
        "identity": "authenticated query user id; path uid ignored",
        "role": "path role must match training role; role 1 control, role 0 participant",
        "messages": "JSON {topic,...}; begin/end control; ready/pat/finish participant; malformed/unknown/role mismatch -> code -1",
        "heartbeat": "shared reserved ping/pong",
        "lifecycle": "same user/role replacement; stale close guarded by session identity; onError delegates close; failed recipient removed",
        "consumers": "bw-frontend/frontend/src/views/manage/organization/handkeyZuXun/train/student/js/handKeyTrain.js",
        "source": "backend/src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java",
    },
]

CONSUMERS = [
    "bw-frontend/frontend/src/common/ws/SocketConnection.js",
    "bw-frontend/frontend/src/views/manage/unionJob/js/UnionWs.js",
    "bw-frontend/frontend/src/views/manage/unionJob/disturbCode/js/train.js",
    "bw-frontend/frontend/src/views/manage/organization/handkeyZuXun/train/student/js/handKeyTrain.js",
    "bw-frontend/frontend/src/views/manage/organization/electronKeyZuXun/train/student/js/handKeyTrain.js",
    "bw-frontend/frontend/src/views/manage/organization/telexZuXun/train/student/js/datagramTrain.js",
    "bw-frontend/frontend/src/views/manage/organization/datagramZuXun/train/student/js/datagramTrain.js",
]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def git_commit(root: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", str(root), "rev-parse", "HEAD"], text=True, stderr=subprocess.STDOUT
        ).strip()
    except (OSError, subprocess.CalledProcessError) as error:
        raise RuntimeError(f"cannot resolve source commit for {root}: {error}") from error


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", required=True, help="directory receiving generated evidence")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    output = Path(args.output_dir).resolve()
    source_paths = [
        *(root / "backend/src/main/java/com/nip/ws").rglob("*.java"),
        *(root / "backend/src/test/java/com/nip/testsupport").rglob("*.java"),
        *(root / path for path in CONSUMERS),
    ]
    source_paths = sorted({path.resolve() for path in source_paths})
    missing = [path for path in source_paths if not path.is_file()]
    if missing:
        raise FileNotFoundError("missing inventory source: " + ", ".join(map(str, missing)))
    output.mkdir(parents=True, exist_ok=True)
    commit = git_commit(root)
    generated_at = datetime.now(timezone.utc).isoformat()
    command = " ".join(shlex.quote(item) for item in sys.argv)
    relative_sources = [
        {"path": str(path.relative_to(root)), "sha256": sha256(path)} for path in source_paths
    ]

    tsv = output / "ws-contracts.tsv"
    columns = ["family", "class", "path", "path_params", "handshake", "identity", "role", "messages", "heartbeat", "lifecycle", "consumers", "source"]
    with tsv.open("w", encoding="utf-8", newline="") as stream:
        stream.write("\t".join(columns) + "\n")
        for row in ENDPOINTS:
            stream.write("\t".join(row[column].replace("\t", " ").replace("\n", " ") for column in columns) + "\n")

    metadata = {
        "schema": "rust-migration-ws-inventory/v1",
        "generated_at_utc": generated_at,
        "source_commit": commit,
        "generator_command": command,
        "generator": "backend/scripts/generate-ws-contract-inventory.py",
        "generator_sha256": sha256(Path(__file__).resolve()),
        "protocol_sample_generator": "backend/src/test/java/com/nip/rehearsal/WebSocketContractSnapshotRehearsal.java",
        "sample_output": "ws-protocol-samples.ndjson",
        "endpoint_count": len(ENDPOINTS),
        "endpoint_families": [row["family"] for row in ENDPOINTS],
        "source_files": relative_sources,
        "credentials_policy": "No credential values are written; rehearsal uses isolated fixture references and redacts query values.",
        "coverage_boundary": "Static inventory covers all @ServerEndpoint declarations under backend/src/main/java/com/nip/ws; runtime rehearsal covers synthetic handshake/frame/close sequences and does not claim container/network coverage.",
    }
    with (output / "ws-inventory-metadata.json").open("w", encoding="utf-8") as stream:
        json.dump(metadata, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"ws inventory generation failed: {error}", file=sys.stderr)
        raise SystemExit(2)
