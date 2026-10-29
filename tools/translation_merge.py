"""Merge translated batch fragments into a language's strings.xml.

    python3 tools/translation_merge.py es batch1.tsv batch2.tsv ...

Each TSV line is `kind<TAB>key<TAB>quantity<TAB>translation` (the shape
tools/translation_batches.py emits; the translator fills the last column and
drops `skip` rows). For a `plural` row, quantity names the item. Later files
override earlier ones for the same key+quantity. Keys are emitted in the
English file's order; unknown keys are refused. Writes the file UTF-8 without
BOM, CRLF endings.

The translation column holds the raw XML text as it should appear in the file
(escapes and entities included, exactly as the English column showed them).
"""

from __future__ import annotations

import sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
EN = RES / "values" / "strings.xml"

INDENT = "    "


def english_order():
    """[(kind, key, quantity)] in file order, for validation."""
    root = ET.parse(EN).getroot()
    order = []
    for e in root:
        if e.tag == "string":
            order.append(("string", e.get("name"), ""))
        elif e.tag == "plurals":
            order.append(("plurals", e.get("name"), ""))
    return order


def main(argv):
    if len(argv) < 2:
        print(__doc__.strip().splitlines()[0])
        print("Usage: python3 tools/translation_merge.py <code> [batch.tsv ...]")
        return 2
    code = argv[1]
    order = english_order()
    known = {(kind, key, quantity) for kind, key, quantity in order}
    known_keys = {key for _, key, _ in order}

    strings = {}   # key -> text
    plurals = {}   # key -> {quantity: text}
    for path in argv[2:]:
        p = Path(path)
        if not p.is_file():
            print(f"!! no such batch file: {p}")
            return 2
        for lineno, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
            line = line.strip()
            if not line:
                continue
            parts = line.split("\t")
            if len(parts) != 4:
                print(f"!! {p}:{lineno}: expected 4 tab-separated columns, got {len(parts)}")
                return 2
            kind, key, quantity, text = parts
            if kind == "skip":
                continue
            if kind == "string":
                if key not in known_keys or ("string", key, "") not in known:
                    print(f"!! {p}:{lineno}: {key!r} is not a translatable English string")
                    return 2
                strings[key] = text
            elif kind == "plural":
                if ("plurals", key, "") not in known:
                    print(f"!! {p}:{lineno}: {key!r} is not a translatable English plural")
                    return 2
                plurals.setdefault(key, {})[quantity] = text
            else:
                print(f"!! {p}:{lineno}: unknown kind {kind!r}")
                return 2

    out_dir = RES / f"values-{code}"
    out_dir.mkdir(parents=True, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for kind, key, _ in order:
        if kind == "string" and key in strings:
            lines.append(f"{INDENT}<string name=\"{key}\">{strings[key]}</string>")
        elif kind == "plurals" and key in plurals:
            items = plurals[key]
            lines.append(f"{INDENT}<plurals name=\"{key}\">")
            for quantity in ("zero", "one", "two", "few", "many", "other"):
                if quantity in items:
                    lines.append(f"{INDENT}{INDENT}<item quantity=\"{quantity}\">{items[quantity]}</item>")
            lines.append(f"{INDENT}</plurals>")
    lines.append("</resources>")
    target = out_dir / "strings.xml"
    target.write_text("\r\n".join(lines) + "\r\n", encoding="utf-8", newline="")
    print(f"{target.relative_to(ROOT)}: {len(strings)} strings, {len(plurals)} plurals")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
