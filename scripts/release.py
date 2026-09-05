"""Assemble a GitHub draft release from tested CI artifacts. Never replaces assets.

Publication is a separate explicit `gh release edit TAG --draft=false` after the
real-transport tests pass. Immutable releases must be enabled on the repository.
"""
import argparse, hashlib, json, subprocess
from pathlib import Path

def gh(*args):
    return subprocess.check_output(['gh',*args],text=True).strip()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--artifacts',type=Path,required=True)
    parser.add_argument('--repository',default='brule-io/taskctl')
    parser.add_argument('--version',required=True)
    parser.add_argument('--revision',required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args(); platforms=['windows-x86_64','linux-x86_64','macos-aarch64']
    records=[]; archives=[]; statements=[]; parity=[]
    for platform in platforms:
        platform_records={}
        for kind in ('native','jvm'):
            matches=list(args.artifacts.rglob(kind+'-'+platform+'.json'))
            assert len(matches)==1,(platform,kind,matches)
            meta=json.loads(matches[0].read_text(encoding='utf-8'))
            assert meta['implementation']==kind and meta['version']==args.version and meta['source_revision']==args.revision and not meta['source_dirty'],meta
            archive=matches[0].parent/meta['file']
            assert hashlib.sha256(archive.read_bytes()).hexdigest()==meta['sha256']
            proof=list(args.artifacts.rglob('bootstrap-'+kind+'-'+platform+'.json'))
            assert len(proof)==1 and json.loads(proof[0].read_text(encoding='utf-8'))['archive_sha256']==meta['sha256']
            statement=archive.with_name(archive.name+'.intoto.json')
            attestation=json.loads(statement.read_text(encoding='utf-8'))
            assert attestation['subject']==[dict(name=archive.name,digest=dict(sha256=meta['sha256']))]
            assert attestation['predicate']['buildDefinition']['internalParameters']==meta['build_identity']
            records.append(meta); archives.append(archive); statements.append(statement)
            platform_records[kind]=meta['sha256']
        proofs=list(args.artifacts.rglob('parity-'+platform+'.json'))
        assert len(proofs)==1
        comparison=json.loads(proofs[0].read_text(encoding='utf-8'))
        assert comparison['artifacts']==platform_records and comparison['cases']
        assert all(case['files_equal'] for case in comparison['cases'])
        corpus=list(args.artifacts.rglob('corpus-'+platform+'.json'))
        assert len(corpus)==1
        evidence=json.loads(corpus[0].read_text(encoding='utf-8'))
        assert evidence['behavioral_tests']==evidence['jvm_passed']==evidence['native_passed'] and evidence['behavioral_tests']>0
        parity.extend([proofs[0],corpus[0]])
    args.output.mkdir(parents=True,exist_ok=True)
    tag='v'+args.version
    notes=args.output/'release-notes.md'
    notes.write_text(f'''taskctl {args.version}: native greenfield alpha for humans and agents.

Native executables and JVM reference runtimes: Windows x86_64, Linux x86_64,
macOS arm64. toolchain.lock selects native after corpus/process parity passed;
toolchain-jvm.lock explicitly selects the JVM reference. No fallback is implicit.
Version aliases work without acquisition. See the README for initialization.

Native v1 is not frozen. Historical adapters remain explicit compatibility paths.
No project software license has been selected. Existing third-party licenses remain.

Source: {args.revision}. CI verifies tests, byte-identical repackaging and clean
consumer acquisition/bootstrap on each platform. SHA-256 digests are in the
release manifest, SHA256SUMS and toolchain.lock. No asset is replaced in place.
''',encoding='utf-8',newline='\n')
    assert json.loads(gh('api',f'repos/{args.repository}/immutable-releases'))['enabled']
    gh('release','create',tag,'--repo',args.repository,'--draft','--prerelease','--target',args.revision,'--title',f'taskctl {args.version}','--notes-file',str(notes))
    gh('release','upload',tag,'--repo',args.repository,*[str(p) for p in archives+statements+parity])
    release=json.loads(gh('release','view',tag,'--repo',args.repository,'--json','apiUrl'))
    # Private and public repos share the API asset transport; auth is explicit
    # in the launcher environment and never persisted in this lock.
    # A draft has no published tag endpoint yet; gh resolves its release ID.
    api=json.loads(gh('api',release['apiUrl']))
    assets={p['name']:p for p in api['assets']}
    locks={}
    for kind,name in [('native','toolchain.lock'),('jvm','toolchain-jvm.lock')]:
        lock=f'lockFormat=2\nwrapperVersion=3\ntoolVersion={args.version}\n'
        for meta in records:
            if meta['implementation']!=kind: continue
            asset=assets[meta['file']]
            assert asset.get('digest')=='sha256:'+meta['sha256'],asset
            lock+=f'{meta["platform"]}.url={asset["url"]}\n{meta["platform"]}.sha256={meta["sha256"]}\n'
        (args.output/name).write_text(lock,encoding='utf-8',newline='\n')
        locks[name]=hashlib.sha256(lock.encode()).hexdigest()
    bound_metadata={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in statements+parity}
    for name,sha in bound_metadata.items(): assert assets[name]['digest']=='sha256:'+sha
    manifest=dict(contract='taskctl.release/alpha2',version=args.version,source_revision=args.revision,repository=args.repository,
        preferred_implementation='native',artifacts=records,toolchains=locks,evidence=bound_metadata,
        attestation='Build provenance statements and parity evidence are assets bound by the signed immutable GitHub release attestation; no separate CI OIDC signature is claimed.')
    (args.output/'release-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8',newline='\n')
    checks=''.join(p['sha256']+'  '+p['file']+'\n' for p in records)
    checks+=''.join(sha+'  '+name+'\n' for name,sha in bound_metadata.items())
    for name in ('toolchain.lock','toolchain-jvm.lock','release-manifest.json'): checks+=hashlib.sha256((args.output/name).read_bytes()).hexdigest()+'  '+name+'\n'
    (args.output/'SHA256SUMS').write_text(checks,encoding='utf-8',newline='\n')
    gh('release','upload',tag,'--repo',args.repository,*[str(args.output/name) for name in ('toolchain.lock','toolchain-jvm.lock','release-manifest.json','SHA256SUMS')])
    print(json.dumps(dict(tag=tag,status='draft',source=args.revision,output=str(args.output))))

if __name__=='__main__': main()
