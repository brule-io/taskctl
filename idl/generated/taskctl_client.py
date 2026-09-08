# Generated from taskctl.wire-idl/alpha1. Run :idl:generate; do not edit.
# Model digest: sha256:aaa2425734d1a815dc0e95a6301f7d1b5ecc492d11bc533b12b93a52fde798f9
# Smithy projection digest: sha256:fcdb96d3693693b8a2a77e042bee1cfc57fbcbfc840d95fd4da55f9727a87af3
"""Runtime for the generated experimental client. No lifecycle evaluation lives here."""
from __future__ import annotations
from dataclasses import dataclass
from decimal import Decimal
from datetime import datetime
import hashlib
import http.client
import json
import re
from urllib.parse import urlsplit

MAX_BODY = 1_000_000
MAX_OBJECT = 250_000


def scalar(text: str) -> str:
    if not isinstance(text, str) or any(0xD800 <= ord(c) <= 0xDFFF for c in text):
        raise ValueError('Unicode scalar string required')
    return text


def validate_time(text: str) -> None:
    if not 20 <= len(text) <= 35 or text.startswith('0000-') or text.endswith('-00:00'): raise ValueError('invalid acceptance time')
    # Validate only; do not round fractional nanoseconds or reformat the witness.
    datetime(int(text[0:4]),int(text[5:7]),int(text[8:10]),int(text[11:13]),int(text[14:16]),int(text[17:19]))
    if not text.endswith('Z'):
        hours=int(text[-5:-3]); minutes=int(text[-2:])
        if hours > 18 or minutes > 59 or (hours == 18 and minutes != 0): raise ValueError('invalid time offset')


@dataclass(frozen=True)
class StringValue:
    value: str
    def __post_init__(self): scalar(self.value)


@dataclass(frozen=True)
class IntegerValue:
    value: int
    def __post_init__(self):
        if type(self.value) is not int: raise ValueError('exact integer required')


@dataclass(frozen=True)
class DecimalValue:
    value: Decimal
    def __post_init__(self):
        if not isinstance(self.value, Decimal) or not self.value.is_finite(): raise ValueError('finite Decimal required')


@dataclass(frozen=True)
class BooleanValue:
    value: bool
    def __post_init__(self):
        if type(self.value) is not bool: raise ValueError('boolean required')


@dataclass(frozen=True)
class NullValue: pass


@dataclass(frozen=True)
class ArrayValue:
    values: tuple[Value, ...]
    def __post_init__(self):
        if type(self.values) is not tuple or not all(isinstance(v, VALUE_TYPES) for v in self.values): raise ValueError('typed immutable array required')


@dataclass(frozen=True)
class ObjectValue:
    fields: tuple[tuple[str, Value], ...]
    def __post_init__(self):
        if type(self.fields) is not tuple: raise ValueError('typed immutable fields required')
        keys = []
        for pair in self.fields:
            if type(pair) is not tuple or len(pair) != 2: raise ValueError('field pair required')
            key, value = pair
            scalar(key)
            if not isinstance(value, VALUE_TYPES): raise ValueError('typed field value required')
            keys.append(key)
        if len(set(keys)) != len(keys): raise ValueError('duplicate object key')
    def get(self, key: str) -> Value:
        for name, value in self.fields:
            if name == key: return value
        raise ValueError('required field absent: '+key)
    def exact(self, names: tuple[str, ...]) -> None:
        if {name for name, _ in self.fields} != set(names): raise ValueError('unknown or missing fields')


Value = StringValue | IntegerValue | DecimalValue | BooleanValue | NullValue | ArrayValue | ObjectValue
VALUE_TYPES = (StringValue, IntegerValue, DecimalValue, BooleanValue, NullValue, ArrayValue, ObjectValue)


def object_value(value: Value) -> ObjectValue:
    if not isinstance(value, ObjectValue): raise ValueError('object required')
    return value


def string_value(value: Value) -> str:
    if not isinstance(value, StringValue): raise ValueError('string required')
    return value.value


def array_value(value: Value) -> tuple[Value, ...]:
    if not isinstance(value, ArrayValue): raise ValueError('array required')
    return value.values


