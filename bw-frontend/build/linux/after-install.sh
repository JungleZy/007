#!/bin/bash

# ---------------------------------------------------------------------------
# 本文件覆盖 electron-builder 的默认 deb postinst 模板
# (app-builder-lib/templates/linux/after-install.tpl)。
# 上半段与默认模板逐行一致，请勿删改；下半段是授权机器级副本目录的初始化。
# 注意：`${...}` 会被 electron-builder 做模板替换，shell 变量一律写成 $VAR 不加大括号。
# ---------------------------------------------------------------------------

# Link to the binary
ln -sf '/opt/${sanitizedProductName}/${executable}' '/usr/bin/${executable}'

# SUID chrome-sandbox for Electron 5+
chmod 4755 '/opt/${sanitizedProductName}/chrome-sandbox' || true

update-mime-database /usr/share/mime || true
update-desktop-database /usr/share/applications || true

# ---------------------------------------------------------------------------
# 授权信息的机器级副本目录（跨 OS 账户 / 跨提权共享）。
#
# 不建此目录并放开权限，普通用户无法写入 /var/lib，机器级副本就会失效，
# 「普通用户授权后改用 root/sudo 启动即要求重新授权」的问题不会被修复。
#
# 目录对所有本机用户可写。授权在本项目中已确认非安全边界，此处以可用性优先。
# 卸载时不清理该目录（after-remove 未覆盖），以便重装后授权仍然有效。
# ---------------------------------------------------------------------------
LICENSE_DIR=/var/lib/nip-traffic-system
mkdir -p "$LICENSE_DIR" || true
chmod 0777 "$LICENSE_DIR" || true
if [ -f "$LICENSE_DIR/license.json" ]; then
  chmod 0666 "$LICENSE_DIR/license.json" || true
fi

# ---------------------------------------------------------------------------
# DMI 快照。/sys/class/dmi/id/product_uuid 等在内核中是 0400，只有 root 能读；
# 安装脚本正好以 root 运行，在此把它们落成一份普通用户可读的快照，
# 使非 root 运行时硬件设备码的可比对因子从 2 个提升到 4 个
# （否则 Linux 上重装系统就会丢授权）。
# 这些值是本机硬件标识，不含任何用户数据。
# ---------------------------------------------------------------------------
HWID_FILE="$LICENSE_DIR/hwid"
{
  echo "product_uuid=$(cat /sys/class/dmi/id/product_uuid 2>/dev/null)"
  echo "product_serial=$(cat /sys/class/dmi/id/product_serial 2>/dev/null)"
  echo "board_serial=$(cat /sys/class/dmi/id/board_serial 2>/dev/null)"
} > "$HWID_FILE" 2>/dev/null || true
chmod 0644 "$HWID_FILE" 2>/dev/null || true

exit 0
