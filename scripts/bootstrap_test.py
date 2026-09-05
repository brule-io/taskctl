"""Exercise only distribution/bootstrap interfaces with no build tools on PATH."""
import argparse, hashlib, json, os, platform, shutil, subprocess, tarfile, tempfile, zipfile
from pathlib import Path
from package import target
ROOT = Path(__file__).resolve().parents[1]

def fingerprint(root):
    return {p.relative_to(root).as_posix(): (hashlib.sha256(p.read_bytes()).hexdigest(), p.stat().st_mtime_ns)
            for p in root.rglob('*') if p.is_file()}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--lock', type=Path, help='Exercise real release URLs instead of local artifacts')
    args = parser.parse_args()
    system = target(); windows = system.startswith('windows')
    metadata = json.loads((ROOT/f'build/distributions/{system}.json').read_text())
    archive = ROOT/'build/distributions'/metadata['file']
    output = ROOT/'build/proof'; output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='bootstrap ', dir=output) as temporary:
        work=Path(temporary); distribution=work/'distribution'; distribution.mkdir()
        if windows:
            with zipfile.ZipFile(archive) as bundle: bundle.extractall(distribution)
        else:
            with tarfile.open(archive) as bundle: bundle.extractall(distribution, filter='data')
        project=work/'consumer with spaces'; project.mkdir(); (project/'.taskctl').mkdir()
        for p in (distribution/'bootstrap').iterdir(): shutil.copy2(p,project/p.name)
        lock=(args.lock.read_text() if args.lock else f'lockFormat=2\nwrapperVersion=2\ntoolVersion={metadata["version"]}\n{system}.url={archive.as_uri()}\n{system}.sha256={metadata["sha256"]}\n')
        (project/'.taskctl/toolchain.lock').write_text(lock,encoding='utf-8',newline='\n')
        env=os.environ.copy()
        for k in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'): env.pop(k,None)
        env.update(TASKCTL_CACHE=str(work/'cache'),JAVA_HOME=str(work/'absent-java'),GRADLE_USER_HOME=str(work/'absent-gradle'))
        if windows:
            shell=Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/powershell.exe'
            env['PATH']=str(Path(os.environ['SystemRoot'])/'System32')
            launcher=[str(shell),'-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',str(project/'taskctl.ps1')]
        else:
            binaries=work/'bootstrap-bin'; binaries.mkdir()
            for name in ('uname','dirname','mkdir','curl','mktemp','sha256sum','shasum','mv','rm','rmdir','sleep','tar','sed','grep','gzip'):
                found=shutil.which(name)
                if found: (binaries/name).symlink_to(found)
            env['PATH']=str(binaries); launcher=[str(project/'taskctl')]
        def run(arguments, code=0, extra=None):
            p=subprocess.run(launcher+arguments,cwd=work,env=env|(extra or {}),capture_output=True,text=True,encoding='utf-8',timeout=120)
            assert p.returncode==code,(arguments,p.returncode,p.stdout,p.stderr)
            return p
        before=fingerprint(project)
        assert 'offline' in run(['info'],2,{'TASKCTL_OFFLINE':'1'}).stderr
        info=json.loads(run(['info','--format','json']).stdout)
        assert info['version']==metadata['version']
        assert json.loads(run(['info','--format','json'],extra={'TASKCTL_OFFLINE':'1'}).stdout)==info
        assert fingerprint(project)==before
        pin=project/'.taskctl/toolchain.lock'
        pin.write_text(lock.replace(metadata['version'],'99.99.99'),encoding='utf-8',newline='\n')
        assert 'version' in run(['info'],2).stderr.lower()
        pin.write_text(lock,encoding='utf-8',newline='\n')
        cached=work/'cache'/metadata['sha256']/'home'
        jar=next((cached/'lib').glob('*.jar')); original=jar.read_bytes(); jar.write_bytes(original+b'tampered')
        assert 'checksum' in run(['info'],2).stderr.lower()
        jar.write_bytes(original)
        # A classpath injection is ignored: only manifest-bound libraries run.
        (cached/'lib/unlisted.jar').write_bytes(b'not a jar')
        run(['info'])
        pin.write_text(lock.replace(metadata['sha256'],'0'*64),encoding='utf-8',newline='\n')
        assert 'checksum' in run(['info'],2).stderr.lower()
        result=dict(platform=system,version=metadata['version'],archive_sha256=metadata['sha256'],transport='release' if args.lock else 'file',
            checks=['cold acquisition','warm offline operation','no Java/Gradle/Git/Python on consumer PATH','consumer bytes and mtimes unchanged',
                    'explicit repository launcher from unrelated cwd','wrong version rejected','corrupt cached library rejected','unlisted JAR ignored','wrong archive digest rejected'])
        (output/f'bootstrap-{system}.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
        print(json.dumps(result))
if __name__=='__main__': main()