def positive_long(value: Value) -> int:
    if not isinstance(value, IntegerValue) or not 0 < value.value <= 9223372036854775807: raise ValueError('positive signed long required')
    return value.value


def _decode_external(value: object) -> Value:
    """Quarantined stdlib JSON boundary. No untyped data leaves this decoder."""
    if value is None: return NullValue()
    if type(value) is bool: return BooleanValue(value)
    if isinstance(value, str): return StringValue(value)
    if isinstance(value, VALUE_TYPES): return value
    if isinstance(value, list): return ArrayValue(tuple(_decode_external(v) for v in value))
    raise ValueError('unsupported external JSON value')


def _decode_pairs(pairs: list[tuple[str, object]]) -> ObjectValue:
    """Same quarantined boundary; preserve order and reject duplicates."""
    return ObjectValue(tuple((scalar(k), _decode_external(v)) for k, v in pairs))


def _reject_constant(value: str) -> None: raise ValueError('non-finite number: '+value)


def _decode_integer(text: str) -> IntegerValue:
    negative=text.startswith('-'); digits=text[1:] if negative else text
    number=0
    for start in range(0,len(digits),9):
        part=digits[start:start+9]; number=number*10**len(part)+int(part)
    return IntegerValue(-number if negative else number)


def _integer_text(number: int) -> str:
    if number == 0: return '0'
    negative=number<0; number=abs(number); parts=[]
    while number:
        number,part=divmod(number,1_000_000_000); parts.append(part)
    return ('-' if negative else '')+str(parts[-1])+''.join(f'{part:09d}' for part in reversed(parts[:-1]))


def parse(data: bytes) -> Value:
    if len(data) > MAX_BODY: raise ValueError('bounded body exceeded')
    text = data.decode('utf-8', errors='strict')
    value = _decode_external(json.loads(text, parse_int=_decode_integer,
        parse_float=lambda v: DecimalValue(Decimal(v)), parse_constant=_reject_constant, object_pairs_hook=_decode_pairs))
    if encode(value) != text: raise ValueError('exact typed JSON required')
    return value


def quote(text: str) -> str:
    scalar(text)
    escaped = {'"':'\\"', '\\':'\\\\', '\n':'\\n', '\r':'\\r', '\t':'\\t'}
    return '"'+''.join(escaped[c] if c in escaped else ('\\u%04x' % ord(c) if ord(c) < 32 or 0x7f <= ord(c) <= 0x9f or ord(c) in (0x2028,0x2029,0xfffe,0xffff) else c) for c in text)+'"'


def _decimal_text(value: Decimal, semantic: bool = False) -> str:
    sign, digits, exponent = value.as_tuple()
    if not isinstance(exponent, int): raise ValueError('finite decimal required')
    # Work on the coefficient directly: Decimal.normalize uses ambient precision.
    if semantic:
        if not any(digits): return '0'
        while len(digits) > 1 and digits[-1] == 0: digits = digits[:-1]; exponent += 1
        coefficient = ''.join(str(d) for d in digits)
        if len(coefficient)+abs(exponent) > MAX_BODY: raise ValueError('bounded decimal expansion exceeded')
        if exponent >= 0: text = coefficient + '0'*exponent
        elif -exponent < len(coefficient): text = coefficient[:exponent]+'.'+coefficient[exponent:]
        else: text = '0.'+'0'*(-exponent-len(coefficient))+coefficient
        return ('-' if sign else '')+text
    if value.is_zero(): value = value.copy_abs()
    if exponent < 0:
        if len(digits)+abs(exponent) > MAX_BODY: raise ValueError('bounded decimal expansion exceeded')
        return format(value, 'f')
    text = str(value)
    return text if 'E' in text else text+'E+0'


def encode(value: Value) -> str:
    match value:
        case NullValue(): return 'null'
        case StringValue(text): return quote(text)
        case IntegerValue(number): return _integer_text(number)
        case DecimalValue(number): return _decimal_text(number)
        case BooleanValue(flag): return 'true' if flag else 'false'
        case ArrayValue(values): return '['+','.join(encode(v) for v in values)+']'
        case ObjectValue(fields): return '{'+','.join(quote(k)+':'+encode(v) for k,v in fields)+'}'
    raise ValueError('typed value required')


