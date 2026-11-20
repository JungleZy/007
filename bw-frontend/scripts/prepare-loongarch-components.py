"""Prepare old-ABI LoongArch components. Requires bsdtar (Windows tar.exe works)."""
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import urllib.parse
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
CACHE = PROJECT / '.cache'
DEST = CACHE / 'kylin-loongarch64-components'
SPEC = json.loads((PROJECT / 'scripts/kylin-loongarch64-components.json').read_text())


def download(url, destination, expected=None):
    if not destination.exists():
        print('Downloading ' + url, flush=True)
        partial = destination.with_suffix(destination.suffix + '.partial')
        subprocess.run(['curl.exe' if shutil.which('curl.exe') else 'curl', '-fL', '--retry', '3',
                        '-C', '-', '-o', str(partial), url], check=True)
        partial.replace(destination)
    actual = hashlib.sha256(destination.read_bytes()).hexdigest()
    if expected and actual != expected:
        raise ValueError('Checksum mismatch: ' + str(destination))
    return actual


def rpm_files(file):
    # bsdtar can convert RPM/cpio payloads to tar without installing the RPM.
    data = subprocess.check_output(['tar', '-cf', '-', '@' + str(file)])
    result = {}
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        for item in archive:
            name = item.name.removeprefix('./')
            if item.isfile():
                result[name] = archive.extractfile(item).read()
            elif item.issym():
                result[name] = item.linkname
    return result


def write(file, data):
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_bytes(data)


def main():
    if DEST.exists():
        raise ValueError('Archive existing components before preparing again: ' + str(DEST))
    CACHE.mkdir(exist_ok=True)
    rpms = json.loads((PROJECT / 'scripts/loongarch-mysql-rpms.json').read_text())
    records = []
    payloads = {}
    for repository, name, filename, checksum in rpms['packages']:
        url = rpms['repository'] + repository + '/loongarch64/release/Packages/' + urllib.parse.quote(filename)
        file = CACHE / filename
        digest = download(url, file, checksum)
        records.append({'name': name, 'url': url, 'sha256': digest})
        payloads[name] = rpm_files(file)
    # Validate all downloaded archives before creating the prepared component directory.
    for component in ('electron', 'java'):
        info = SPEC[component]
        download(info['url'], CACHE / info['url'].rsplit('/', 1)[1], info['sha256'])
    DEST.mkdir()
    with zipfile.ZipFile(CACHE / SPEC['electron']['url'].rsplit('/', 1)[1]) as archive:
        for item in archive.infolist():
            if item.is_dir():
                continue
            relative = Path(item.filename)
            if relative.is_absolute() or '..' in relative.parts:
                raise ValueError('Invalid Electron archive member')
            write(DEST / 'electron' / relative, archive.read(item))
    with tarfile.open(CACHE / SPEC['java']['url'].rsplit('/', 1)[1]) as archive:
        for item in archive:
            if not (item.isfile() or item.issym() or item.islnk()):
                continue
            relative = Path(*Path(item.name).parts[1:])
            if relative.is_absolute() or '..' in relative.parts:
                raise ValueError('Invalid Java archive member')
            if relative.parts and relative.parts[0] in ('bin', 'conf', 'lib', 'legal', 'release'):
                write(DEST / 'java' / relative, archive.extractfile(item).read())
    mysql = DEST / 'mysql'
    write(mysql / 'bin/mysqld', payloads['mysql-server']['usr/libexec/mysqld'])
    for name in ('mysql', 'mysqldump'):
        write(mysql / 'bin' / name, payloads['mysql']['usr/bin/' + name])
    for package, files in payloads.items():
        for name, data in files.items():
            if isinstance(data, bytes) and name.startswith('usr/share/mysql/'):
                write(mysql / 'share' / name.removeprefix('usr/share/mysql/'), data)
                # RPM builds may derive share/mysql from the overridden basedir.
                write(mysql / 'share/mysql' / name.removeprefix('usr/share/mysql/'), data)
            if isinstance(data, bytes) and name.startswith(('usr/share/licenses/', 'usr/share/doc/')):
                write(mysql / 'dependency-licenses' / package / name, data)
            if package not in ('mysql', 'mysql-common', 'mysql-errmsg', 'mysql-server') and name.startswith(('usr/lib64/', 'lib64/')) and '.so' in name:
                if isinstance(data, str):
                    target = (Path(name).parent / data).as_posix()
                    data = files.get(target)
                if isinstance(data, bytes) and data.startswith(b'\x7fELF'):
                    write(mysql / 'lib' / Path(name).name, data)
    licenses = [(name, data) for files in payloads.values() for name, data in files.items()
                if isinstance(data, bytes) and 'mysql' in name and Path(name).name in ('LICENSE', 'COPYING')]
    if not licenses:
        raise ValueError('Missing MySQL license')
    write(mysql / 'LICENSE', licenses[0][1])
    write(mysql / 'README', b'MySQL 8.0.21 from Loongnix Server 8.4; see dependency-licenses and component-sources.json.\n')
    (mysql / 'runtime.json').write_text(json.dumps({'clientFamily': 'mysql', 'clientVersion': '8.0.21',
        'libc': 'glibc', 'minLibc': '2.28', 'testedOn': []}, indent=2))
    (DEST / 'component-sources.json').write_text(json.dumps(records, indent=2))
    (mysql / 'dependency-licenses/component-sources.json').write_text(json.dumps(records, indent=2))
    subprocess.run([sys.executable, str(PROJECT / 'scripts/verify-loongarch-mysql.py'),
                    str(mysql), '--output', str(DEST / 'mysql-inspection.json')], check=True)
    print('Prepared ' + str(DEST), flush=True)


if __name__ == '__main__':
    main()
