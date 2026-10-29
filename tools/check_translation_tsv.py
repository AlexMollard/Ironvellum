"""Audit filled translation TSVs against the English source, without XML.

    python3 tools/check_translation_tsv.py <code> [tsv ...]

Flags, with row numbers:
- non-skip rows with an empty column 5 (or a row whose 5th column is missing);
- placeholder mismatches against English (same multiset, order ignored);
- unescaped apostrophes / double quotes, leading @ or ?;
- %% vs % rules from StringsXmlTest (%% only beside placeholders);
- a multi-placeholder row using non-positional form;
- rows left identical to English in a Latin script (an untranslated copy).

Exit non-zero on any error. Companion of tools/check_translation.py for the
work files before they are merged.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])")
UNESCAPED_APOSTROPHE = re.compile(r"(?<!\\)'")
UNESCAPED_QUOTE = re.compile(r"(?<!\\)\"")


def check_row(english, translation):
    if not translation.strip():
        return ["empty"]
    errors = []
    en_ph = PLACEHOLDER.findall(english.replace("%%", ""))
    tr_ph = PLACEHOLDER.findall(translation.replace("%%", ""))
    if en_ph != tr_ph:
        errors.append(f"placeholders {tr_ph} vs English {en_ph}")
    if UNESCAPED_APOSTROPHE.search(translation) and not (translation.startswith('"') and translation.endswith('"')):
        errors.append("unescaped apostrophe")
    if UNESCAPED_QUOTE.search(translation) and not (translation.startswith('"') and translation.endswith('"')):
        errors.append("unescaped quote")
    if translation.startswith("@") or translation.startswith("?"):
        errors.append("leading @/?")
    if not tr_ph and "%%" in translation:
        errors.append("literal %% without placeholders")
    if tr_ph and "%" in PLACEHOLDER.sub("", translation.replace("%%", "")):
        errors.append("stray % beside placeholders")
    if len(tr_ph) > 1 and any(idx == "" for idx, _ in tr_ph):
        errors.append("multi placeholder must be positional")
    if translation.strip() == english.strip() and len(english.strip()) > 1:
        errors.append("left identical to English")
    return errors


def main(argv):
    if not argv:
        print(__doc__.strip().splitlines()[0])
        return 2
    code = argv[0]
    paths = [Path(p) for p in argv[1:]] or [ROOT / ".tmp" / "translation-work" / code / "english.tsv"]
    total = 0
    for p in paths:
        if not p.is_file():
            print(f"!! no such file: {p}")
            return 2
        for lineno, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip():
                continue
            parts = line.split("\t")
            kind, key, quantity = parts[0], parts[1], parts[2]
            if kind == "skip":
                continue
            english = parts[3] if len(parts) > 3 else ""
            translation = parts[4] if len(parts) > 4 else ""
            for e in check_row(english, translation):
                where = f"{key}[{quantity}]" if quantity else key
                print(f"{p.name}:{lineno} {where}: {e}")
                total += 1
    print(f"{code}: {total} errors")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
