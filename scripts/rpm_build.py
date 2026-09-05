"""Runs only in the isolated Fedora builder, without a network."""
import hashlib, json, re, shutil, subprocess, tarfile
from pathlib import Path

root=Path('/work'); output=root/'output'; output.mkdir(exist_ok=True)
meta=json.loads((root/'input.json').read_text()); exceptions=json.loads((root/'lint-exceptions.json').read_text())
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def run(args): return subprocess.check_output([str(a) for a in args],text=True).strip()
artifacts=[]
for release in (meta['release']-1,meta['release']):
    subprocess.run(['rpmbuild','-ba','--define','_topdir /work/rpmbuild','--define',f'packaging_release {release}',str(root/'taskctl.spec')],check=True)
    rpms=list((root/'rpmbuild/RPMS/x86_64').glob(f'taskctl-*-{release}.fc44.x86_64.rpm'))
    sources=list((root/'rpmbuild/SRPMS').glob(f'taskctl-*-{release}.fc44.src.rpm'))
    assert len(rpms)==len(sources)==1,(rpms,sources)
    if release==meta['release']-1:
        shutil.copy2(rpms[0],output/'upgrade-baseline.rpm'); continue
    for path,kind in ((rpms[0],'binary-rpm'),(sources[0],'source-rpm')):
        shutil.copy2(path,output/path.name)
        artifacts.append(dict(file=path.name,sha256=sha(path),bytes=path.stat().st_size,format=kind,implementation='native'))
    binary=rpms[0]

scripts=run(['rpm','-qp','--scripts','--triggers','--filetriggers',binary]); assert not scripts,scripts
files=run(['rpm','-qpl',binary]).splitlines()
assert files and all(p.startswith(('/usr/bin/taskctl','/usr/libexec/taskctl','/usr/share/taskctl','/usr/share/doc/taskctl','/usr/share/licenses/taskctl','/usr/share/man/man1/taskctl.1')) for p in files),files
assert not any('/.agents' in p or '/.taskctl' in p for p in files)
requires=run(['rpm','-qpR',binary]); assert not re.search(r'java|jre|jvm|gradle',requires,re.I),requires
lint=subprocess.run(['rpmlint',str(root/'taskctl.spec'),str(binary),str(sources[0])],capture_output=True,text=True)
text=lint.stdout+lint.stderr; (output/'rpmlint.txt').write_text(text)
print(text)
findings=[]
for line in text.splitlines():
    match=re.search(r': ([EW]): ([a-z0-9-]+)(?:\s|$)',line)
    if match: findings.append(dict(severity=match[1],tag=match[2],line=line))
unexpected=[f for f in findings if f['tag'] not in exceptions]
assert not unexpected,unexpected
assert lint.returncode==0 or findings,('rpmlint failed without recognizable diagnostics',lint.returncode)
assert len(findings)==sum(int(n) for n in re.findall(r'(\d+) (?:errors?|warnings?)',text)), 'Unparsed lint findings'
for finding in findings: finding['reason']=exceptions[finding['tag']]
environment=dict(os_release=Path('/etc/os-release').read_text(),rpm=run(['rpm','--version']),
    rpmlint=run(['rpm','-q','rpmlint']),packages=run(['rpm','-qa','--qf','%{NAME}-%{VERSION}-%{RELEASE}.%{ARCH}\n']).splitlines())
proof=dict(contract='taskctl.rpm-build/alpha1',artifacts=artifacts,files=files,requires=requires.splitlines(),scriptlets=scripts,
    lint=dict(exit_code=lint.returncode,findings=findings,unexpected=[]),environment=environment,
    upstream_sha256=meta['upstream']['sha256'],compiler_invoked=False)
(output/'rpm-build.json').write_text(json.dumps(proof,indent=2)+'\n')
