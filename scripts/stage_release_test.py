"""Prepare source-free release smoke tests; consumes only downloaded metadata."""
import hashlib, json
from pathlib import Path
from package import target
root=Path(__file__).resolve().parents[1]/'build/distributions'
manifest=json.loads((root/'release-manifest.json').read_text(encoding='utf-8'))
assert manifest['contract']=='taskctl.release/alpha2' and manifest['preferred_implementation']=='native'
for name,sha in manifest['toolchains'].items(): assert hashlib.sha256((root/name).read_bytes()).hexdigest()==sha
for kind in ('native','jvm'):
    record=next(item for item in manifest['artifacts'] if item['platform']==target() and item['implementation']==kind)
    assert hashlib.sha256((root/record['file']).read_bytes()).hexdigest()==record['sha256']
    statement=root/(record['file']+'.intoto.json')
    assert hashlib.sha256(statement.read_bytes()).hexdigest()==manifest['evidence'][statement.name]
    (root/(kind+'-'+target()+'.json')).write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8',newline='\n')
    print('Verified release archive, lock and provenance:',record['file'])
