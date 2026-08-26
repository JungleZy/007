## commits
e2008b9 fix(delivery-6.1): release 资产按架构重命名防 basename 冲突

## diff
commit e2008b9b36a651ebbe8f074bb52da616ce88134d
Author: Jungle <zipoqiy@163.com>
Date:   Thu Aug 27 04:24:10 2026 +0800

    fix(delivery-6.1): release 资产按架构重命名防 basename 冲突

diff --git a/.github/workflows/build-quarkus-native.yml b/.github/workflows/build-quarkus-native.yml
index b5b979d..f32375d 100644
--- a/.github/workflows/build-quarkus-native.yml
+++ b/.github/workflows/build-quarkus-native.yml
@@ -12,23 +12,26 @@ jobs:
     strategy:
       fail-fast: false
       matrix:
         include:
           - os: ubuntu-22.04
             arch: x86_64
             runner: ubuntu-22.04
             native_march: x86-64
+            asset_suffix: linux-amd64
           - os: ubuntu-24.04
             arch: arm64
             runner: ubuntu-24.04-arm
             native_march: armv8-a
+            asset_suffix: linux-arm64
           - os: windows-2022
             arch: x86_64
             runner: windows-2022
+            asset_suffix: windows-amd64
     runs-on: ${{ matrix.runner }}
     steps:
       - name: Checkout
         uses: actions/checkout@v4
 
       - name: Set up GraalVM (Java 21 with Native Image)
         uses: graalvm/setup-graalvm@v1
         with:
@@ -70,16 +73,35 @@ jobs:
           REQ="2.28"
           TOP=$(printf '%s\n' "$REQ" "$MAX_VER" | sort -V | tail -n 1)
           if [ "$TOP" != "$REQ" ]; then
             echo "glibc requirement too high: ${MAX_VER} (> ${REQ})"
             exit 1
           fi
           echo "glibc baseline OK: ${MAX_VER} <= ${REQ}"
 
+      - name: Rename binary with architecture suffix
+        shell: bash
+        run: |
+          set -euo pipefail
+          shopt -s nullglob
+          renamed=0
+          for f in target/*-runner; do
+            mv "$f" "${f}-${{ matrix.asset_suffix }}"
+            renamed=1
+          done
+          for f in target/*-runner.exe; do
+            mv "$f" "${f%.exe}-${{ matrix.asset_suffix }}.exe"
+            renamed=1
+          done
+          if [ "$renamed" -eq 0 ]; then
+            echo "No runner binary found to rename"
+            exit 1
+          fi
+
       - name: Upload Native Artifact
         uses: actions/upload-artifact@v4
         with:
           name: quarkus-native-${{ matrix.os }}-${{ runner.arch }}
           path: |
             target/*-runner*
             target/*-runner.exe
           if-no-files-found: error
