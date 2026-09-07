"""Fail unless the exact existing behavioral test identities pass on both VMs."""
import hashlib, json, xml.etree.ElementTree as ET
from pathlib import Path
from package import ROOT, target

def cases(paths):
    result=[]
    for path in paths:
        tree=ET.parse(path).getroot()
        for case in tree.iter('testcase'):
            assert not any(case.find(tag) is not None for tag in ('failure','error','skipped')), path
            result.append(case.attrib['classname']+'#'+case.attrib['name'])
    assert result and len(result)==len(set(result)), 'Missing or duplicate test results'
    return sorted(result)

original=cases(p for module in ('core','repository','compatibility','conformance','cli')
    for p in (ROOT/module/'build/test-results/test').glob('TEST-*.xml'))
excluded=[name for name in original if name.startswith('io.brule.tasking.conformance.ArchitecturePolicyTest#')]
expected=[name for name in original if name not in excluded]
jvm=cases((ROOT/'native-tests/build/test-results/test').glob('TEST-*.xml'))
native=cases((ROOT/'native-tests/build/test-results/test-native').glob('TEST-*.xml'))
assert expected==jvm==native, dict(missing_jvm=sorted(set(expected)-set(jvm)),missing_native=sorted(set(expected)-set(native)))
result=dict(contract='taskctl.corpus/alpha1',platform=target(),source_tests=len(original),behavioral_tests=len(expected),
    jvm_passed=len(jvm),native_passed=len(native),test_identity_sha256=hashlib.sha256('\n'.join(expected).encode()).hexdigest(),
    jvm_only_source_policy=excluded,behavioral_test_identities=expected)
output=ROOT/'build/proof'; output.mkdir(parents=True,exist_ok=True)
(output/f'corpus-{target()}.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
print(f'{len(expected)} identical behavioral tests passed on JVM and Native Image; {len(excluded)} additional JVM source-policy tests passed.')
