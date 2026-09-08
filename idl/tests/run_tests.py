import json
from pathlib import Path
import sys
import unittest
root=Path(__file__).resolve().parents[2]
names=[]
class RecordingResult(unittest.TextTestResult):
    def addSuccess(self,test):
        names.append(test.id())
        super().addSuccess(test)
suite=unittest.defaultTestLoader.discover(str(root/'idl/tests'),pattern='test_*.py')
result=unittest.TextTestRunner(resultclass=RecordingResult,verbosity=2).run(suite)
if not result.wasSuccessful(): sys.exit(1)
proof=root/'build/proof'; proof.mkdir(parents=True,exist_ok=True)
(proof/'idl-python.json').write_text(json.dumps(dict(classification='observed-generated-client-tests',python=sys.version,
    tests=sorted(names),passed=result.testsRun,failures=len(result.failures),errors=len(result.errors)),indent=2)+'\n',encoding='utf-8',newline='\n')
