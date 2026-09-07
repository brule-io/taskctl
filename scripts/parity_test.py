"""Run both shipped implementations against identical preimages and compare
outputs, exit codes and exact resulting files. No protocol-specific normalization.
Only implementation/build/runtime diagnostics may differ in `info`.
"""
import hashlib, json, os, shutil, stat, subprocess, tarfile, tempfile, zipfile
from decimal import Decimal
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
            # Protocol numbers must not become binary floats even in test code.
            # Fixture writes containing decimals must preserve exact source tokens.
            return json.loads(result.stdout,parse_float=Decimal) if '--format' in args and 'json' in args else result.stdout
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
        assert first['contract']=='taskctl.doctor/alpha1' and first['health']=='ok' and 'work' not in first
        for command in ('context','snapshot','frontier','roadmap','epic'):
            inspected=json_pair('empty '+command,[command])['result']
            if command in ('context','snapshot'): assert inspected['contract']==f'taskctl.{command}/alpha1'
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
        restricted=json_pair('blocked provider context',['context'])['result']
        assert restricted['counts']['ready']==0 and restricted['counts']['required_capabilities_unavailable']>0
        assert json_pair('blocked provider snapshot',['snapshot'])['result']['derived']['frontier']==[]
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
        # Exact ancestral bytes, including 29 historical closures. These are
        # never silently converted into modern closure receipts.
        restore(None); repo.mkdir()
        (repo/'source.txt').write_text('Existing product source.\n',encoding='utf-8')
        (repo/'AGENTS.md').write_text('Preserved contributor authority.\n',encoding='utf-8')
        source=work/'ancestral source'
        fixture=ROOT/'conformance/src/test/resources/fantastikt-import'
        shutil.copytree(fixture/'.agents',source/'.agents')
        def source_image():
            return {p.relative_to(source).as_posix():(p.read_bytes(),p.stat().st_mtime_ns) for p in source.rglob('*') if p.is_file()}
        source_before=source_image()
        origin=json.loads((fixture/'sources.json').read_text(encoding='utf-8'))
        inspect=['--source',source,'--source-repository',origin['repository'],'--source-revision',origin['revision'],'--adapter','fantastikt-loom-agent-2026']
        summary=json_pair('ancestral import inspection',['import','inspect',*inspect])['result']
        assert summary['tasks']==40 and summary['closed_tasks']==29
        planned=json_pair('ancestral import plan',['import','plan',*inspect])['result']
        assert planned['structural_frontier']==['TASK.draft-mvp.038']
        import_plan=write('import-plan.json',planned)
        admission=dict(protocol='taskctl.import-review/1',classification='actor-assertion',manifest=planned['manifest_id'],
            actor='parity-test',recorded_at='2026-09-06T00:00:00Z',rationale='Reviewed mapping, preserving historical evidence classification.')
        import_review=write('import-review.json',admission)
        importing=['import','apply','--source',source,'--file',import_plan,'--review',import_review,'--repo',repo,'--id','test.import','--toolchain',lock]
        json_pair('import bounded write plan',[*importing,'--plan'])
        assert not (repo/'.agents').exists()
        old_source=(source/'.agents/README.md').read_bytes()
        (source/'.agents/README.md').write_bytes(old_source+b'\nSource changed.\n')
        json_pair('import source drift refusal',importing,code=4)
        (source/'.agents/README.md').write_bytes(old_source)
        stamp=source_before['.agents/README.md'][1]; os.utime(source/'.agents/README.md',ns=(stamp,stamp))
        write('import-plan.json',planned|dict(structural_frontier=[]))
        json_pair('import forged plan refusal',importing,code=4)
        write('import-plan.json',planned)
        write('import-review.json',admission|dict(manifest='sha256:'+'0'*64))
        json_pair('import wrong manifest review refusal',importing,code=2)
        write('import-review.json',admission)
        json_pair('reviewed ancestral import',importing,mutation=True,initialization=True)
        assert source_image()==source_before
        assert (repo/'source.txt').read_text(encoding='utf-8')=='Existing product source.\n'
        assert (repo/'AGENTS.md').read_text(encoding='utf-8')=='Preserved contributor authority.\n'
        assert not list((repo/'.agents/receipts').glob('*'))
        imported=json_pair('imported doctor',['doctor'])['result']
        assert imported['protocol']=='taskctl.native/alpha3' and imported['tasks']==40
        for command in ('context','snapshot','status','frontier','roadmap','epic'):
            json_pair('imported '+command,[command])
        assert not json_pair('import is not current proof',['frontier'])['result']['tasks']
        historical=json_pair('imported historical closure',['history','TASK.draft-mvp.029'])['result']
        assert historical['imports'] and not historical['receipts']
        json_pair('import collision refusal',importing,code=2)
        root_plan=json_pair('import root review plan',['reconcile','TASK.draft-mvp.001','--plan'])['result']
        review=review_file(root_plan)
        json_pair('import explicit root revalidation',['reconcile','TASK.draft-mvp.001','--file',review,'--expect-revision',root_plan['revision']],mutation=True)
        json_pair('import stale review CAS',['reconcile','TASK.draft-mvp.001','--file',review,'--expect-revision',root_plan['revision']],code=4)
        assert not list((repo/'.agents/receipts').glob('*'))
        json_pair('import reviewed history',['history','TASK.draft-mvp.001'])
        assert source_image()==source_before
        # A conformance-local adapter emits this through canonical Bootstrap,
        # exercising a second import shape without registering a test adapter in
        # the product CLI or copying private consumer records into this corpus.
        projection=json.loads((output/'divergent-native-preimage.json').read_text(encoding='utf-8'))
        assert projection['protocol']=='taskctl.conformance-preimage/1'
        assert projection['adapter']=='conformance-object-work' and projection['adapter_version']=='1.0.0'
        assert projection['source_revision']=='7426b442a1fc217894af2c6f9f77ce8bc05413af'
        restore(None); repo.mkdir()
        for relative,content in projection['files'].items():
            path=repo/relative
            assert relative.startswith('.agents/') and path.resolve().is_relative_to(repo.resolve())
            path.parent.mkdir(parents=True,exist_ok=True); path.write_text(content,encoding='utf-8',newline='\n')
        (repo/'source.txt').write_text('Divergent consumer source remains untouched.\n',encoding='utf-8')
        (repo/'.git').mkdir(); (repo/'.git/sentinel').write_text('No Git execution.\n',encoding='utf-8')
        observed=json_pair('divergent imported doctor',['doctor'])['result']
        assert observed['tasks']==3 and observed['roadmaps']==0 and observed['epics']==2
        for command in ('context','snapshot','status','affected','frontier','roadmap','epic'):
            json_pair('divergent '+command,[command])
        assert not json_pair('divergent structural readiness is not current proof',['frontier'])['result']['tasks']
        original_history=json_pair('divergent historical unchecked closure',['history','TASK.specimen.WORK-1'])['result']
        assert original_history['imports'] and not original_history['receipts']
        for work_id in ('WORK-1','WORK-01','WORK-2'):
            task_id='TASK.specimen.'+work_id
            plan=json_pair('divergent reconcile plan '+work_id,['reconcile',task_id,'--plan'])['result']
            review=review_file(plan,evidence_data={'specimen':'Explicit review of fictional current contract and source mapping.'})
            json_pair('divergent reconcile '+work_id,['reconcile',task_id,'--file',review,'--expect-revision',plan['revision']],mutation=True)
            json_pair('divergent stale review '+work_id,['reconcile',task_id,'--file',review,'--expect-revision',plan['revision']],code=4)
        assert json_pair('divergent ready after explicit reviews',['frontier'])['result']['tasks']==['TASK.specimen.WORK-2']
        shown=json_pair('divergent close contract',['show','TASK.specimen.WORK-2'])['result']
        asserted=evidence|dict(task=shown['id'],contract=shown['contract_digest'],evidence={'specimen':'Current fictional result checked.'})
        receipt=write('divergent-receipt.json',asserted)
        json_pair('divergent verify',['verify',shown['id'],'--receipt',receipt])
        json_pair('divergent bounded close plan',['close',shown['id'],'--receipt',receipt,'--expect-revision',shown['revision'],'--plan'])
        json_pair('divergent evidenced close',['close',shown['id'],'--receipt',receipt,'--expect-revision',shown['revision']],mutation=True)
        receipts={p.name:p.read_bytes() for p in (repo/'.agents/receipts').iterdir()}
        json_pair('divergent current snapshot',['snapshot'])
        old_root=json_pair('divergent root before revision',['show','TASK.specimen.WORK-1'])['result']
        root_path=next(p for p in (repo/'.agents/tasks').iterdir() if json.loads(p.read_text(encoding='utf-8'))['id']==old_root['id'])
        root_text=root_path.read_text(encoding='utf-8')
        original_extensions=json.loads(root_text,parse_float=Decimal)['extensions']
        # Do not round-trip opaque protocol numbers through Python binary floats.
        original_requirement='["Keep exact input bytes."]'
        assert root_text.count(original_requirement)==1
        changed=work/'divergent-root-revision.json'
        changed.write_text(root_text.replace(original_requirement,'["Keep exact bytes and audit their origin."]'),encoding='utf-8',newline='\n')
        json_pair('divergent material revision',['revise',old_root['id'],'--file',changed,'--expect-revision',old_root['revision']],mutation=True)
        assert json.loads(root_path.read_text(encoding='utf-8'),parse_float=Decimal)['extensions']==original_extensions
        for work_id in ('WORK-1','WORK-01'):
            task_id='TASK.specimen.'+work_id
            plan=json_pair('divergent changed review plan '+work_id,['reconcile',task_id,'--plan'])['result']
            review=review_file(plan,evidence_data={'specimen':'Explicit review of the stronger fictional archive requirement.'})
            json_pair('divergent changed review '+work_id,['reconcile',task_id,'--file',review,'--expect-revision',plan['revision']],mutation=True)
        remains=json_pair('divergent transitive changed inputs',['affected'])['result']['tasks']
        assert any(t['task']=='TASK.specimen.WORK-2' and t['currency']=='affected' and t['lifecycle']=='closed' for t in remains)
        assert receipts=={p.name:p.read_bytes() for p in (repo/'.agents/receipts').iterdir()}
        json_pair('divergent retained native and import history',['history','TASK.specimen.WORK-2'])
        assert (repo/'source.txt').read_text(encoding='utf-8')=='Divergent consumer source remains untouched.\n'
        # Read projections must remain useful when ordinary records are much
        # larger than the agent briefing. The complete snapshot retains all
        # typed state and exact imported values; no second semantic decoder.
        current=json_pair('before large read-model specimen',['doctor'])['result']
        large=[d|dict(id=f'TASK.briefing.{n:03}',title='🧬'*400,intent='Bounded work. '*900,requires=[]) for n in range(48)]
        long_id='TASK.'+'long'*600
        large.append(d|dict(id=long_id,title='Complete identity remains available',requires=[]))
        large_seed=write('large-read-model-seed.json',dict(contract='taskctl.seed/alpha1',tasks=large))
        json_pair('seed large read-model specimen',['seed','--file',large_seed,'--expect-revision',current['revision']],mutation=True)
        diagnostics=json_pair('distinct large doctor',['doctor'])['result']
        assert diagnostics['contract']=='taskctl.doctor/alpha1' and diagnostics['tasks']==52 and 'work' not in diagnostics
        briefing=json_pair('bounded large context',['context'])['result']
        assert briefing['contract']=='taskctl.context/alpha1' and briefing['truncated'] and not briefing['complete_ledger']
        assert len(briefing['work'])==12 and briefing['omitted_items']>0 and briefing['omitted_overlong_identities']==1
        assert briefing['counts']['tasks']==52
        assert len(json.dumps(briefing,ensure_ascii=False,separators=(',',':')).encode())<=briefing['limits']['max_result_utf8_bytes']==32768
        assert all(item['id']!=long_id for item in briefing['work'])
        full=json_pair('complete large typed snapshot',['snapshot'])['result']
        assert full['contract']=='taskctl.snapshot/alpha1'
        records={record['id']:record for record in full['records']['tasks']}
        assert len(records)==52 and records[long_id]['id']==long_id
        assert records['TASK.briefing.000']['title']=='🧬'*400
        labels=records['TASK.specimen.WORK-1']['extensions']['legacy.object-work/v1']['labels']
        assert labels['exact_integer']==900719925474099312345678901234567890
        assert labels['exact_decimal']==Decimal('0.123456789012345678901234567890')
        assert len(full['history']['revisions'])>52 and full['imports'] and full['receipts']
        assert full['imports'][0]['manifest']['source_revision']==projection['source_revision']
        assert full['imports'][0]['manifest']['evidence_classification']=='historical-narrative-unverified'
        assert full['revision']==briefing['revision']==diagnostics['revision']
        pair('bounded context text',['context'])
        pair('diagnostic text',['doctor'])
        json_pair('invalid context option',['context','--limit','unbounded'],code=2)
    result=dict(contract='taskctl.parity/alpha1',platform=system,version=native['version'],
        artifacts={kind:meta['sha256'] for kind,meta in metadata.items()},cases=checks,
        allowed_differences={'info.result':['implementation','java_runtime','vm','build']},
        protocol_semantics_changed=False)
    (output/f'parity-{system}.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(platform=system,cases=len(checks),result='JVM/native behavior and resulting files match')))

if __name__=='__main__': main()
