#!/usr/bin/env python3
"""
keysim 的 root 助手：只干一件特权活——把 keysim 的 USB/IP 连接交给内核的 vhci_hcd。

为什么需要 root：往 /sys/devices/platform/vhci_hcd.0/attach 写 "port sockfd devid speed"
是向内核注册 USB 设备，任何语言的用户态程序都必须有 root 才能做。设备本身（描述符、
端点、CDC-ACM 行为）跑在普通用户权限的 keysim-usbip 里，本助手不碰业务数据。

攻击面刻意收窄：
  - 只监听本机 unix socket，且只接受安装时指定 uid 的调用方（SO_PEERCRED 校验）；
  - 只允许连回 127.0.0.1 的端口，不接受任意主机；
  - 只做 attach / detach / probe 三件事，不执行调用方给的任何命令串。

协议：一行一个 JSON 请求，一行一个 JSON 响应。
  {"op":"probe"}
  {"op":"attach","port":3240,"busid":"1-1"}
  {"op":"detach","vhciPort":0}
"""
import argparse
import json
import os
import socket
import struct
import subprocess
import sys

VHCI_BASE = '/sys/devices/platform/vhci_hcd.0'
ATTACH = f'{VHCI_BASE}/attach'
DETACH = f'{VHCI_BASE}/detach'
STATUS = f'{VHCI_BASE}/status'
USBIP_VERSION = 0x0111
OP_REQ_IMPORT = 0x8003
OP_REP_IMPORT = 0x0003
FREE_PORT_STATUS = 4  # VDEV_ST_NULL


def log(message):
    print(f'[keysim-attachd] {message}', file=sys.stderr, flush=True)


def modprobe():
    if os.path.exists(ATTACH):
        return True, None
    result = subprocess.run(['modprobe', 'vhci-hcd'], capture_output=True, text=True)
    if result.returncode != 0:
        return False, (result.stderr or '').strip() or 'modprobe vhci-hcd 失败'
    return os.path.exists(ATTACH), None if os.path.exists(ATTACH) else 'vhci-hcd 已加载但没有 attach 节点'


def parse_status():
    """/sys/.../status 的列在不同内核版本里有无 hub 列，两种都认。"""
    ports = []
    try:
        with open(STATUS, 'r', encoding='utf-8') as handle:
            for line in handle:
                tokens = line.split()
                if not tokens or tokens[0] in ('hub', 'prt'):
                    continue
                hub = None
                if tokens[0] in ('hs', 'ss'):
                    hub, tokens = tokens[0], tokens[1:]
                if not tokens[0].isdigit():
                    continue
                ports.append({'hub': hub, 'port': int(tokens[0]), 'status': int(tokens[1])})
    except OSError as error:
        return [], str(error)
    return ports, None


def free_port(speed):
    ports, error = parse_status()
    if error:
        return None, error
    wanted_hub = 'ss' if speed >= 5 else 'hs'
    for entry in ports:
        if entry['status'] != FREE_PORT_STATUS:
            continue
        if entry['hub'] is None or entry['hub'] == wanted_hub:
            return entry['port'], None
    return None, '没有空闲的 vhci 端口'


def usbip_import(port, busid):
    """以 USB/IP 客户端身份完成 import 握手，返回 (socket, devid, speed)。"""
    connection = socket.create_connection(('127.0.0.1', port), timeout=5)
    request = struct.pack('>HHI', USBIP_VERSION, OP_REQ_IMPORT, 0) + busid.encode().ljust(32, b'\0')
    connection.sendall(request)
    head = connection.recv(8, socket.MSG_WAITALL)
    if len(head) != 8:
        connection.close()
        raise RuntimeError('模拟器没有回应 import 请求')
    version, code, status = struct.unpack('>HHI', head)
    if code != OP_REP_IMPORT or status != 0:
        connection.close()
        raise RuntimeError(f'import 被拒绝（code={code:#06x} status={status}）')
    device = connection.recv(312, socket.MSG_WAITALL)
    if len(device) != 312:
        connection.close()
        raise RuntimeError('import 响应里的设备结构不完整')
    busnum, devnum, speed = struct.unpack('>III', device[288:300])
    return connection, (busnum << 16) | devnum, speed


