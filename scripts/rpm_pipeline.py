"""Optional RPM packaging of a verified release; no JVM/native compiler invocation.

Docker is used only for clean Fedora environments. DOCKER_CONTEXT can select a
remote engine; files are copied into isolated containers, never host-mounted.
"""
import argparse, hashlib, io, json, os, re, shutil, subprocess, tarfile, uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
FEDORA_IMAGE='registry.fedoraproject.org/fedora@sha256:3d020c33fbb50af70acaf673e0e6cad94ccef310e9fce6856112e077a5402117'
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path): return json.loads(path.read_text(encoding='utf-8'))
def write(path,value): path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def command(args): return subprocess.check_output([str(a) for a in args],text=True,encoding='utf-8').strip()

def prepare(inputs,work,release):
    manifest=read(inputs/'release-manifest.json')
    assert manifest['contract']=='taskctl.release/alpha2'
    meta=next(a for a in manifest['artifacts'] if a['platform']=='linux-x86_64' and a['implementation']=='native')
    verified=read(inputs/'release-verification.json')['verificationResult']['statement']
    assert verified['predicate']['tag']=='v'+meta['version']
    signed={s['name']:s['digest']['sha256'] for s in verified['subject'] if 'name' in s}
    for name in ('release-manifest.json','toolchain.lock','parity-linux-x86_64.json','corpus-linux-x86_64.json',meta['file'],meta['file']+'.intoto.json'):
        assert sha(inputs/name)==signed[name],name
    assert [s['digest']['sha1'] for s in verified['subject'] if 'uri' in s]==[meta['source_revision']]
    version=meta['version']; assert re.fullmatch(r'\d+\.\d+\.\d+(?:-[a-z]+\.\d+)?',version)
    assert not meta['source_dirty'] and meta['source_revision']==manifest['source_revision']
    archive=inputs/meta['file']; assert sha(archive)==meta['sha256']
    assert sha(inputs/'toolchain.lock')==manifest['toolchains']['toolchain.lock']
    assert f'linux-x86_64.sha256={meta["sha256"]}' in (inputs/'toolchain.lock').read_text(encoding='utf-8')
    for name in ('parity-linux-x86_64.json','corpus-linux-x86_64.json',meta['file']+'.intoto.json'):
        assert sha(inputs/name)==manifest['evidence'][name]
    corpus=read(inputs/'corpus-linux-x86_64.json'); parity=read(inputs/'parity-linux-x86_64.json')
    assert corpus['behavioral_tests']==corpus['jvm_passed']==corpus['native_passed'] and corpus['native_passed']>0
    assert parity['artifacts']['native']==meta['sha256']
    reference=next(a for a in manifest['artifacts'] if a['platform']=='linux-x86_64' and a['implementation']=='jvm')
    assert parity['artifacts']['jvm']==reference['sha256'] and parity['cases'] and all(c['files_equal'] for c in parity['cases'])
    statement=read(inputs/(meta['file']+'.intoto.json'))
    assert statement['subject']==[dict(name=meta['file'],digest=dict(sha256=meta['sha256']))]
    assert statement['predicate']['buildDefinition']['internalParameters']==meta['build_identity']
    sources=work/'rpmbuild/SOURCES'; sources.mkdir(parents=True)
    shutil.copy2(archive,sources/archive.name)
    shutil.copy2(inputs/'toolchain.lock',work/'toolchain.lock')
    support=sources/'taskctl-rpm-support.tar.gz'
    # Deterministic support sources, alongside the exact original native archive.
    import gzip
    with support.open('wb') as raw, gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=0) as gz, tarfile.open(fileobj=gz,mode='w') as bundle:
        files={name:ROOT/'packaging/rpm'/name for name in ('taskctl-global','taskctl.1','README.rpm.md')}
        files['toolchain.lock']=inputs/'toolchain.lock'
        for name,path in sorted(files.items()):
            data=path.read_bytes(); info=tarfile.TarInfo(name); info.size=len(data); info.mode=0o644
            bundle.addfile(info,io.BytesIO(data))
    (sources/'taskctl-rpm-sources.sha256').write_text(''.join(f'{sha(p)}  {p.name}\n' for p in (sources/archive.name,support)),encoding='utf-8',newline='\n')
    rpm_version=version.replace('-','~')
    spec=(ROOT/'packaging/rpm/taskctl.spec.in').read_text(encoding='utf-8').replace('@UPSTREAM_VERSION@',version).replace('@RPM_VERSION@',rpm_version).replace('@RPM_RELEASE@',str(release))
    (work/'taskctl.spec').write_text(spec,encoding='utf-8',newline='\n')
    packaging_revision=command(['git','rev-parse','HEAD'])
    packaging_dirty=bool(command(['git','status','--porcelain']))
    identity=dict(contract='taskctl.rpm/alpha1',version=version,rpm_version=rpm_version,release=release,
        platform='linux-x86_64',implementation='native',upstream=meta,upstream_manifest_sha256=sha(inputs/'release-manifest.json'),
        upstream_parity_sha256=sha(inputs/'parity-linux-x86_64.json'),upstream_corpus_sha256=sha(inputs/'corpus-linux-x86_64.json'),
        toolchain_lock_sha256=sha(inputs/'toolchain.lock'),packaging_revision=packaging_revision,packaging_dirty=packaging_dirty,
        fedora_image=FEDORA_IMAGE,spec_sha256=sha(work/'taskctl.spec'),sources={p.name:sha(p) for p in sources.iterdir()},
        graalvm=meta['build_identity']['graalvm'],compiler_invoked=False)
    write(work/'input.json',identity)
    for name in ('rpm_build.py','rpm_test.py'):
        shutil.copy2(ROOT/'scripts'/name,work/name)
    shutil.copy2(ROOT/'packaging/rpm/lint-exceptions.json',work/'lint-exceptions.json')
    return identity

