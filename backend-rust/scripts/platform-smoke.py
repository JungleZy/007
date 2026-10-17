#!/usr/bin/env python3
"""Smoke the real release artifact without a database or third-party Python packages.

Native Linux: python3 scripts/platform-smoke.py target/release/rust-backend
Native Windows: python scripts/platform-smoke.py target/x86_64-pc-windows-gnu/release/rust-backend.exe
QEMU user mode: python3 scripts/platform-smoke.py --qemu qemu-aarch64 --sysroot /usr/aarch64-linux-gnu target/aarch64-unknown-linux-gnu/release/rust-backend
Wine: run this script with Windows Python under Wine, not Linux Python wrapping wine.
For example, from backend-rust with a Windows x64 Python 3.11+ distribution:
  wine /absolute/path/to/python.exe scripts/platform-smoke.py target/x86_64-pc-windows-gnu/release/rust-backend.exe
A headless Wine installation may need xvfb-run -a before wine. Wine is detected
through ntdll and is explicitly reported as emulation, never native Windows.

Windows gets a private console and a real CTRL_C_EVENT, matching Tokio ctrl_c().
CTRL_BREAK_EVENT and TerminateProcess are NOT graceful shutdown substitutes.
Linux/QEMU gets SIGTERM. Any forced cleanup is a failure, not a passing exit.
Only process readiness and rejection of unauthenticated credentials are proved;
this is intentionally not a successful login or database readiness test.
"""

import argparse
import ctypes
import http.client
import json
import os
from pathlib import Path
import platform
import signal
import socket
import subprocess
import sys
import tempfile
import time


