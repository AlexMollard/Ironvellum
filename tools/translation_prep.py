"""Prepare one language's translation: dump the English entries to fill.

    python3 tools/translation_prep.py es

Writes .tmp/translation-work/<code>/english.tsv: every row of
tools/translation_batches.py, with an empty fifth column for the translation.
The translating agent fills column 5, splits the file into batches, and feeds
them to tools/translation_merge.py.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import translation_batches as tb  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent


def main(argv):
    if len(argv) != 1:
        print(__doc__.strip().splitlines()[0])
        return 2
    code = argv[0]
    out_dir = ROOT / ".tmp" / "translation-work" / code
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / "english.tsv"
    rows = list(tb.entries())
    with open(out, "w", encoding="utf-8", newline="\r\n") as h:
        for kind, key, quantity, text in rows:
            h.write(f"{kind}\t{key}\t{quantity}\t{text}\t\n")
    strings = sum(1 for r in rows if r[0] in ("string", "plural"))
    print(f"{out}: {len(rows)} rows ({strings} to translate)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
