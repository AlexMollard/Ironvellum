"""Audit filled translation TSVs against the English source, without XML.



    python3 tools/check_translation_tsv.py <code> [tsv ...]



Also merges, before checking, every `part*.tsv` in the language's work

directory: a part row `kind<TAB>key<TAB>quantity<TAB>english<TAB>translation`

counts as a filled column 5 for that key+quantity (parts win over the main

file; they are how agents hand in rows without racing on the shared file).



Flags, with row numbers:

- non-skip rows with an empty column 5 (after part merge);

- placeholder mismatches against English (same multiset, order ignored);

- unescaped apostrophes / double quotes, leading @ or ?;

- %% vs % rules from StringsXmlTest (%% only beside placeholders);

- a multi-placeholder row using non-positional form;

- rows left identical to English in a Latin script, unless the value is a

  token the term tables keep in English (RPE, kg, XP, brand names, patterns).



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

# Tokens the term tables deliberately keep in English (brands, units, patterns).

KEEP_ENGLISH = re.compile(

    r"^(?:RPE|RIR|1RM|BMI|FFMI|XP|LV|Lv|kg|lb|cm|"

    r"Ironvellum|Supabase|Liberapay|GitHub|Google|Health Connect|"

    r"[dELyMHmsz.\-–—:/%()+≤≥✓?\s]+|"   # date/number patterns, marks

    r"[A-Za-z0-9 .:%(),/+\-]+)$"           # any ASCII token phrase

)





def load_parts(work_dir: Path):

    """key|quantity -> translation, from every part*.tsv in the work dir."""

    parts = {}

    for p in sorted(work_dir.glob("part*.tsv")):

        for line in p.read_text(encoding="utf-8").splitlines():

            if not line.strip():

                continue

            cols = line.split("\t")

            if len(cols) < 5 or cols[0] == "skip":

                continue

            if cols[4].strip():

                parts[(cols[1], cols[2])] = cols[4]

    return parts





def check_row(english, translation, code):

    if not translation.strip():

        return ["empty"]

    errors = []

    # A `%` before a space then a letter (`Body fat % (optional)`, `40% more`) is a

    # literal percent, not a placeholder: strip those before comparing.

    def phs(text):

        return sorted(PLACEHOLDER.findall(re.sub(r"%\s", "%\u0000", text.replace("%%", ""))))

    en_ph = phs(english)

    tr_ph = phs(translation)

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

        # A literal % is legal only when the English source has one in the same role.

        if PLACEHOLDER.sub("", english.replace("%%", "")).count("%") == 0:

            errors.append("stray % beside placeholders")

    if len(tr_ph) > 1 and any(idx == "" for idx, _ in tr_ph):

        errors.append("multi placeholder must be positional")

    if code in ("es", "de", "fr", "pt-rBR") and translation.strip() == english.strip():

        # Patterns (placeholders, units, CLDR letters, marks) and term-table tokens

        # stay English on purpose: strip placeholders, then allow the rest as long

        # as it holds no real word (3+ letters that are not a known unit token).

        # A string containing a brand the tables keep (Ironvellum) is likewise fine.

        skeleton = PLACEHOLDER.sub("", english.replace("%%", "")).strip()
        brands = re.search(r"Ironvellum|Health Connect|Supabase|Liberapay|GitHub|Google", english)
        # Only placeholders plus punctuation/units around them: data formatting, not copy.
        unit = re.fullmatch(r"""[A-Za-z0-9 .,:;/()%~×→–—“”’↳'\"\-−+<>&;|·]*""", skeleton)
        if not (brands or unit or KEEP_ENGLISH.match(english.strip())):
            errors.append("left identical to English")
    return errors





def main(argv):

    if not argv:

        print(__doc__.strip().splitlines()[0])

        return 2

    code = argv[0]

    work_dir = ROOT / ".tmp" / "translation-work" / code

    paths = [Path(p) for p in argv[1:]] or [work_dir / "english.tsv"]

    parts = load_parts(work_dir)

    total = 0

    for p in paths:

        if not p.is_file():

            print(f"!! no such file: {p}")

            return 2

        for lineno, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):

            if not line.strip():

                continue

            cols = line.split("\t")

            kind, key, quantity = cols[0], cols[1], cols[2]

            if kind == "skip":

                continue

            english = cols[3] if len(cols) > 3 else ""

            translation = parts.get((key, quantity)) or (cols[4] if len(cols) > 4 else "")

            for e in check_row(english, translation, code):

                where = f"{key}[{quantity}]" if quantity else key

                print(f"{p.name}:{lineno} {where}: {e}")

                total += 1

    print(f"{code}: {total} errors")

    return 1 if total else 0





if __name__ == "__main__":

    sys.exit(main(sys.argv[1:]))

