#!/usr/bin/env python3
"""
keysim 虚拟串口设备：在系统里真开一个 PTY，对外就是一个字符设备节点（/dev/pts/N）。
任何按路径打开串口的程序都能用它：串口调试助手、pyserial、minicom、cat，
以及桌面模式下真正去读串口的仓外桥接程序。

与宿主进程的约定：
  stdin  : 每帧 4 字节大端长度 + 载荷，载荷原样写入 PTY 主端（= 设备"收到"的字节）
  stdout : 每行一个 JSON 事件
           {"type":"device","path":"/dev/pts/N","links":[...]}   设备就绪
           {"type":"rx","hex":".."}                             对端往串口写了数据
           {"type":"warn"/"error","message":".."}
不带任何第三方依赖，只用标准库。
"""
import errno
import json
import os
import pty
import select
import struct
import sys
import tty


def emit(event):
    sys.stdout.write(json.dumps(event, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def make_links(path, links):
    made = []
    for link in links:
        try:
            directory = os.path.dirname(link) or "."
            os.makedirs(directory, exist_ok=True)
            if os.path.islink(link) or os.path.exists(link):
                os.remove(link)
            os.symlink(path, link)
            made.append(link)
        except OSError as error:
            emit({"type": "warn", "message": f"创建设备链接 {link} 失败：{error.strerror}"})
    return made


def main():
    links = sys.argv[1:]
    master, slave = pty.openpty()
    path = os.ttyname(slave)
    # 原始模式：不做回显与换行转换，字节原样进出，才是串口语义
    tty.setraw(master)
    tty.setraw(slave)
    try:
        os.chmod(path, 0o666)
    except OSError as error:
        emit({"type": "warn", "message": f"放开 {path} 权限失败：{error.strerror}（同用户仍可打开）"})

    made = make_links(path, links)
    emit({"type": "device", "path": path, "links": made, "pid": os.getpid()})

    stdin_fd = sys.stdin.fileno()
    os.set_blocking(stdin_fd, False)
    os.set_blocking(master, False)
    pending = b""
    try:
        while True:
            readable, _, _ = select.select([stdin_fd, master], [], [], 0.5)
            if stdin_fd in readable:
                chunk = os.read(stdin_fd, 65536)
                if not chunk:
                    break
                pending += chunk
                while len(pending) >= 4:
                    (length,) = struct.unpack(">I", pending[:4])
                    if len(pending) < 4 + length:
                        break
                    payload = pending[4:4 + length]
                    pending = pending[4 + length:]
                    try:
                        os.write(master, payload)
                    except OSError as error:
                        if error.errno not in (errno.EIO, errno.EAGAIN):
                            raise
            if master in readable:
                try:
                    data = os.read(master, 65536)
                except OSError as error:
                    # 没有对端打开从端时读主端会得到 EIO，不是故障
                    if error.errno in (errno.EIO, errno.EAGAIN):
                        continue
                    raise
                if data:
                    emit({"type": "rx", "hex": data.hex(" ")})
    except KeyboardInterrupt:
        pass
    finally:
        for link in made:
            try:
                os.remove(link)
            except OSError:
                pass
        os.close(master)
        os.close(slave)


if __name__ == "__main__":
    main()