class Failure(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise Failure(message)


def execution_mode(qemu):
    if qemu:
        return 'QEMU user-mode Linux arm64 (emulated)'
    if os.name == 'nt':
        ntdll = ctypes.WinDLL('ntdll')
        try:
            version = ntdll.wine_get_version
        except AttributeError:
            return f'native Windows {platform.machine()}'
        version.restype = ctypes.c_char_p
        return f'Wine {version().decode("ascii")} (emulated Windows)'
    return f'native {platform.system()} {platform.machine()}'


def windows_ctrl_c(process):
    # CREATE_NEW_PROCESS_GROUP disables Ctrl+C in the child and CTRL_BREAK is
    # not handled by the backend. Instead attach only to its private console.
    from ctypes import wintypes

    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.FreeConsole.argtypes = []
    kernel.FreeConsole.restype = wintypes.BOOL
    kernel.AttachConsole.argtypes = [wintypes.DWORD]
    kernel.AttachConsole.restype = wintypes.BOOL
    kernel.SetConsoleCtrlHandler.argtypes = [ctypes.c_void_p, wintypes.BOOL]
    kernel.SetConsoleCtrlHandler.restype = wintypes.BOOL
    kernel.GenerateConsoleCtrlEvent.argtypes = [wintypes.DWORD, wintypes.DWORD]
    kernel.GenerateConsoleCtrlEvent.restype = wintypes.BOOL
    kernel.FreeConsole()  # A CI Python process may have no original console.
    if not kernel.AttachConsole(process.pid):
        raise ctypes.WinError(ctypes.get_last_error())
    # Set this AFTER creating the server: it must not inherit Ctrl+C-ignore.
    if not kernel.SetConsoleCtrlHandler(None, True):
        raise ctypes.WinError(ctypes.get_last_error())
    if not kernel.GenerateConsoleCtrlEvent(0, 0):  # CTRL_C_EVENT, current console
        raise ctypes.WinError(ctypes.get_last_error())
    # Keep our attachment until process exit so delivery cannot race detachment.


def request(port, path, headers=None):
    connection = http.client.HTTPConnection('127.0.0.1', port, timeout=2)
    try:
        connection.request('GET', path, headers=headers or {})
        response = connection.getresponse()
        return response.status, dict(response.getheaders()), response.read()
    finally:
        connection.close()


def request_json(port, path, headers=None):
    status, response_headers, raw = request(port, path, headers)
    require(status == 200, f'{path}: expected HTTP 200, got {status}: {raw!r}')
    response_headers = {key.lower(): value for key, value in response_headers.items()}
    require(response_headers.get('content-type', '').startswith('application/json'),
            f'{path}: expected JSON content type, got {response_headers!r}')
    return json.loads(raw), response_headers


def check_protocol(port):
    for path in ('/health', '/live'):
        body, _ = request_json(port, path)
        require(body == {'status': 'ok'}, f'{path}: unexpected response {body!r}')
    body, _ = request_json(port, '/ready')
    require(body == {'status': 'ready', 'readiness': 'process-only', 'dependencies': False},
            f'/ready: expected DB-free readiness, got {body!r}')
    body, _ = request_json(port, '/q/openapi')
    require(body.get('openapi') == '3.1.0', f'/q/openapi: unexpected version {body!r}')
    require('/api/phase1/contract/auth' in body.get('paths', {}),
            '/q/openapi: missing real auth contract route')

    auth_path = '/api/phase1/contract/auth'
    cases = (
        (auth_path, {}, 203, 'token不能为空'),
        (auth_path + '?token=ignored&deviceId=ignored', {}, 203, 'token不能为空'),
        (auth_path, {'token': 'platform-smoke-token'}, 204, '设备标识不能为空'),
        (auth_path, {'token': 'platform-smoke-token', 'deviceId': 'platform-smoke-device'},
         206, '账号登录凭证异常'),
    )
    for path, headers, code, message in cases:
        body, _ = request_json(port, path, headers)
        require(body.get('code') == code and body.get('message') == message,
                f'{path}: expected auth {code}/{message}, got {body!r}')

    request_id = 'platform-smoke-request'
    _, headers = request_json(port, '/health', {'x-request-id': request_id})
    require(headers.get('x-request-id') == request_id,
            f'/health: request ID was not propagated: {headers!r}')
    status, _, raw = request(port, '/api')
    require(status == 404, f'/api: expected HTTP 404, got {status}: {raw!r}')


def smoke(args):
    binary = args.binary.resolve(strict=True)
    require(binary.is_file(), f'not a binary file: {binary}')
    require(not args.qemu or (os.name == 'posix' and platform.system() == 'Linux'),
            '--qemu requires a Linux host')
    require(bool(args.qemu) == bool(args.sysroot), '--qemu and --sysroot must be used together')
    command = [str(binary)]
    if args.qemu:
        command = [args.qemu, '-L', str(args.sysroot.resolve(strict=True)), *command]
    mode = execution_mode(args.qemu)
    print(f'Artifact smoke: {mode}; binary={binary}', flush=True)

    # Never inherit a real database URL or feature flags from a developer shell.
    environment = {key: value for key, value in os.environ.items()
                   if not key.upper().startswith(('RUST_BACKEND_', 'RUST_PHASE'))}
    environment['RUST_LOG'] = 'rust_backend=info'
    with socket.socket() as reservation:
        reservation.bind(('127.0.0.1', 0))
        port = reservation.getsockname()[1]
    environment['RUST_BACKEND_ADDR'] = f'127.0.0.1:{port}'

    options = {}
    if os.name == 'nt':
        options['creationflags'] = subprocess.CREATE_NEW_CONSOLE
    process = None
    with tempfile.TemporaryFile() as log:
        try:
            process = subprocess.Popen(command, env=environment, stdin=subprocess.DEVNULL,
                                       stdout=log, stderr=subprocess.STDOUT, **options)
            deadline = time.monotonic() + args.startup_timeout
            while True:
                require(process.poll() is None,
                        f'backend exited before readiness: {process.returncode}')
                try:
                    body, _ = request_json(port, '/health')
                    require(body == {'status': 'ok'}, f'unexpected startup health: {body!r}')
                    break
                except (OSError, http.client.HTTPException):
                    require(time.monotonic() < deadline, 'backend did not become ready in time')
                    time.sleep(0.1)
            check_protocol(port)
            require(process.poll() is None, 'backend exited during HTTP checks')
            if os.name == 'nt':
                windows_ctrl_c(process)
                shutdown = 'CTRL_C_EVENT'
            else:
                process.send_signal(signal.SIGTERM)
                shutdown = 'SIGTERM'
            try:
                exit_code = process.wait(timeout=args.shutdown_timeout)
            except subprocess.TimeoutExpired as error:
                raise Failure('graceful shutdown timed out; forced cleanup is NOT a pass') from error
            require(exit_code == 0, f'backend exited {exit_code} after {shutdown}, expected 0')
            log.seek(0)
            output = log.read().decode('utf-8', errors='replace')
            for marker in ('rust backend listening', 'shutdown signal received',
                           'rust backend stopped gracefully'):
                require(marker in output, f'missing backend lifecycle log: {marker}')
            with socket.socket() as probe:
                probe.settimeout(2)
                require(probe.connect_ex(('127.0.0.1', port)) != 0,
                        'listener remained reachable after backend exit')
            print(f'PASS [{mode}]: health/live/ready process-only, OpenAPI, '
                  f'HTTP 200 auth 203/204/206, query credentials rejected, '
                  f'request ID, /api=404; {shutdown}, exit=0, graceful logs, listener closed')
        except BaseException:
            log.seek(0)
            print(log.read().decode('utf-8', errors='replace'), file=sys.stderr)
            raise
        finally:
            if process is not None and process.poll() is None:
                print('FAIL: force-killing backend for cleanup, not graceful shutdown', file=sys.stderr)
                process.kill()
                process.wait(timeout=10)


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('binary', type=Path, help='the actual produced release artifact')
    parser.add_argument('--qemu', help='qemu-aarch64 executable for Linux user-mode emulation')
    parser.add_argument('--sysroot', type=Path, help='arm64 runtime loader/library root for QEMU')
    parser.add_argument('--startup-timeout', type=float, default=60)
    parser.add_argument('--shutdown-timeout', type=float, default=15)
    args = parser.parse_args()
    try:
        smoke(args)
    except (Failure, OSError, ValueError, http.client.HTTPException) as error:
        print(f'FAIL: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
