"""Inspect the actual self-host graph through a packaged implementation, read-only."""
import argparse, hashlib, json, os, subprocess, tarfile, tempfile, zipfile
from pathlib import Path
from package import ROOT, target, digest

def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--kind',choices=['native','jvm'],required=True)
    args=parser.parse_args(); system=target(); windows=system.startswith('windows')
    meta=json.loads((ROOT/f'build/distributions/{args.kind}-{system}.json').read_text(encoding='utf-8'))
    archive=ROOT/'build/distributions'/meta['file']; assert digest(archive)==meta['sha256']
    def inventory():
        paths=[ROOT/'AGENTS.md',ROOT/'taskctl',ROOT/'taskctl.ps1',ROOT/'taskctl.bat']
        paths += [p for folder in ('.agents','.taskctl') for p in (ROOT/folder).rglob('*') if p.is_file()]
        return {p.relative_to(ROOT).as_posix():(digest(p),p.stat().st_mtime_ns) for p in paths}
    before=inventory(); results={}
    env=os.environ.copy()
    for key in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','TASKCTL_REPOSITORY','TASKCTL_DISTRIBUTION'): env.pop(key,None)
    env.update(TASKCTL_OFFLINE='1',JAVA_HOME=str(ROOT/'build/absent-java'))
    with tempfile.TemporaryDirectory(prefix='selfhost-consumer-') as temporary:
        directory=Path(temporary)
        if windows:
            with zipfile.ZipFile(archive) as bundle: bundle.extractall(directory)
        else:
            with tarfile.open(archive) as bundle: bundle.extractall(directory,filter='data')
        if windows and args.kind=='jvm':
            command=[str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/powershell.exe'),'-NoProfile','-ExecutionPolicy','Bypass','-File',str(directory/'taskctl.ps1')]
        else: command=[str(directory/('taskctl.exe' if windows else 'taskctl'))]
        for action in ('doctor','context','frontier','status','roadmap','epic','affected'):
            run=subprocess.run(command+[action,'--repo',str(ROOT),'--format','json'],cwd=directory,env=env,capture_output=True,text=True,encoding='utf-8',check=True,timeout=120)
            results[action]=json.loads(run.stdout)['result']
    assert inventory()==before, 'Self-host read commands mutated repository state'
    assert results['doctor']['repository_id']=='brule-io.taskctl'
    assert results['doctor']['tasks']>=22 and results['doctor']['roadmaps']>=4 and results['doctor']['epics']>=2
    assert results['affected']['tasks']==[], 'Self-host graph has unresolved currency'
    proof=dict(contract='taskctl.selfhost-proof/1',platform=system,implementation=args.kind,version=meta['version'],
        artifact_sha256=meta['sha256'],repository_id='brule-io.taskctl',source_revision=meta['source_revision'],
        read_only=True,ledger_revision=results['doctor']['revision'],commands=results,
        inventory_sha256=hashlib.sha256(json.dumps({p:v[0] for p,v in sorted(before.items())},sort_keys=True).encode()).hexdigest())
    output=ROOT/'build/proof'; output.mkdir(parents=True,exist_ok=True)
    (output/f'selfhost-{args.kind}-{system}.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(platform=system,implementation=args.kind,commands=len(results),read_only=True)))

if __name__=='__main__': main()
