"""Exercise the external distribution using owned disposable consumers only.

Python orchestrates the experiment; consumer wrappers never require Python,
Gradle, Kotlin, a system JVM, a Git executable, or this repository's sources.
"""
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
WINDOWS = platform.system() == 'Windows'
PLATFORM = 'windows-x86_64' if WINDOWS else 'linux-x86_64'
ADAPTER = 'fantastikt-loom-agent-2026'
OPEN_RECEIPT = '```yaml\nclosed_at: null\nclosed_by: null\nsummary: null\nverification:\n  - null\nfollow_ups:\n  - null\n```\n'

def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8', newline='\n')

def fingerprint(root):
    return {p.relative_to(root).as_posix(): (hashlib.sha256(p.read_bytes()).hexdigest(), p.stat().st_mtime_ns)
            for p in root.rglob('*') if p.is_file()}

def changed(before, after):
    return sorted(p for p in before.keys() | after.keys() if before.get(p) != after.get(p))

def git(root, *args):
    return subprocess.run(['git', '--no-optional-locks', '-C', str(root), *args], capture_output=True, check=True)

def specimen(root, frozen=False):
    root.mkdir(parents=True)
    if frozen:
        shutil.copytree(ROOT / 'conformance/src/test/resources/fantastikt/.agents', root / '.agents')
    else:
        write(root / '.agents/epics/open/EPIC.test.001.proof.md',
              '---\nkind: epic\nschema: loom.agent/v1\nref: EPIC.test.001\n---\n\n# EPIC.test.001: Proof\n\n## Description\n\nDisposable proof.\n\n## Outcomes\n\n- Proven behavior.\n\n## Boundaries\n\n- Test fixtures only.\n\n## Closure\n\n' + OPEN_RECEIPT)
        write(root / '.agents/roadmaps/open/ROADMAP.test.001.proof.md',
              '---\nkind: roadmap\nschema: loom.agent/v1\nref: ROADMAP.test.001\nepic: EPIC.test.001\nordinal: 0\n---\n\n# ROADMAP.test.001: Proof\n\n## Description\n\nDisposable grouping.\n\n## Outcomes\n\n- Proven behavior.\n\n## Exit Criteria\n\n- [ ] Proof complete.\n\n## Closure\n\n' + OPEN_RECEIPT)
        for number in (1, 2):
            ref = f'TASK.test.{number:03}'
            depends = '' if number == 1 else '\n  - TASK.test.001'
            write(root / f'.agents/tasks/open/{ref}.proof.md',
                  f'---\nkind: task\nschema: loom.agent/v1\nref: {ref}\nroadmap: ROADMAP.test.001\neffort: LOW\nimpact: HIGH\ndepends: {depends}\n---\n\n# {ref}: Proof task\n\n## Description\n\nDisposable execution contract.\n\n## Requirements\n\n- Preserve bounded changes.\n\n## Deliverables\n\n- [x] Demonstrate the boundary.\n\n## Closure\n\n' + OPEN_RECEIPT)
    write(root / 'build.gradle.kts', 'error("THE CONSUMER BUILD MUST NEVER EXECUTE")\n')
    write(root / 'source.txt', 'initial\n')
    write(root / '.gitignore', '.agents/.lock\n.agents/.taskctl-close-*\n')
    git(root, 'init', '-q')
    git(root, 'config', 'core.autocrlf', 'false')
    git(root, 'add', '.')
    git(root, '-c', 'user.name=Extraction Proof', '-c', 'user.email=proof@example.invalid', 'commit', '-qm', 'Disposable baseline')
    write(root / 'source.txt', 'staged change\n')
    git(root, 'add', 'source.txt')
    write(root / 'source.txt', 'unstaged change\n')
    write(root / 'untracked.txt', 'unrelated untracked work\n')

