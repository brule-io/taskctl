"""Check candidate source/evidence links; no protocol evaluation or external I/O."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def local(name):
    path = (ROOT / name).resolve()
    if not path.is_relative_to(ROOT) or not path.is_file():
        raise ValueError(f"missing or external candidate path: {name}")
    return path


def check():
    index = json.loads(local('docs/spec/candidate.json').read_text(encoding='utf-8'))
    if index['contract'] != 'taskctl.specification-index/1' or index['status'] != 'proposal-not-frozen':
        raise ValueError('unexpected candidate identity or freeze claim')
    document = local(index['document']).read_text(encoding='utf-8')
    selected = {p.relative_to(ROOT).as_posix() for module in index['modules']
                for p in (ROOT / module / 'src/main/kotlin').rglob('*.kt')}
    recorded = {entry['path'] for entry in index['sources']}
    if selected != recorded or len(recorded) != len(index['sources']):
        raise ValueError('candidate source inventory changed; review the candidate explicitly')
    for entry in index['sources']:
        path = local(entry['path'])
        if digest(path) != entry['sha256']:
            raise ValueError(f"candidate source changed: {entry['path']}")
        text = path.read_text(encoding='utf-8')
        literals = sorted(set(re.findall(r'"((?:taskctl[./]|tasking/)[A-Za-z0-9/._-]+)"', text)))
        if literals != entry['protocol_or_hash_literals']:
            raise ValueError(f"candidate literal inventory mismatch: {entry['path']}")
    evidence = {}
    for name, expected in index['proofs'].items():
        path = local(name)
        if digest(path) != expected['sha256']:
            raise ValueError(f'historical proof bytes changed: {name}')
        proof = json.loads(path.read_text(encoding='utf-8'))
        if not (proof['behavioral_tests'] == proof['jvm_passed'] == proof['native_passed'] == expected['behavioral_tests']):
            raise ValueError(f'historical proof did not pass: {name}')
        if proof['test_identity_sha256'] != expected['test_identity_sha256']:
            raise ValueError(f'historical identity digest mismatch: {name}')
        evidence[name] = set(proof['behavioral_test_identities'] + proof['jvm_only_source_policy'])
    for name, expected in index['test_sources'].items():
        if digest(local(name)) != expected:
            raise ValueError(f'candidate witness source changed: {name}')
    invariant_ids = [item['id'] for item in index['invariants']]
    if invariant_ids != [f'C{number:02}' for number in range(1, 14)]:
        raise ValueError('incomplete or duplicated candidate invariant index')
    witnesses = set()
    for invariant in index['invariants']:
        if f"### {invariant['id']} — " not in document or not invariant['witnesses']:
            raise ValueError(f"missing candidate section/witness: {invariant['id']}")
        for witness in invariant['witnesses']:
            identity = witness['identity']
            if identity not in evidence[witness['proof']] or witness['source'] not in index['test_sources']:
                raise ValueError(f'unbound test witness: {identity}')
            klass, method = identity.split('#', 1)
            source = local(witness['source'])
            text = source.read_text(encoding='utf-8')
            method = method.removesuffix('()')
            if source.stem != klass.rsplit('.', 1)[-1] or not ('fun `' + method + '`' in text or 'fun ' + method + '(' in text):
                raise ValueError(f'missing test declaration: {identity}')
            witnesses.add(identity)
    for name, expected in index['independent_adapter_evidence'].items():
        if digest(local(name)) != expected:
            raise ValueError(f'independent adapter proof changed: {name}')
    for target in re.findall(r'\]\(([^)]+)\)', document):
        if '://' not in target and not target.startswith('#'):
            local((Path(index['document']).parent / target.split('#')[0]).as_posix())
    return dict(candidate=index['candidate'], status=index['status'], source_files=len(recorded),
                invariants=len(invariant_ids), distinct_witnesses=len(witnesses),
                historical_tests_rerun=False, external_io=False)


if __name__ == '__main__':
    print(json.dumps(check()))
