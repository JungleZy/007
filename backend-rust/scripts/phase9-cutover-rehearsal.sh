#!/usr/bin/env bash
set -euo pipefail

# Domain cutover rehearsal only. It never touches production ports or data.
# Usage: cutover-rehearsal.sh RUST_BINARY [PORT]
if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "usage: $0 RUST_BINARY [PORT]" >&2
  exit 64
fi
binary=$1
port=${2:-18100}
[[ -x "$binary" ]] || { echo "binary is not executable: $binary" >&2; exit 66; }
log=$(mktemp "${TMPDIR:-/tmp}/rust-cutover.XXXXXX.log")
pid=''
cleanup() { [[ -z "$pid" ]] || kill "$pid" 2>/dev/null || true; rm -f "$log"; }
trap cleanup EXIT
RUST_BACKEND_ADDR="127.0.0.1:$port" "$binary" >"$log" 2>&1 &
pid=$!
for _ in $(seq 1 100); do
  if curl --silent --show-error --fail "http://127.0.0.1:$port/health" >/dev/null; then break; fi
  sleep 0.1
done
curl --silent --show-error --fail "http://127.0.0.1:$port/ready"
kill -TERM "$pid"
wait "$pid"
echo "cutover rehearsal OK: Rust start/readiness/clean stop on isolated port $port"
