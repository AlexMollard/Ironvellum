"""Check v2 scheme interpretations against Android's SetScheme.decode."""
import math
import re
from unicodedata import decimal

from .contract import require


# Kotlin Char.isWhitespace is the union of Java's whitespace and space characters.
KOTLIN_SPACE = "\t\n\v\f\r\x1c\x1d\x1e\x1f \u00a0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u2028\u2029\u202f\u205f\u3000"
KINDS = {"working", "warmup", "top", "backoff", "drop", "failure", "rest_pause"}
DECIMAL_DOUBLE = re.compile(r"[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fFdD]?")
HEX_DOUBLE = re.compile(r"[+-]?0[xX](?:[0-9a-fA-F]+(?:\.[0-9a-fA-F]*)?|\.[0-9a-fA-F]+)[pP][+-]?[0-9]+[fFdD]?")
SCHEME_FIELDS = {"t", "wr", "rr", "tr", "rir", "bo", "br", "bf", "bp", "rt", "ds", "dc", "dp", "ck"}


def _integer(text, default, low, high):
    if not text:
        return default
    negative = text.startswith("-")
    digits = text[1:] if text[0] in "+-" else text
    if not digits:
        return default
    value = 0
    for char in digits:
        digit = decimal(char, None) if ord(char) <= 0xffff else None
        if digit is None:
            return default
        value = 10 * value + digit
        if value > (2147483648 if negative else 2147483647):
            return default
    return min(high, max(low, -value if negative else value))


def _range(text, default, low, high):
    first, *rest = text.split("-", 2)
    start = _integer(first, None, low, high)
    if start is None:
        return dict(zip(("min", "max"), default))
    stop = _integer(rest[0], start, low, high) if rest else start
    return {"min": start, "max": max(start, stop)}


def _fixed_kg(text):
    # Java Double also accepts hexadecimal exponents, optional type suffixes and ASCII trim.
    text = text.strip("".join(map(chr, range(33))))
    try:
        if DECIMAL_DOUBLE.fullmatch(text):
            value = float(text.rstrip("fFdD"))
        elif HEX_DOUBLE.fullmatch(text):
            value = float.fromhex(text.rstrip("fFdD"))
        else:
            return None
    except OverflowError:
        return None
    return value if math.isfinite(value) and value >= 0 else None


def _custom_kinds(text):
    kinds = []
    # Include empty and unknown segments as WORKING, just as Kotlin's split and fromWire do.
    for match in re.finditer(r"([^,]*)(?:,|\Z)", text):
        kind = match[1].strip(KOTLIN_SPACE).lower()
        if kind != "warmup":
            kinds.append(kind.upper() if kind in KINDS else "WORKING")
            if len(kinds) == 30:
                break
        if match.end() == len(text) and not match[0].endswith(","):
            break
    return kinds


def check_scheme(raw, scheme):
    fields = {}
    for match in re.finditer(r"[^;]+", raw or ""):
        name, sep, value = match[0].partition("=")
        field = name.strip(KOTLIN_SPACE)
        if sep and field in SCHEME_FIELDS:
            fields[field] = value.strip(KOTLIN_SPACE)

    kind = fields.get("t")
    if kind not in ("top_backoff", "drops", "custom"):
        kind = "straight"
    expected = dict(type=kind, warmupRamp=fields.get("wr") == "1", workingRange=None,
                    topRange=None, topRir=None, backoffSets=None, backoffRange=None,
                    backoffFixedKg=None, backoffPercent=None, restAfterTopSec=None,
                    dropSets=None, dropCount=None, dropPercent=None, customKinds=None)
    if kind in ("straight", "drops"):
        working = _range(fields.get("rr", ""), (1, 1), 1, 500)
        if working["min"] < working["max"]:
            expected["workingRange"] = working
    if kind == "top_backoff":
        fixed = _fixed_kg(fields.get("bf", ""))
        expected.update(topRange=_range(fields.get("tr", ""), (4, 6), 1, 100),
                        topRir=_range(fields.get("rir", ""), (0, 1), 0, 10),
                        backoffSets=_integer(fields.get("bo"), 2, 1, 10),
                        backoffRange=_range(fields.get("br", ""), (6, 8), 1, 100),
                        backoffFixedKg=fixed,
                        backoffPercent=_integer(fields.get("bp"), 10, 5, 50) if fixed is None else None,
                        restAfterTopSec=_integer(fields.get("rt"), 180, 30, 600))
    elif kind == "drops":
        expected.update(dropSets=_integer(fields.get("ds"), 1, 1, 30),
                        dropCount=_integer(fields.get("dc"), 1, 1, 4),
                        dropPercent=_integer(fields.get("dp"), 20, 5, 50))
    elif kind == "custom":
        expected["customKinds"] = _custom_kinds(fields["ck"]) if "ck" in fields else []
    checked = dict(scheme)
    if checked["backoffFixedKg"] is not None:
        checked["backoffFixedKg"] = float(checked["backoffFixedKg"])
    require(checked == expected)