def canonical(value: Value) -> str:
    match value:
        case NullValue(): return 'n'
        case StringValue(text): return 's'+str(len(scalar(text).encode('utf-8')))+':'+text
        case IntegerValue(number): return 'i'+canonical(StringValue(_integer_text(number)))
        case DecimalValue(number): return 'd'+canonical(StringValue(_decimal_text(number, True)))
        case BooleanValue(flag): return 'b1' if flag else 'b0'
        case ArrayValue(values): return 'l'+str(len(values))+':'+''.join(canonical(v) for v in values)
        case ObjectValue(fields): return 'm'+str(len(fields))+':'+''.join(canonical(StringValue(k))+canonical(v) for k,v in sorted(fields, key=lambda p:p[0].encode('utf-16-be')))
    raise ValueError('typed value required')


def digest(domain: str, value: Value) -> str:
    return 'sha256:'+hashlib.sha256(canonical(ArrayValue((StringValue(domain),value))).encode('utf-8')).hexdigest()


class TransportError(RuntimeError): pass


class KernelRejected(TransportError):
    def __init__(self, status: int, code: str, message: str):
        self.status=status; self.code=code
        super().__init__(message)


class _Transport:
    def __init__(self, endpoint: str):
        parsed=urlsplit(endpoint)
        if parsed.scheme != 'http' or parsed.hostname != '127.0.0.1' or parsed.port is None or not 1 <= parsed.port <= 65535 or parsed.username is not None or parsed.password is not None or '?' in endpoint or '#' in endpoint or parsed.path not in ('','/'):
            raise ValueError('explicit loopback endpoint required')
        self.port=parsed.port
    def exchange(self, method: str, path: str, body: ObjectValue | None = None) -> ObjectValue:
        data=encode(body).encode('utf-8') if body is not None else None
        if data is not None and len(data)>MAX_OBJECT: raise ValueError('bounded request exceeded')
        # http.client performs no redirects, proxy discovery or application retry.
        connection=http.client.HTTPConnection('127.0.0.1',self.port,timeout=15)
        try:
            connection.request(method,path,body=data,headers={'Content-Type':'application/json'} if data is not None else {})
            response=connection.getresponse()
            if response.getheader('Content-Type') != 'application/json': raise TransportError('response content type invalid')
            value=object_value(parse(response.read(MAX_BODY+1)))
            if response.status != 200:
                error=KernelError.from_value(value)
                raise KernelRejected(response.status,error.code.value,error.message.value)
            return value
        finally: connection.close()


@dataclass(frozen=True)
class Revision:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if re.fullmatch("sha256:[0-9a-f]{64}", self.value) is None: raise ValueError('invalid Revision')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> Revision: return cls(string_value(value))


@dataclass(frozen=True)
class EventId:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if re.fullmatch("sha256:[0-9a-f]{64}", self.value) is None: raise ValueError('invalid EventId')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> EventId: return cls(string_value(value))


@dataclass(frozen=True)
class RecordId:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if re.fullmatch("(TASK|ROADMAP|EPIC)\\.[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*", self.value) is None: raise ValueError('invalid RecordId')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> RecordId: return cls(string_value(value))


@dataclass(frozen=True)
class AcceptedAt:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if re.fullmatch("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?(Z|[+-][0-9]{2}:[0-9]{2})", self.value) is None: raise ValueError('invalid AcceptedAt')
        validate_time(self.value)
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> AcceptedAt: return cls(string_value(value))


@dataclass(frozen=True)
class SnapshotProtocol:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if self.value not in ("taskctl.kernel-snapshot/alpha1",): raise ValueError('invalid SnapshotProtocol')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> SnapshotProtocol: return cls(string_value(value))


@dataclass(frozen=True)
class TransitionProtocol:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if self.value not in ("taskctl.kernel-transition/alpha1",): raise ValueError('invalid TransitionProtocol')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> TransitionProtocol: return cls(string_value(value))


