"""Assemble an optional RPM release from successful Fedora CI, without replacing assets.

Defaults to a companion packaging release because published upstream releases are
immutable. --tag may select an existing draft for future unified release assembly.
Tar/wrapper releases never depend on this script or the optional RPM workflow.
"""
import argparse, hashlib, json, subprocess
from pathlib import Path

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def gh(*args): return subprocess.check_output(['gh',*args],text=True,encoding='utf-8').strip()
def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--artifacts',type=Path,required=True)
    parser.add_argument('--repository',default='brule-io/taskctl'); parser.add_argument('--tag')
    parser.add_argument('--run',required=True,type=int,help='Successful optional Fedora RPM workflow run')
    args=parser.parse_args(); root=args.artifacts
    meta=json.loads((root/'rpm-manifest.json').read_text(encoding='utf-8'))
    assert meta['contract']=='taskctl.rpm/alpha1' and not meta['packaging_dirty'] and not meta['compiler_invoked']
    proof=meta['test']; assert proof['passed'] and proof['canonical_executable_unchanged'] and proof['network_mode']=='none'
    assert proof['canonical_executable_sha256']==meta['graalvm']['executable_sha256']
    assert all(t['internet_socket_calls']==0 for t in proof['transaction_traces'].values())
    run=json.loads(gh('run','view',str(args.run),'--repo',args.repository,'--json','status,conclusion,headSha,url,workflowName'))
    assert run['status']=='completed' and run['conclusion']=='success' and run['headSha']==meta['packaging_revision']
    assert run['workflowName']=='Fedora RPM (optional)'
    assets={p.name:p for p in root.iterdir() if p.is_file()}
    checks={name:digest for digest,name in (line.split('  ',1) for line in (root/'RPM-SHA256SUMS').read_text(encoding='utf-8').splitlines())}
    assert set(checks)==set(assets)-{'RPM-SHA256SUMS'}
    assert all(sha(assets[name])==digest for name,digest in checks.items())
    for record in meta['artifacts']:
        assert sha(assets[record['file']])==record['sha256']
        statement=json.loads(assets[record['file']+'.intoto.json'].read_text(encoding='utf-8'))
        assert statement['subject']==[dict(name=record['file'],digest=dict(sha256=record['sha256']))]
        assert statement['predicate']['buildDefinition']['internalParameters']['packaging_revision']==meta['packaging_revision']
    tag=args.tag or f'rpm-v{meta["version"]}-{meta["release"]}'
    notes=root.parent/'rpm-release-notes.md'
    notes.write_text(f'''Fedora 44 x86_64 RPM packaging of taskctl {meta['version']}.

The executable is byte-identical to the canonical native archive in
[v{meta['version']}](https://github.com/{args.repository}/releases/tag/v{meta['version']}).
No taskctl compiler or alternate implementation is involved. The existing tar.gz
and repository-pinned wrapper distributions remain independently available.

Install a verified local package with `sudo dnf install ./taskctl-*.x86_64.rpm`;
upgrade with `sudo dnf upgrade ./taskctl-*.x86_64.rpm`; erase with
`sudo dnf remove taskctl`. `/usr/bin/taskctl` is global; `./taskctl` retains the
repository's exact pin. No package scriptlets, triggers, network operations,
services, project initialization or project-owned paths are installed.

[Fedora CI]({run['url']}) passed install, version/help, unprivileged greenfield init,
doctor/frontier, a packaging-release upgrade, and erase with networking disabled.
Transactions preserved project/user bytes, mtimes and modes. Cached `./taskctl`
remained operational after erase. Package-content checks and rpmlint ran; raw
findings and documented exceptions are attached. The native executable is kept
unstripped to preserve its proven digest. The license decision remains pending:
`LicenseRef-taskctl-license-pending` grants no project license. Existing
third-party notices are included. No shell completions currently exist upstream.

The source RPM contains the standard spec and the original binary archive: it
repackages the proven artifact, not the Kotlin sources. It provides the future
COPR build seam once licensing/publishing eligibility is settled. No COPR project
or official Fedora submission is created by this release.

Packaging source: `{meta['packaging_revision']}`.
Canonical tool source: `{meta['upstream']['source_revision']}`.
`RPM-SHA256SUMS`, `rpm-manifest.json`, and per-RPM in-toto provenance bind the
package, canonical archive/executable, GraalVM identity, Fedora environment and
tests. The signed immutable GitHub release attestation binds published assets;
no separate RPM signing key, CI OIDC build signature or SLSA level is claimed.
Native protocol v1 remains unfrozen.
''',encoding='utf-8',newline='\n')
    assert json.loads(gh('api',f'repos/{args.repository}/immutable-releases'))['enabled']
    lookup=subprocess.run(['gh','release','view',tag,'--repo',args.repository,'--json','apiUrl,isDraft'],capture_output=True,text=True)
    if lookup.returncode==0:
        found=json.loads(lookup.stdout); assert found['isDraft'],'Published releases are immutable; select a new packaging tag'
        api=json.loads(gh('api',found['apiUrl']))
        assert not set(assets)&{a['name'] for a in api['assets']},'Never replace assets'
    else:
        gh('release','create',tag,'--repo',args.repository,'--draft','--prerelease','--target',meta['packaging_revision'],
            '--title',f'taskctl {meta["version"]}: Fedora RPM {meta["release"]}','--notes-file',str(notes))
    gh('release','upload',tag,'--repo',args.repository,*[str(p) for p in assets.values()])
    api=json.loads(gh('api',json.loads(gh('release','view',tag,'--repo',args.repository,'--json','apiUrl'))['apiUrl']))
    uploaded={a['name']:a for a in api['assets']}
    for name,path in assets.items(): assert uploaded[name]['digest']=='sha256:'+sha(path)
    print(json.dumps(dict(tag=tag,status='draft',assets=len(assets),notes=str(notes))))
if __name__=='__main__': main()
