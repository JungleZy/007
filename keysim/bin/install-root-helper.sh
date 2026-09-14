#!/usr/bin/env bash
# 装一次的 root 助手：之后在网页上点「开启虚拟串口」就能挂/卸设备，不再输密码。
#
#   sudo keysim/bin/install-root-helper.sh            # 安装并启动
#   sudo keysim/bin/install-root-helper.sh --uninstall # 卸掉
#
# 它只装一个 systemd 服务，跑 keysim/bin/keysim-attachd.py；那个脚本只做
# vhci_hcd 的 attach/detach，不执行调用方给的命令。设备模拟本身仍以普通用户运行。
set -euo pipefail

UNIT=/etc/systemd/system/keysim-attachd.service
HELPER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HELPER="$HELPER_DIR/keysim-attachd.py"
SOCKET=/run/keysim/keysim-attachd.sock

if [[ "${1:-}" == "--uninstall" ]]; then
  systemctl disable --now keysim-attachd.service 2>/dev/null || true
  rm -f "$UNIT"
  systemctl daemon-reload
  echo "已卸载 keysim-attachd"
  exit 0
fi

if [[ $EUID -ne 0 ]]; then
  echo "需要 root：sudo $0" >&2
  exit 1
fi
if [[ ! -f "$HELPER" ]]; then
  echo "找不到助手脚本：$HELPER" >&2
  exit 1
fi
if ! command -v systemctl >/dev/null; then
  echo "本机没有 systemd。可以改为手动常驻：sudo $HELPER --allow-uid <你的uid>" >&2
  exit 1
fi

# 允许调用的普通用户：优先 sudo 的原始用户
TARGET_UID="${SUDO_UID:-0}"
TARGET_USER="${SUDO_USER:-root}"
PYTHON="$(command -v python3)"

cat > "$UNIT" <<UNITEOF
[Unit]
Description=keysim USB/IP attach helper（只做 vhci_hcd 的 attach/detach）
After=network.target

[Service]
Type=simple
ExecStartPre=-/sbin/modprobe vhci-hcd
ExecStart=$PYTHON $HELPER --socket $SOCKET --allow-uid $TARGET_UID
RuntimeDirectory=keysim
RuntimeDirectoryMode=0755
Restart=on-failure
RestartSec=2
# 只需要这两项能力：加载模块 + 写 sysfs
NoNewPrivileges=yes
ProtectHome=read-only
ProtectSystem=full
PrivateTmp=yes

[Install]
WantedBy=multi-user.target
UNITEOF

systemctl daemon-reload
systemctl enable --now keysim-attachd.service
sleep 1
systemctl --no-pager --lines=5 status keysim-attachd.service || true
echo
echo "已安装：$UNIT"
echo "控制 socket：$SOCKET（仅 uid=$TARGET_UID / $TARGET_USER 可用）"
echo "接下来在 keysim 控制台点「开启虚拟串口」即可，不再需要密码。"
