#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  printf 'Usage: %s JAVA_BASE_URL RUST_BASE_URL FIXTURE_JSON\n' "$0" >&2
  exit 64
fi
java_base=${1%/}
rust_base=${2%/}
fixture=$3
[[ -f "$fixture" ]] || { echo "fixture does not exist: $fixture" >&2; exit 66; }
for tool in jq curl diff; do
  command -v "$tool" >/dev/null || { echo "$tool is required" >&2; exit 69; }
done
items=$(jq -c 'if type == "array" and length > 0 then .[] else error("expected a nonempty fixture array") end' "$fixture")
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT

request() {
  local base=$1 item=$2 payload=$3 method path header
  method=$(jq -er '.method | select(type == "string" and length > 0)' <<<"$item") || return 1
  path=$(jq -er '.path | select(type == "string" and startswith("/"))' <<<"$item") || return 1
  local args=(--silent --show-error --connect-timeout 5 --max-time 30 --output "$payload" --write-out '%{http_code}' --request "$method")
  local headers
  headers=$(jq -er '(.headers // {}) | to_entries | map("\(.key): \(.value)") | join("\n")' <<<"$item") || return 1
  if [[ -n "$headers" ]]; then
    while IFS= read -r header; do args+=(--header "$header"); done <<<"$headers"
  fi
  if jq -e 'has("body")' <<<"$item" >/dev/null; then
    args+=(--header 'content-type: application/json' --data "$(jq -c '.body' <<<"$item")")
  fi
  curl "${args[@]}" "$base$path"
}

failures=0
index=0
while IFS= read -r item; do
  label=$(jq -r '.case_id // (.method + " " + .path)' <<<"$item")
  if ! java_status=$(request "$java_base" "$item" "$work/java.body") ||
     ! rust_status=$(request "$rust_base" "$item" "$work/rust.body"); then
    echo "ERROR[$index] $label: request failed" >&2
    failures=$((failures + 1))
  elif ! jq -s -S 'if length == 1 then .[0] else error("expected exactly one JSON response") end' "$work/java.body" >"$work/java.json" ||
       ! jq -s -S 'if length == 1 then .[0] else error("expected exactly one JSON response") end' "$work/rust.body" >"$work/rust.json"; then
    echo "ERROR[$index] $label: invalid JSON response" >&2
    failures=$((failures + 1))
  elif [[ "$java_status" != "$rust_status" ]] || ! diff -q "$work/java.json" "$work/rust.json" >/dev/null; then
    echo "FAIL[$index] $label: Java HTTP $java_status, Rust HTTP $rust_status; status or JSON differs" >&2
    failures=$((failures + 1))
  else
    echo "PASS[$index] $label"
  fi
  index=$((index + 1))
done <<<"$items"

if [[ "$failures" -ne 0 ]]; then
  echo "$failures contract case(s) failed" >&2
  exit 1
fi
echo "$index contract case(s) passed"
