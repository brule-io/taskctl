"""Repackage the same clean inputs and require byte-identical archives."""
import hashlib, json, subprocess, sys
from pathlib import Path
from package import target
root=Path(__file__).resolve().parents[1]
metadata=root/f'build/distributions/{target()}.json'
first=json.loads(metadata.read_text())
subprocess.run([sys.executable,str(root/'scripts/package.py')],check=True)
second=json.loads(metadata.read_text())
assert first==second,(first,second)
assert hashlib.sha256((root/'build/distributions'/second['file']).read_bytes()).hexdigest()==first['sha256']
print('Byte-identical repackage:',first['file'],first['sha256'])
