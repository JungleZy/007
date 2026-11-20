"""Verify each delivered Debian payload against its isolated style build."""
import hashlib
import json
import lzma
from pathlib import Path
import struct
import tarfile

PROJECT = Path(__file__).resolve().parents[1]


def digest(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verify(style):
    releases = PROJECT / 'out/releases'
    source = PROJECT / 'out/kylin-v10-loongarch64' / style / 'linux-loong64-unpacked'
    report = json.loads((releases / f'kylin-loongarch64-{style}-report.json').read_text(encoding='utf-8'))
    assert report['interfaceStyle'] == style and report['architecture'] == 'loongarch64'
    deb = releases / report['filename']
    assert deb.stat().st_size == report['bytes']
    assert digest(deb) == report['sha256']
    prefix = 'opt/nip-traffic-system-kylin-loongarch64/'
    expected = {prefix + file.relative_to(source).as_posix(): file for file in source.rglob('*') if file.is_file()}
    checked = elf_count = 0
    data_found = False
    with deb.open('rb') as stream:
        assert stream.read(8) == b'!<arch>\n'
        while header := stream.read(60):
            assert len(header) == 60 and header[58:60] == b'`\n'
            length = int(header[48:58])
            if header[:16].decode().strip().rstrip('/') == 'data.tar.xz':
                data_found = True
                with lzma.LZMAFile(stream) as compressed, tarfile.open(fileobj=compressed, mode='r|') as archive:
                    for item in archive:
                        assert item.uid == item.gid == 0
                        if not item.isfile():
                            continue
                        if item.name in expected:
                            original = expected.pop(item.name)
                            checksum = hashlib.sha256()
                            with archive.extractfile(item) as data:
                                start = data.read(64)
                                checksum.update(start)
                                if start[:4] == b'\x7fELF':
                                    assert item.mode == 0o755 and start[4:6] == b'\x02\x01'
                                    assert struct.unpack_from('<H', start, 18)[0] == 258
                                    assert struct.unpack_from('<I', start, 48)[0] == 3
                                    elf_count += 1
                                for chunk in iter(lambda: data.read(1024 * 1024), b''):
                                    checksum.update(chunk)
                            assert checksum.hexdigest() == digest(original), item.name
                            checked += 1
                        elif item.name == 'usr/bin/nip-traffic-system-kylin-loongarch64':
                            assert item.mode == 0o755
                        elif item.name.endswith('.desktop'):
                            assert (' (' + style + ')').encode() in archive.extractfile(item).read()
                break
            stream.seek(length + length % 2, 1)
    assert data_found and not expected
    assert elf_count == report['elfFilesVerified']
    return {'interfaceStyle': style, 'filename': deb.name, 'sha256': report['sha256'],
            'payloadFilesVerified': checked, 'oldAbiElfFilesVerified': elf_count}


if __name__ == '__main__':
    results = []
    for style in ('HJ', 'HJJ', 'LJ'):
        result = verify(style)
        results.append(result)
        print(json.dumps(result, ensure_ascii=True), flush=True)
    (PROJECT / 'out/releases/LoongArch64-verification.json').write_text(
        json.dumps(results, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
