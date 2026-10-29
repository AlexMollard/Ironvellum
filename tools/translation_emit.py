"""Emit values-<folder>/strings.xml from a completed translation tsv.

    python3 tools/translation_emit.py <code> <folder>

The tsv (`<work>/<code>/english.tsv`, parts merged) is the source of truth:
one row per resource, columns kind/key/quantity/English/translation. `skip`
rows (translatable="false") are left out. Translation text is written verbatim
(it already carries Android escaping `\\'`, `\\"`, `\\n`); only XML markup
characters are escaped.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
 <!--
 Translators: see docs/TRANSLATING.md. Every sentence that names a glossary concept exists twice: "key" is
 the plain wording and is what a language ships on; "key__t" is the optional themed wording. Placeholders are
 positional (%1$s, %2$d) so word order can change. Comments above a string say what fills a placeholder.
 -->
"""


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def emit(code, folder):
    src = ROOT / ".tmp" / "translation-work" / code / "english.tsv"
    lines = [l for l in src.read_bytes().replace(b"\r\n", b"\n").replace(b"\r", b"\n").decode("utf-8").split("\n") if l.strip()]
    final = [HEADER]
    pend = None
    ns = np = 0

    def close():
        nonlocal pend
        if pend:
            final.append("    </plurals>\n")
            pend = None

    for l in lines:
        c = l.split("\t")
        kind, key, qty, tr = c[0], c[1], c[2], (c[4] if len(c) > 4 else "")
        if kind == "skip":
            close()
            continue
        if not tr.strip():
            print(f"!! {code}: empty {key} {qty}", file=sys.stderr)
            return 1
        t = esc(tr)
        if kind == "string":
            close()
            final.append(f'    <string name="{key}">{t}</string>\n')
            ns += 1
        else:
            if pend != key:
                close()
                final.append(f'    <plurals name="{key}">\n')
                pend = key
                np += 1
            final.append(f'        <item quantity="{qty}">{t}</item>\n')
    close()
    final.append("</resources>\n")
    d = ROOT / "app/src/main/res" / f"values-{folder}"
    d.mkdir(parents=True, exist_ok=True)
    (d / "bools.xml").write_bytes(
        ('<?xml version="1.0" encoding="utf-8"?>\r\n<resources>\r\n    <bool name="themed_strings_complete">true</bool>\r\n</resources>\r\n').encode("utf-8")
    )
    (d / "strings.xml").write_bytes("".join(final).encode("utf-8"))
    print(f"{code}: {ns} strings, {np} plurals")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__.strip().splitlines()[0])
        sys.exit(2)
    sys.exit(emit(sys.argv[1], sys.argv[2]))
