"""Prepare source-free release smoke tests; consumes only downloaded metadata."""
import hashlib, json
from pathlib import Path
from package import target
root=Path(__file__).resolve().parents[1]/'build/distributions'
manifest=json.loads((root/'release-manifest.json').read_text())
assert manifest['contract']=='taskctl.release/alpha1'
assert hashlib.sha256((root/'toolchain.lock').read_bytes()).hexdigest()==manifest['toolchain_sha256']
record=next(item for item in manifest['artifacts'] if item['platform']==target())
assert hashlib.sha256((root/record['file']).read_bytes()).hexdigest()==record['sha256']
(root/(target()+'.json')).write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8',newline='\n')
print('Verified release archive and lock:',record['file'])
