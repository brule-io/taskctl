"""Build a local runtime archive from installDist. No consumer build or writes."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '0.1.0-dev.1'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java-home', type=Path, required=True)
    args = parser.parse_args()
    target = 'windows-x86_64' if platform.system() == 'Windows' else 'linux-x86_64'
    suffix = '.exe' if platform.system() == 'Windows' else ''
    output = ROOT / 'build/distributions'
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='package-', dir=output) as temporary:
        stage = Path(temporary)
        subprocess.run([str(args.java_home / ('bin/jlink' + suffix)), '--add-modules',
                        'java.base,java.logging,java.net.http,jdk.crypto.ec,jdk.unsupported',
                        '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=zip-6',
                        '--output', str(stage / 'runtime')], check=True)
        shutil.copytree(ROOT / 'cli/build/install/taskctl/lib', stage / 'lib')
        java_info = subprocess.run([str(stage / ('runtime/bin/java' + suffix)), '-version'], capture_output=True, text=True, check=True).stderr
        java_info = '\n'.join(line for line in java_info.splitlines() if not line.startswith('NOTE:'))
        (stage / 'distribution.json').write_text(json.dumps({'version': VERSION, 'platform': target,
            'adapter': 'fantastikt-loom-agent-2026', 'donor_revision': 'b932b0eecbc2b6053b3f2235ad2963fd31fbb4c4',
            'java': java_info.strip()}, indent=2) + '\n', encoding='utf-8')
        (stage / 'distribution.properties').write_text('toolVersion=' + VERSION + '\nadapter=fantastikt-loom-agent-2026\n', encoding='utf-8', newline='\n')
        files = sorted(p for p in stage.rglob('*') if p.is_file())
        manifest = ''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.relative_to(stage).as_posix() + '\n' for p in files)
        (stage / 'files.sha256').write_text(manifest, encoding='utf-8', newline='\n')
        files.append(stage / 'files.sha256')
        if target.startswith('windows'):
            archive = output / f'taskctl-{VERSION}-{target}.zip'
            with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as bundle:
                for file in sorted(files):
                    info = zipfile.ZipInfo(file.relative_to(stage).as_posix(), date_time=(2026, 9, 4, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    bundle.writestr(info, file.read_bytes())
        else:
            archive = output / f'taskctl-{VERSION}-{target}.tar.gz'
            with tarfile.open(archive, 'w:gz', dereference=True) as bundle:
                for file in sorted(files):
                    info = bundle.gettarinfo(str(file), arcname=file.relative_to(stage).as_posix())
                    info.mtime = 1788480000
                    info.uid = info.gid = 0
                    info.uname = info.gname = ''
                    with file.open('rb') as content:
                        bundle.addfile(info, content)
        result = {'version': VERSION, 'platform': target, 'file': archive.name,
                  'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(), 'bytes': archive.stat().st_size,
                  'java': java_info.strip()}
        (output / f'{target}.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(result))

if __name__ == '__main__':
    main()
