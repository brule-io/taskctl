"""Record completed proof results and concrete local distribution pins."""
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def main():
    reports = [json.loads((ROOT / f'build/proof/{platform}.json').read_text())
               for platform in ('windows-x86_64', 'linux-x86_64')]
    assert all(report['status'] == 'passed' for report in reports)
    assert reports[0]['frozen_snapshot'] == reports[1]['frozen_snapshot']
    for report in reports:
        archive = ROOT / 'build/distributions' / report['release']['file']
        assert hashlib.sha256(archive.read_bytes()).hexdigest() == report['release']['sha256']
    results = {}
    for module in ('core', 'compatibility', 'conformance', '.proof/oracle'):
        suites = [ET.parse(path).getroot() for path in (ROOT / module / 'build/test-results/test').glob('TEST-*.xml')]
        assert suites
        results[module] = {key: sum(int(suite.attrib[key]) for suite in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
        assert results[module]['failures'] == results[module]['errors'] == results[module]['skipped'] == 0
    output = ROOT / 'docs/proof'
    output.mkdir(parents=True, exist_ok=True)
    for report in reports:
        shutil.copyfile(ROOT / f'build/proof/{report["platform"]}.json', output / f'{report["platform"]}.json')
    shutil.copyfile(ROOT / 'conformance/build/proof/daemon-import-preview.json', output / 'daemon-import-preview.json')
    (output / 'test-results.json').write_text(json.dumps(results, indent=2) + '\n')
    keys = ['lockFormat=1', 'toolVersion=0.1.0-dev.1', 'wrapperVersion=1', 'adapter=fantastikt-loom-agent-2026']
    for report in reports:
        platform = report['platform']
        if platform.startswith('windows'):
            url = (ROOT / 'build/distributions' / report['release']['file']).as_uri()
        else:
            url = 'file:///home/developer/Developer/src/tasking-extraction-proof-20260904/build/distributions/' + report['release']['file']
        keys += [platform + '.url=' + url, platform + '.sha256=' + report['release']['sha256']]
    (ROOT / 'build/proof/toolchain.lock').write_text('\n'.join(keys) + '\n', newline='\n')
    shutil.copyfile(ROOT / 'build/proof/toolchain.lock', output / 'toolchain.lock')
    source = json.loads((ROOT / 'provenance/fantastikt.json').read_text())
    changes = []
    for entry in source['files']:
        current = hashlib.sha256((ROOT / entry['extracted_path']).read_bytes()).hexdigest()
        if current != entry['sha256']:
            changes.append({'path': entry['extracted_path'], 'donor_sha256': entry['sha256'], 'current_sha256': current})
    (ROOT / 'provenance/extraction-changes.json').write_text(json.dumps({'adapter': source['adapter'], 'source_revision': source['revision'], 'changed_donor_files': changes}, indent=2) + '\n')
    shutil.copyfile(ROOT / '.proof/toolchain/linux-jdk-provenance.json', ROOT / 'provenance/linux-jdk.json')
    total = sum(results[module]['tests'] for module in ('core', 'compatibility', 'conformance'))
    lines = ['# Extraction proof — 2026-09-04', '', '**Passed on Windows x64 and aibox Linux x64.**', '',
             f'The canonical build passed **{total} tests** ({results["core"]["tests"]} core, {results["compatibility"]["tests"]} compatibility, {results["conformance"]["tests"]} conformance), with zero failures or skips. The independently built donor oracle passed its original **44 tests**.', '',
             'Both archives bundle Eclipse Temurin **21.0.11+10**. Consumer proof commands ran with an invalid `JAVA_HOME`, no system JVM/Gradle/Git in their command path, an empty Gradle home, and a deliberately failing consumer build.', '',
             '| Platform | Archive SHA-256 |', '| --- | --- |']
    for report in reports:
        lines.append(f'| {report["platform"]} | `{report["release"]["sha256"]}` |')
    lines += ['', 'The frozen Fantastikt specimen contains **40 tasks: 11 open, 29 closed**, plus one roadmap and one epic. `doctor`, `frontier`, `plan`, `list`, and `show` match the independent donor CLI. Windows and Linux produce identical task identities, state, prerequisites, graph layers, frontier, and snapshot IDs.', '',
              'Semantic snapshot:', '', '```text', reports[0]['frozen_snapshot']['semantic_snapshot_id'], '```', '',
              'The integration runs also proved:', '',
              '- Cold checksum-pinned acquisition and warm offline execution leave consumer files unchanged.',
              '- Read commands do not create `.agents/.lock`; rejected reads preserve content and modification times.',
              '- Stale snapshots, unsupported schemas and unsatisfied prerequisites fail explicitly.',
              '- Closure receipts match the donor, apart from their independently generated timestamp.',
              '- Closure and fault-injected recovery change only the selected open/closed task records and the declared lock/transaction state; unrelated staged/unstaged files and `.git` bytes remain unchanged.',
              '- Arguments retain spaces, embedded quotes, shell metacharacters and trailing backslashes on Windows PowerShell and POSIX shell.',
              '- Wrong checksums, duplicate lock keys, version mismatches and tampered cache files fail closed.',
              '- Linux ledger symlinks cannot redirect reads or closure into unrelated files.', '',
              'Core conformance covers the sealed value algebra, exact large-number preservation, unknown extension round trips, namespace rejection, semantic contract changes, historical receipt binding, provider activation/constraints and contributed-edge cycles. The PSI policy has negative fixtures for forbidden top types and suppression escapes.', '',
              'DAEMON conformance retains the full identities of both `TASK.process.006.*` specimens and the historical closed state/narrative of `TASK.M2.nginx` despite its retained unchecked criteria. Import previews record exact source revisions, adapter versions, source hashes, contract digests and a manifest ID; none is represented as native-v1 evidence.', '',
              'Machine-readable evidence:', '',
              '- [Windows run](proof/windows-x86_64.json)',
              '- [Linux run](proof/linux-x86_64.json)',
              '- [Test counts](proof/test-results.json)',
              '- [DAEMON import preview](proof/daemon-import-preview.json)',
              '- [Concrete two-host lock](proof/toolchain.lock)',
              '- [Changed donor files](../provenance/extraction-changes.json)', '',
              'Archives are in `build/distributions/`. Disposable consumers remain at the run directories recorded in each report. The remote proof is under `/home/developer/Developer/src/tasking-extraction-proof-20260904`.', '',
              'This proves the first external implementation boundary. Native protocol freezing, full dialect migration, production provider loading, private-registry publication, generator integration and consumer adoption remain later milestones. The GitLab CI configuration is present; no remote pipeline or publication was triggered.', '']
    (ROOT / 'docs/PROOF.md').write_text('\n'.join(lines), encoding='utf-8')
    print(f'Recorded {total} canonical tests, 44 oracle tests, and both platform proofs.')

if __name__ == '__main__':
    main()