def main():
    run = ROOT / '.proof/runs' / (PLATFORM + '-' + uuid.uuid4().hex[:10])
    run.mkdir(parents=True)
    release = json.loads((ROOT / f'build/distributions/{PLATFORM}.json').read_text())
    archive = ROOT / 'build/distributions' / release['file']
    checks = []
    env = os.environ.copy()
    for key in ('JDK_JAVA_OPTIONS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS'):
        env.pop(key, None)
    env.update({'TASKCTL_CACHE': str(run / 'cache'), 'JAVA_HOME': str(run / 'no-system-jvm'),
                'GRADLE_USER_HOME': str(run / 'empty-gradle-home'), 'TERM': 'dumb', 'NO_COLOR': '1'})
    if WINDOWS:
        shell = Path(os.environ['SystemRoot']) / 'System32/WindowsPowerShell/v1.0/powershell.exe'
        env['PATH'] = str(Path(os.environ['SystemRoot']) / 'System32')
    else:
        # An actual command path without java, git, gradle, kotlinc or python.
        binaries = run / 'bootstrap-only-bin'
        binaries.mkdir()
        for name in ('uname', 'dirname', 'mkdir', 'curl', 'mktemp', 'sha256sum', 'mv', 'rm', 'tar', 'sed', 'grep', 'gzip'):
            (binaries / name).symlink_to(shutil.which(name))
        env['PATH'] = str(binaries)

    def pin(root, override=None):
        for filename in ('taskctl', 'taskctl.ps1', 'taskctl.cmd'):
            shutil.copyfile(ROOT / 'packaging' / filename, root / filename)
        (root / 'taskctl').chmod(0o755)
        values = {'lockFormat': '1', 'toolVersion': release['version'], 'wrapperVersion': '1', 'adapter': ADAPTER,
                  'windows-x86_64.url': 'file:///unused', 'windows-x86_64.sha256': '0' * 64,
                  'linux-x86_64.url': 'file:///unused', 'linux-x86_64.sha256': '0' * 64}
        values.update({f'{PLATFORM}.url': archive.as_uri(), f'{PLATFORM}.sha256': release['sha256']})
        values.update(override or {})
        write(root / '.taskctl/toolchain.lock', ''.join(f'{key}={value}\n' for key, value in values.items()))

    def execute(argv, root, expected=0, extra_env=None):
        completed = subprocess.run([str(v) for v in argv], cwd=root, env=env | (extra_env or {}),
                                   capture_output=True, text=True, encoding='utf-8', timeout=90)
        if expected is not None:
            assert completed.returncode == expected, (argv, completed.returncode, completed.stdout, completed.stderr)
        return completed

    def tool(root, arguments, expected=0, extra_env=None):
        launcher = [shell, '-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', root / 'taskctl.ps1'] if WINDOWS else [root / 'taskctl']
        return execute(launcher + arguments, root, expected, extra_env)

    new = run / 'Fantastikt specimen'
    old = run / 'original specimen'
    specimen(new, frozen=True); specimen(old, frozen=True); pin(new)
    before = fingerprint(new)
    assert 'offline' in tool(new, ['info'], expected=2, extra_env={'TASKCTL_OFFLINE': '1'}).stderr
    info = json.loads(tool(new, ['info']).stdout)
    assert info['version'] == release['version'] and info['native_v1'] == 'not-frozen'
    assert fingerprint(new) == before
    assert json.loads(tool(new, ['info'], extra_env={'TASKCTL_OFFLINE': '1'}).stdout) == info
    checks.append('cold acquisition and warm offline use; bootstrap leaves consumer unchanged')
    home = run / 'cache' / release['sha256'] / 'home'
    java = home / ('runtime/bin/java.exe' if WINDOWS else 'runtime/bin/java')
    oracle_lib = ROOT / '.proof/oracle/build/install/fantastikt-oracle/lib'

    def oracle(root, arguments, expected=0):
        return execute([java, '-cp', str(oracle_lib / '*'), 'io.brule.workflow.cli.TaskCtlKt', *arguments], root, expected)

    snapshot = json.loads(tool(new, ['snapshot', '--repo', str(new)]).stdout)
    for arguments in (['doctor'], ['frontier'], ['plan'], ['list', '--state', 'all'], ['show', snapshot['tasks'][0]['id']]):
        left = oracle(old, [*arguments, '--repo', str(old)])
        right = tool(new, [*arguments, '--repo', str(new)])
        assert (left.stdout, left.stderr) == (right.stdout, right.stderr), (arguments, left.stdout, right.stdout)
    assert fingerprint(new) == before
    assert (old / '.agents/.lock').exists() and not (new / '.agents/.lock').exists()
    checks.append('frozen Fantastikt doctor/frontier/plan/list/show parity; external reads create no lock')
    assert len(snapshot['tasks']) == 40
    assert sum(item['state'] == 'open' for item in snapshot['tasks']) == 11

    mutation = run / 'closure specimen'; original_mutation = run / 'original closure'
    specimen(mutation); specimen(original_mutation); pin(mutation)
    baseline = json.loads(tool(mutation, ['snapshot']).stdout)['snapshot_id']
    task2 = mutation / '.agents/tasks/open/TASK.test.002.proof.md'
    original_task2 = task2.read_bytes()
    write(task2, task2.read_text().replace('Preserve bounded changes.', 'Preserve a changed contract.'))
    state_before = fingerprint(mutation)
    close_args = ['close', 'TASK.test.001', '--summary', 'Boundary proof with spaces', '--evidence', 'integration proof: passed',
                  '--closed-by', 'proof-agent', '--expect-frontier', 'TASK.test.002']
    failure = tool(mutation, [*close_args, '--expect-snapshot', baseline], expected=1)
    assert 'stale record snapshot' in failure.stderr
    assert set(changed(state_before, fingerprint(mutation))) <= {'.agents/.lock'}
    task2.write_bytes(original_task2)
    state_before = fingerprint(mutation)
    tool(mutation, [*close_args, '--expect-snapshot', baseline])
    oracle(original_mutation, close_args)
    closed_path = '.agents/tasks/closed/TASK.test.001.proof.md'
    def receipt_without_time(root):
        return re.sub(r'closed_at: [^\n]+', 'closed_at: <clock>', (root / closed_path).read_text())
    assert receipt_without_time(mutation) == receipt_without_time(original_mutation)
    writes = changed(state_before, fingerprint(mutation))
    assert set(writes) <= {'.agents/.lock', '.agents/tasks/open/TASK.test.001.proof.md', closed_path}, writes
    assert tool(mutation, ['frontier']).stdout == oracle(original_mutation, ['frontier']).stdout
    checks.append('stale snapshot rejected under lock; evidenced closure and receipts match donor with bounded writes')

    blocked = run / 'blocked specimen'; specimen(blocked); pin(blocked)
    failure = tool(blocked, ['close', 'TASK.test.002', '--summary', 'Blocked', '--evidence', 'fixture'], expected=1)
    assert 'unsatisfied dependencies' in failure.stderr
    malformed = blocked / '.agents/tasks/open/TASK.test.001.proof.md'
    write(malformed, malformed.read_text().replace('schema: loom.agent/v1', 'schema: unsupported/v99'))
    before_bad = fingerprint(blocked)
    failure = tool(blocked, ['doctor'], expected=1)
    assert 'TASK_SCHEMA' in failure.stderr
    assert fingerprint(blocked) == before_bad
    checks.append('blocked closure and unsupported schema fail without task/source/Git writes')

    quoted = run / 'quoted arguments'; specimen(quoted); pin(quoted)
    summary = 'Literal "quoted" spaces $() & ; path C:\\proof\\'
    evidence = 'evidence "quoted" with spaces and \\tail\\'
    # PowerShell -File itself uses Windows quoting; this checks the complete
    # Python -> shell -> wrapper -> bundled JVM path, not just an API call.
    tool(quoted, ['close', 'TASK.test.001', '--summary', summary, '--evidence', evidence, '--closed-by', 'proof-agent'])
    document = (quoted / closed_path).read_text()
    assert 'summary: ' + summary in document, document
    assert '  - ' + evidence in document, document
    checks.append('spaces, embedded quotes, literal shell metacharacters and trailing backslashes survive argument forwarding')

    recovery = run / 'recovery specimen'; specimen(recovery); pin(recovery)
    prior = fingerprint(recovery)
    proof_jar = next((ROOT / 'conformance/build/libs').glob('*-proof.jar'))
    separator = ';' if WINDOWS else ':'
    result = execute([java, '-cp', str(home / 'lib/*') + separator + str(proof_jar),
                      'io.brule.tasking.conformance.RecoveryProbeKt', recovery], recovery)
    assert result.stdout.strip() == 'packaged-recovery: ok'
    recovery_writes = changed(prior, fingerprint(recovery))
    assert set(recovery_writes) <= {'.agents/.lock', '.agents/tasks/open/TASK.test.001.proof.md', closed_path}, recovery_writes
    checks.append('packaged runtime recovers an interrupted committed closure using the original snapshot and receipt')

    wrong_pin = run / 'wrong pin'; specimen(wrong_pin); pin(wrong_pin, {f'{PLATFORM}.sha256': 'f' * 64})
    assert 'checksum mismatch' in tool(wrong_pin, ['info'], expected=2).stderr
    pin(wrong_pin, {'toolVersion': '0.0.0-wrong'})
    assert 'match distribution' in tool(wrong_pin, ['info'], expected=2).stderr
    pin(wrong_pin)
    with (wrong_pin / '.taskctl/toolchain.lock').open('a') as lock:
        lock.write('lockFormat=1\n')
    assert 'uplicate' in tool(wrong_pin, ['info'], expected=2).stderr
    cached_archive = home.parent / ('archive.zip' if WINDOWS else 'archive.tar.gz')
    with cached_archive.open('ab') as stream: stream.write(b'tamper')
    assert 'checksum mismatch' in tool(new, ['info'], expected=2).stderr
    shutil.copyfile(archive, cached_archive)
    library = next((home / 'lib').glob('core-*.jar'))
    contents = library.read_bytes()
    library.write_bytes(contents + b'tamper')
    assert 'checksum mismatch' in tool(new, ['info'], expected=2).stderr
    library.write_bytes(contents)
    checks.append('bad pins, duplicate lock keys, version mismatch and tampered archive/runtime cache fail closed')

    if not WINDOWS:
        linked = run / 'linked specimen'; specimen(linked); pin(linked)
        outside = run / 'outside.md'; write(outside, 'must remain unchanged\n')
        (linked / '.agents/tasks/open/TASK.test.003.link.md').symlink_to(outside)
        assert 'ledger link' in tool(linked, ['doctor'], expected=1).stderr
        assert 'ledger link' in tool(linked, close_args, expected=1).stderr
        assert outside.read_text() == 'must remain unchanged\n'
        checks.append('ledger symlinks cannot redirect read or closure paths outside the specimen')

    output = ROOT / 'build/proof'
    output.mkdir(parents=True, exist_ok=True)
    report = {'status': 'passed', 'platform': PLATFORM, 'run_directory': str(run), 'release': release,
              'tool_info': info, 'checks': checks, 'closure_writes': writes, 'recovery_writes': recovery_writes,
              'frozen_snapshot': snapshot, 'oracle_revision': 'b932b0eecbc2b6053b3f2235ad2963fd31fbb4c4'}
    (output / f'{PLATFORM}.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': 'passed', 'platform': PLATFORM, 'checks': len(checks), 'report': str(output / f'{PLATFORM}.json')}))

if __name__ == '__main__':
    main()
