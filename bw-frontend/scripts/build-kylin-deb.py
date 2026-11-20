"""Create a Kylin ARM64 or old-ABI LoongArch Debian package with explicit file modes."""
import argparse
import gzip
import hashlib
import io
import json
import lzma
import math
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import struct
import tarfile
import tempfile

PACKAGE = 'nip-traffic-system-kylin-arm64'
DEPENDENCIES = ['libc6 (>= 2.28)', 'libstdc++6 (>= 7)', 'libgcc1 | libgcc-s1', 'libgtk-3-0',
    'libnss3', 'libnspr4', 'libasound2', 'libatk1.0-0', 'libatk-bridge2.0-0', 'libcairo2',
    'libcups2', 'libdbus-1-3', 'libexpat1', 'libglib2.0-0', 'libpango-1.0-0', 'libx11-6',
    'libxcb1', 'libxcomposite1', 'libxdamage1', 'libxext6', 'libxfixes3', 'libxkbcommon0',
    'libxrandr2', 'libgbm1', 'libdrm2', 'libxshmfence1', 'libudev1', 'xdg-utils']

LAUNCHER = '''#!/bin/sh
if [ "$(id -u)" = 0 ]; then
  echo '请使用普通桌面用户启动软件，不要使用 sudo 运行。' >&2
  exit 1
fi
exec /opt/nip-traffic-system-kylin-arm64/nip-traffic-system --no-sandbox "$@"
'''

POSTINST = '''#!/bin/sh
set -e
if [ "$1" = configure ]; then
  mkdir -p /var/lib/nip-traffic-system
  chmod 0777 /var/lib/nip-traffic-system
  if [ -f /var/lib/nip-traffic-system/license.json ]; then
    chmod 0666 /var/lib/nip-traffic-system/license.json
  fi
  if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database /usr/share/applications || true
  fi
fi
exit 0
'''

POSTRM = '''#!/bin/sh
set -e
# Never delete application user data, databases, backups or machine license records.
if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database /usr/share/applications || true
fi
exit 0
'''

def sha256(file):
    digest = hashlib.sha256()
    with file.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''): digest.update(chunk)
    return digest.hexdigest()

def verify_manifest(directory, target='linux-arm64'):
    manifest = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
    if manifest['target'] != target: raise ValueError('Runtime target mismatch: ' + str(directory))
    actual = {file.relative_to(directory).as_posix(): file for file in directory.rglob('*') if file.is_file() and file.name != 'manifest.json'}
    if set(actual) != set(manifest['files']): raise ValueError('Runtime inventory mismatch: ' + str(directory))
    for name, file in actual.items():
        if sha256(file) != manifest['files'][name]: raise ValueError('Runtime checksum mismatch: ' + name)

def file_mode(relative, file, architecture='arm64'):
    with file.open('rb') as stream: header = stream.read(64)
    if header[:4] == b'\x7fELF':
        machine = {'arm64': 183, 'loongarch64': 258}[architecture]
        if len(header) < 64 or header[4:6] != b'\x02\x01' or struct.unpack_from('<H', header, 18)[0] != machine:
            raise ValueError('Non-' + architecture.upper() + ' ELF file in package: ' + relative)
        if architecture == 'loongarch64' and struct.unpack_from('<I', header, 48)[0] != 3:
            raise ValueError('Expected old-ABI LoongArch ELF: ' + relative)
        return 0o755, True
    if header[:2] == b'MZ': raise ValueError('Windows binary in Linux package: ' + relative)
    return (0o755 if header.startswith(b'#!') else 0o644), False

def add_bytes(archive, name, data, mode=0o644):
    info = tarfile.TarInfo(name)
    info.size = len(data)
    info.mode = mode
    info.uid = info.gid = 0
    info.uname = info.gname = 'root'
    archive.addfile(info, io.BytesIO(data))

