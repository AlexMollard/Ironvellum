"""Repair / rebuild a translation work file from pristine English + part files.

    python3 tools/translation_fix.py <code>

Rebuilds `.tmp/translation-work/<code>/english.tsv` from the pristine English
order (tools/translation_batches), one row per entry, CRLF, five columns.

Translation sources, in increasing precedence:
1. the previous work file, but a value only survives when every row that
   claimed that key+quantity agreed on it (a racing writer's overwrite does
   not count as a claim);
2. every part*.tsv in the work directory (the agent hand-ins win).

Run this whenever a checker complains about the work file's shape, then rerun
the checker.
"""

from __future__ import annotations

import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import translation_batches as tb  # noqa: E402
import check_translation_tsv as ct  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent


def main(argv):
    if len(argv) != 1:
        print(__doc__.strip().splitlines()[0])
        return 2
    code = argv[0]
    work = ROOT / ".tmp" / "translation-work" / code
    path = work / "english.tsv"

    claims = {}   # (key, quantity) -> Counter of values the old file claimed
    if path.is_file():
        for l in path.read_bytes().replace(b"\r\n", b"\n").replace(b"\r", b"\n").decode("utf-8").split("\n"):
            if not l.strip():
                continue
            cols = l.split("\t")
            if len(cols) > 4 and cols[4].strip():
                claims.setdefault((cols[1], cols[2]), Counter())[cols[4]] += 1
    unambiguous = {k: next(iter(c)) for k, c in claims.items() if len(c) == 1}

    parts = ct.load_parts(work)

    out = []
    for kind, key, quantity, english in tb.entries():
        tr = unambiguous.get((key, quantity), "")
        tr = parts.get((key, quantity), tr)
        out.append("\t".join([kind, key, quantity, english, tr]))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\r\n".join(out) + "\r\n", encoding="utf-8", newline="")
    filled = sum(1 for r in out if r.split("\t")[0] != "skip" and r.split("\t")[4].strip())
    print(f"{code}: {len(out)} rows, {filled} translated")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
