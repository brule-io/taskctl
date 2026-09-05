"""Read exact local/GitLab-origin Git objects; retain bounded planning witnesses.

No product edits, checkout, fetch, migration, or historical reinterpretation.
The aibox copies supply previously verified canonical GitLab revisions.
"""
import base64
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
LOCAL = {
    'fantastikt': 'b932b0eecbc2b6053b3f2235ad2963fd31fbb4c4',
    'loom-ir': 'd66b540ab8bcdc12360ba74ba290aa7aec080a0c',
    'brule-message-bus': '1d2a454bc6819cb9f6e96cecb2ea69a41c1a57aa',
    'dropzone/dropzone-app': 'f9828c2df1f14f18864cf080fe336e144d2db5cb',
    'dropzone/dropzone-biz': '34c5030551f98e708e8ccf8b4f10b194a19f58aa',
}
REMOTE = [
    {'directory': 'daemon-geist-workspace', 'repository': 'https://gitlab.com/daemon-eng/geist/daemon-geist-workspace',
     'revision': '4328917ad11541fc06e9e951f836380c3d10c7dd', 'files': [
         '.agents/AGENTS.md', 'ROADMAP.geist.converse-tool-call-and-copyability.md',
         'TASK.core.388.shell-exec-agent-prompt-contract.md',
         'TASK.operator.1159.converse-prompt-tool-call-loop.md',
         'TASK.operator.1160.converse-copyable-evidence-surface.md',
         'TASK.operator.1161.converse-tool-call-browser-audit.md']},
    {'directory': 'daemon-platform-workspace', 'repository': 'https://gitlab.com/daemon-eng/platform/daemon-platform-workspace',
     'revision': '28d716e2f480676aa18e6a208751066b3c7dbc2a', 'files': [
         '.agents/AGENTS.md', '.agents/memos/MEMO.process.multi-roadmap-contract.md',
         'ROADMAP.platform.alpha.hardening.phase2.md', 'ROADMAP.platform.alpha.release.md',
         'TASK.alpha.034.spec-first-tooling.md']},
]

def main():
    output = ROOT / 'conformance/src/test/resources/planning-history'
    output.mkdir(parents=True, exist_ok=True)
    inventory = []
    for name, revision in LOCAL.items():
        repo = ROOT.parent / name
        paths = subprocess.check_output(['git', '-C', str(repo), 'ls-tree', '-r', '--name-only', revision, '.agents'], text=True).splitlines()
        counts = {kind: len([p for p in paths if p.startswith('.agents/' + kind + '/') and Path(p).name.startswith(prefix)])
                  for kind, prefix in [('tasks', 'TASK'), ('roadmaps', 'ROADMAP'), ('epics', 'EPIC')]}
        inventory.append({'repository': name, 'revision': revision, 'counts': counts})
    remote_script = '''import subprocess,json,base64
specs = SPECS
result = []
for spec in specs:
 repo='/home/developer/Developer/src/daemon-meta-workspace/'+spec['directory']
 paths=subprocess.check_output(['git','-C',repo,'ls-tree','-r','--name-only',spec['revision'],'.agents'],text=True).splitlines()
 for requested in spec['files']:
  matches=[p for p in paths if p==requested or ('/' not in requested and p.rsplit('/',1)[-1]==requested)]
  assert len(matches)==1,(requested,matches)
  path=matches[0]
  data=subprocess.check_output(['git','-C',repo,'show',spec['revision']+':'+path])
  result.append({'repository':spec['repository'],'revision':spec['revision'],'path':path,'directory':spec['directory'],'bytes':base64.b64encode(data).decode()})
print(json.dumps(result))
'''.replace('SPECS', repr(REMOTE))
    data = subprocess.check_output(['ssh', '-o', 'BatchMode=yes', 'aibox', 'python3 -'], input=remote_script.encode())
    sources = []
    for item in json.loads(data):
        content = base64.b64decode(item.pop('bytes'))
        filename = item.pop('directory') + '/' + item['path'].replace('.agents/', '', 1)
        target = output / filename
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)
        sources.append(item | {'file': filename, 'sha256': hashlib.sha256(content).hexdigest()})
    manifest = {'schema': 'tasking/planning-source-witness/1', 'classification': 'historical-source-evidence-not-native-import',
                'local_inventory': inventory, 'sources': sources}
    (output / 'sources.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'sources': len(sources), 'local_inventory': inventory}))

if __name__ == '__main__':
    main()
