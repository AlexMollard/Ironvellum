"""Versioned schemas and exact, bounded JSON encoding and validation."""
import hashlib
from importlib.resources import files
import json
import math
import re
from decimal import Decimal, InvalidOperation

from jsonschema import Draft202012Validator, validators
import simplejson

from .page_contract import with_views


MAX_CONTEXT = 8 << 20
MAX_FRAME = 64 << 10
MAX_RESULT = 1 << 20
MAX_TIME = 253402300799999


class Refusal(ValueError):
    """Only fixed classification strings from the connector belong in this exception."""


def require(condition, code="invalid_data"):
    if not condition:
        raise Refusal(code)


def _number(token, constructor):
    require(len(token) <= 128)
    try:
        number = constructor(token)
        approximate = float(number)
        require(math.isfinite(approximate) and (not number or approximate != 0))
        return number if number else constructor(0)
    except (InvalidOperation, OverflowError):
        raise Refusal("invalid_data") from None


class _WireDecimal(Decimal):
    """Retain a validated JSON token when Decimal's default spelling exceeds the wire cap."""

    def __new__(cls, token):
        number = super().__new__(cls, token)
        number.token = token
        return number

    def __str__(self):
        # simplejson's decimal encoder emits this spelling as an unquoted JSON number.
        return self.token


def _decimal(token):
    value = _number(token, Decimal)
    if len(str(value)) > 128:
        return _WireDecimal(token)
    return value


def _pairs(items):
    result = {}
    for key, value in items:
        require(key not in result)
        result[key] = value
    return result


def _check_strings(value):
    if isinstance(value, str):
        value.encode("utf-8", "strict")
    elif isinstance(value, dict):
        for key, child in value.items():
            _check_strings(key)
            _check_strings(child)
    elif isinstance(value, list):
        for child in value:
            _check_strings(child)


def loads(raw, cap, depth_limit=16):
    require(len(raw) <= cap, "file_limit")
    depth = 0
    quoted = False
    escaped_at = -1
    for match in re.finditer(rb'["\\{}\[\]]', raw):
        c = match[0]
        if match.start() == escaped_at:
            continue
        if quoted and c == b"\\":
            escaped_at = match.end()
        elif c == b'"':
            quoted = not quoted
        elif not quoted:
            depth += 1 if c in (b"{", b"[") else -1
            require(0 <= depth <= depth_limit)
    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=_pairs,
                           parse_int=lambda s: _number(s, int),
                           parse_float=_decimal,
                           parse_constant=lambda _: require(False))
        _check_strings(value)
        return value
    except (UnicodeError, json.JSONDecodeError, RecursionError):
        raise Refusal("invalid_data") from None


def encode(value):
    return simplejson.dumps(value, ensure_ascii=False, allow_nan=False,
                            separators=(",", ":"), use_decimal=True).encode("utf-8")


def resource(name):
    return json.loads(files(__package__).joinpath("contracts", name).read_bytes())


FILES = resource("files-v1.schema.json")["$defs"]
CONTEXT_V2 = resource("context-v2.schema.json")
DEFINITIONS = {
    "files-v1.schema.json": FILES,
    "context-v2.schema.json": CONTEXT_V2["$defs"],
    "tool-types-v1.schema.json": resource("tool-types-v1.schema.json")["$defs"],
    "tool-types-v2.schema.json": resource("tool-types-v2.schema.json")["$defs"],
}


def _inline(schema, local):
    if isinstance(schema, list):
        return [_inline(item, local) for item in schema]
    if not isinstance(schema, dict):
        return schema
    if "$ref" in schema:
        filename, key = schema["$ref"].split("#/$defs/")
        definitions = DEFINITIONS[filename] if filename else local
        return _inline(definitions[key], definitions)
    return {key: _inline(value, local) for key, value in schema.items()}


TOOLS = _inline(resource("tools-v1.json"), FILES)
TOOLS_V2 = {name: dict(TOOLS[name], outputSchema=output)
            for name, output in _inline(resource("tool-outputs-v2.json"), {}).items()}
TOOLS_V2 = with_views(TOOLS_V2, _inline(CONTEXT_V2["$defs"], CONTEXT_V2["$defs"]))
INTEGER = Draft202012Validator.TYPE_CHECKER.redefine(
    "integer", lambda _, n: type(n) is int or isinstance(n, Decimal) and n == n.to_integral_value())
Validator = validators.extend(Draft202012Validator, type_checker=INTEGER)
CONTEXT_SCHEMA = Validator(_inline(FILES["context"], FILES))
CONTEXT_SCHEMA_V2 = Validator(CONTEXT_V2)
OPAQUE_ID_V2 = re.compile(CONTEXT_V2["$defs"]["opaqueId"]["pattern"])
PROPOSAL_SCHEMA = Validator(_inline(FILES["proposal"], FILES))
INPUTS = {name: Validator(tool["inputSchema"]) for name, tool in TOOLS.items()}
INPUTS_V2 = {name: Validator(tool["inputSchema"]) for name, tool in TOOLS_V2.items()}


def validate(schema, value):
    require(schema.is_valid(value))


def catalogue():
    manifest = resource("movements-v1.json")["movements"]
    policy = resource("draft-movement-policy-v1.json")
    digest = hashlib.sha256(b"ironvellum-movements-v1\0")
    for row in manifest:
        for field in ("key", "name", "metric"):
            digest.update(row[field].encode() + b"\0")
    require(len(manifest) == 584 and digest.hexdigest() == FILES["catalogue"]["properties"]["sha256"]["const"])
    return {r["key"]: dict(r, draftable=r["metric"] == policy["requiredMetric"]
                           and r["key"] not in policy["excludedKeys"]) for r in manifest}


MOVEMENTS = catalogue()


def check_entries(entries):
    for entry in entries:
        require(MOVEMENTS.get(entry["exerciseKey"], {}).get("draftable"), "unsupported_movement")