def add_file(archive, name, file, mode):
    info = tarfile.TarInfo(name)
    info.size = file.stat().st_size
    info.mode = mode
    info.uid = info.gid = 0
    info.uname = info.gname = 'root'
    with file.open('rb') as stream: archive.addfile(info, stream)

def add_parent_directories(archive, names):
    # dpkg requires directory entries before files; tar extraction alone can hide omissions.
    parents = {parent.as_posix() for name in names for parent in PurePosixPath(name).parents
               if parent.as_posix() != '.'}
    for name in sorted(parents, key=lambda value: (value.count('/'), value)):
        info = tarfile.TarInfo(name)
        info.type = tarfile.DIRTYPE
        info.mode = 0o755
        info.uid = info.gid = 0
        info.uname = info.gname = 'root'
        archive.addfile(info)

def write_ar(destination, members):
    with destination.open('wb') as output:
        output.write(b'!<arch>\n')
        for name, file in members:
            size = file.stat().st_size
            header = f'{name + "/":<16}{0:<12}{0:<6}{0:<6}{"100644":<8}{size:<10}`\n'.encode('ascii')
            if len(header) != 60: raise ValueError('Invalid ar member header')
            output.write(header)
            with file.open('rb') as stream: shutil.copyfileobj(stream, output, 1024 * 1024)
            if size % 2: output.write(b'\n')