@dataclass(frozen=True)
class ApplyProtocol:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if self.value not in ("taskctl.kernel-apply/alpha1",): raise ValueError('invalid ApplyProtocol')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> ApplyProtocol: return cls(string_value(value))


@dataclass(frozen=True)
class ResultProtocol:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if self.value not in ("taskctl.kernel-result/alpha1",): raise ValueError('invalid ResultProtocol')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> ResultProtocol: return cls(string_value(value))


@dataclass(frozen=True)
class EventProtocol:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if self.value not in ("taskctl.kernel-event/alpha1",): raise ValueError('invalid EventProtocol')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> EventProtocol: return cls(string_value(value))


@dataclass(frozen=True)
class EventPageProtocol:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if self.value not in ("taskctl.kernel-event-page/alpha1",): raise ValueError('invalid EventPageProtocol')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> EventPageProtocol: return cls(string_value(value))


@dataclass(frozen=True)
class ErrorProtocol:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if self.value not in ("taskctl.kernel-error/alpha1",): raise ValueError('invalid ErrorProtocol')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> ErrorProtocol: return cls(string_value(value))


@dataclass(frozen=True)
class TransitionKind:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if self.value not in ("seed", "close", "revise", "reconcile", "track_history", "import", "track_planning", "amend_planning", "planning_disposition", "assess_planning",): raise ValueError('invalid TransitionKind')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> TransitionKind: return cls(string_value(value))


@dataclass(frozen=True)
class ErrorCode:
    value: str
    def __post_init__(self):
        scalar(self.value)
        if self.value not in ("STALE_REVISION", "VALIDATION", "ABSENT_RECORD", "CONTENT_TYPE", "BODY_LIMIT", "METHOD", "PATH", "STORAGE",): raise ValueError('invalid ErrorCode')
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> ErrorCode: return cls(string_value(value))


@dataclass(frozen=True)
class Message:
    value: str
    def __post_init__(self):
        scalar(self.value)
    def to_value(self) -> StringValue: return StringValue(self.value)
    @classmethod
    def from_value(cls, value: Value) -> Message: return cls(string_value(value))


@dataclass(frozen=True)
class Snapshot:
    protocol: SnapshotProtocol
    revision: Revision
    state: ObjectValue
    def __post_init__(self):
        if not (isinstance(self.protocol, SnapshotProtocol)): raise ValueError('invalid Snapshot.protocol')
        if not (isinstance(self.revision, Revision)): raise ValueError('invalid Snapshot.revision')
        if not (isinstance(self.state, ObjectValue)): raise ValueError('invalid Snapshot.state')
    def to_value(self) -> ObjectValue:
        return ObjectValue((
            ("protocol", self.protocol.to_value()),
            ("revision", self.revision.to_value()),
            ("state", self.state),
        ))
    @classmethod
    def from_value(cls, value: Value) -> Snapshot:
        value=object_value(value)
        value.exact(("protocol", "revision", "state",))
        return cls(
            protocol=SnapshotProtocol.from_value(value.get("protocol")),
            revision=Revision.from_value(value.get("revision")),
            state=object_value(value.get("state")),
        )


@dataclass(frozen=True)
class Transition:
    protocol: TransitionProtocol
    kind: TransitionKind
    data: Value
    def __post_init__(self):
        if not (isinstance(self.protocol, TransitionProtocol)): raise ValueError('invalid Transition.protocol')
        if not (isinstance(self.kind, TransitionKind)): raise ValueError('invalid Transition.kind')
        if not (isinstance(self.data, VALUE_TYPES)): raise ValueError('invalid Transition.data')
    def to_value(self) -> ObjectValue:
        return ObjectValue((
            ("protocol", self.protocol.to_value()),
            ("kind", self.kind.to_value()),
            ("data", self.data),
        ))
    @classmethod
    def from_value(cls, value: Value) -> Transition:
        value=object_value(value)
        value.exact(("protocol", "kind", "data",))
        return cls(
            protocol=TransitionProtocol.from_value(value.get("protocol")),
            kind=TransitionKind.from_value(value.get("kind")),
            data=value.get("data"),
        )


