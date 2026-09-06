"""Clean Fedora install/upgrade/erase with no network and protected project state."""
import hashlib, json, os, pwd, re, shutil, subprocess, tarfile
from pathlib import Path

root=Path('/work'); output=root/'output'; output.mkdir(exist_ok=True)
meta=json.loads((root/'input.json').read_text()); packages=root/'packages'
build=json.loads((packages/'rpm-build.json').read_text())
binary=packages/next(a['file'] for a in build['artifacts'] if a['format']=='binary-rpm')
expected_binary=meta['graalvm']['executable_sha256']
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def fingerprint(path):
    return {p.relative_to(path).as_posix():[sha(p),p.stat().st_mtime_ns,p.stat().st_mode & 0o7777] for p in path.rglob('*') if p.is_file()}
def run(args,code=0,cwd=root,env=None,**identity):
    p=subprocess.run([str(a) for a in args],cwd=cwd,env=env,capture_output=True,text=True,encoding='utf-8',timeout=120,**identity)
    assert p.returncode==code,(args,p.returncode,p.stdout,p.stderr)
    return p.stdout
checks=[]; observations={}; traces={}
def checked(name): checks.append(name); print('PASS:',name,flush=True)

assert Path('/etc/os-release').read_text().find('VERSION_ID=44')>=0
assert sorted(p.name for p in Path('/sys/class/net').iterdir())==['lo']
assert shutil.which('taskctl') is None and shutil.which('java') is None
run(['useradd','--create-home','--shell','/bin/sh','taskctl-test'])
home=Path('/home/taskctl-test'); projects=home/'projects'; projects.mkdir()
sentinel=projects/'existing'; sentinel.mkdir()
for name in ('.agents/keep.yaml','.taskctl/toolchain.lock','.git/config','src/preserve.txt'):
    path=sentinel/name; path.parent.mkdir(parents=True,exist_ok=True); path.write_text('Unrelated state: '+name+'\n')
run(['chown','-R','taskctl-test:taskctl-test',home])
env=os.environ.copy(); env.update(HOME=str(home),TASKCTL_OFFLINE='1',JAVA_HOME='/absent-java',PATH='/usr/bin:/bin')
for name in ('GH_TOKEN','GITHUB_TOKEN','TASKCTL_GITHUB_TOKEN','TASKCTL_REPOSITORY','TASKCTL_DISTRIBUTION'): env.pop(name,None)
account=pwd.getpwnam('taskctl-test')
def user(args,cwd=home): return run(args,cwd=cwd,env=env,user=account.pw_uid,group=account.pw_gid,extra_groups=[])
def transaction(action,path,label):
    before=fingerprint(home)
    trace=output/(label+'-network.trace')
    log=run(['strace','-f','-e','trace=%network','-o',trace,'dnf','-y','--disable-repo=*',action,path])
    (output/(label+'.log')).write_text(log)
    network=trace.read_text()
    assert not re.search(r'(?:socket\(AF_INET6?|sa_family=AF_INET6?)\b',network),network
    assert fingerprint(home)==before,'RPM transaction changed project or user state'
    traces[label]=dict(sha256=sha(trace),internet_socket_calls=0)
    checked(label+' offline transaction preserves project/user bytes, mtimes and modes')

transaction('install',packages/'upgrade-baseline.rpm','install')
assert shutil.which('taskctl')=='/usr/bin/taskctl'
assert sha(Path('/usr/libexec/taskctl/taskctl'))==expected_binary
canonical_files={}
with tarfile.open(root/meta['upstream']['file']) as archive:
    destinations={'taskctl':'/usr/libexec/taskctl/taskctl','LICENSE':'/usr/share/licenses/taskctl/LICENSE','NOTICE.md':'/usr/share/licenses/taskctl/NOTICE.md',
        'THIRD-PARTY-NOTICES.zip':'/usr/share/licenses/taskctl/THIRD-PARTY-NOTICES.zip'}
    destinations.update({name:'/usr/share/taskctl/'+name for name in ('bootstrap/taskctl','bootstrap/taskctl.ps1','bootstrap/taskctl.bat','distribution.json','distribution.properties')})
    for original,destination in destinations.items():
        digest=hashlib.sha256(archive.extractfile(original).read()).hexdigest()
        assert sha(Path(destination))==digest,destination
        canonical_files[destination]=digest
