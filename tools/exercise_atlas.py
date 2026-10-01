#!/usr/bin/env python3
"""Export the exercise catalogue and build .tmp/exercise-atlas.html.

Runs the CatalogueExport unit test (which writes .tmp/exercises.json), then
injects the JSON into tools/exercise_atlas.html at /*__ATLAS_DATA__*/null.
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
OUT = TMP / "exercise-atlas.html"
TOKEN = "/*__ATLAS_DATA__*/null"


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
    if not TEMPLATE.exists():
        print(f"No template at {TEMPLATE}; stopping after the JSON.")
        return 0

    html = TEMPLATE.read_text(encoding="utf-8")
    if TOKEN not in html:
        print(f"Template lacks {TOKEN}", file=sys.stderr)
        return 1
    payload = json.dumps(data, ensure_ascii=False).replace("</", "<\\/")
    with open(OUT, "w", encoding="utf-8", newline="") as f:
        f.write(html.replace(TOKEN, payload))
    print(OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