@dataclass(frozen=True)
class ApplyRequest:
    protocol: ApplyProtocol
    expected_revision: Revision
    transition: Transition
    def __post_init__(self):
        if not (isinstance(self.protocol, ApplyProtocol)): raise ValueError('invalid ApplyRequest.protocol')
        if not (isinstance(self.expected_revision, Revision)): raise ValueError('invalid ApplyRequest.expected_revision')
        if not (isinstance(self.transition, Transition)): raise ValueError('invalid ApplyRequest.transition')
    def to_value(self) -> ObjectValue:
        return ObjectValue((
            ("protocol", self.protocol.to_value()),
            ("expected_revision", self.expected_revision.to_value()),
            ("transition", self.transition.to_value()),
        ))
    @classmethod
    def from_value(cls, value: Value) -> ApplyRequest:
        value=object_value(value)
        value.exact(("protocol", "expected_revision", "transition",))
        return cls(
            protocol=ApplyProtocol.from_value(value.get("protocol")),
            expected_revision=Revision.from_value(value.get("expected_revision")),
            transition=Transition.from_value(value.get("transition")),
        )


@dataclass(frozen=True)
class Result:
    protocol: ResultProtocol
    revision: Revision
    changed: tuple[RecordId, ...]
    accepted_at: AcceptedAt
    def __post_init__(self):
        if not (isinstance(self.protocol, ResultProtocol)): raise ValueError('invalid Result.protocol')
        if not (isinstance(self.revision, Revision)): raise ValueError('invalid Result.revision')
        if not (type(self.changed) is tuple and all(isinstance(item, RecordId) for item in self.changed)): raise ValueError('invalid Result.changed')
        if not (isinstance(self.accepted_at, AcceptedAt)): raise ValueError('invalid Result.accepted_at')
        if len(set(self.changed)) != len(self.changed): raise ValueError('duplicate changed identity')
    def to_value(self) -> ObjectValue:
        return ObjectValue((
            ("protocol", self.protocol.to_value()),
            ("revision", self.revision.to_value()),
            ("changed", ArrayValue(tuple(item.to_value() for item in self.changed))),
            ("accepted_at", self.accepted_at.to_value()),
        ))
    @classmethod
    def from_value(cls, value: Value) -> Result:
        value=object_value(value)
        value.exact(("protocol", "revision", "changed", "accepted_at",))
        return cls(
            protocol=ResultProtocol.from_value(value.get("protocol")),
            revision=Revision.from_value(value.get("revision")),
            changed=tuple(RecordId.from_value(item) for item in array_value(value.get("changed"))),
            accepted_at=AcceptedAt.from_value(value.get("accepted_at")),
        )


@dataclass(frozen=True)
class Event:
    protocol: EventProtocol
    sequence: int
    parent: EventId | None
    before: Revision
    transition: Transition
    result: Result
    def __post_init__(self):
        if not (isinstance(self.protocol, EventProtocol)): raise ValueError('invalid Event.protocol')
        if not (type(self.sequence) is int and 0 < self.sequence <= 9223372036854775807): raise ValueError('invalid Event.sequence')
        if not (self.parent is None or (isinstance(self.parent, EventId))): raise ValueError('invalid Event.parent')
        if not (isinstance(self.before, Revision)): raise ValueError('invalid Event.before')
        if not (isinstance(self.transition, Transition)): raise ValueError('invalid Event.transition')
        if not (isinstance(self.result, Result)): raise ValueError('invalid Event.result')
        if (self.sequence == 1) != (self.parent is None): raise ValueError('invalid event parent boundary')
    def to_value(self) -> ObjectValue:
        return ObjectValue((
            ("protocol", self.protocol.to_value()),
            ("sequence", IntegerValue(self.sequence)),
            ("parent", NullValue() if self.parent is None else self.parent.to_value()),
            ("before", self.before.to_value()),
            ("transition", self.transition.to_value()),
            ("result", self.result.to_value()),
        ))
    @classmethod
    def from_value(cls, value: Value) -> Event:
        value=object_value(value)
        value.exact(("protocol", "sequence", "parent", "before", "transition", "result",))
        return cls(
            protocol=EventProtocol.from_value(value.get("protocol")),
            sequence=positive_long(value.get("sequence")),
            parent=None if isinstance(value.get("parent"), NullValue) else EventId.from_value(value.get("parent")),
            before=Revision.from_value(value.get("before")),
            transition=Transition.from_value(value.get("transition")),
            result=Result.from_value(value.get("result")),
        )


