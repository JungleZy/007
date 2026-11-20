"""Inspect candidate runtime archives without executing or extracting foreign binaries."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import tarfile
import zipfile


def inspect_elf(name, data):
    if not data.startswith(b'\x7fELF'):
        return None
    if len(data) < 64 or data[4:6] != b'\x02\x01':
        raise ValueError('Expected ELF64 little endian: ' + name)
    machine = struct.unpack_from('<H', data, 18)[0]
    flags = struct.unpack_from('<I', data, 48)[0]
    versions = sorted({tuple(map(int, value.split(b'.')))
                       for value in re.findall(rb'GLIBC_(\d+\.\d+)', data)})
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    interpreter = None
    segments = []
    if count and (size < 56 or offset + size * count > len(data)):
        raise ValueError('Invalid ELF program headers: ' + name)
    for index in range(count):
        header = offset + size * index
        kind, _, start, address, _, length, _, _ = struct.unpack_from('<IIQQQQQQ', data, header)
        segments.append((kind, start, address, length))
        if struct.unpack_from('<I', data, header)[0] == 3:
            start = struct.unpack_from('<Q', data, header + 8)[0]
            length = struct.unpack_from('<Q', data, header + 32)[0]
            if start + length > len(data):
                raise ValueError('Invalid ELF interpreter: ' + name)
            interpreter = data[start:start + length].rstrip(b'\0').decode('ascii')
    dynamic = []
    for kind, start, _, length in segments:
        if kind == 2:
            for entry in range(start, start + length, 16):
                tag, value = struct.unpack_from('<qQ', data, entry)
                if tag == 0:
                    break
                dynamic.append((tag, value))
    needed = []
    strings = next((value for tag, value in dynamic if tag == 5), None)
    if strings is not None:
        table = next(start + strings - address for kind, start, address, length in segments
                     if kind == 1 and address <= strings < address + length)
        for tag, value in dynamic:
            if tag == 1:
                end = data.index(b'\0', table + value)
                needed.append(data[table + value:end].decode('ascii'))
    return {'file': name, 'machine': machine, 'flags': hex(flags),
            'interpreter': interpreter, 'needed': needed,
            'highestGlibcSymbol': '.'.join(map(str, versions[-1])) if versions else None}


def inspect_archive(file):
    sha = hashlib.sha256()
    md5 = hashlib.md5()
    with file.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            sha.update(chunk)
            md5.update(chunk)
    binaries = []
    if zipfile.is_zipfile(file):
        with zipfile.ZipFile(file) as archive:
            for member in archive.infolist():
                if member.is_dir():
                    continue
                with archive.open(member) as stream:
                    prefix = stream.read(4)
                    if prefix == b'\x7fELF':
                        binaries.append(inspect_elf(member.filename, prefix + stream.read()))
    else:
        with tarfile.open(file, 'r|*') as archive:
            for member in archive:
                if not member.isfile():
                    continue
                with archive.extractfile(member) as stream:
                    prefix = stream.read(4)
                    if prefix == b'\x7fELF':
                        binaries.append(inspect_elf(member.name, prefix + stream.read()))
    incompatible = [item['file'] for item in binaries if item['machine'] != 258 or
                    (item['highestGlibcSymbol'] and
                     tuple(map(int, item['highestGlibcSymbol'].split('.'))) > (2, 28))]
    return {'archive': file.name, 'sha256': sha.hexdigest(), 'md5': md5.hexdigest(),
            'elfCount': len(binaries), 'incompatibleWithLoongArchGlibc228': incompatible,
            'binaries': binaries,
            'executionStatus': 'Not executed; ABI, dependencies and target startup still require validation'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--sha256', help='Expected publisher SHA-256')
    parser.add_argument('--md5', help='Publisher MD5, where SHA-256 is not published')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = inspect_archive(args.archive)
    for algorithm in ('sha256', 'md5'):
        expected = getattr(args, algorithm)
        if expected and report[algorithm] != expected.lower():
            raise ValueError('Publisher checksum mismatch: ' + algorithm)
    encoded = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(encoded + '\n', encoding='utf-8')
    print(encoded)
    if not report['elfCount'] or report['incompatibleWithLoongArchGlibc228']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
