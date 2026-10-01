#!/usr/bin/env python3
"""Export the exercise catalogue and rebuild docs/exercise-atlas.html.

Runs the CatalogueExport unit test (which writes .tmp/exercises.json), then
injects the JSON into tools/exercise_atlas.html at /*__ATLAS_DATA__*/null.
Writes the committed standalone page to docs/ and a bare fragment (no document
skeleton, which the Artifact host adds itself) to .tmp/ for publishing.
"""
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TMP = ROOT / ".tmp"
JSON_PATH = TMP / "exercises.json"
TEMPLATE = ROOT / "tools" / "exercise_atlas.html"
OUT = ROOT / "docs" / "exercise-atlas.html"
FRAGMENT = TMP / "exercise-atlas.html"
TOKEN = "/*__ATLAS_DATA__*/null"
HEAD = (
    '<!doctype html>\n<html lang="en">\n<meta charset="utf-8">\n'
    '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
)


def main() -> int:
    TMP.mkdir(exist_ok=True)
    commit = subprocess.run(
        ["git", "rev-parse", "--short", "HEAD"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout.strip()
    env = dict(os.environ)
    env.pop("NoDefaultCurrentDirectoryInExePath", None)
    env["IRONVELLUM_EXPORT"] = str(JSON_PATH)
    env["IRONVELLUM_COMMIT"] = commit
    gradlew = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    cmd = [gradlew, ":app:testFossDebugUnitTest",
           "--tests", "com.ironvellum.app.domain.CatalogueExport", "--rerun"]
    if subprocess.run(cmd, cwd=ROOT, env=env).returncode != 0:
        return 1

    data = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    print(f"{JSON_PATH} ({len(data['exercises'])} exercises)")
    html = TEMPLATE.read_text(encoding="utf-8").replace("\r\n", "\n")
    if TOKEN not in html:
        print(f"Template lacks {TOKEN}", file=sys.stderr)
        return 1

    # One exercise per line keeps the committed page's diffs readable.
    head = {k: v for k, v in data.items() if k != "exercises"}
    rows = ",\n".join(json.dumps(e, ensure_ascii=False) for e in data["exercises"])
    payload = json.dumps(head, ensure_ascii=False)[:-1] + ',\n"exercises": [\n' + rows + "\n]}"
    page = html.replace(TOKEN, payload.replace("</", "<\\/"))
    for path, text in ((OUT, HEAD + page), (FRAGMENT, page)):
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(text.replace("\n", "\r\n"))
        print(path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
