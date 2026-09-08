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
