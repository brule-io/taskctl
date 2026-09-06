"""Deterministic native/JVM archives with a common, verified launcher contract."""
import argparse, gzip, hashlib, json, os, platform, re, shutil, subprocess, tarfile, tempfile, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
VERSION = (ROOT/'VERSION').read_text(encoding='utf-8').strip()
EPOCH = 1788480000

def target():
    return {('Windows','amd64'):'windows-x86_64', ('Linux','x86_64'):'linux-x86_64',
            ('Darwin','arm64'):'macos-aarch64'}[(platform.system(), platform.machine().lower())]

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def output_of(command):
    result = subprocess.run([str(p) for p in command], cwd=ROOT, capture_output=True, text=True, check=True)
    return '\n'.join(line for line in (result.stdout + result.stderr).splitlines() if not line.startswith('NOTE:')).strip()

def third_party_notices(stage, libraries, graal_home=None):
    # Native linking removes the JAR/runtime containers that previously carried
    # these notices. Preserve their exact bytes in one separately readable bundle.
    notices={}
    for library in libraries:
        with zipfile.ZipFile(library) as jar:
            for member in jar.namelist():
                if not member.endswith('/') and re.search(r'(^|/)(license|notice|copying|copyright)([._/-]|$)',member,re.I):
                    notices['libraries/'+library.name+'/'+member]=jar.read(member)
    if graal_home:
        for path in sorted((graal_home/'legal').rglob('*')):
            if path.is_file(): notices['graalvm/'+path.relative_to(graal_home/'legal').as_posix()]=path.read_bytes()
    with zipfile.ZipFile(stage/'THIRD-PARTY-NOTICES.zip','w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as bundle:
        for name,data in sorted(notices.items()):
            info=zipfile.ZipInfo(name,date_time=(2026,9,4,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=0o100644 << 16; bundle.writestr(info,data)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--kind', choices=['jvm','native'], default='jvm')
    parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', '')))
    args = parser.parse_args()
    system = target(); windows = system.startswith('windows'); suffix = '.exe' if windows else ''
    output = ROOT/'build/distributions'; output.mkdir(parents=True, exist_ok=True)
    revision = output_of(['git','rev-parse','HEAD'])
    dirty = bool(output_of(['git','status','--porcelain']))
    libraries = sorted((ROOT/'cli/build/install/taskctl/lib').glob('*.jar'))
    assert libraries, 'Build :cli:installDist first'
    inputs = {p.name:digest(p) for p in libraries}
    identity = dict(implementation=args.kind, source_revision=revision, source_dirty=dirty,
        kotlin='2.4.10', gradle='9.6.0', libraries=inputs)
    with tempfile.TemporaryDirectory(prefix='package-', dir=output) as temporary:
        stage = Path(temporary)
        if args.kind == 'jvm':
            subprocess.run([str(args.java_home/('bin/jlink'+suffix)), '--add-modules',
                'java.base,java.logging,java.net.http,jdk.crypto.ec,jdk.unsupported',
                '--strip-debug','--no-header-files','--no-man-pages','--compress=zip-6',
                '--output',str(stage/'runtime')], check=True)
            shutil.copytree(ROOT/'cli/build/install/taskctl/lib',stage/'lib')
            identity['java'] = output_of([stage/('runtime/bin/java'+suffix), '-version'])
            classpath = ':'.join('$base/lib/'+p.name for p in libraries)
            (stage/'taskctl').write_text('#!/bin/sh\nset -eu\nbase=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)\n'
                f'exec "$base/runtime/bin/java" "-Dtaskctl.distribution=$base" -cp "{classpath}" io.brule.tasking.cli.MainKt "$@"\n', encoding='utf-8', newline='\n')
        else:
            binary = ROOT/'cli/build/native/nativeCompile'/('taskctl'+suffix)
            assert binary.is_file(), 'Build :cli:nativeCompile first'
            shutil.copyfile(binary,stage/('taskctl'+suffix))
            for dll in binary.parent.glob('*.dll'): shutil.copyfile(dll,stage/dll.name)
            pin = json.loads((ROOT/'packaging/graalvm.json').read_text(encoding='utf-8'))
            home = Path(os.environ.get('GRAALVM_HOME') or (ROOT/'build/graalvm-home.txt').read_text(encoding='utf-8'))
            identity['graalvm'] = dict(distribution=pin['distribution'], release=pin['release'],
                toolchain=pin['artifacts'][system], native_image=output_of([home/('bin/native-image.cmd' if windows else 'bin/native-image'), '--version']),
                native_build_tools='0.11.5', arguments=['--no-fallback','-march=compatibility','-J-Xmx5g'], resources=['VERSION'],
                executable_sha256=digest(binary))
            assert pin['java'] in identity['graalvm']['native_image'], 'Unexpected Native Image toolchain'
        (stage/'bootstrap').mkdir()
        third_party_notices(stage,libraries,home if args.kind=='native' else None)
        for name in ('taskctl','taskctl.ps1','taskctl.bat'):
            shutil.copyfile(ROOT/'packaging'/name,stage/'bootstrap'/name)
        shutil.copyfile(ROOT/'NOTICE.md',stage/'NOTICE.md')
        shutil.copyfile(ROOT/'LICENSE',stage/'LICENSE')
        shutil.copyfile(ROOT/'README.md',stage/'README.md')
        (stage/'docs').mkdir()
        for name in ('SEMANTIC-0.3.md','BOOTSTRAP.md','NATIVE.md','IMPORT.md','PRE-V1.md','EXTENSIONS.md','VERSIONING.md'):
            shutil.copyfile(ROOT/'docs'/name,stage/'docs'/name)
        script = (ROOT/'packaging/distribution.ps1').read_text(encoding='utf-8')
        script = script.replace('__IMPLEMENTATION__',args.kind).replace('__LIBRARIES__',';'.join(p.name for p in libraries))
        (stage/'taskctl.ps1').write_text(script,encoding='utf-8',newline='\n')
        body = '"%~dp0taskctl.exe" %*' if args.kind == 'native' else 'powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0taskctl.ps1" %*'
        (stage/'taskctl.bat').write_text('@echo off\r\n'+body+'\r\nexit /b %ERRORLEVEL%\r\n',encoding='utf-8')
        if (stage/'taskctl').exists(): (stage/'taskctl').chmod(0o755)
        (stage/'bootstrap/taskctl').chmod(0o755)
        entrypoint = ('taskctl.exe' if args.kind == 'native' else 'taskctl.ps1') if windows else 'taskctl'
        distribution = dict(version=VERSION, platform=system, implementation=args.kind, source_revision=revision,
            source_dirty=dirty, license='Apache-2.0', license_sha256=digest(ROOT/'LICENSE'), native_v1='not-frozen', launcher_contract='taskctl.launcher/1', entrypoint=entrypoint, build_identity=identity)
        (stage/'distribution.json').write_text(json.dumps(distribution,indent=2)+'\n',encoding='utf-8',newline='\n')
        (stage/'distribution.properties').write_text(f'toolVersion={VERSION}\nlauncherContract=taskctl.launcher/1\nentrypoint={entrypoint}\n',encoding='utf-8',newline='\n')
        files = sorted(p for p in stage.rglob('*') if p.is_file())
        (stage/'files.sha256').write_text(''.join(digest(p)+'  '+p.relative_to(stage).as_posix()+'\n' for p in files),encoding='utf-8',newline='\n')
        files.append(stage/'files.sha256')
        archive = output/f'taskctl-{VERSION}-{args.kind}-{system}{".zip" if windows else ".tar.gz"}'
        if windows:
            with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as bundle:
                for p in sorted(files):
                    info=zipfile.ZipInfo(p.relative_to(stage).as_posix(),date_time=(2026,9,4,0,0,0))
                    info.compress_type=zipfile.ZIP_DEFLATED; info.external_attr=0o100644 << 16
                    bundle.writestr(info,p.read_bytes())
        else:
            with archive.open('wb') as raw, gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=EPOCH) as compressed:
                with tarfile.open(fileobj=compressed,mode='w',dereference=True) as bundle:
                    for p in sorted(files):
                        info=bundle.gettarinfo(str(p),arcname=p.relative_to(stage).as_posix())
                        info.mtime=EPOCH; info.uid=info.gid=0; info.uname=info.gname=''
                        info.mode=0o755 if os.access(p,os.X_OK) else 0o644
                        with p.open('rb') as content: bundle.addfile(info,content)
        result = distribution | dict(file=archive.name,sha256=digest(archive),bytes=archive.stat().st_size)
        (output/f'{args.kind}-{system}.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
        statement = dict(_type='https://in-toto.io/Statement/v1',subject=[dict(name=archive.name,digest=dict(sha256=result['sha256']))],
            predicateType='https://slsa.dev/provenance/v1',predicate=dict(
                buildDefinition=dict(buildType='https://github.com/brule-io/taskctl/distribution/alpha1',
                    externalParameters=dict(version=VERSION,platform=system,implementation=args.kind),internalParameters=identity,
                    resolvedDependencies=[dict(uri='git+https://github.com/brule-io/taskctl',digest=dict(gitCommit=revision))]),
                runDetails=dict(builder=dict(id='https://github.com/brule-io/taskctl/.github/workflows/ci.yml'),
                    metadata=dict(invocationId=os.environ.get('GITHUB_RUN_ID','local')+'/'+os.environ.get('GITHUB_RUN_ATTEMPT','1')))))
        (output/(archive.name+'.intoto.json')).write_text(json.dumps(statement,indent=2)+'\n',encoding='utf-8',newline='\n')
        print(json.dumps(dict(file=archive.name,sha256=result['sha256'],bytes=result['bytes'],implementation=args.kind)))

if __name__=='__main__': main()