def build(project, source, output, revision='1', compression=4, architecture='arm64', style=None):
    target = {'arm64': 'linux-arm64', 'loongarch64': 'linux-loong64'}[architecture]
    package = 'nip-traffic-system-kylin-' + architecture
    install = 'opt/' + package
    launcher = LAUNCHER.replace(PACKAGE, package)
    metadata = json.loads((project / 'package.json').read_text(encoding='utf-8'))
    if style is not None:
        if style not in ('HJ', 'HJJ', 'LJ'): raise ValueError('Invalid interface style')
        marker = json.loads((source / 'resources/interface-style.json').read_text())
        if marker['interfaceStyle'] != style or marker['appAsarSha256'] != sha256(source / 'resources/app.asar'):
            raise ValueError('Interface style or ASAR checksum mismatch')
    if not re.fullmatch(r'[1-9][0-9]*', revision): raise ValueError('Invalid Debian revision')
    version = metadata['version'] + '-' + revision
    filename = (f"{metadata['productName']}-LoongArch64-{style}-{version}_{architecture}.deb" if style
                else f'{package}_{version}_{architecture}.deb')
    if style and architecture != 'loongarch64': raise ValueError('Style packages currently require LoongArch64')
    if (output / filename).exists(): raise ValueError('Package already exists: ' + filename)
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:[-+.][A-Za-z0-9.]+)?', version): raise ValueError('Invalid package version')
    verify_manifest(source / 'bin/integrated' / target, target)
    verify_manifest(source / 'bin/file/tools/mysql' / target, target)
    for root in ['bin/integrated', 'bin/file/tools/mysql']:
        if {file.name for file in (source / root).iterdir()} != {target}: raise ValueError('Unexpected target directory')
    files = []
    elf_count = 0
    highest_glibc = (0, 0)
    for file in sorted(source.rglob('*')):
        if file.is_symlink(): raise ValueError('Unexpected package symlink: ' + str(file))
        if not file.is_file(): continue
        relative = file.relative_to(source).as_posix()
        mode, elf = file_mode(relative, file, architecture)
        if elf:
            elf_count += 1
            versions = [tuple(map(int, match.split(b'.'))) for match in re.findall(rb'GLIBC_(\d+\.\d+)', file.read_bytes())]
            highest_glibc = max([highest_glibc] + versions)
            if any(item > (2, 28) for item in versions): raise ValueError('glibc > 2.28 required by ' + relative)
        files.append((relative, file, mode))
    if elf_count < 10: raise ValueError('Incomplete application payload: ' + architecture)
    total_size = sum(file.stat().st_size for _, file, _ in files)
    desktop = f'''[Desktop Entry]
Type=Application
Name={metadata['productName']}{' (' + style + ')' if style else ''}
Comment=Integrated training system for Kylin V10 {architecture}
Exec=/usr/bin/{package}
Icon={package}
Terminal=false
Categories=Education;Utility;
StartupWMClass={metadata['name']}
'''
    control = f'''Package: {package}
Version: {version}
Architecture: {architecture}
Section: education
Priority: optional
Maintainer: Wisdom, Inc <guosheng@jszx.com>
Installed-Size: {math.ceil(total_size / 1024)}
Depends: {', '.join(DEPENDENCIES)}
Description: Integrated traffic training system for Kylin V10 desktop {architecture}
 Includes Electron 30, Java 21, MySQL 8 and application initialization resources.
  Native target validation is required before production rollout.
'''
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='deb-stage-', dir=output) as temp:
        temp = Path(temp)
        data_tar = temp / 'data.tar.xz'
        print('Writing ' + architecture + ' Debian payload with explicit executable permissions...', flush=True)
        with lzma.open(data_tar, 'wb', preset=compression) as compressed, tarfile.open(fileobj=compressed, mode='w|', format=tarfile.GNU_FORMAT) as archive:
            add_parent_directories(archive, [install + '/' + relative for relative, _, _ in files] + [
                'usr/bin/' + package,
                'usr/share/applications/' + package + '.desktop',
                'usr/share/icons/hicolor/512x512/apps/' + package + '.png'])
            for relative, file, mode in files: add_file(archive, install + '/' + relative, file, mode)
            add_bytes(archive, 'usr/bin/' + package, launcher.encode('utf-8'), 0o755)
            add_bytes(archive, 'usr/share/applications/' + package + '.desktop', desktop.encode('utf-8'))
            add_file(archive, 'usr/share/icons/hicolor/512x512/apps/' + package + '.png', project / 'build/icons/512x512.png', 0o644)
        control_tar = temp / 'control.tar.gz'
        with tarfile.open(control_tar, 'w:gz', format=tarfile.GNU_FORMAT) as archive:
            add_bytes(archive, 'control', control.encode('utf-8'))
            add_bytes(archive, 'postinst', POSTINST.encode('utf-8'), 0o755)
            add_bytes(archive, 'postrm', POSTRM.encode('utf-8'), 0o755)
        marker = temp / 'debian-binary'
        marker.write_bytes(b'2.0\n')
        deb = output / filename
        write_ar(deb, [('debian-binary', marker), ('control.tar.gz', control_tar), ('data.tar.xz', data_tar)])
        report = {'filename': deb.name, 'bytes': deb.stat().st_size, 'sha256': sha256(deb),
            'architecture': architecture, 'elfFilesVerified': elf_count, 'highestGlibcSymbol': '.'.join(map(str, highest_glibc)),
            'dependencies': DEPENDENCIES, 'executionStatus': 'Cross-built; Kylin target machine validation pending'}
        if style: report['interfaceStyle'] = style
        report_name = f'kylin-{architecture}-{style}-report.json' if style else f'kylin-{architecture}-report.json'
        (output / report_name).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(report, ensure_ascii=False, indent=2), flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--source')
    parser.add_argument('--output')
    parser.add_argument('--arch', choices=['arm64', 'loongarch64'], default='arm64')
    parser.add_argument('--style', choices=['HJ', 'HJJ', 'LJ'])
    parser.add_argument('--compression', type=int, choices=range(10), default=4)
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    output = Path(args.output) if args.output else project / ('out/kylin-v10-' + args.arch)
    target = 'linux-loong64' if args.arch == 'loongarch64' else 'linux-arm64'
    source = Path(args.source) if args.source else output / (target + '-unpacked')
    build(project, source, output, architecture=args.arch, style=args.style, compression=args.compression)
