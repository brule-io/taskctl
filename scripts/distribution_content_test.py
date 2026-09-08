"""Inspect packaged consumer guides and experimental dependency exclusion."""
import argparse
import hashlib
import json
import posixpath
import re
import tarfile
import zipfile
from pathlib import Path
from urllib.parse import unquote, urlsplit
from package import ROOT, target


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--kind',choices=['native','jvm'],required=True)
    args=parser.parse_args()
    metadata=json.loads((ROOT/f'build/distributions/{args.kind}-{target()}.json').read_text(encoding='utf-8'))
    archive=ROOT/'build/distributions'/metadata['file']
    assert hashlib.sha256(archive.read_bytes()).hexdigest()==metadata['sha256']
    if archive.suffix=='.zip':
        with zipfile.ZipFile(archive) as bundle:
            names=set(bundle.namelist())
            documents={name:bundle.read(name) for name in names if name=='README.md' or name.startswith('docs/')}
    else:
        with tarfile.open(archive) as bundle:
            names={entry.name for entry in bundle.getmembers() if entry.isfile()}
            documents={name:bundle.extractfile(name).read() for name in names if name=='README.md' or name.startswith('docs/')}
    required={'README.md','LICENSE','NOTICE.md','THIRD-PARTY-NOTICES.zip','docs/READ-MODELS.md',
              'docs/PLANNING-HISTORY.md','docs/EFFECTIVE-PROFILES.md','docs/EVIDENCE-TIME.md',
              'docs/VALUE-BOUNDARIES.md','docs/ASSERTION-BOUNDARIES.md','docs/spec/NATIVE-V1-CANDIDATE.md',
              'docs/spec/candidate.json',f'docs/RELEASE-{metadata["version"]}.md'}
    assert required<=names,required-names
    assert not any(name.startswith(('docs/proof/','core/','kernel/','idl/')) for name in names)
    libraries=metadata['build_identity']['libraries']
    assert not any(name.startswith(('kernel-','idl-','postgresql-','smithy-')) for name in libraries)
    if args.kind=='native': assert not any(name.startswith(('runtime/','lib/')) or name.endswith('.jar') for name in names)
    assert documents['docs/spec/candidate.json']==(ROOT/'docs/spec/candidate.json').read_bytes()
    local_links=0;bound_links=0
    for name,raw in documents.items():
        if not name.endswith('.md'): continue
        chunks=re.split(r'(```[\s\S]*?```|~~~[\s\S]*?~~~|`[^`\n]*`)',raw.decode('utf-8'))
        prose=''.join(chunk for index,chunk in enumerate(chunks) if index%2==0)
        for link in re.findall(r'\]\(([^)]+)\)',prose):
            parsed=urlsplit(link)
            if parsed.scheme or parsed.netloc:
                if link.startswith(f'https://github.com/brule-io/taskctl/blob/{metadata["source_revision"]}/') or link.startswith(f'https://github.com/brule-io/taskctl/tree/{metadata["source_revision"]}/'):
                    bound_links+=1
                continue
            if not parsed.path: continue
            resolved=posixpath.normpath(posixpath.join(posixpath.dirname(name),unquote(parsed.path)))
            assert resolved in names,(name,link,resolved)
            local_links+=1
    assert local_links>0 and bound_links>0
    result=dict(contract='taskctl.distribution-content/1',platform=target(),implementation=args.kind,
                version=metadata['version'],source_revision=metadata['source_revision'],archive_sha256=metadata['sha256'],
                guide_files=len(documents),local_links_checked=local_links,source_bound_links=bound_links,
                experimental_dependencies_excluded=True,native_v1='not-frozen')
    out=ROOT/'build/proof';out.mkdir(parents=True,exist_ok=True)
    (out/f'content-{args.kind}-{target()}.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(result))


if __name__=='__main__': main()
