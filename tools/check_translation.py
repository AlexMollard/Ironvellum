"""Check one Ironvellum translation against the English strings.xml.

    python3 tools/check_translation.py es

Parses app/src/main/res/values/strings.xml and app/src/main/res/values-<code>/strings.xml
and reports, with key names:

- translatable English keys missing from the translation;
- extra keys that do not exist in English;
- entries copied that are translatable="false" in English;
- placeholder mismatches (same multiset of %n$x tokens, order ignored);
- unescaped apostrophes or double quotes;
- plural quantity sets that do not match the language's CLDR list;
- a bools.xml whose themed_strings_complete does not match whether every
  __t key is present.

Length is a warning only: keys ending _button, _tab, _chip, _short, _label
or starting nav_ that run more than 30% over the English length.

Exit code is non-zero on any error; warnings alone still exit 0.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

# CLDR plural categories per language, as docs/TRANSLATING.md lists them.
CLDR = {
    "es": {"one", "other"},
    "de": {"one", "other"},
    "fr": {"one", "many", "other"},
    "pt-rBR": {"one", "other"},
    "ja": {"other"},
    "ko": {"other"},
    "zh": {"other"},
    "zh-rCN": {"other"},
    "b+zh+Hans": {"other"},
    "en": {"one", "other"},
}

# Mirrors of StringsXmlTest's rules (app/src/test/kotlin/com/ironvellum/app/StringsXmlTest.kt).
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])")
UNESCAPED_APOSTROPHE = re.compile(r"(?<!\\)'")
UNESCAPED_QUOTE = re.compile(r"(?<!\\)\"")
LONG_SUFFIXES = ("_button", "_tab", "_chip", "_short", "_label")


class Entry:
    def __init__(self, name, text, items, translatable):
        self.name = name
        self.text = text          # str for <string>, None for <plurals>
        self.items = items        # {quantity: text} for <plurals>, None for <string>
        self.translatable = translatable

    @property
    def is_plural(self):
        return self.items is not None

    def values(self):
        if self.text is not None:
            return [self.text]
        return list(self.items.values())


def read_strings(path):
    """Parse a strings.xml into an ordered dict of name -> Entry."""
    root = ET.parse(path).getroot()
    out = {}
    for e in root:
        if e.tag not in ("string", "plurals"):
            continue
        name = e.get("name")
        translatable = e.get("translatable") != "false"
        if e.tag == "string":
            out[name] = Entry(name, entry_text(e), None, translatable)
        else:
            items = {}
            for item in e.findall("item"):
                items[item.get("quantity")] = entry_text(item)
            out[name] = Entry(name, None, items, translatable)
    return out


def entry_text(e):
    """Rebuild the element's raw text, keeping inner markup (<xliff:g>, <b>) verbatim.

    ElementTree already unescapes entities, so the raw source's `&lt;1 min` comes
    back as `<1 min`; re-escape so the checker sees what a translator writes.
    """
    parts = []
    if e.text:
        parts.append(e.text.replace("&", "&amp;").replace("<", "&lt;"))
    for child in e:
        inner = ET.tostring(child, encoding="unicode")
        parts.append(inner)
        if child.tail:
            parts.append(child.tail.replace("&", "&amp;").replace("<", "&lt;"))
    return "".join(parts)


def placeholders(text):
    """Multiset of (index, type) tuples; %% is an escape, not a placeholder."""
    return sorted(PLACEHOLDER.findall(text.replace("%%", "")))


def check(english, translation, code):
    """Return (errors, warnings) for one translation against English."""
    errors = []
    warnings = []
    value_errors = []
    cldr = CLDR.get(code)
    if cldr is None:
        errors.append(f"unknown language code '{code}': no CLDR plural list (add it to CLDR)")

    en_translatable = {k for k, e in english.items() if e.translatable}
    # An untranslatable English key can still need a locale-specific value
    # (java.time patterns, separators), so copied translatable="false" keys are
    # allowed when the translation's value differs from English.
    allowed_copied = {k for k, e in translation.items()
                      if k in english and not english[k].translatable and same_value(english[k], e)}

    for key in sorted(en_translatable - set(translation)):
        errors.append(f"missing: {key}")
    for key in sorted(set(translation) - set(english)):
        errors.append(f"extra: {key} (no such key in English)")
    for key in sorted(set(translation) & {k for k, e in english.items() if not e.translatable} - allowed_copied):
        errors.append(f'copied a translatable="false" entry verbatim: {key}')

    for key in sorted(set(translation) & en_translatable):
        en, tr = english[key], translation[key]
        if en.is_plural != tr.is_plural:
            errors.append(f"kind mismatch: {key} is {'a plural' if tr.is_plural else 'a string'} but English is {'a plural' if en.is_plural else 'a string'}")
            continue
        if tr.is_plural:
            if cldr is not None and set(tr.items) != cldr:
                errors.append(f"plural quantities for {key}: {sorted(tr.items)} but {code} uses {sorted(cldr)}")
            for quantity, text in sorted(tr.items.items()):
                check_value(f"{key}[{quantity}]", text, en.items.get(quantity), errors, warnings, quantity in en.items)
        else:
            check_value(key, tr.text, en.text, errors, warnings, True)

    # Placeholder parity: same multiset as English, per string item, order free.
    for key in sorted(set(translation) & en_translatable):
        en, tr = english[key], translation[key]
        if en.is_plural != tr.is_plural:
            continue
        if tr.is_plural:
            for quantity, text in tr.items.items():
                en_text = en.items.get(quantity)
                if en_text is None:
                    continue
                if placeholders(text) != placeholders(en_text):
                    errors.append(f"placeholders for {key}[{quantity}]: translation has "
                                  f"{placeholders(text)}, English has {placeholders(en_text)}")
        elif placeholders(tr.text) != placeholders(en.text):
            errors.append(f"placeholders for {key}: translation has {placeholders(tr.text)}, "
                          f"English has {placeholders(en.text)}")

    return errors, warnings


def check_value(key, text, en_text, errors, warnings, compare_length):
    if UNESCAPED_APOSTROPHE.search(text) and not (text.startswith('"') and text.endswith('"')):
        errors.append(f"unescaped apostrophe: {key}: {text}")
    if UNESCAPED_QUOTE.search(text) and not (text.startswith('"') and text.endswith('"')):
        errors.append(f"unescaped double quote: {key}: {text}")
    if text.startswith("@") or text.startswith("?"):
        errors.append(f"leading @ or ? must be escaped: {key}")
    if compare_length and en_text and is_long_key(key):
        if len(text) > len(en_text) * 1.3 + 1:
            warnings.append(f"length: {key} is {len(text)} chars vs English {len(en_text)}")


def same_value(a, b):
    """True when two entries hold the same text in every value."""
    if a.is_plural != b.is_plural:
        return False
    return a.values() == b.values()


def is_long_key(key):
    base = key[:-3] if key.endswith("__t") else key
    return base.endswith(LONG_SUFFIXES) or base.startswith("nav_")


def check_bools(dir_path, english, translation):
    """themed_strings_complete must exist and match whether every __t key is present."""
    errors = []
    themed_keys = {k for k in english if k.endswith("__t") and english[k].translatable}
    have = set(translation)
    bools = Path(dir_path) / "bools.xml"
    declared = None
    if bools.is_file():
        m = re.search(r'name="themed_strings_complete"\s*>\s*(true|false)\s*<', bools.read_text(encoding="utf-8"))
        declared = m.group(1) if m else None
    if declared is None:
        errors.append("bools.xml must declare themed_strings_complete (true only when every key__t is translated)")
        return errors
    missing_themed = sorted(themed_keys - have)
    if declared == "true" and missing_themed:
        errors.append(f"themed_strings_complete is true but {len(missing_themed)} __t keys are missing, first: {missing_themed[0]}")
    if declared == "false" and not missing_themed:
        errors.append("themed_strings_complete is false but every __t key is present; set it true")
    return errors


def main(argv):
    if len(argv) != 2:
        print(__doc__.strip().splitlines()[0])
        print("Usage: python3 tools/check_translation.py <code>   e.g. de")
        return 2
    code = argv[1]
    en_path = RES / "values" / "strings.xml"
    tr_dir = RES / f"values-{code}"
    tr_path = tr_dir / "strings.xml"
    if not tr_path.is_file():
        print(f"!! {tr_path} does not exist")
        return 1
    english = read_strings(en_path)
    translation = read_strings(tr_path)

    errors, warnings = check(english, translation, code)
    errors += check_bools(tr_dir, english, translation)

    for e in errors:
        print(f"error: {e}")
    for w in warnings:
        print(f"warn:  {w}")
    print(f"{code}: {len(errors)} errors, {len(warnings)} warnings "
          f"({len(translation)}/{sum(1 for e in english.values() if e.translatable)} translatable keys)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
