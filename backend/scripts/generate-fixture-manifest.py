#!/usr/bin/env python3
"""Generate redacted Phase 0 migration fixtures and behavior manifest."""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend-root", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    backend = args.backend_root.resolve()
    output = args.output_dir.resolve()
    if not backend.is_dir():
        parser.error(f"backend root does not exist: {backend}")
    output.mkdir(parents=True, exist_ok=True)

    source_paths = [
        "src/test/java/com/nip/testsupport/Fixtures.java",
        "src/test/java/com/nip/common/exception/ExceptionBoundaryTest.java",
        "src/test/java/com/nip/controller/AdminAuthorizationTest.java",
        "src/test/java/com/nip/controller/RuleAndQuestionBankAuthorizationTest.java",
        "src/test/java/com/nip/controller/ControllerAuthorizationGuardTest.java",
        "src/test/java/com/nip/common/security/PasswordHasherTest.java",
        "src/test/java/com/nip/controller/OpaqueSessionTokenTest.java",
        "src/test/java/com/nip/common/utils/ScoreMathTest.java",
        "src/test/java/com/nip/common/utils/TickerPatUtilsCharacterizationTest.java",
        "src/test/java/com/nip/service/ClassicHandSettlementRegressionTest.java",
        "src/test/java/com/nip/service/GeneralTickerPatScoreTest.java",
        "src/test/java/com/nip/service/GeneralTelexPatMessageBodyTest.java",
        "src/test/java/com/nip/ws/WebSocketHandshakeAuthorizationTest.java",
        "src/test/java/com/nip/ws/WebSocketHeartbeatTest.java",
        "src/test/java/com/nip/ws/WebSocketUnionLifecycleTest.java",
        "src/test/java/com/nip/controller/SimulationRestAuthorizationTest.java",
    ]
    files = []
    for relative in source_paths:
        path = backend / relative
        if not path.is_file():
            raise SystemExit(f"required fixture source missing: {path}")
        files.append({"path": str(path.relative_to(backend)), "sha256": sha256(path)})

    manifest = {
        "schema": "rust-migration-fixture-manifest/v1",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "generator": "backend/scripts/generate-fixture-manifest.py",
        "command": "python3 backend/scripts/generate-fixture-manifest.py --backend-root backend --output-dir docs/reviews/rust-migration",
        "fixturePolicy": {
            "credentials": "never persisted in this manifest; runtime tests generate isolated UUID credentials",
            "businessData": "synthetic test rows only; no production rows or response bodies",
            "identitySource": "server-side token and deviceId fixture headers; WS credentials remain query-only",
        },
        "personas": [
            {"id": "anonymous", "setup": "no token/deviceId", "representativeExpectation": "JWT endpoint returns HTTP 200 envelope code 203"},
            {"id": "ordinary-user", "setup": "Fixtures.user + role is_admin=1", "representativeExpectation": "admin-only write returns HTTP 200 envelope code 207 and data is unchanged"},
            {"id": "no-role-user", "setup": "Fixtures.user without UserRole row", "representativeExpectation": "admin-only write returns HTTP 200 envelope code 207 and data is unchanged"},
            {"id": "administrator", "setup": "Fixtures.user + role is_admin=0", "representativeExpectation": "authorized admin operation may return code 200"},
        ],
        "trainingSamples": [
            {"id": "classic-handkey", "source": "service/ClassicHandSettlementRegressionTest.java", "coverage": ["submit", "pause", "finish", "terminal guard", "score and clock" ]},
            {"id": "general-ticker", "source": "service/GeneralTickerPatScoreTest.java", "coverage": ["full page", "partial final page", "missing report", "deduction" ]},
            {"id": "general-telex", "source": "service/GeneralTelexPatMessageBodyTest.java", "coverage": ["message body shape", "capture timing" ]},
            {"id": "theory-exam", "source": "controller/TheoryExamIdentityAuthorizationTest.java", "coverage": ["server-authoritative score", "owner identity", "terminal state" ]},
            {"id": "simulation", "source": "controller/SimulationRestAuthorizationTest.java", "coverage": ["room membership", "admin control", "channel state" ]},
        ],
        "pureFunctionSamples": [
            {"id": "score-math", "source": "utils/ScoreMathTest.java", "cases": ["rate(120,60000)=120", "rate(7,120000)=4", "accuracy(1,3)=33.00", "zero denominator=0"]},
            {"id": "ticker-message-resolution", "source": "common/utils/TickerPatUtilsCharacterizationTest.java", "cases": ["resolver message/log/time/value arrays stay aligned", "malformed pat keys are rejected"]},
            {"id": "password-verification", "source": "common/security/PasswordHasherTest.java", "cases": ["PBKDF2 vector", "legacy MD5 verification and upgrade", "corrupt metadata rejection"]},
        ],
        "protocolSamples": [
            {"id": "rest-auth", "source": "common/exception/ExceptionBoundaryTest.java + interceptor/InterceptorCredentialSourceTest.java", "cases": ["missing token=203", "missing deviceId=204", "unknown token=206", "query credentials rejected"]},
            {"id": "business-boundary", "source": "common/exception/TerminalBusinessCodeContractTest.java", "cases": ["retryable invalid page=202", "terminal finished state=208", "HTTP status remains 200"]},
            {"id": "websocket-lifecycle", "source": "ws/WebSocketHeartbeatTest.java + ws/WebSocketUnionLifecycleTest.java", "cases": ["handshake", "heartbeat", "disconnect", "reconnect", "close cleanup"]},
        ],
        "sourceFiles": files,
    }
    try:
        manifest["sourceCommit"] = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=backend.parent, text=True
        ).strip()
    except (OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"cannot determine source commit: {error}") from error
    (output / "fixture-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


if __name__ == "__main__":
    main()
