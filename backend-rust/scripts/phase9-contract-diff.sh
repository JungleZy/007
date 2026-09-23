#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf 'Usage: %s JAVA_BASE_URL RUST_BASE_URL FIXTURE_JSON\n' "$0" >&2
  printf 'Fixture: array of {method,path,headers?,body?}; compares HTTP status and sorted JSON responses.\n' >&2
}

if [[ $# -ne 3 ]]; then usage; exit 64; fi
java_base=${1%/}
rust_base=${2%/}
fixture=$3
[[ -f "$fixture" ]] || { echo "fixture does not exist: $fixture" >&2; exit 66; }
command -v jq >/dev/null || { echo 'jq is required' >&2; exit 69; }
command -v curl >/dev/null || { echo 'curl is required' >&2; exit 69; }

request() {
  local base=$1 item=$2 headers_file=$3
  local method path body header
  method=$(jq -r '.method' <<<"$item")
  path=$(jq -r '.path' <<<"$item")
  body=$(jq -c '.body // empty' <<<"$item")
  local args=(--silent --show-error --output - --dump-header "$headers_file" --request "$method")
  while IFS= read -r header; do args+=(--header "$header"); done < <(jq -r '.headers // {} | to_entries[] | "\(.key): \(.value)"' <<<"$item")
  if [[ -n "$body" ]]; then args+=(--header 'content-type: application/json' --data "$body"); fi
  curl "${args[@]}" "$base$path"
}

failures=0
index=0
while IFS= read -r item; do
  java_headers=$(mktemp)
  rust_headers=$(mktemp)
  java_payload=$(request "$java_base" "$item" "$java_headers")
  rust_payload=$(request "$rust_base" "$item" "$rust_headers")
  java_status=$(awk 'NR==1 {print $2}' "$java_headers")
  rust_status=$(awk 'NR==1 {print $2}' "$rust_headers")
  if [[ "$java_status" != "$rust_status" ]] || ! diff -u <(jq -S . <<<"$java_payload") <(jq -S . <<<"$rust_payload"); then
    echo "FAIL[$index] $(jq -r '.method + " " + .path' <<<"$item")" >&2
    echo "Java HTTP $java_status: $java_payload" >&2
    echo "Rust HTTP $rust_status: $rust_payload" >&2
    failures=$((failures + 1))
  else
    echo "PASS[$index] $(jq -r '.method + " " + .path' <<<"$item")"
  fi
  rm -f "$java_headers" "$rust_headers"
  index=$((index + 1))
done < <(jq -c '.[]' "$fixture")

if [[ "$failures" -ne 0 ]]; then
  echo "$failures contract case(s) failed" >&2
  exit 1
fi
echo "$index contract case(s) passed"
