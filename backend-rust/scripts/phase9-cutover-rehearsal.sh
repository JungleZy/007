#!/usr/bin/env bash
set -euo pipefail
# Real local HTTP rehearsal; consumes already-built artifacts, never builds or pulls.
exec python3 "$(dirname "$0")/phase9-cutover-rehearsal.py" "$@"
