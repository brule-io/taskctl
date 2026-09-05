"""Acquire a reviewed, digest-pinned build toolchain; never used by consumers."""
import hashlib, json, os, shutil, tarfile, urllib.request, zipfile
from pathlib import Path
from package import ROOT, target

def install():
    pin = json.loads((ROOT/'packaging/graalvm.json').read_text(encoding='utf-8'))
    artifact = pin['artifacts'][target()]
    cache = ROOT/'build/toolchains'/artifact['sha256']
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache/('download.zip' if target().startswith('windows') else 'download.tar.gz')
    if not archive.exists():
        part = archive.with_suffix('.part')
        with urllib.request.urlopen(artifact['url'], timeout=120) as response, part.open('wb') as out:
            shutil.copyfileobj(response, out)
        assert hashlib.sha256(part.read_bytes()).hexdigest() == artifact['sha256'], 'GraalVM checksum mismatch'
        part.replace(archive)
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == artifact['sha256'], 'GraalVM checksum mismatch'
    directory = cache/'extracted'
    if not directory.exists():
        directory.mkdir()
        if archive.suffix == '.zip':
            with zipfile.ZipFile(archive) as bundle: bundle.extractall(directory)
        else:
            with tarfile.open(archive) as bundle: bundle.extractall(directory, filter='tar')
    executables = list(directory.rglob('bin/native-image.cmd' if target().startswith('windows') else 'bin/native-image'))
    assert len(executables) == 1, executables
    home = executables[0].parent.parent
    (ROOT/'build/graalvm-home.txt').write_text(str(home), encoding='utf-8')
    if os.environ.get('GITHUB_ENV'):
        with open(os.environ['GITHUB_ENV'], 'a', encoding='utf-8') as env:
            env.write(f'GRAALVM_HOME={home}\n')
    print(json.dumps(dict(home=str(home), release=pin['release'], archive_sha256=artifact['sha256'])))
    return home

if __name__ == '__main__': install()
