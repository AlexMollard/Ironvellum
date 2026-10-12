"""Reads the two upstream art projects into one list of records. Data only: nothing from --src is ever executed.

    bryllim/workout-guide  code MIT, assets CC BY-SA 4.0   (packages/workout-guide/manifest.json + assets/<slug>/frame-N.png)
    everkinetic/data       CC BY-SA 4.0                    (src/exercises/<id>/<id>.json + dist/png/<id>-relaxation|tension.png)

A workout-guide exercise whose first frame is traced from an Everkinetic drawing names that drawing in its manifest
(attribution.source.url, "dist/svg/0042-tension.svg"); those are merged into one record that keeps Everkinetic's
own two drawings (cleaner, consistent) and workout-guide's tidier name.
"""
import json
import os
import re
import sys
from pathlib import Path

WG = Path("wg/packages/workout-guide")
EK = Path("ek")


def load(src):
    src = Path(src)
    wg = json.loads((src / WG / "manifest.json").read_text(encoding="utf-8"))
    ek = {}
    root = src / EK / "src" / "exercises"
    for d in sorted(os.listdir(root)):
        p = root / d / (d + ".json")
        if p.exists():
            ek[d] = json.loads(p.read_text(encoding="utf-8"))
    return wg, ek


def records(src):
    src = Path(src)
    wg, ek = load(src)
    dist = src / EK / "dist" / "png"
    have = {i for i in ek if (dist / (i + "-relaxation.png")).exists() and (dist / (i + "-tension.png")).exists()}
    out, used = [], set()
    for w in wg:
        s = w["attribution"].get("source")
        eid = re.search(r"/(\d{4})-", s["url"]).group(1) if s else None
        rec = {"id": "wg:" + w["slug"], "wg": w, "ek": ek.get(eid) if eid else None, "ekId": eid}
        if eid and eid in have:
            rec["art"] = "ek"
            used.add(eid)
        else:
            rec["art"] = "wg"
        out.append(rec)
    for i, e in ek.items():
        if i not in used and not any(r["ekId"] == i for r in out):
            out.append({"id": "ek:" + i, "wg": None, "ek": e, "ekId": i, "art": "ek" if i in have else None})
    return out


if __name__ == "__main__":
    recs = records(sys.argv[1])
    print(len(recs), "records;", sum(1 for r in recs if r["art"]), "with drawings")
