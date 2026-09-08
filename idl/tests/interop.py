"""Explicit disposable-database harness for the generated client, not a launcher."""
from pathlib import Path
import http.client
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'generated'))
from taskctl_client import *

mode,endpoint,input_path,output_path=sys.argv[1:]
fixture=object_value(parse(Path(input_path).read_bytes()))
client=TaskLedgerClient(endpoint)
before=Snapshot.from_value(fixture.get('before'))
seed=Transition.from_value(fixture.get('seed'))
if mode == 'lost-reply':
    try:
        client.apply(before.revision,seed)
        raise AssertionError('expected uncertain reply')
    except (http.client.RemoteDisconnected,ConnectionResetError):
        result=ObjectValue((('outcome',StringValue('uncertain-no-retry')),))
elif mode == 'lifecycle':
    assert client.snapshot() == before
    assert client.events().event is None
    first=client.apply(before.revision,seed)
    seeded=client.snapshot()
    assert seeded.revision == first.revision
    try:
        client.apply(before.revision,seed)
        raise AssertionError('stale revision accepted')
    except KernelRejected as error:
        assert error.status == 409 and error.code == 'STALE_REVISION'
    assert client.snapshot() == seeded
    for operation in ('invalid_close','close_leaf'):
        try:
            client.apply(seeded.revision,Transition.from_value(fixture.get(operation)))
            raise AssertionError('invalid closure accepted')
        except KernelRejected as error:
            assert error.status == 400
        assert client.snapshot() == seeded
    second=client.apply(seeded.revision,Transition.from_value(fixture.get('close_root')))
    third=client.apply(second.revision,Transition.from_value(fixture.get('close_leaf')))
    after=client.snapshot()
    assert after.revision == third.revision
    events=[]; cursor=0
    while True:
        page=client.events(cursor)
        if page.event is None: break
        assert page.event.sequence == cursor+1
        if cursor: assert page.event.parent == events[-1].event_id
        else: assert page.event.parent is None
        events.append(page); cursor=page.event.sequence
    assert len(events) == 3
    result=ObjectValue((('snapshot',after.to_value()),('events',ArrayValue(tuple(page.to_value() for page in events)))))
else: raise ValueError('explicit experiment mode required')
Path(output_path).write_bytes(encode(result).encode('utf-8'))