@dataclass(frozen=True)
class EventPage:
    protocol: EventPageProtocol
    event: Event | None
    event_id: EventId | None
    def __post_init__(self):
        if not (isinstance(self.protocol, EventPageProtocol)): raise ValueError('invalid EventPage.protocol')
        if not (self.event is None or (isinstance(self.event, Event))): raise ValueError('invalid EventPage.event')
        if not (self.event_id is None or (isinstance(self.event_id, EventId))): raise ValueError('invalid EventPage.event_id')
        if (self.event is None) != (self.event_id is None): raise ValueError('event page nullability mismatch')
    def to_value(self) -> ObjectValue:
        return ObjectValue((
            ("protocol", self.protocol.to_value()),
            ("event", NullValue() if self.event is None else self.event.to_value()),
            ("event_id", NullValue() if self.event_id is None else self.event_id.to_value()),
        ))
    @classmethod
    def from_value(cls, value: Value) -> EventPage:
        value=object_value(value)
        value.exact(("protocol", "event", "event_id",))
        return cls(
            protocol=EventPageProtocol.from_value(value.get("protocol")),
            event=None if isinstance(value.get("event"), NullValue) else Event.from_value(value.get("event")),
            event_id=None if isinstance(value.get("event_id"), NullValue) else EventId.from_value(value.get("event_id")),
        )


@dataclass(frozen=True)
class KernelError:
    protocol: ErrorProtocol
    code: ErrorCode
    message: Message
    def __post_init__(self):
        if not (isinstance(self.protocol, ErrorProtocol)): raise ValueError('invalid KernelError.protocol')
        if not (isinstance(self.code, ErrorCode)): raise ValueError('invalid KernelError.code')
        if not (isinstance(self.message, Message)): raise ValueError('invalid KernelError.message')
    def to_value(self) -> ObjectValue:
        return ObjectValue((
            ("protocol", self.protocol.to_value()),
            ("code", self.code.to_value()),
            ("message", self.message.to_value()),
        ))
    @classmethod
    def from_value(cls, value: Value) -> KernelError:
        value=object_value(value)
        value.exact(("protocol", "code", "message",))
        return cls(
            protocol=ErrorProtocol.from_value(value.get("protocol")),
            code=ErrorCode.from_value(value.get("code")),
            message=Message.from_value(value.get("message")),
        )


class TaskLedgerClient:
    def __init__(self, endpoint: str): self._transport=_Transport(endpoint)
    def snapshot(self) -> Snapshot:
        snapshot=Snapshot.from_value(self._transport.exchange('GET',"/kernel/alpha1/snapshot"))
        if digest('taskctl.kernel-snapshot/alpha1',snapshot.state) != snapshot.revision.value: raise ValueError('snapshot identity mismatch')
        return snapshot
    def apply(self, expected_revision: Revision, transition: Transition) -> Result:
        request=ApplyRequest(ApplyProtocol('taskctl.kernel-apply/alpha1'),expected_revision,transition)
        return Result.from_value(self._transport.exchange('POST',"/kernel/alpha1/apply",request.to_value()))
    def events(self, after: int = 0) -> EventPage:
        if type(after) is not int or not 0 <= after <= 9223372036854775807: raise ValueError('nonnegative signed long cursor required')
        page=EventPage.from_value(self._transport.exchange('GET',"/kernel/alpha1/events?after="+str(after)))
        if (page.event is None) != (page.event_id is None): raise ValueError('event page nullability mismatch')
        if page.event is not None:
            if page.event.sequence <= after or digest('taskctl.kernel-event/alpha1',page.event.to_value()) != page.event_id.value: raise ValueError('event page identity mismatch')
        return page
