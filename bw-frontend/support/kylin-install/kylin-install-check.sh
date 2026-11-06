#!/bin/bash
# Read-only package diagnostics. Does not install, remove, download or change repositories.
set -u
export LC_ALL=C
report="$PWD/kylin-install-report.txt"
package="${1:-}"
if [ -z "$package" ]; then
  candidate="$PWD/nip-traffic-system-kylin-arm64_3.1.1_arm64.deb"
  if [ -f "$PWD/nip-traffic-system-kylin-arm64_3.1.1-1_arm64.deb" ]; then
    candidate="$PWD/nip-traffic-system-kylin-arm64_3.1.1-1_arm64.deb"
  fi
  if [ -f "$candidate" ]; then package="$candidate"; fi
fi
if [ -n "$package" ] && [ -f "$package" ]; then
  package="$(cd -- "$(dirname -- "$package")" && pwd)/$(basename -- "$package")"
fi

bounded() {
  if command -v timeout >/dev/null 2>&1; then timeout 60 "$@"; else "$@"; fi
}

{
  printf '%s\n' '=== System (no credentials collected) ==='
  date -u '+%Y-%m-%dT%H:%M:%SZ'
  uname -m
  uname -r
  if [ -r /etc/os-release ]; then cat /etc/os-release; fi
  if [ -r /etc/kylin-release ]; then cat /etc/kylin-release; fi
  printf '\n%s\n' '=== Runtime baseline ==='
  getconf GNU_LIBC_VERSION 2>&1 || true
  if command -v dpkg >/dev/null 2>&1; then
    dpkg --print-architecture
    dpkg --version | head -n 1
    libc="$(dpkg-query -W -f='${Version}' libc6 2>/dev/null || true)"
    if [ -n "$libc" ]; then
      printf 'Installed libc6: %s\n' "$libc"
      if dpkg --compare-versions "$libc" ge 2.28; then
        printf '%s\n' 'GLIBC_BASELINE_OK (>= 2.28)'
      else
        printf '%s\n' 'GLIBC_BASELINE_TOO_OLD: this package requires >= 2.28. Do not replace system glibc manually.'
      fi
    fi
    printf '\n%s\n' '=== Installed dependency versions ==='
    dpkg-query -W -f='${binary:Package}\t${Status}\t${Version}\n' \
      libc6 libstdc++6 libgcc1 libgcc-s1 libgtk-3-0 libnss3 libnspr4 libasound2 \
      libatk1.0-0 libatk-bridge2.0-0 libcairo2 libcups2 libdbus-1-3 libexpat1 \
      libglib2.0-0 libpango-1.0-0 libx11-6 libxcb1 libxcomposite1 libxdamage1 \
      libxext6 libxfixes3 libxkbcommon0 libxrandr2 libgbm1 libdrm2 libxshmfence1 libudev1 xdg-utils 2>&1 || true
    printf '\n%s\n' '=== Package manager incomplete operations ==='
    dpkg --audit 2>&1 || true
  else
    printf '%s\n' 'DPKG_NOT_FOUND: this system may not use Debian packages.'
    if command -v rpm >/dev/null 2>&1; then rpm --version; fi
  fi
  printf '\n%s\n' '=== Free disk space (installed files need about 3.16 GB plus database space) ==='
  df -h / /opt /tmp "$HOME" 2>&1 || true
  printf '\n%s\n' '=== Local installer ==='
  if [ -n "$package" ] && [ -f "$package" ]; then
    printf 'File: %s\n' "$package"
    wc -c < "$package"
    actual="$(sha256sum "$package" | cut -d ' ' -f 1)"
    printf 'SHA256: %s\n' "$actual"
    if [ "$actual" = 35728c9b260db67e7b8386f1e9a877f8d7e7ee785363f3aab843eb90f7bfccdb ] ||
       [ "$actual" = 53bc3ff8967570c5273f0b2c3896b141e6cbb62d7c2e61de9f23a168fc8fef95 ]; then
      printf '%s\n' 'PACKAGE_CHECKSUM_OK'
    else
      printf '%s\n' 'PACKAGE_CHECKSUM_DIFFERENT: compare filename/version or transfer integrity before retrying.'
    fi
    if command -v dpkg-deb >/dev/null 2>&1; then
      dpkg-deb --info "$package" 2>&1
      printf 'dpkg-deb exit: %s\n' "$?"
    fi
    if command -v apt-get >/dev/null 2>&1; then
      printf '\n%s\n' '=== READ-ONLY simulated installation (no download/install/remove) ==='
      bounded apt-get --simulate --no-download install "$package" 2>&1
      printf 'Simulation exit: %s\n' "$?"
    fi
  else
    printf '%s\n' 'INSTALLER_NOT_FOUND: rerun with the .deb path as the first argument.'
  fi
} > "$report" 2>&1
printf 'Report saved: %s\n' "$report"
printf '%s\n' 'No packages were installed or removed. Send kylin-install-report.txt for diagnosis.'