def container(image,work,script,output):
    name='taskctl-rpm-'+uuid.uuid4().hex
    command(['docker','create','--name',name,'--network','none',image,'python3','/work/'+script])
    try:
        subprocess.run(['docker','cp',str(work)+os.sep+'.',name+':/work'],check=True)
        run=subprocess.run(['docker','start','--attach',name])
        # Preserve diagnostics even on failure.
        output.mkdir(parents=True,exist_ok=True)
        subprocess.run(['docker','cp',name+':/work/output/.',str(output)],check=False)
        assert run.returncode==0, f'{script} failed; inspect {output}'
        assert command(['docker','inspect','--format','{{.HostConfig.NetworkMode}}',name])=='none'
    finally:
        subprocess.run(['docker','rm','-f',name],check=True,stdout=subprocess.DEVNULL)

def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--inputs',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True); parser.add_argument('--release',type=int,default=1)
    parser.add_argument('--build-network',choices=['default','host'],default='default',help='Dependency image setup only; package build and tests always use network=none')
    args=parser.parse_args(); assert args.release>=1
    inputs=args.inputs.resolve(); output=args.output.resolve(); assert not output.exists(),'Use a fresh output directory'
    work=output/'work'; work.mkdir(parents=True)
    identity=prepare(inputs,work,args.release)
    for target in ('runtime','builder'):
        subprocess.run(['docker','build','--network',args.build_network,'--target',target,'-t',f'taskctl-rpm-{target}:f44',str(ROOT/'packaging/rpm')],check=True)
    container('taskctl-rpm-builder:f44',work,'rpm_build.py',output/'build')
    test=output/'test-input'; test.mkdir()
    for name in ('input.json','rpm_test.py','toolchain.lock'): shutil.copy2(work/name,test/name)
    shutil.copytree(output/'build',test/'packages')
    shutil.copy2(inputs/identity['upstream']['file'],test/identity['upstream']['file'])
    container('taskctl-rpm-runtime:f44',test,'rpm_test.py',output/'test')
    proof=read(output/'test/rpm-test.json'); assert proof['passed'] and proof['canonical_executable_unchanged']
    published=output/'artifacts'; published.mkdir()
    build=read(output/'build/rpm-build.json')
    for record in build['artifacts']:
        source=output/'build'/record['file']; assert sha(source)==record['sha256']
        shutil.copy2(source,published/source.name)
    for path in (work/'taskctl.spec',output/'build/rpm-build.json',output/'build/rpmlint.txt',output/'test/rpm-test.json'):
        shutil.copy2(path,published/path.name)
    shutil.copy2(inputs/'toolchain.lock',published/'upstream-toolchain.lock')
    metadata=identity|dict(artifacts=build['artifacts'],build_environment=build['environment'],test=proof,
        evidence={p.name:sha(p) for p in published.iterdir() if p.suffix!='.rpm'})
    write(published/'rpm-manifest.json',metadata)
    for record in build['artifacts']:
        provenance=dict(_type='https://in-toto.io/Statement/v1',subject=[dict(name=record['file'],digest=dict(sha256=record['sha256']))],
            predicateType='https://slsa.dev/provenance/v1',predicate=dict(buildDefinition=dict(
                buildType='https://github.com/brule-io/taskctl/rpm-repack/alpha1',
                externalParameters=dict(upstream_version=identity['version'],rpm_release=args.release,implementation='native'),
                internalParameters=identity|dict(build_environment=build['environment']),
                resolvedDependencies=[dict(uri='https://github.com/brule-io/taskctl/releases/download/v'+identity['version']+'/'+identity['upstream']['file'],digest=dict(sha256=identity['upstream']['sha256'])),
                    dict(uri='git+https://github.com/brule-io/taskctl',digest=dict(gitCommit=identity['packaging_revision']))]),
                runDetails=dict(builder=dict(id='https://github.com/brule-io/taskctl/.github/workflows/rpm.yml'),
                    metadata=dict(invocationId=os.environ.get('GITHUB_RUN_ID','local')+'/'+os.environ.get('GITHUB_RUN_ATTEMPT','1')))))
        write(published/(record['file']+'.intoto.json'),provenance)
    (published/'RPM-SHA256SUMS').write_text(''.join(f'{sha(p)}  {p.name}\n' for p in sorted(published.iterdir())),encoding='utf-8',newline='\n')
    print(json.dumps(dict(artifacts=str(published),upstream=identity['version'],fedora=44,passed=True)))

if __name__=='__main__': main()
