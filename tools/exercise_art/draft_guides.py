#!/usr/bin/env python3
"""Drafts tools/exercise_art/guides.json: a how-to for every movement the library adds.

    python3 -P tools/exercise_art/draft_guides.py --written <dir holding out_*.json>

Everkinetic records carry their own steps (CC BY-SA 4.0, attributed in the app's credits): the first becomes the setup
line and the rest the steps; its tips, where it has any, become the cues. The movements with no upstream text were
written fresh (--written), in the same setup / steps / cues / mistakes shape as ExerciseGuides.
"""
import argparse
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
# docs/GLOSSARY.md words the app never shows; the guides are screened against the same list as GlossaryTest.
RETIRED = ["quests?", "lifters?", "routines?", "workouts?", "sessions?", "stats", "skills?", "plans?", "programs?", "gear",
           "equipment", "equip", "equipped", "schedule", "templates?", "friends?", "feed", "comments?"]
SWAPS = [(r"\blifters\b", "people"), (r"\blifter\b", "person"), (r"\bequipment\b", "bar"), (r"\bworkouts?\b", "training")]


def clean(t):
    t = t.replace("’", "'").replace("‘", "'").replace("“", '"').replace("”", '"').replace("–", "-").replace("—", "-")
    t = re.sub(r"\s+", " ", t).strip()
    for rx, sub in SWAPS:
        t = re.sub(rx, sub, t, flags=re.I)
    if t and t[-1] not in ".!?":
        t += "."
    return t


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--written", required=True)
    a = ap.parse_args()
    lib = json.loads((HERE / "library.json").read_text(encoding="utf-8"))
    written = {}
    for f in sorted(Path(a.written).glob("out_*.json")):
        written.update(json.loads(f.read_text(encoding="utf-8")))
    out, missing = {}, []
    for e in lib:
        up = e["upstream"]
        if up["steps"]:
            steps = [clean(s) for s in up["steps"]]
            if len(steps) >= 4:
                setup, rest = steps[0], steps[1:]
            else:
                setup, rest = "Set up as the drawing shows, ready for the first rep.", steps
            out[e["name"]] = {"setup": setup, "steps": rest, "cues": [clean(t) for t in up["tips"]], "mistakes": [], "source": "Everkinetic"}
        elif e["name"] in written:
            w = written[e["name"]]
            out[e["name"]] = {"setup": clean(w["setup"]), "steps": [clean(s) for s in w["steps"]],
                              "cues": [clean(c).rstrip(".") for c in w["cues"]], "mistakes": [clean(m).rstrip(".") for m in w["mistakes"]],
                              "source": "written"}
        else:
            missing.append(e["name"])
    bad = [(n, m.group(0)) for n, g in out.items() for t in [g["setup"]] + g["steps"] + g["cues"] + g["mistakes"]
           for m in re.finditer(r"(?i)(?<![\w'])(%s)(?![\w'])" % "|".join(RETIRED), t)]
    (HERE / "guides.json").write_text(json.dumps(out, indent=1, ensure_ascii=False) + chr(10), encoding="utf-8")
    print("guides", len(out), "missing", missing, "retired words", bad)


if __name__ == "__main__":
    main()
