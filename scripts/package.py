"""Deterministic runtime distributions; no consumer build or repository writes."""
import argparse, gzip, hashlib, json, os, platform, shutil, subprocess, tarfile, tempfile, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
VERSION = (ROOT / 'VERSION').read_text().strip()
EPOCH = 1788480000

def target():
    pair = (platform.system(), platform.machine().lower())
    return {('Windows','amd64'):'windows-x86_64', ('Linux','x86_64'):'linux-x86_64',
            ('Darwin','arm64'):'macos-aarch64'}[pair]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', '')))
    args = parser.parse_args()
    system = target(); suffix = '.exe' if system.startswith('windows') else ''
    output = ROOT / 'build/distributions'; output.mkdir(parents=True, exist_ok=True)
    revision = subprocess.check_output(['git','rev-parse','HEAD'], cwd=ROOT, text=True).strip()
    dirty = bool(subprocess.check_output(['git','status','--porcelain'], cwd=ROOT))
    with tempfile.TemporaryDirectory(prefix='package-', dir=output) as temporary:
        stage = Path(temporary)
        subprocess.run([str(args.java_home / ('bin/jlink' + suffix)), '--add-modules',
            'java.base,java.logging,java.net.http,jdk.crypto.ec,jdk.unsupported',
            '--strip-debug','--no-header-files','--no-man-pages','--compress=zip-6',
            '--output',str(stage/'runtime')],check=True)
        shutil.copytree(ROOT/'cli/build/install/taskctl/lib',stage/'lib')
        (stage/'bootstrap').mkdir()
        for name in ('taskctl','taskctl.ps1','taskctl.bat'):
            shutil.copyfile(ROOT/'packaging'/name, stage/'bootstrap'/name)
        shutil.copyfile(ROOT/'NOTICE.md',stage/'NOTICE.md')
        (stage/'taskctl').write_text('#!/bin/sh\nset -eu\nbase=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)\nexec "$base/runtime/bin/java" "-Dtaskctl.distribution=$base" -cp "$base/lib/*" io.brule.tasking.cli.MainKt "$@"\n',encoding='utf-8',newline='\n')
        (stage/'taskctl').chmod(0o755)
        (stage/'bootstrap/taskctl').chmod(0o755)
        (stage/'taskctl.bat').write_text('@echo off\r\n"%~dp0runtime\\bin\\java.exe" "-Dtaskctl.distribution=%~dp0." -cp "%~dp0lib\\*" io.brule.tasking.cli.MainKt %*\r\nexit /b %ERRORLEVEL%\r\n',encoding='utf-8')
        java = subprocess.run([str(stage/('runtime/bin/java'+suffix)),'-version'],capture_output=True,text=True,check=True).stderr
        java = '\n'.join(line for line in java.splitlines() if not line.startswith('NOTE:'))
        distribution = dict(version=VERSION,platform=system,source_revision=revision,source_dirty=dirty,java=java.strip(),native_v1='not-frozen')
        (stage/'distribution.json').write_text(json.dumps(distribution,indent=2)+'\n',encoding='utf-8',newline='\n')
        (stage/'distribution.properties').write_text('toolVersion='+VERSION+'\n',encoding='utf-8',newline='\n')
        files=sorted(p for p in stage.rglob('*') if p.is_file())
        (stage/'files.sha256').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(stage).as_posix()+'\n' for p in files),encoding='utf-8',newline='\n')
        files.append(stage/'files.sha256')
        extension='.zip' if suffix else '.tar.gz'
        archive=output/f'taskctl-{VERSION}-{system}{extension}'
        if suffix:
            with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as bundle:
                for p in sorted(files):
                    info=zipfile.ZipInfo(p.relative_to(stage).as_posix(),date_time=(2026,9,4,0,0,0))
                    info.compress_type=zipfile.ZIP_DEFLATED
                    info.external_attr=0o100644 << 16
                    bundle.writestr(info,p.read_bytes())
        else:
            with archive.open('wb') as raw, gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=EPOCH) as compressed:
                with tarfile.open(fileobj=compressed,mode='w',dereference=True) as bundle:
                    for p in sorted(files):
                        info=bundle.gettarinfo(str(p),arcname=p.relative_to(stage).as_posix())
                        info.mtime=EPOCH; info.uid=info.gid=0; info.uname=info.gname=''
                        info.mode=0o755 if os.access(p,os.X_OK) else 0o644
                        with p.open('rb') as content: bundle.addfile(info,content)
        result=distribution | dict(file=archive.name,sha256=hashlib.sha256(archive.read_bytes()).hexdigest(),bytes=archive.stat().st_size)
        (output/f'{system}.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
        print(json.dumps(result))
if __name__=='__main__': main()
