"""Run both shipped implementations against identical preimages and compare
outputs, exit codes and exact resulting files. No protocol-specific normalization.
Only implementation/build/runtime diagnostics may differ in `info`.
"""
import hashlib, json, os, shutil, stat, subprocess, tarfile, tempfile, zipfile
from pathlib import Path
from package import ROOT, target, digest

def main():
    system=target(); windows=system.startswith('windows')
    output=ROOT/'build/proof'; output.mkdir(parents=True,exist_ok=True)
    metadata={kind:json.loads((ROOT/f'build/distributions/{kind}-{system}.json').read_text(encoding='utf-8')) for kind in ('jvm','native')}
    assert metadata['jvm']['version']==metadata['native']['version']
    assert metadata['jvm']['build_identity']['libraries']==metadata['native']['build_identity']['libraries']
    checks=[]
    with tempfile.TemporaryDirectory(prefix='parity ',dir=output) as temporary:
        work=Path(temporary); repo=work/'project'; distributions={}
        env=os.environ.copy()
        for key in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','TASKCTL_DISTRIBUTION','TASKCTL_REPOSITORY'): env.pop(key,None)
        env.update(JAVA_HOME=str(work/'absent-java'),TASKCTL_OFFLINE='1')
        for kind,meta in metadata.items():
            archive=ROOT/'build/distributions'/meta['file']; assert digest(archive)==meta['sha256']
            directory=work/kind; directory.mkdir(); distributions[kind]=directory
            if windows:
                with zipfile.ZipFile(archive) as bundle: bundle.extractall(directory)
            else:
                with tarfile.open(archive) as bundle: bundle.extractall(directory,filter='data')
        native=metadata['native']; archive=ROOT/'build/distributions'/native['file']
        lock=work/'toolchain.lock'
        lock.write_text(f'lockFormat=2\nwrapperVersion=3\ntoolVersion={native["version"]}\n{system}.url={archive.as_uri()}\n{system}.sha256={native["sha256"]}\n',encoding='utf-8')
        def capture():
            if not repo.exists(): return None
            return {p.relative_to(repo).as_posix():(p.read_bytes(),p.stat().st_mtime_ns,stat.S_IMODE(p.stat().st_mode)) for p in repo.rglob('*') if p.is_file()}
        def restore(state):
            # Only this disposable fixture root may be recursively removed.
            assert repo.resolve().is_relative_to(work.resolve()) and repo.name=='project'
            if repo.exists(): shutil.rmtree(repo)
            if state is None: return
            repo.mkdir()
            for relative,(data,mtime,mode) in state.items():
                p=repo/relative; p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(data)
                p.chmod(mode); os.utime(p,ns=(mtime,mtime))
        def contents(state): return None if state is None else {key:(value[0],value[2]) for key,value in state.items()}
        def run(kind,args):
            directory=distributions[kind]
            if windows and kind=='jvm':
                shell=Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/powershell.exe'
                command=[str(shell),'-NoProfile','-ExecutionPolicy','Bypass','-File',str(directory/'taskctl.ps1')]
            else: command=[str(directory/('taskctl.exe' if windows else 'taskctl'))]
            return subprocess.run(command+[str(a) for a in args],cwd=work,env=env|dict(TASKCTL_REPOSITORY=str(repo)),capture_output=True,text=True,encoding='utf-8',timeout=120)
        def pair(label,args,code=0,mutation=False,initialization=False,info=False):
            before=capture(); observations=[]
            for kind in ('jvm','native'):
                restore(before)
                result=run(kind,args)
                assert result.returncode==code,(label,kind,result.returncode,result.stdout,result.stderr)
                after=capture()
                if not mutation: assert after==before,(label,kind,'read or failed command changed repository')
                elif not initialization:
                    changed={p for p in set(before or {})|set(after or {}) if (before or {}).get(p)!=(after or {}).get(p)}
                    assert all(p.startswith('.agents/') for p in changed),(label,changed)
                stdout=result.stdout
                if info:
                    value=json.loads(stdout); diagnostic=value['result']
                    assert diagnostic['implementation']==kind
                    assert diagnostic['build']=={key:value for key,value in metadata[kind].items() if key not in ('file','sha256','bytes')}
                    assert diagnostic['java_runtime'] and diagnostic['vm']
                    for key in ('implementation','java_runtime','vm','build'): del diagnostic[key]
                    stdout=json.dumps(value,sort_keys=True)
                observations.append((result.returncode,stdout,result.stderr,contents(after)))
            assert observations[0]==observations[1],(label,'JVM/native mismatch',observations[0][:3],observations[1][:3])
            checks.append(dict(case=label,exit_code=code,stdout_sha256=hashlib.sha256(observations[0][1].encode()).hexdigest(),
                stderr_sha256=hashlib.sha256(observations[0][2].encode()).hexdigest(),files_equal=True,read_only=not mutation))
            return json.loads(result.stdout) if '--format' in args and 'json' in args else result.stdout
        def json_pair(label,args,**options): return pair(label,[*args,'--format','json'],**options)
        for spelling in ('--version','-V','version'):
            assert pair('version '+spelling,[spelling])==f'taskctl {native["version"]}\n'
        json_pair('structured version',['version'])
        pair('invalid version options',['--version','extra'],code=2)
        json_pair('rich implementation diagnostics',['info'],info=True)
        pair('help',['help'])
        pair('historical adapter help',['--adapter','fantastikt-loom-agent-2026','--help'])
        pair('historical empty snapshot',['--adapter','fantastikt-loom-agent-2026','snapshot','--repo',repo])
        json_pair('unknown command',['banana'],code=2)
        json_pair('missing repository',['doctor'],code=5)
        initialize=['init','--repo',repo,'--id','test.parity','--toolchain',lock]
        json_pair('init plan',[*initialize,'--plan'])
        json_pair('init apply',initialize,mutation=True,initialization=True)
        (repo/'source.txt').write_text('Unrelated source must remain byte-identical.\n',encoding='utf-8')
        (repo/'.git').mkdir(); (repo/'.git/sentinel').write_text('No implicit Git operation.\n',encoding='utf-8')
        first=json_pair('empty doctor',['doctor'])['result']
        for command in ('context','snapshot','frontier','roadmap','epic'):
            json_pair('empty '+command,[command])
        pair('empty frontier text',['frontier'])
        json_pair('repeat initialization refusal',initialize,code=2)
        def write(name,value):
            path=work/name; path.write_text(json.dumps(value,ensure_ascii=False),encoding='utf-8'); return path
        a=dict(protocol='tasking/core-draft-1',id='TASK.api',title='Durable API 🧬',state='open',intent='Persist results.',requirements=['Durable'],acceptance=['Restart succeeds.'])
        b=a|dict(id='TASK.ui',requires=['TASK.api'])
        c=a|dict(id='TASK.metrics')
        r=dict(protocol='tasking/planning-draft-1',kind='roadmap',id='ROADMAP.ui',title='UI',intent='Advance UI.',tasks=['TASK.ui'])
        backend=r|dict(id='ROADMAP.backend',tasks=['TASK.api','TASK.metrics'])
        e=dict(protocol='tasking/planning-draft-1',kind='epic',id='EPIC.delivery',title='Delivery',scope='Durable delivery.',tasks=['TASK.api','TASK.ui'])
        observation=e|dict(id='EPIC.observation',tasks=['TASK.api','TASK.metrics'])
        seed=write('seed.json',dict(contract='taskctl.seed/alpha1',tasks=[a,b,c],roadmaps=[r,backend],epics=[e,observation]))
        seeded=json_pair('seed cross-cutting graph',['seed','--file',seed,'--expect-revision',first['revision']],mutation=True)['result']
        json_pair('stale seed CAS',['seed','--file',seed,'--expect-revision',first['revision']],code=4)
        for args in (['frontier'],['frontier','--roadmap','ROADMAP.ui'],['frontier','--epic','EPIC.delivery'],['roadmap','ROADMAP.backend'],['epic','EPIC.observation']):
            json_pair('graph '+' '.join(args),args)
        for args in (['show','banana'],['show','TASK.absent'],['frontier','--roadmap','TASK.api'],['doctor','--unexpected','x'],['doctor','--repo',repo,'--repo',repo]):
            json_pair('invalid '+' '.join(map(str,args)),args,code=2)
        # Existing decoder/lifecycle tests cover numeric algebra; this also proves
        # exact opaque source survival through the shipped process boundary.
        task_path=next(p for p in (repo/'.agents/tasks').iterdir() if json.loads(p.read_text(encoding='utf-8'))['id']=='TASK.api')
        opaque='extensions:\n  test.opaque/v1:\n    # preserve spelling and comment\n    huge: 123456789012345678901234567890.000001\n'
        task_path.write_text('protocol: tasking/core-draft-1\nid: TASK.api\ntitle: Durable API 🧬\nstate: open\nintent: Persist results.\nrequirements: [Durable]\nacceptance: [Restart succeeds.]\n'+opaque,encoding='utf-8',newline='\n')
        shown=json_pair('show exact semantic contract',['show','TASK.api'])['result']
        evidence=dict(protocol='taskctl.receipt/alpha1',classification='actor-assertion',task='TASK.api',contract=shown['contract_digest'],actor='parity-test',recorded_at='2026-09-05T00:00:00Z',evidence={'integration':'Caller assertion; taskctl did not run a test.'})
        receipt=write('receipt.json',evidence)
        json_pair('verify evidence',['verify','TASK.api','--receipt',receipt])
        wrong=write('wrong.json',evidence|dict(contract='sha256:'+'0'*64))
        json_pair('reject wrong contract',['verify','TASK.api','--receipt',wrong],code=2)
        json_pair('reject wrong task',['verify','TASK.ui','--receipt',receipt],code=2)
        json_pair('stale close CAS',['close','TASK.api','--receipt',receipt,'--expect-revision',seeded['revision']],code=4)
        json_pair('evidenced close',['close','TASK.api','--receipt',receipt,'--expect-revision',shown['revision']],mutation=True)
        assert task_path.read_text(encoding='utf-8').endswith(opaque)
        json_pair('frontier after closure',['frontier','--roadmap','ROADMAP.ui'])
        json_pair('closed task evidence',['show','TASK.api'])
        json_pair('doctor after closure',['doctor'])
        receipts={p.name:p.read_bytes() for p in (repo/'.agents/receipts').iterdir()}
        task_path.write_text(task_path.read_text(encoding='utf-8').replace('[Restart succeeds.]','[Changed acceptance.]'),encoding='utf-8',newline='\n')
        json_pair('historical receipt cannot prove changed contract',['doctor'],code=2)
        assert receipts=={p.name:p.read_bytes() for p in (repo/'.agents/receipts').iterdir()}
        task_path.write_text(task_path.read_text(encoding='utf-8').replace('[Changed acceptance.]','[Restart succeeds.]'),encoding='utf-8',newline='\n')
        current=json_pair('restored valid contract',['doctor'])['result']
        # Revision/currency corpus runs through both shipped executables, including
        # immutable history, review evidence, transitive changes and bounded plans.
        d=a|dict(protocol='tasking/core-draft-2',id='TASK.rev.a',verification=['integration'])
        e2=d|dict(id='TASK.rev.b',requires=['TASK.rev.a'])
        f=d|dict(id='TASK.rev.c',requires=['TASK.rev.b'])
        revision_seed=write('revision-seed.json',dict(contract='taskctl.seed/alpha1',tasks=[f,e2,d]))
        json_pair('revision seed mutation plan',['seed','--file',revision_seed,'--expect-revision',current['revision'],'--plan'])
        json_pair('revision seed',['seed','--file',revision_seed,'--expect-revision',current['revision']],mutation=True)
        for task in (d,e2,f):
            shown=json_pair('revision show '+task['id'],['show',task['id']])['result']
            asserted=write('revision-receipt.json',evidence|dict(task=task['id'],contract=shown['contract_digest']))
            json_pair('revision close '+task['id'],['close',task['id'],'--receipt',asserted,'--expect-revision',shown['revision']],mutation=True)
        receipts={p.name:p.read_bytes() for p in (repo/'.agents/receipts').iterdir()}
        shown=json_pair('closed revision before edit',['show',d['id']])['result']
        material=write('material-revision.json',d|dict(state='closed',acceptance=['Durable after power loss.']))
        json_pair('revise mutation plan',['revise',d['id'],'--file',material,'--expect-revision',shown['revision'],'--plan'])
        json_pair('revise immutable contract',['revise',d['id'],'--file',material,'--expect-revision',shown['revision']],mutation=True)
        json_pair('stale revise CAS',['revise',d['id'],'--file',material,'--expect-revision',shown['revision']],code=4)
        affected=json_pair('transitive affected',['affected',d['id']])['result']['tasks']
        assert {t['task'] for t in affected}=={d['id'],e2['id'],f['id']}
        assert all(t['lifecycle']=='closed' and t['currency']=='affected' for t in affected)
        pair('human currency',['status'])
        json_pair('closed immutable history',['history',d['id']])
        json_pair('reconcile without explicit review',['reconcile',d['id']],code=2)
        plan=json_pair('review current inputs',['reconcile',d['id'],'--plan'])['result']
        def review_file(plan,outcome='revalidated',evidence_data=None):
            return write('review.json',dict(protocol='taskctl.reconciliation/1',classification='actor-assertion',
                task=plan['task'],reviewed_head=plan['reviewed_head'],observations=plan['observations'],outcome=outcome,
                actor='parity-test',recorded_at='2026-09-06T00:00:00Z',rationale='Reviewed against current inputs.',
                evidence={'integration':'Explicit caller revalidation assertion.'} if evidence_data is None else evidence_data,successor=None))
        missing=review_file(plan,evidence_data={})
        json_pair('reject evidence-free revalidation',['reconcile',d['id'],'--file',missing,'--expect-revision',plan['revision']],code=2)
        review=review_file(plan,outcome='unresolved')
        json_pair('record unresolved review',['reconcile',d['id'],'--file',review,'--expect-revision',plan['revision']],mutation=True)
        assert next(t for t in json_pair('unresolved status',['status'])['result']['tasks'] if t['task']==d['id'])['currency']=='unresolved'
        downstream_plan=json_pair('downstream review while blocked',['reconcile',e2['id'],'--plan'])['result']
        blocked=review_file(downstream_plan)
        json_pair('reject review over unresolved upstream',['reconcile',e2['id'],'--file',blocked,'--expect-revision',downstream_plan['revision']],code=2)
        for task in (d,e2,f):
            plan=json_pair('reconcile inputs '+task['id'],['reconcile',task['id'],'--plan'])['result']
            review=review_file(plan)
            json_pair('review mutation plan '+task['id'],['reconcile',task['id'],'--file',review,'--expect-revision',plan['revision'],'--plan'])
            json_pair('evidenced reconciliation '+task['id'],['reconcile',task['id'],'--file',review,'--expect-revision',plan['revision']],mutation=True)
            json_pair('stale reconciliation CAS '+task['id'],['reconcile',task['id'],'--file',review,'--expect-revision',plan['revision']],code=4)
            if task==e2:
                remains=json_pair('transitive currency remains after intermediate review',['affected'])['result']['tasks']
                assert f['id'] in {t['task'] for t in remains}
        assert not json_pair('all revised tasks current',['affected'])['result']['tasks']
        assert receipts=={p.name:p.read_bytes() for p in (repo/'.agents/receipts').iterdir()}
        current=json_pair('revision doctor after reconciliations',['doctor'])['result']
        bad=write('dangling.json',dict(contract='taskctl.seed/alpha1',tasks=[a|dict(id='TASK.dangling',requires=['TASK.absent'])]))
        json_pair('reject dangling dependency',['seed','--file',bad,'--expect-revision',current['revision']],code=2)
        required=write('required.json',dict(contract='taskctl.seed/alpha1',tasks=[a|dict(id='TASK.required',required_extensions=['test.provider/v1'])]))
        json_pair('store unsupported required feature',['seed','--file',required,'--expect-revision',current['revision']],mutation=True)
        json_pair('inspect unsupported feature',['doctor'])
        json_pair('required provider blocks readiness',['frontier'],code=3)
        pair('diagnostic stderr exit code',['show','banana'],code=2)
        # Adoption has an explicit entry point; init still refuses existing code.
        restore(None); repo.mkdir()
        (repo/'source.txt').write_text('Existing source.\n',encoding='utf-8')
        (repo/'AGENTS.md').write_text('Existing contributor authority.\n',encoding='utf-8')
        adoption=['adopt','--repo',repo,'--id','test.adopt','--toolchain',lock]
        json_pair('adopt existing code plan',[*adoption,'--plan'])
        json_pair('adopt existing code',adoption,mutation=True,initialization=True)
        assert (repo/'source.txt').read_text(encoding='utf-8')=='Existing source.\n'
        assert (repo/'AGENTS.md').read_text(encoding='utf-8')=='Existing contributor authority.\n'
        json_pair('adopted doctor',['doctor'])
        json_pair('adoption collision refusal',adoption,code=2)
    result=dict(contract='taskctl.parity/alpha1',platform=system,version=native['version'],
        artifacts={kind:meta['sha256'] for kind,meta in metadata.items()},cases=checks,
        allowed_differences={'info.result':['implementation','java_runtime','vm','build']},
        protocol_semantics_changed=False)
    (output/f'parity-{system}.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(platform=system,cases=len(checks),result='JVM/native behavior and resulting files match')))

if __name__=='__main__': main()
