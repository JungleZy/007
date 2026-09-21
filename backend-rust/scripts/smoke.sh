#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: smoke.sh PATH_TO_RUST_BACKEND_BINARY [HOST:PORT]

Starts the real rust-backend binary, verifies /health, /live, /ready, and
that /api is absent, then sends SIGTERM and requires a clean exit.
The default address is 127.0.0.1:18002. RUST_BACKEND_ADDR is set by this
script and is overridden by the optional HOST:PORT argument.
EOF
}

if [[ $# -lt 1 || $# -gt 2 ]]; then
  usage
  exit 64
fi

binary=$1
address=${2:-127.0.0.1:18002}
if [[ ! -x "$binary" ]]; then
  echo "binary is missing or not executable: $binary" >&2
  exit 66
fi

host=${address%:*}
port=${address##*:}
if [[ "$host" == "$address" || -z "$host" || -z "$port" ]]; then
  echo "address must be HOST:PORT: $address" >&2
  exit 64
fi

log_file=$(mktemp "${TMPDIR:-/tmp}/rust-backend-smoke.XXXXXX.log")
pid=''
cleanup() {
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    kill -KILL "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  fi
  rm -f "$log_file"
}
trap cleanup EXIT

RUST_BACKEND_ADDR="$address" "$binary" >"$log_file" 2>&1 &
pid=$!

for _ in $(seq 1 100); do
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "rust-backend exited before becoming ready" >&2
    cat "$log_file" >&2
    exit 1
  fi
  if curl --silent --show-error --connect-timeout 1 "http://$host:$port/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.1
done

if ! kill -0 "$pid" 2>/dev/null; then
  echo "rust-backend exited before health check completed" >&2
  cat "$log_file" >&2
  exit 1
fi

request_body() {
  local path=$1
  curl --silent --show-error --fail-with-body "http://$host:$port$path"
}

health_body=$(request_body /health)
live_body=$(request_body /live)
ready_body=$(request_body /ready)

case "$health_body" in
  *'"status":"ok"'*) ;;
  *) echo "unexpected /health response: $health_body" >&2; exit 1 ;;
esac
case "$live_body" in
  *'"status":"ok"'*) ;;
  *) echo "unexpected /live response: $live_body" >&2; exit 1 ;;
esac
case "$ready_body" in
  *'"status":"ready"'*'"readiness":"process-only"'*'"dependencies":false'*) ;;
  *) echo "unexpected /ready response: $ready_body" >&2; exit 1 ;;
esac
openapi_body=$(request_body /q/openapi)
case "$openapi_body" in
  *'"openapi":"3.1.0"'*) ;;
  *) echo "unexpected /q/openapi response: $openapi_body" >&2; exit 1 ;;
esac

missing_token_body=$(curl --silent --show-error --fail-with-body "http://$host:$port/api/phase1/contract/auth")
case "$missing_token_body" in
  *'"code":203'*'"message":"token不能为空"'*) ;;
  *) echo "unexpected missing-token response: $missing_token_body" >&2; exit 1 ;;
esac

query_credentials_body=$(curl --silent --show-error --fail-with-body "http://$host:$port/api/phase1/contract/auth?token=ignored&deviceId=ignored")
case "$query_credentials_body" in
  *'"code":203'*) ;;
  *) echo "query credentials were accepted: $query_credentials_body" >&2; exit 1 ;;
esac

missing_device_body=$(curl --silent --show-error --fail-with-body -H 'token: phase1-smoke-token' "http://$host:$port/api/phase1/contract/auth")
case "$missing_device_body" in
  *'"code":204'*'"message":"设备标识不能为空"'*) ;;
  *) echo "unexpected missing-device response: $missing_device_body" >&2; exit 1 ;;
esac

invalid_credentials_body=$(curl --silent --show-error --fail-with-body -H 'token: phase1-smoke-token' -H 'deviceId: phase1-smoke-device' "http://$host:$port/api/phase1/contract/auth")
case "$invalid_credentials_body" in
  *'"code":206'*'"message":"账号登录凭证异常"'*) ;;
  *) echo "unexpected invalid-credentials response: $invalid_credentials_body" >&2; exit 1 ;;
esac

request_id_headers=$(curl --silent --show-error --dump-header - --output /dev/null -H 'x-request-id: phase1-smoke-request' "http://$host:$port/health")
case "$request_id_headers" in
  *$'x-request-id: phase1-smoke-request'*) ;;
  *) echo "request ID was not propagated: $request_id_headers" >&2; exit 1 ;;
esac
if [[ "${RUST_PHASE2_TOOLS_ENABLED:-false}" == "true" ]]; then
  tools_body=$(request_body /api/tools/getTwelvemonth)
  case "$tools_body" in
    *'"code":200'*'"data":[{'*) ;;
    *) echo "unexpected Phase 2 tools response: $tools_body" >&2; exit 1 ;;
  esac
fi



api_status=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "http://$host:$port/api")
if [[ "$api_status" != "404" ]]; then
  echo "expected /api to be absent (404), got HTTP $api_status" >&2
  exit 1
fi

kill -TERM "$pid"

# wait(1) gives the real child exit status; the watchdog prevents a hung
# server from blocking the CI job forever while preserving a clean-exit check.
(
  sleep 10
  if kill -0 "$pid" 2>/dev/null; then
    kill -KILL "$pid" 2>/dev/null || true
  fi
) &
watchdog_pid=$!
set +e
wait "$pid"
exit_code=$?
set -e
kill "$watchdog_pid" 2>/dev/null || true
wait "$watchdog_pid" 2>/dev/null || true

if [[ "$exit_code" -ne 0 ]]; then
  echo "rust-backend exited with status $exit_code after SIGTERM" >&2
  cat "$log_file" >&2
  exit 1
fi
echo "rust-backend smoke OK: health/live/ready, OpenAPI, 203/204/206 header auth, request ID, /api=404, SIGTERM clean exit"