def do_attach(request):
    port = int(request.get('port', 0))
    busid = str(request.get('busid', '1-1'))
    if not 1 <= port <= 65535:
        return {'ok': False, 'error': '端口不合法'}
    ready, error = modprobe()
    if not ready:
        return {'ok': False, 'error': error or 'vhci-hcd 不可用'}
    try:
        connection, devid, speed = usbip_import(port, busid)
    except (OSError, RuntimeError) as error:
        return {'ok': False, 'error': f'连接模拟器失败：{error}'}
    with connection:
        vhci_port, error = free_port(speed)
        if vhci_port is None:
            return {'ok': False, 'error': error}
        payload = f'{vhci_port} {connection.fileno()} {devid} {speed}'
        try:
            with open(ATTACH, 'w', encoding='utf-8') as handle:
                handle.write(payload)
        except OSError as error:
            return {'ok': False, 'error': f'写 attach 失败：{error}'}
    log(f'attach 成功：vhci 端口 {vhci_port}，devid {devid}，speed {speed}')
    return {'ok': True, 'vhciPort': vhci_port, 'devid': devid, 'speed': speed}


def do_detach(request):
    vhci_port = request.get('vhciPort')
    if vhci_port is None:
        return {'ok': False, 'error': '缺少 vhciPort'}
    try:
        with open(DETACH, 'w', encoding='utf-8') as handle:
            handle.write(str(int(vhci_port)))
    except OSError as error:
        return {'ok': False, 'error': f'写 detach 失败：{error}'}
    log(f'detach 成功：vhci 端口 {vhci_port}')
    return {'ok': True, 'vhciPort': int(vhci_port)}


def do_probe(_request):
    ports, error = parse_status()
    return {
        'ok': True,
        'root': os.geteuid() == 0,
        'moduleLoaded': os.path.exists(ATTACH),
        'attachPath': ATTACH,
        'ports': ports,
        'error': error
    }


HANDLERS = {'attach': do_attach, 'detach': do_detach, 'probe': do_probe}


def serve(path, allow_uid):
    if os.path.exists(path):
        os.unlink(path)
    directory = os.path.dirname(path)
    os.makedirs(directory, exist_ok=True)
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(path)
    os.chmod(path, 0o660)
    if allow_uid is not None:
        try:
            os.chown(path, allow_uid, -1)
        except OSError as error:
            log(f'设置 socket 属主失败：{error}')
    server.listen(4)
    log(f'就绪：{path}（只接受 uid={allow_uid if allow_uid is not None else "任意"} 的调用方）')

    while True:
        connection, _ = server.accept()
        with connection:
            try:
                credentials = connection.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, struct.calcsize('3i'))
                _, peer_uid, _ = struct.unpack('3i', credentials)
                if allow_uid is not None and peer_uid != allow_uid and peer_uid != 0:
                    connection.sendall(json.dumps({'ok': False, 'error': f'拒绝 uid={peer_uid}'}).encode() + b'\n')
                    continue
                raw = b''
                while not raw.endswith(b'\n'):
                    chunk = connection.recv(4096)
                    if not chunk:
                        break
                    raw += chunk
                    if len(raw) > 65536:
                        break
                if not raw.strip():
                    continue
                request = json.loads(raw.decode())
                handler = HANDLERS.get(str(request.get('op')))
                response = handler(request) if handler else {'ok': False, 'error': f"未知操作 {request.get('op')}"}
            except Exception as error:  # 助手绝不能因为一次坏请求而退出
                response = {'ok': False, 'error': f'{type(error).__name__}: {error}'}
                log(f'处理请求失败：{response["error"]}')
            connection.sendall(json.dumps(response, ensure_ascii=False).encode() + b'\n')


def main():
    parser = argparse.ArgumentParser(description='keysim 的 root 助手：只做 vhci_hcd 的 attach/detach')
    parser.add_argument('--socket', default='/run/keysim/keysim-attachd.sock')
    parser.add_argument('--allow-uid', type=int, default=None)
    parser.add_argument('--once', action='store_true', help='只处理一次请求后退出（调试用）')
    arguments = parser.parse_args()
    if os.geteuid() != 0:
        log('必须以 root 运行（systemd 服务或 sudo）')
        return 1
    serve(arguments.socket, arguments.allow_uid)
    return 0


if __name__ == '__main__':
    sys.exit(main())