run(['rpm','-V','taskctl'])
checked('installed canonical executable digest and RPM file verification')
before=fingerprint(home)
for args in (['--version'],['-V'],['version']): assert user(['taskctl',*args])==f'taskctl {meta["version"]}\n'
observations['version']=json.loads(user(['taskctl','version','--format','json']))
assert observations['version']['result']['version']==meta['version']
observations['help']=user(['taskctl','help']); assert 'init --repo PATH' in observations['help']
observations['info']=json.loads(user(['taskctl','info','--format','json']))
assert observations['info']['result']['build']['build_identity']==meta['upstream']['build_identity']
assert fingerprint(home)==before and not (home/'.cache/taskctl').exists()
checked('version aliases, JSON version, help and info without repository or acquisition')

fresh=projects/'greenfield'
initialize=['taskctl','init','--repo',str(fresh),'--id','test.fedora-rpm','--toolchain','/usr/share/taskctl/toolchain.lock','--format','json']
plan=json.loads(user(initialize+['--plan'])); assert not fresh.exists()
observations['init']=json.loads(user(initialize)); assert observations['init']['result']['plan_digest']==plan['result']['plan_digest']
assert sha(fresh/'.taskctl/toolchain.lock')==meta['toolchain_lock_sha256']
checked('unprivileged greenfield init uses exact canonical bootstrap templates and release pin')
before=fingerprint(projects)
observations['doctor']=json.loads(user(['taskctl','doctor','--format','json'],fresh))
assert observations['doctor']['result']['repository_id']=='test.fedora-rpm'
observations['frontier']=json.loads(user(['taskctl','frontier','--format','json'],fresh))
assert observations['frontier']['result']['tasks']==[]
assert fingerprint(projects)==before
checked('doctor and empty frontier preserve repository bytes and mtimes')

# Seed only the original verified archive to model a previously acquired pin.
cache=home/'.cache/taskctl'/meta['upstream']['sha256']; cache.mkdir(parents=True)
shutil.copy2(root/meta['upstream']['file'],cache/'archive.tar.gz')
run(['chown','-R','taskctl-test:taskctl-test',home/'.cache'])
assert json.loads(user(['./taskctl','doctor','--format','json'],fresh))==observations['doctor']
assert sha(cache/'home/taskctl')==expected_binary
checked('repository wrapper uses its own verified archive cache offline')
independent=projects/'different-pin'; independent.mkdir(); (independent/'.taskctl').mkdir()
shutil.copy2(fresh/'taskctl',independent/'taskctl')
lock=(fresh/'.taskctl/toolchain.lock').read_text().replace('toolVersion='+meta['version'],'toolVersion=99.99.99')
(independent/'.taskctl/toolchain.lock').write_text(lock)
run(['chown','-R','taskctl-test:taskctl-test',independent])
assert user(['./taskctl','--version'],independent)=='taskctl 99.99.99\n'
assert user(['taskctl','--version'],independent)==f'taskctl {meta["version"]}\n'
checked('global command and repository pin resolve independently')

baseline=run(['rpm','-q','--qf','%{VERSION}-%{RELEASE}','taskctl'])
transaction('upgrade',binary,'upgrade')
upgraded=run(['rpm','-q','--qf','%{VERSION}-%{RELEASE}','taskctl'])
assert baseline!=upgraded and upgraded==meta['rpm_version']+'-'+str(meta['release'])+'.fc44'
assert sha(Path('/usr/libexec/taskctl/taskctl'))==expected_binary
run(['rpm','-V','taskctl'])
assert json.loads(user(['taskctl','doctor','--format','json'],fresh))==observations['doctor']
assert json.loads(user(['./taskctl','doctor','--format','json'],fresh))==observations['doctor']
checked('DNF packaging-release upgrade preserves native identity and both command paths')

transaction('remove','taskctl','erase')
assert shutil.which('taskctl') is None
assert all(not Path(p).exists() for p in build['files']), 'Owned files remain after removal'
before=fingerprint(projects)
assert json.loads(user(['./taskctl','doctor','--format','json'],fresh))==observations['doctor']
assert json.loads(user(['./taskctl','frontier','--format','json'],fresh))==observations['frontier']
assert fingerprint(projects)==before
checked('erase removes only package files; cached repository wrapper still operates')
proof=dict(contract='taskctl.rpm-test/alpha1',passed=True,platform='fedora-44-x86_64',network_mode='none',
    canonical_executable_unchanged=True,canonical_files=canonical_files,canonical_executable_sha256=expected_binary,upstream_archive_sha256=meta['upstream']['sha256'],
    rpm_sha256=sha(binary),upgrade=dict(from_evr=baseline,to_evr=upgraded,kind='packaging-release upgrade of the same canonical executable'),
    unprivileged_commands=True,checks=checks,observations=observations,transaction_traces=traces,
    os_release=Path('/etc/os-release').read_text(),dnf=run(['rpm','-q','dnf5']),runtime_packages=run(['rpm','-qa']).splitlines())
(output/'rpm-test.json').write_text(json.dumps(proof,indent=2)+'\n')
