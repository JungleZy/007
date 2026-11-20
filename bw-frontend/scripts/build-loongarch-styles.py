"""Build isolated HJ/HJJ/LJ desktop payloads and publish their Debian packages in out/releases."""
import json
import os
from pathlib import Path
import subprocess
import sys

PROJECT = Path(__file__).resolve().parents[1]
STYLES = ('HJ', 'HJJ', 'LJ')


def main():
    os.environ['PYTHONIOENCODING'] = 'utf-8'
    releases = PROJECT / 'out/releases'
    releases.mkdir(parents=True, exist_ok=True)
    cache = PROJECT / '.cache'
    cache.mkdir(exist_ok=True)
    version = json.loads((PROJECT / 'package.json').read_text(encoding='utf-8'))['version']
    for style in STYLES:
        if (PROJECT / 'out/kylin-v10-loongarch64' / style / 'linux-loong64-unpacked').exists():
            raise ValueError('Archive existing style payload before rebuilding: ' + style)
        if any(releases.glob(f'*-LoongArch64-{style}-{version}-1_loongarch64.deb')):
            raise ValueError('Release already exists: ' + style)
    jobs = []
    logs = []
    try:
        for style in STYLES:
            log = (cache / f'loongarch-style-{style}.log').open('w', encoding='utf-8')
            logs.append(log)
            print('Building frontend and desktop: ' + style, flush=True)
            command = (['cmd.exe', '/c', 'npm'] if os.name == 'nt' else ['npm']) + [
                'run', 'build-e-kylin-loongarch64', '--', '--style', style]
            subprocess.run(command, cwd=PROJECT, stdout=log, stderr=subprocess.STDOUT, check=True)
            source = PROJECT / 'out/kylin-v10-loongarch64' / style / 'linux-loong64-unpacked'
            print('Compressing Debian package: ' + style, flush=True)
            jobs.append((style, subprocess.Popen([sys.executable, 'scripts/build-kylin-deb.py',
                '--arch', 'loongarch64', '--style', style, '--source', str(source),
                '--output', str(releases), '--compression', '1'], cwd=PROJECT, stdout=log, stderr=subprocess.STDOUT)))
        for style, job in jobs:
            if job.wait() != 0:
                raise RuntimeError('Package failed; see style log: ' + style)
        reports = [json.loads((releases / f'kylin-loongarch64-{style}-report.json').read_text(encoding='utf-8')) for style in STYLES]
        (releases / 'LoongArch64-SHA256SUMS.txt').write_text(''.join(
            item['sha256'] + '  ' + item['filename'] + '\n' for item in reports), encoding='utf-8')
        print('All three LoongArch packages built in ' + str(releases), flush=True)
    finally:
        # Never leave compression jobs writing after this command has returned.
        for _, job in jobs:
            job.wait()
        for log in logs:
            log.close()


if __name__ == '__main__':
    main()
