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
    records=[]; archives=[]
    for platform in platforms:
        matches=list(args.artifacts.rglob(platform+'.json'))
        assert len(matches)==1,(platform,matches)
        meta=json.loads(matches[0].read_text())
        assert meta['version']==args.version and meta['source_revision']==args.revision and not meta['source_dirty'],meta
        archive=matches[0].parent/meta['file']
        assert hashlib.sha256(archive.read_bytes()).hexdigest()==meta['sha256']
        proof=list(args.artifacts.rglob('bootstrap-'+platform+'.json'))
        assert len(proof)==1 and json.loads(proof[0].read_text())['archive_sha256']==meta['sha256']
        records.append(meta); archives.append(archive)
    args.output.mkdir(parents=True,exist_ok=True)
    tag='v'+args.version
    notes=args.output/'release-notes.md'
    notes.write_text(f'''taskctl {args.version}: native greenfield alpha for humans and agents.

Standalone runtimes: Windows x86_64, Linux x86_64, macOS arm64.
Download the matching archive and toolchain.lock. The archive includes taskctl,
taskctl.ps1/taskctl.bat and Java. See the README for initialization commands.

Native v1 is not frozen. Historical adapters remain explicit compatibility paths.
No project software license has been selected. Existing third-party licenses remain.

Source: {args.revision}. CI verifies tests, byte-identical repackaging and clean
consumer acquisition/bootstrap on each platform. SHA-256 digests are in the
release manifest, SHA256SUMS and toolchain.lock. No asset is replaced in place.
''',encoding='utf-8',newline='\n')
    assert json.loads(gh('api',f'repos/{args.repository}/immutable-releases'))['enabled']
    gh('release','create',tag,'--repo',args.repository,'--draft','--prerelease','--target',args.revision,'--title',f'taskctl {args.version}','--notes-file',str(notes))
    gh('release','upload',tag,'--repo',args.repository,*[str(p) for p in archives])
    release=json.loads(gh('release','view',tag,'--repo',args.repository,'--json','apiUrl'))
    # Private and public repos share the API asset transport; auth is explicit
    # in the launcher environment and never persisted in this lock.
    # A draft has no published tag endpoint yet; gh resolves its release ID.
    api=json.loads(gh('api',release['apiUrl']))
    assets={p['name']:p for p in api['assets']}
    lock=f'lockFormat=2\nwrapperVersion=2\ntoolVersion={args.version}\n'
    for meta in records:
        asset=assets[meta['file']]
        assert asset.get('digest')=='sha256:'+meta['sha256'],asset
        lock+=f'{meta["platform"]}.url={asset["url"]}\n{meta["platform"]}.sha256={meta["sha256"]}\n'
    (args.output/'toolchain.lock').write_text(lock,encoding='utf-8',newline='\n')
    manifest=dict(contract='taskctl.release/alpha1',version=args.version,source_revision=args.revision,repository=args.repository,artifacts=records,
        toolchain_sha256=hashlib.sha256(lock.encode()).hexdigest())
    (args.output/'release-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8',newline='\n')
    checks=''.join(p['sha256']+'  '+p['file']+'\n' for p in records)
    for name in ('toolchain.lock','release-manifest.json'): checks+=hashlib.sha256((args.output/name).read_bytes()).hexdigest()+'  '+name+'\n'
    (args.output/'SHA256SUMS').write_text(checks,encoding='utf-8',newline='\n')
    gh('release','upload',tag,'--repo',args.repository,*[str(args.output/name) for name in ('toolchain.lock','release-manifest.json','SHA256SUMS')])
    print(json.dumps(dict(tag=tag,status='draft',source=args.revision,output=str(args.output))))

if __name__=='__main__': main()
