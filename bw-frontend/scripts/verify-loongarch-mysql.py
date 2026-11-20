"""Verify bundled MySQL ELF ABI and direct/transitive shared-library inventory."""
import argparse
import importlib.util
import json
from pathlib import Path

spec = importlib.util.spec_from_file_location('loongarch_elf', Path(__file__).with_name('inspect-loongarch-runtime.py'))
elf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(elf)

# Supplied by Kylin's base system; other MySQL dependencies must be bundled.
SYSTEM = {'libc.so.6', 'libm.so.6', 'libpthread.so.0', 'libdl.so.2', 'librt.so.1',
          'libresolv.so.2', 'libutil.so.1', 'ld.so.1', 'libstdc++.so.6', 'libgcc_s.so.1', 'libz.so.1'}


def verify(root):
    files = [root / 'bin' / name for name in ('mysqld', 'mysql', 'mysqldump')]
    files.extend(file for file in (root / 'lib').rglob('*') if file.is_file())
    available = {file.name for file in files}
    reports = []
    for file in files:
        info = elf.inspect_elf(file.relative_to(root).as_posix(), file.read_bytes())
        if info is None:
            raise ValueError('Expected ELF library: ' + str(file))
        if info['machine'] != 258 or info['flags'] != '0x3':
            raise ValueError('Expected old-ABI LoongArch: ' + str(file))
        if info['interpreter'] not in (None, '/lib64/ld.so.1'):
            raise ValueError('Unexpected ELF interpreter: ' + str(file))
        if info['highestGlibcSymbol'] and tuple(map(int, info['highestGlibcSymbol'].split('.'))) > (2, 28):
            raise ValueError('Requires newer glibc: ' + str(file))
        missing = set(info['needed']) - available - SYSTEM
        if missing:
            raise ValueError('Missing libraries for ' + str(file) + ': ' + ', '.join(sorted(missing)))
        reports.append(info)
    return {'elfFilesVerified': len(reports), 'systemLibraries': sorted(SYSTEM), 'binaries': reports,
            'executionStatus': 'Static verification only; native startup and symbol resolution pending'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = verify(args.root)
    if args.output:
        args.output.write_text(json.dumps(report, indent=2) + '\n')
    print('Verified MySQL old-ABI ELF and library inventory: ' + str(report['elfFilesVerified']))
