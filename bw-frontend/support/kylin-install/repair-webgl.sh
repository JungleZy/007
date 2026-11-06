#!/bin/bash
set -euo pipefail
base="$(cd -- "$(dirname -- "$0")" && pwd)"
install="${1:-/opt/nip-traffic-system-kylin-arm64}"
target="$install/resources/app.asar"
old='@OLD_HASH@'
fixed='@FIXED_HASH@'
hash() { sha256sum "$1" | cut -d ' ' -f 1; }
if [ "$(id -u)" != 0 ]; then echo 'Please run with sudo.' >&2; exit 1; fi
if pgrep -f '^/opt/nip-traffic-system-kylin-arm64/nip-traffic-system( |$)' >/dev/null; then
  echo 'Please close the application first.' >&2; exit 1
fi
[ -f "$target" ] || { echo "Application archive not found: $target" >&2; exit 1; }
[ "$(hash "$base/payload/app.asar")" = "$fixed" ] || { echo 'Patch checksum mismatch.' >&2; exit 1; }
current="$(hash "$target")"
if [ "$current" = "$fixed" ]; then echo 'WEBGL_PATCH_ALREADY_INSTALLED'; exit 0; fi
[ "$current" = "$old" ] || { echo 'Unknown installed version; no changes made.' >&2; exit 1; }
backup="$target.before-webgl"
if [ -e "$backup" ]; then
  [ "$(hash "$backup")" = "$old" ] || { echo 'Existing backup mismatch.' >&2; exit 1; }
else
  cp -p -- "$target" "$backup"
fi
temporary="$(mktemp "$target.webgl.XXXXXX")"
trap 'rm -f -- "$temporary"' EXIT
cp -- "$base/payload/app.asar" "$temporary"
chmod 0644 "$temporary"
chown 0:0 "$temporary"
[ "$(hash "$temporary")" = "$fixed" ] || { echo 'Copied patch checksum mismatch.' >&2; exit 1; }
mv -f -- "$temporary" "$target"
echo 'WEBGL_PATCH_OK'
echo 'Start nip-traffic-system-kylin-arm64 as your normal desktop user (without sudo).'
