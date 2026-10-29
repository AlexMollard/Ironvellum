"""Emit one batch of the English strings.xml as a TSV for the translator.

    python3 tools/translation_batches.py 0 100      # entries 0..99
    python3 tools/translation_batches.py --list      # batch count and sizes

Columns: kind (string|plural|bools), key, quantity, English text (raw, as in the
file, entities escaped as in source). translatable="false" entries are emitted
as kind=skip so the translator sees the file order but knows to leave them out.

Read by the translating session, never by the build.
"""

from __future__ import annotations

import sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
EN = ROOT / "app" / "src" / "main" / "res" / "values" / "strings.xml"


def entries():
    root = ET.parse(EN).getroot()
    for e in root:
        if e.tag == "string":
            yield ("string" if e.get("translatable") != "false" else "skip", e.get("name"), "", raw(e))
        elif e.tag == "plurals":
            translatable = e.get("translatable") != "false"
            for item in e.findall("item"):
                yield ("plural" if translatable else "skip", e.get("name"), item.get("quantity"), raw(item))


def raw(e):
    """The element's source text: keep escapes and entities as the file holds them."""
    text = ET.tostring(e, encoding="unicode")
    # ET.tostring wraps in <tag ...>children</tag>; strip the wrapper.
    start = text.index(">") + 1
    end = text.rindex("</")
    inner = text[start:end]
    return inner.replace("\n", " ").strip()


def main(argv):
    if argv and argv[0] == "--list":
        es = list(entries())
        print(f"{len(es)} rows")
        return 0
    start, count = int(argv[0]), int(argv[1])
    es = list(entries())
    for kind, key, quantity, text in es[start : start + count]:
        print(f"{kind}\t{key}\t{quantity}\t{text}")


if __name__ == "__main__":
    main(sys.argv[1:])
