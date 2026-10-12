#!/usr/bin/env python3
"""Drafts tools/exercise_art/library.json and mapping.json from the upstream records plus curation.py.

    python3 -P tools/exercise_art/draft_library.py --src <upstream dir> --catalogue <catalogue.json> [--out tools/exercise_art]

--catalogue is the JSON the unit test CatalogueExport writes (IRONVELLUM_EXPORT=<file> ./gradlew :app:testFossDebugUnitTest
--tests "*CatalogueExport*"): a new movement borrows the muscle profile of its closest existing analogue ("like"), so the
shares stay on the scale MuscleMap documents instead of being invented here. library.json is then edited by hand.
"""
import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import curation  # noqa: E402
import upstream  # noqa: E402

# Profiles with no existing analogue. pattern/compound/stretch/noLead as in MuscleMap.ExerciseProfile.
def P(muscles, pattern, compound=False, stretch=False, noLead=False):
    return {"muscles": muscles, "pattern": pattern, "compound": compound, "stretchBias": stretch, "noLead": noLead}


CUSTOM = [
    (r"bird dog", P({"LOWER_BACK": 1.0, "GLUTES": 0.5, "ABS": 0.5, "REAR_DELTS": 0.5}, "CORE"), None),
    (r"static neck", P({"NECK": 1.0}, "ISOLATION"), "hold"),
    (r"upright row", P({"SIDE_DELTS": 1.0, "TRAPS": 1.0, "FRONT_DELTS": 0.5, "BICEPS": 0.5, "FOREARMS": 0.5}, "VERTICAL_PULL", True), None),
    (r"iron cross", P({"MID_CHEST": 1.0, "FRONT_DELTS": 0.5, "LATS": 0.5}, "ISOLATION", False, True), None),
    (r"kettlebell swing", P({"GLUTES": 1.0, "HAMSTRINGS": 1.0, "LOWER_BACK": 0.5, "QUADS": 0.5, "ADDUCTORS": 0.5}, "HINGE", True), None),
    (r"leg swing", P({"HIP_FLEXORS": 0.5, "HAMSTRINGS": 0.5, "GLUTES": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"arm circles", P({"FRONT_DELTS": 0.5, "SIDE_DELTS": 0.5, "ROTATOR_CUFF": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"ankle circles", P({"TIBIALIS": 0.5, "CALVES": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"knee circles", P({"QUADS": 0.5, "HAMSTRINGS": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"ball wall circles", P({"FRONT_DELTS": 0.5, "ROTATOR_CUFF": 0.5, "SERRATUS": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"cat-cow", P({"ABS": 0.5, "LOWER_BACK": 0.5, "RHOMBOIDS": 0.5}, "CORE", False, False, True), "mobility"),
    (r"world's greatest", P({"HIP_FLEXORS": 0.5, "HAMSTRINGS": 0.5, "GLUTES": 0.5, "ROTATOR_CUFF": 0.5}, "LUNGE", True, False, True), "mobility"),
    (r"torso twist", P({"OBLIQUES": 0.5, "LOWER_BACK": 0.5}, "CORE", False, False, True), "mobility"),
    (r"hip airplane", P({"ABDUCTORS": 0.5, "GLUTES": 0.5, "ADDUCTORS": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"doorway chest", P({"MID_CHEST": 0.5, "FRONT_DELTS": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"child's pose", P({"LATS": 0.5, "LOWER_BACK": 0.5, "GLUTES": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"hip flexor stretch", P({"HIP_FLEXORS": 0.5, "QUADS": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"hamstring stretch", P({"HAMSTRINGS": 0.5, "CALVES": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"quad stretch", P({"QUADS": 0.5, "HIP_FLEXORS": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"forward fold", P({"HAMSTRINGS": 0.5, "LOWER_BACK": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"shoulder stretch", P({"REAR_DELTS": 0.5, "ROTATOR_CUFF": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"calf stretch", P({"CALVES": 0.5, "TIBIALIS": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"butterfly", P({"ADDUCTORS": 0.5, "HIP_FLEXORS": 0.5}, "ISOLATION", False, False, True), "mobility"),
    (r"battle rope", P({"FRONT_DELTS": 0.5, "ABS": 0.5, "FOREARMS": 0.5, "TRAPS": 0.5}, "CORE", True, False, True), "cardio"),
    (r"treadmill incline", P({"GLUTES": 0.5, "CALVES": 0.5, "QUADS": 0.5, "HAMSTRINGS": 0.5}, "LUNGE", True, False, True), "cardio"),
    (r"burpee", P({"QUADS": 0.5, "MID_CHEST": 0.5, "ABS": 0.5, "TRICEPS": 0.5}, "SQUAT", True, False, True), "conditioning"),
    (r"sprawl", P({"QUADS": 0.5, "MID_CHEST": 0.5, "ABS": 0.5, "TRICEPS": 0.5}, "SQUAT", True, False, True), "conditioning"),
    (r"high knees", P({"HIP_FLEXORS": 0.5, "QUADS": 0.5, "CALVES": 0.5, "ABS": 0.5}, "LUNGE", True, False, True), "conditioning"),
    (r"jumping jack", P({"CALVES": 0.5, "SIDE_DELTS": 0.5, "ABDUCTORS": 0.5, "ADDUCTORS": 0.5}, "ISOLATION", True, False, True), "conditioning"),
    (r"lateral shuffle", P({"ABDUCTORS": 0.5, "QUADS": 0.5, "GLUTES": 0.5, "CALVES": 0.5}, "LUNGE", True, False, True), "conditioning"),
    (r"mountain climber", P({"ABS": 0.5, "HIP_FLEXORS": 0.5, "FRONT_DELTS": 0.5, "QUADS": 0.5}, "CORE", True, False, True), "conditioning"),
]

# (regex on the lowercased name, existing analogue, angle) - first match wins.
LIKE = [
    (r"shoulder press|military press|overhead press|push press|arnold|standing dumbbell press", "Dumbbell Shoulder Press", None),
    (r"rear|reverse pec|reverse fly|reverse pec deck|face pull|pull-apart|t raise|y raise|snow angel|bent-over cable lateral", "Reverse Fly", None),
    (r"decline.*(bench|press)|reverse-grip triceps bench", "Bench Press", "lower"),
    (r"incline.*(press)", "Incline Bench Press", None),
    (r"close-grip bench|jm press", "Close-Grip Bench Press", None),
    (r"bench press|chest press|floor press|neck press|landmine press", "Bench Press", None),
    (r"incline.*(fly|crossover)", "Dumbbell Fly", "upper"),
    (r"decline.*fly", "Dumbbell Fly", "lower"),
    (r"fly|crossover|pec deck", "Dumbbell Fly", None),
    (r"pullover", "Dumbbell Pullover", None),
    (r"plank|hollow|l-sit|crab|inchworm|dragon", "Plank", None),
    (r"decline push|feet-elevated push|bosu|exercise ball push", "Push-up", "upper"),
    (r"incline push|wall push|knee push", "Push-up", "lower"),
    (r"pike|handstand push|wall walk", "Pike Push-up", None),
    (r"push-up|push up|pushup|hindu|typewriter", "Push-up", None),
    (r"dip", "Dip", None),
    (r"lateral raise|side raise", "Lateral Raise", None),
    (r"front raise", "Front Raise", None),
    (r"shrug", "Dumbbell Shrug", None),
    (r"internal rotation", "Dumbbell External Rotation", None),
    (r"pulldown|pull-down", "Lat Pulldown", None),
    (r"pull-up|chin-up|chin up|hang|commando", "Pull-up", None),
    (r"\brows?\b", "Seated Cable Row", None),
    (r"back extension|hyperextension|superman", "Back Extension", None),
    (r"good morning", "Good Morning", None),
    (r"dead bug", "Dead Bug", None),
    (r"romanian|stiff|deadlift|pull-through|snatch", "Romanian Deadlift", None),
    (r"hip thrust|bridge|frog pump", "Hip Thrust", None),
    (r"kickback|donkey kick|fire hydrant|abduct|clamshell|lateral walk|monster walk|side-lying", "Hip Abduction", None),
    (r"adduct", "Hip Adduction", None),
    (r"leg curl|hamstring", "Lying Leg Curl", None),
    (r"leg extension", "Leg Extension", None),
    (r"seated.*calf|donkey calf|leg press calf", "Seated Calf Raise", None),
    (r"calf", "Standing Calf Raise", None),
    (r"lunge|split squat|step-up|step up|step-down|skater squat|single-leg|one-leg|pistol|shrimp", "Walking Lunge", None),
    (r"squat|wall sit|leg press|belt|sissy", "Back Squat", None),
    (r"skull|tricep|triceps|pushdown|tate", "Triceps Pushdown", None),
    (r"curl", "Bicep Curl", None),
    (r"wrist", "Wrist Curl", None),
    (r"russian twist|woodchop|side bend|pallof|oblique", "Woodchop", None),
    (r"hanging|captain|knee raise|knee tuck", "Hanging Leg Raise", None),
    (r"leg raise|leg lift|flutter|v-up|toe touch|heel tap|dead bug|bird dog|bear crawl", "Lying Leg Raise", None),
    (r"crunch|sit-up|rollout|ab wheel|draw-in|pull-in|rock", "Plank", None),
    (r"farmer|carry", "Plank", None),
]

def load_catalogue(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return {e["name"]: e for e in data["exercises"]}


def like_for(name):
    n = name.lower()
    for rx, prof, kind in CUSTOM:
        if re.search(rx, n):
            return prof, kind
    for rx, like, extra in LIKE:
        if re.search(rx, n):
            return like, extra
    return None, None


PUSH_MUSCLES = {"MID_CHEST", "UPPER_CHEST", "LOWER_CHEST", "TRICEPS", "FRONT_DELTS", "SIDE_DELTS", "SERRATUS"}
PULL_MUSCLES = {"LATS", "RHOMBOIDS", "TRAPS", "BICEPS", "BRACHIALIS", "FOREARMS", "REAR_DELTS", "ROTATOR_CUFF"}
LEG_MUSCLES = {"QUADS", "HAMSTRINGS", "GLUTES", "CALVES", "ADDUCTORS", "ABDUCTORS", "TIBIALIS", "LOWER_BACK"}


def group_for(profile, name):
    pat = profile["pattern"]
    if pat in ("VERTICAL_PULL", "HORIZONTAL_PULL"):
        return "PULL"
    if pat in ("VERTICAL_PUSH", "HORIZONTAL_PUSH"):
        return "PUSH"
    if pat in ("SQUAT", "HINGE", "LUNGE"):
        return "LEGS"
    if pat == "CORE":
        return "CORE"
    lead = next(iter(profile["muscles"]))
    if lead in PUSH_MUSCLES:
        return "PUSH"
    if lead in PULL_MUSCLES:
        return "PULL"
    if lead in LEG_MUSCLES:
        return "LEGS"
    return "CORE"


def classify(name, rec, entry):
    """weighted / metric / category / tier / loadFactor / gear from the upstream tags and the name."""
    n = name.lower()
    up = entry["upstream"]
    equip = " ".join(up["equipment"]).lower() + " " + (up["wgEquipment"] or "").lower()
    wgt = up["wgType"]
    banded = bool(re.search(r"band", equip + n))
    if wgt:
        weighted = wgt in ("weight_reps", "assisted_bodyweight") and not banded
    else:
        weighted = bool(re.search(r"barbell|dumbbell|cable|machine|smith|plate|weight|t-bar|bar\b", equip)) and not banded
    if re.search(r"bodyweight|body", equip) and not re.search(r"barbell|dumbbell|cable|machine|smith|plate", equip) and not wgt:
        weighted = False
    if re.search(r"^(assisted|weighted)", n):
        weighted = True
    # load transmission, MovementDifficulty.loadFactor bands
    load = 1.0
    if weighted:
        if re.search(r"smith", n + equip):
            load = 0.90
        elif re.search(r"crossover|cable fly|flat bench cable fly", n):
            load = 0.50
        elif re.search(r"leg press|belt squat|lying squat|hack squat", n) and not re.search(r"barbell", n):
            load = 0.70
        elif re.search(r"cable|machine|pulldown|pec deck|pushdown", n + " " + equip.replace("wg", "")):
            load = 0.85
    gear = []
    if re.search(r"^assisted", n):
        load = 1.0
    if weighted and load == 1.0 and not re.search(r"^assisted", n):
        bench = bool(re.search(r"bench|incline|decline|lying|preacher|flat|skull|floor press|seated overhead|pullover", n))
        if re.search(r"barbell|ez-bar|landmine|trap bar|bar|snatch|jefferson|zercher|good morning|jm press|decline bench|bench press|skull crusher", n + " " + equip):
            gear = [["BARBELL", "BENCH"]] if bench else [["BARBELL"]]
        elif re.search(r"dumbbell", n + " " + equip):
            gear = [["DUMBBELLS", "BENCH"]] if bench else [["DUMBBELLS"]]
    if re.search(r"pull-up|chin-up|hang|commando", n) and not re.search(r"^assisted", n):
        gear = [["PULL_UP_BAR"], ["RINGS"]]
    if re.search(r"^(chest dip|dip)|parallel", n):
        gear = [["DIP_BARS"], ["RINGS"]]
    return weighted, load, gear


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--catalogue", required=True)
    ap.add_argument("--out", default=str(Path(__file__).resolve().parent))
    a = ap.parse_args()
    cat = load_catalogue(a.catalogue)
    lower = {k.lower(): k for k in cat}
    recs = upstream.records(a.src)

    for k, (name, _) in curation.MAP.items():
        if name.lower() not in lower:
            sys.exit("MAP target is not in the catalogue: %s -> %s" % (k, name))

    mapping, library, dropped, noart = [], [], [], []
    for r in recs:
        rid = r["id"]
        w, e = r["wg"], r["ek"]
        if r["art"] is None:
            noart.append(rid)
            continue
        art = "ek_" + r["ekId"] if r["art"] == "ek" else "wg_" + w["slug"]
        frames = 2 if r["art"] == "ek" else 3
        src = {"kind": "ek", "id": r["ekId"]} if r["art"] == "ek" else {"kind": "wg", "id": w["slug"]}
        if rid in curation.MAP:
            name, conf = curation.MAP[rid]
            mapping.append({"record": rid, "exercise": cat[lower[name.lower()]]["name"], "confidence": conf, "art": art, "frames": frames, "source": src})
            continue
        if w:
            if rid in curation.DROP:
                dropped.append({"record": rid, "reason": curation.DROP[rid]})
                continue
            name = curation.RENAME.get(rid, w["name"]).replace("Tricep ", "Triceps ")
        else:
            name = curation.NAMES.get(r["ekId"])
            if not name:
                dropped.append({"record": rid, "reason": "duplicate or near-duplicate of a listed movement"})
                continue
        if name.lower() in lower:
            sys.exit("new name collides with the catalogue: %s (%s)" % (name, rid))
        up = {
            "title": (e or {}).get("title") or w["name"],
            "equipment": (e or {}).get("equipment") or [w["equipment"]],
            "type": (e or {}).get("type") or w["exerciseType"],
            "primary": (e or {}).get("primary") or [w["primaryMuscle"]],
            "secondary": (e or {}).get("secondary") or (w["secondaryMuscles"] if w else []),
            "wgType": w["exerciseType"] if w else None,
            "wgEquipment": w["equipment"] if w else None,
            "wgStretch": w["isStretch"] if w else False,
            "steps": (e or {}).get("steps") or [],
            "tips": (e or {}).get("tips") or [],
        }
        entry = {"name": name, "record": rid, "art": art, "frames": frames, "source": src, "upstream": up}
        like, extra = like_for(name)
        kind = None
        if isinstance(like, dict):
            profile, kind = like, extra
            entry["like"] = None
        elif like:
            base = cat[like]
            muscles = dict(base["muscles"])
            if extra in ("upper", "lower"):
                region = "UPPER_CHEST" if extra == "upper" else "LOWER_CHEST"
                muscles = {region: 1.0, **{k: v for k, v in muscles.items() if k != region}}
            stretch = base["stretchBias"]
            if base["pattern"] == "ISOLATION" and "TRICEPS" in muscles and re.search(r"overhead|lying|skull|incline|decline|behind the head|across face|preacher", name.lower()):
                stretch = True
            profile = {"muscles": muscles, "pattern": base["pattern"], "compound": base["compound"],
                       "stretchBias": stretch, "noLead": base["noLeadByDesign"]}
            entry["like"] = like
        else:
            sys.exit("no profile rule for %s" % name)
        entry["profile"] = profile
        group = cat[like]["group"] if isinstance(like, str) and cat[like]["group"] in ("PUSH", "PULL", "LEGS", "CORE") else group_for(profile, name)
        weighted, load, gear = classify(name, r, entry)
        metric, category = "REPS", ""
        wgt = up["wgType"]
        if kind == "mobility":
            group, metric, category, weighted = "MOBILITY", "DURATION", "Mobility", False
        elif kind == "cardio":
            group, metric, category = "CARDIO", ("DISTANCE_TIME" if wgt == "distance_duration" else "DURATION"), "Cardio"
            weighted = False
        elif kind == "conditioning":
            group, metric, category, weighted = "CORE", "REPS", "", False
        elif kind == "hold" or wgt == "duration" or up["type"] == "isometric":
            metric = "HOLD"
        entry.update({"group": group, "metric": metric, "category": category, "weighted": weighted,
                      "loadFactor": load, "gear": gear})
        entry["tier"] = 1 if (not profile["compound"] or metric == "HOLD" or kind) else 2
        if metric == "HOLD":
            entry["profile"] = dict(profile, stretchBias=False)
        for key, val in curation.OVERRIDE.get(name, {}).items():
            entry[key] = val
        library.append(entry)

    # One art per existing movement: the best-confidence record, then the one named like it, then the first.
    def rank(m):
        slug = m["source"]["id"] if m["source"]["kind"] == "wg" else ""
        named = re.sub(r"[^a-z]", "", slug) == re.sub(r"[^a-z]", "", m["exercise"].lower())
        return (m["confidence"] != "high", not named)

    chosen = {}
    for m in sorted(mapping, key=rank):
        chosen.setdefault(m["exercise"], m)
    for m in mapping:
        m["used"] = chosen[m["exercise"]] is m
    art_by_name = {m["exercise"]: m for m in chosen.values()}
    for e in library:
        art_by_name[e["name"]] = {"art": e["art"], "frames": e["frames"], "confidence": "new", "source": e["source"]}

    # Skill-tree techniques that take the animated drawing: the skill is the same movement as a mapped or added one.
    skills = {e["name"] for e in cat.values() if e.get("skill")}
    techniques = {}
    for sk in sorted(skills):
        target = curation.TECHNIQUE_EXTRA.get(sk, sk)
        m = art_by_name.get(target)
        if m:
            techniques[sk] = {"exercise": target, "art": m["art"], "frames": m["frames"],
                              "confidence": "medium" if (m["confidence"] == "medium" or sk in curation.TECHNIQUE_EXTRA) else "high"}
    unarted = sorted(n for n, e in cat.items() if e["group"] in ("PUSH", "PULL", "LEGS", "CORE") and n not in art_by_name)

    out = Path(a.out)
    (out / "library.json").write_text(json.dumps(library, indent=1, ensure_ascii=False) + chr(10), encoding="utf-8")
    (out / "mapping.json").write_text(json.dumps({"mapped": mapping, "techniques": techniques, "dropped": dropped, "noDrawing": noart, "existingWithoutArt": unarted}, indent=1, ensure_ascii=False) + chr(10), encoding="utf-8")
    print("records", len(recs), "mapped", len(mapping), "(exercises", len(chosen), ") new", len(library), "dropped", len(dropped), "no drawing", len(noart), "techniques", len(techniques), "existing without art", len(unarted))
    for x in library:
        print("%-52s %-6s %-9s w=%d lf=%.2f t=%d %-22s %s" % (
            x["name"], x["group"], x["metric"], x["weighted"], x["loadFactor"], x["tier"], x["like"] or "(custom)", x["gear"]))


if __name__ == "__main__":
    main()
