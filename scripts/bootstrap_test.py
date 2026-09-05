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
        info=json.loads(run(['info','--format','json']).stdout)['result']
        assert info['version']==metadata['version']
        assert json.loads(run(['info','--format','json'],extra={'TASKCTL_OFFLINE':'1'}).stdout)['result']==info
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
        pin.write_text(lock,encoding='utf-8',newline='\n')
        # Greenfield: invoke the packaged initializer, then use ONLY the generated
        # repository interface. No templates are copied by this path.
        fresh=work/'generated project ðŸ§¬'
        lock_path=work/'release.lock'; lock_path.write_text(lock,encoding='utf-8',newline='\n')
        def standalone(arguments, code=0):
            if windows:
                command=[str(shell),'-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',str(distribution/'taskctl.ps1')]+[str(a) for a in arguments]
            else: command=[str(distribution/'taskctl')]+[str(a) for a in arguments]
            p=subprocess.run(command,cwd=work,env=env,capture_output=True,text=True,encoding='utf-8',timeout=120)
            assert p.returncode==code,(arguments,p.returncode,p.stdout,p.stderr)
            return p
        initialize=['init','--contract','taskctl.init/alpha1','--repo',fresh,'--id','test.greenfield','--toolchain',lock_path,'--format','json']
        plan=json.loads(standalone(initialize+['--plan']).stdout)['result']
        assert not fresh.exists()
        assert json.loads(standalone(initialize).stdout)['result']['plan_digest']==plan['plan_digest']
        launcher=[str(shell),'-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',str(fresh/'taskctl.ps1')] if windows else [str(fresh/'taskctl')]
        env['TASKCTL_CACHE']=str(work/'native-cold-cache')
        before=fingerprint(fresh)
        assert 'offline' in run(['doctor'],2,{'TASKCTL_OFFLINE':'1'}).stderr
        doctor=json.loads(run(['doctor','--format','json']).stdout)['result']
        assert doctor['repository_id']=='test.greenfield' and doctor['tasks']==doctor['roadmaps']==doctor['epics']==0
        assert json.loads(run(['frontier','--format','json']).stdout)['result']['tasks']==[]
        assert 'No ready tasks.' in run(['frontier']).stdout
        run(['context']); run(['roadmap']); run(['epic'])
        assert fingerprint(fresh)==before
        # Reconstruct from committable files only; empty dirs and ignored runtime
        # state are absent just as they would be in a new checkout.
        checkout=work/'committed bootstrap checkout'; checkout.mkdir()
        for path in fresh.rglob('*'):
            if path.is_file() and '.agents/runtime/' not in path.relative_to(fresh).as_posix():
                destination=checkout/path.relative_to(fresh); destination.parent.mkdir(parents=True,exist_ok=True)
                shutil.copy2(path,destination)
        saved_launcher=launcher
        launcher=[str(shell),'-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',str(checkout/'taskctl.ps1')] if windows else [str(checkout/'taskctl')]
        before_checkout=fingerprint(checkout)
        run(['doctor']); run(['frontier'])
        assert fingerprint(checkout)==before_checkout
        launcher=saved_launcher
        # The declared plan enters through native validation; grouping never
        # supplies an implied dependency or an ownership tree.
        a=dict(protocol='tasking/core-draft-1',id='TASK.api',title='Durable API',state='open',intent='Persist results.',requirements=['Durability'],acceptance=['Restart succeeds.'])
        b=a|dict(id='TASK.ui',title='UI',requires=['TASK.api'])
        r=dict(protocol='tasking/planning-draft-1',kind='roadmap',id='ROADMAP.ui',title='UI',intent='Advance UI.',tasks=['TASK.ui'])
        e=dict(protocol='tasking/planning-draft-1',kind='epic',id='EPIC.delivery',title='Delivery',scope='Durable delivery.',tasks=['TASK.api','TASK.ui'])
        seed=work/'seed.json'; seed.write_text(json.dumps(dict(contract='taskctl.seed/alpha1',tasks=[a,b],roadmaps=[r],epics=[e])),encoding='utf-8')
        seeded=json.loads(run(['seed','--file',str(seed),'--expect-revision',doctor['revision'],'--format','json']).stdout)['result']
        run(['seed','--file',str(seed),'--expect-revision',doctor['revision']],4)
        assert json.loads(run(['frontier','--roadmap','ROADMAP.ui','--format','json']).stdout)['result']['tasks']==[]
        shown=json.loads(run(['show','TASK.api','--format','json']).stdout)['result']
        receipt=work/'receipt.json'
        receipt.write_text(json.dumps(dict(protocol='taskctl.receipt/alpha1',classification='actor-assertion',task='TASK.api',contract=shown['contract_digest'],actor='bootstrap-test',recorded_at='2026-09-05T00:00:00Z',evidence={'integration':'Captured fixture assertion; no subprocess claim.'})),encoding='utf-8')
        before=fingerprint(fresh)
        run(['verify','TASK.api','--receipt',str(receipt)])
        assert fingerprint(fresh)==before
        run(['close','TASK.api','--receipt',str(receipt),'--expect-revision',seeded['revision']])
        assert json.loads(run(['frontier','--roadmap','ROADMAP.ui','--format','json']).stdout)['result']['tasks']==['TASK.ui']
        run(['doctor'],extra={'TASKCTL_OFFLINE':'1'})
        # Re-init is explicit refusal, and arbitrary source is never overwritten.
        before=fingerprint(fresh); standalone(initialize,2); assert fingerprint(fresh)==before
        existing=work/'existing'; existing.mkdir(); (existing/'source.txt').write_text('preserve me')
        standalone(['init','--repo',existing,'--id','test.existing','--toolchain',lock_path],2)
        assert list(existing.iterdir())==[existing/'source.txt']
        # Bad native plans fail before destination creation.
        bad=work/'invalid seed.json'; bad.write_text(json.dumps(dict(contract='taskctl.seed/alpha1',tasks=[b])),encoding='utf-8')
        invalid=work/'invalid'
        standalone(['init','--repo',invalid,'--id','test.invalid','--toolchain',lock_path,'--seed',bad],2)
        assert not invalid.exists()
        result=dict(platform=system,version=metadata['version'],archive_sha256=metadata['sha256'],transport='release' if args.lock else 'file',
            checks=['cold acquisition','warm offline operation','no Java/Gradle/Git/Python on consumer PATH','consumer bytes and mtimes unchanged',
                    'explicit repository launcher from unrelated cwd','wrong version rejected','corrupt cached library rejected','unlisted JAR ignored','wrong archive digest rejected',
                    'mutation-free init plan and deterministic apply','greenfield init from standalone distribution','empty native doctor and frontier','committed-only checkout reconstruction',
                    'seed admission with revision CAS','global prerequisite evaluation before roadmap filter','read-only evidence validation and evidenced closure',
                    'warm offline native commands','existing source and repeated-init refusal','invalid seed rejected before writes'])
        (output/f'bootstrap-{system}.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
        print(json.dumps(result))
if __name__=='__main__': main()
