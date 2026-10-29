"""Fill remaining empty translation rows from a hand-authored answer file.

    python3 tools/translation_apply.py <code> answers.tsv

Answer rows: `key<TAB>translation` or `key<TAB>quantity<TAB>translation`
(the middle column only for plural keys). Updates english.tsv in place for
those keys, CRLF, UTF-8 no BOM. Refuses unknown keys.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main(argv):
    if len(argv) != 2:
        print(__doc__.strip().splitlines()[0])
        return 2
    code, answers_path = argv
    work = ROOT / ".tmp" / "translation-work" / code / "english.tsv"
    answers = {}
    plurals = False
    for l in Path(answers_path).read_text(encoding="utf-8").splitlines():
        if not l.strip():
            continue
        parts = l.split("\t")
        if len(parts) == 3:
            answers[(parts[0], parts[1])] = parts[2]
            plurals = True
        elif len(parts) == 2:
            answers[(parts[0], "")] = parts[1]
        else:
            print(f"!! {answers_path}: expected key<TAB>[quantity<TAB>]translation")
            return 2

    lines = [l for l in work.read_bytes().replace(b"\r\n", b"\n").replace(b"\r", b"\n").decode("utf-8").split("\n") if l.strip()]
    hit = 0
    out = []
    for l in lines:
        cols = l.split("\t")
        key = (cols[1], cols[2])
        if key in answers:
            cols[4] = answers[key]
            hit += 1
        out.append("\t".join(cols))
    present = {(l.split("\t")[1], l.split("\t")[2]) for l in lines}
    missing = set(answers) - present
    work.write_bytes(("\r\n".join(out) + "\r\n").encode("utf-8"))
    print(f"{code}: applied {hit}/{len(answers)} answers" + (f", unknown keys {sorted(missing)}" if missing else ""))
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
