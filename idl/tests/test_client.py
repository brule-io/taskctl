import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest
from decimal import Decimal, localcontext

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'idl/generated'))
from taskctl_client import *


class ClientTest(unittest.TestCase):
    def test_integer_conversion_does_not_depend_on_or_mutate_python_global_digit_limits(self):
        limit=sys.get_int_max_str_digits()
        text='9'*5000
        value=parse(text.encode())
        self.assertIsInstance(value,IntegerValue)
        self.assertEqual(text,encode(value))
        self.assertEqual(limit,sys.get_int_max_str_digits())

    def test_core_goldens_match_exact_typed_bytes_and_framed_digests(self):
        golden=object_value(parse((ROOT/'build/proof/idl-goldens.json').read_bytes()))
        request=ApplyRequest.from_value(golden.get('request'))
        snapshot=Snapshot.from_value(golden.get('snapshot'))
        self.assertEqual(request.to_value(),golden.get('request'))
        self.assertEqual(snapshot.revision.value,digest('taskctl.kernel-snapshot/alpha1',snapshot.state))
        value=golden.get('value')
        # Ambient decimal precision must not alter the protocol's canonical value.
        with localcontext() as context:
            context.prec=3
            self.assertEqual(string_value(golden.get('value_digest')),digest('idl.golden/alpha1',value))
        self.assertEqual(value,parse(encode(value).encode('utf-8')))
        for item in array_value(golden.get('acceptance_times')):
            item=object_value(item); text=string_value(item.get('value'))
            if item.get('valid') == BooleanValue(True): self.assertEqual(text,AcceptedAt(text).value)
            else:
                with self.assertRaises(ValueError): AcceptedAt(text)

    def test_large_integer_decimal_scale_and_numeric_category_remain_distinct(self):
        for source in ('999999999999999999999999999999999999','1.2300','1E+30','42E+0','0.000','0E+3','-0.00'):
            value=DecimalValue(Decimal(source)) if '.' in source or 'E' in source else IntegerValue(int(source))
            result=parse(encode(value).encode())
            self.assertEqual(type(value),type(result))
            self.assertEqual(value,result)
            if isinstance(value,DecimalValue): self.assertEqual(value.value.as_tuple().exponent,result.value.as_tuple().exponent)
        self.assertNotEqual(digest('d',IntegerValue(1)),digest('d',DecimalValue(Decimal('1'))))

    def test_invalid_unicode_duplicate_keys_and_noncanonical_json_are_rejected(self):
        for source in (b'{"x":null,"x":null}',b'NaN',b'Infinity',b'1.0 ',b'"\\ud800"',b'"\xff"',b'{ "x":1}'):
            with self.assertRaises((ValueError,UnicodeError)): parse(source)
        with self.assertRaises(ValueError): StringValue('\ud800')
        with self.assertRaises(ValueError): ObjectValue((('x',NullValue()),('x',NullValue())))
        with self.assertRaises(ValueError): ObjectValue((('x',{'raw':'untyped'}),))

    def test_nested_values_have_no_mutable_collection_aliases(self):
        with self.assertRaises(ValueError): ArrayValue([StringValue('mutable')])
        with self.assertRaises(ValueError): ObjectValue([('x',NullValue())])
        value=ObjectValue((('x',ArrayValue((StringValue('immutable'),))),))
        self.assertEqual(value,parse(encode(value).encode()))

    def test_protocol_fields_and_nominal_identities_validate_before_use(self):
        with self.assertRaises(ValueError): Revision('banana')
        with self.assertRaises(ValueError): EventId('TASK.work')
        with self.assertRaises(ValueError): RecordId('TASK.')
        with self.assertRaises(ValueError): TransitionKind('unknown')
        with self.assertRaises(ValueError): SnapshotProtocol('taskctl.native/v1')
        with self.assertRaises(ValueError): Snapshot('raw string',Revision('sha256:'+'0'*64),ObjectValue(()))
        with self.assertRaises(ValueError): Result.from_value(ObjectValue(()))

    def test_endpoint_scope_and_cursor_validation_precede_network_access(self):
        for endpoint in ('https://127.0.0.1:80','http://localhost:80','http://127.0.0.1','http://127.0.0.1:80/path','http://user@127.0.0.1:80','http://@127.0.0.1:80','http://127.0.0.1:80?query','http://127.0.0.1:80?','http://127.0.0.1:80#'):
            with self.assertRaises(ValueError): TaskLedgerClient(endpoint)
        client=TaskLedgerClient('http://127.0.0.1:1')
        for cursor in (-1,True,9223372036854775808):
            with self.assertRaises(ValueError): client.events(cursor)

    def test_event_parent_nullability_and_changed_identity_constraints_are_generated(self):
        revision=Revision('sha256:'+'0'*64)
        result=Result(ResultProtocol('taskctl.kernel-result/alpha1'),revision,(),AcceptedAt('2026-09-08T03:00:00Z'))
        transition=Transition(TransitionProtocol('taskctl.kernel-transition/alpha1'),TransitionKind('track_planning'),NullValue())
        with self.assertRaises(ValueError): Result(result.protocol,revision,(RecordId('TASK.x'),RecordId('TASK.x')),result.accepted_at)
        with self.assertRaises(ValueError): Event(EventProtocol('taskctl.kernel-event/alpha1'),1,EventId(revision.value),revision,transition,result)
        with self.assertRaises(ValueError): EventPage(EventPageProtocol('taskctl.kernel-event-page/alpha1'),None,EventId(revision.value))


if __name__ == '__main__': unittest.main()
