"""Repackage the same clean inputs and require byte-identical archives."""
import argparse, hashlib, json, subprocess, sys
from pathlib import Path
from package import target
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--kind',choices=['native','jvm'],default='jvm')
args=parser.parse_args()
metadata=root/f'build/distributions/{args.kind}-{target()}.json'
first=json.loads(metadata.read_text(encoding='utf-8'))
subprocess.run([sys.executable,str(root/'scripts/package.py'),'--kind',args.kind],check=True)
second=json.loads(metadata.read_text(encoding='utf-8'))
assert first==second,(first,second)
assert hashlib.sha256((root/'build/distributions'/second['file']).read_bytes()).hexdigest()==first['sha256']
print('Byte-identical repackage:',first['file'],first['sha256'])
