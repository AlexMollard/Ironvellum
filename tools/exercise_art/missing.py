#!/usr/bin/env python3
"""Writes the brief for every drawing the free sets do not have: tools/exercise_art/missing.json and docs/art/missing-exercise-art.md.

    python -P tools/exercise_art/missing.py            (from tools/exercise_art/.venv)

The gaps are every catalogue movement and Codex technique with no exact drawing: techniques on a proxy drawing or the
pictogram, and movements on the placeholder. Pose text comes from poses.json (reviewable data); the style guide below was
based on the approved generated illustrations in owner/.
build.py reads missing.json to map owner-made files in owner/ to the movement they fill, so it is regenerated, never edited.
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build  # noqa: E402

ROOT = HERE.parents[1]
KT = ROOT / "app" / "src" / "main" / "kotlin" / "com" / "ironvellum" / "app"
DOC = ROOT / "docs" / "art" / "missing-exercise-art.md"

REFERENCES = [
    ("goblet-squat-1.png", "front view, standing and squatting figure: the reference for anatomy, head, shorts, hands and line weight"),
    ("front-foot-elevated-split-squat-1.png", "three-quarter view with a bench: the reference for how furniture and a loaded dumbbell are drawn"),
    ("push-up-1.png", "side view on the floor: the reference for prone figures, long horizontal framing and bare feet"),
    ("dead-hang-1.png", "front view hanging from a bar: the reference for a fixed bar, straight arms and a hanging body"),
    ("l-sit-1.png", "front view on parallel bars: the reference for bars as tubes and a held, supported body"),
    ("handstand-push-up-1.png", "inverted figure: the reference for upside-down anatomy and balance on the hands"),
]

STYLE = {
    "source": "Approved generated Ironvellum illustrations in tools/exercise_art/owner/, inspired by Everkinetic and Workout Guide.",
    "canvas": "Square transparent PNG, at least 512 pixels wide (1024 recommended). Centre the complete figure and equipment with generous margins. No cropping, scenery, floor line, shadow, labels or arrows.",
    "geometry": "Pure white contour lines on transparency, or black on pure white. All enclosed interiors must be empty. No filled regions, grey shading, glow, textures, hatching or doubled outlines. The builder traces the source PNG into vector paths.",
    "lineWeight": "One even weight of about 4 units on the 512 canvas (0.8% of its width), the same for the body outline, the muscle lines and "
                  "the equipment. Contours are closed, slightly hand-inked (a gentle wobble, tapered line ends), never mechanical vector "
                  "strokes. Muscle-separation marks and fabric folds are short tapered strokes of the same weight, never filled shading or hatching.",
    "figure": "One athletic, muscular adult male, heroic but anatomical proportions (about 7.5 heads tall). The head is a smooth FACELESS egg-shaped "
              "oval: no eyes, nose, mouth, hair or ears, only a neck line where it joins the trapezius. Defined deltoids, pecs, a six-pack with "
              "serratus lines, forearm and calf muscle separations, drawn as short contour lines, not shaded. Hands show the fingers as separate "
              "contour shapes; feet are bare with toe lines visible. He wears plain "
              "mid-thigh shorts with a few fold lines and nothing else (no shirt, no belt, no wristbands).",
    "equipment": "Drawn in the same single line weight, outline only, no fill and no shading. Barbells are a straight bar with plate discs as nested "
                 "ellipses at each end and a short collar; dumbbells have two rounded or hexagonal heads on a short handle; benches are a flat or "
                 "inclined pad outline on two or four tubular legs; pull-up bars, parallettes and dip bars are plain tubes with end caps; machines "
                 "are drawn as tubular frames with a seat pad, weight stack as stacked slabs, and cables as single thin lines; rings are two "
                 "circles on straps hung from above. Only the equipment the movement needs is drawn.",
    "views": "Chosen per exercise as in a classic exercise guide: front, side profile (facing either way), three-quarter front or back, or back. "
             "The camera never moves between the frames of one exercise: the same view, the same figure size, the same equipment position; "
             "only the athlete moves (and the bar/rings stay put for a hang).",
    "frames": "One representative still per exercise, numbered 1. A hold shows the full hold; a repetition shows a clear defining position. "
              "Frame-count overrides in frame-counts.json are authoritative. No animation frames are needed for a still.",
}

BASE_PROMPT = (
    "Black-and-white exercise-guide line illustration in the classic anatomical-guide style, drawn as a clean outline: a single uniform line "
    "weight of about 0.8% of the image width, slightly hand-inked contours with tapered ends, white lines on a fully transparent background "
    "(or, if transparency is unavailable, black lines on pure white). Square 1:1 canvas; the whole figure and its equipment are centred, "
    "fill about 70-80% of the canvas, and are never cropped; no floor line, shadow, shading, hatching, fills, gradients, colour, text, "
    "arrows, labels or motion lines. One athletic, muscular adult male with realistic anatomical proportions and a smooth faceless "
    "egg-shaped head (no eyes, nose, mouth, hair or ears), defined deltoids, pecs, abs with serratus lines and forearm and calf muscle "
    "separations drawn as short contour lines only, fingers drawn as separate shapes, bare feet with toe lines, plain mid-thigh shorts "
    "with a few fold lines, no shirt. Any equipment is drawn in the same single line weight, outline only: barbells as a straight bar "
    "with plates shown as nested ellipses, dumbbells with two rounded heads, benches as a padded outline on tubular legs, bars and "
    "parallettes as plain tubes, rings as two circles on straps, machines as tubular frames with single thin cables. The camera is "
    "fixed: the same view, figure size and equipment position in every frame of the exercise."
)

FRAME_WORD = {False: ("start", "mid", "end"), True: ("set-up", "arriving", "full hold")}
LINE_ORDER = ["Planche", "Lever", "Handstand", "Rings", "Movement", "Pull", "Push", "Core", "Mobility", "Legs", "Squat", "Bench", "Press", "Deadlift"]


def slugify(name):
    return build.slugify(name)


def gaps():
    lib, mp, _ = build.load()
    report = json.loads(build.REPORT.read_text(encoding="utf-8"))
    drawn = {k.lower() for k in build.art_table(lib, mp, report)}
    seed = (KT / "data" / "Seed.kt").read_text(encoding="utf-8")
    skills = re.findall(r'SkillDef\(\s*"([^"]+)",\s*(\d+),\s*"([^"]+)"', (KT / "domain" / "Skills.kt").read_text(encoding="utf-8"))
    skill_names = {s[0] for s in skills}
    out = []
    for name, tier, line in skills:
        if name.lower() not in drawn:
            t = mp["techniques"].get(name)
            now = "proxy drawing of %s" % t["exercise"] if t and t.get("proxy") else "pictogram"
            out.append({"name": name, "kind": "technique", "priority": 1, "line": line, "tier": int(tier), "currently": now})
    for name, grp in re.findall(r'ExerciseEntity\(name = "([^"]+)", muscleGroup = MuscleGroup\.(\w+)', seed):
        if name not in skill_names and name.lower() not in drawn:
            out.append({"name": name, "kind": "movement", "priority": 2, "group": grp, "currently": "placeholder"})
    for name, grp in re.findall(r'activity\("([^"]+)",\s*"([^"]+)"', seed):
        if name.lower() not in drawn:
            out.append({"name": name, "kind": "activity", "priority": 3, "group": grp, "currently": "placeholder"})
    out.sort(key=lambda e: (e["priority"], LINE_ORDER.index(e["line"]) if e.get("line") in LINE_ORDER else 99, e.get("tier", 0), e["name"]))
    return out


def prompt_for(e, pose, frame=None, still=False):
    words = FRAME_WORD[bool(pose["hold"])]
    lines = [BASE_PROMPT, "",
             "Subject: %s (%s). View: %s. Equipment: %s." % (e["name"], "a static hold" if pose["hold"] else "a repetition movement",
                                                              pose["view"], pose["equipment"]),
             ""]
    for i, f in enumerate(pose["frames"]):
        if frame is None or frame == i:
            lines.append("Representative still: " + f if still else "Frame %d of 3 (%s): %s" % (i + 1, words[i], f))
    lines.append("Must get right: " + pose["note"])
    if still:
        lines += ["", "Draw ONLY this representative still as one standalone image. No animation or additional frames."]
    elif frame is None:
        lines += ["", "Draw ONLY frame N of 3 now (N = 1, then 2, then 3 as separate images), keeping the athlete's proportions, the camera "
                      "angle, the figure size and the equipment position identical across the three."]
    else:
        lines += ["", "Draw ONLY this frame, as one image; it is frame %d of 3 of one exercise, so keep the athlete's proportions, the camera "
                      "angle and the figure size exactly as in the other frames." % (frame + 1)]
    return "\n".join(lines)


def main():
    poses = json.loads((HERE / "poses.json").read_text(encoding="utf-8"))
    entries = []
    counts_path = HERE / "frame-counts.json"
    frame_counts = json.loads(counts_path.read_text(encoding="utf-8")) if counts_path.exists() else {}
    for e in gaps():
        p = poses[e["name"]]
        slug = slugify(e["name"])
        owner = HERE / "owner"
        filled = any((owner / ("%s-1.%s" % (slug, ext))).exists() for ext in ("svg", "png"))
        count = frame_counts.get(slug, 1)
        indices = [2] if count == 1 else list(range(count))
        entries.append(dict(e, slug=slug, filled=filled, view=p["view"], equipment=p["equipment"], hold=p["hold"],
                            hanging=p["hanging"], frameCount=count, files=["%s-%d.png" % (slug, i + 1) for i in range(count)],
                            poses=[p["frames"][i] for i in indices], note=p["note"], prompt=prompt_for(e, p, indices[0], still=count == 1),
                            prompts=[prompt_for(e, p, i, still=count == 1) for i in indices]))
    doc = {
        "about": "Every catalogue movement and Codex technique with no exact drawing. Drop owner-made frames in tools/exercise_art/owner/ "
                 "named by `files` (or the same names with .png), then run build.py --owner-only. Generated by missing.py; do not edit.",
        "style": STYLE,
        "basePrompt": BASE_PROMPT,
        "references": [{"file": "tools/exercise_art/owner/" + r, "why": w} for r, w in REFERENCES],
        "fileConvention": {
            "folder": "tools/exercise_art/owner/",
            "names": "<slug>-1.png for the approved representative still; frame-counts.json defines required frames",
            "svg": "any SVG; it is rendered at 1024 px wide and its ALPHA is the ink, so line colour does not matter",
            "png": "RGBA with a transparent background and at least 512 px wide (1024 recommended); an opaque PNG is read as dark-on-light "
                   "or light-on-dark from its corners",
            "framing": "the same canvas size and the same camera for every frame of an exercise; the build registers the frames to one ground line, centre and scale",
        },
        "counts": {
            "total": len(entries),
            "priority1_codexTechniques": sum(1 for e in entries if e["priority"] == 1),
            "priority2_movements": sum(1 for e in entries if e["priority"] == 2),
            "priority3_activities": sum(1 for e in entries if e["priority"] == 3),
        },
        "entries": entries,
    }
    (HERE / "missing.json").write_bytes((json.dumps(doc, indent=1, ensure_ascii=False) + chr(10)).replace(chr(10), chr(13) + chr(10)).encode("utf-8"))
    DOC.write_bytes(render_md(doc).replace(chr(10), chr(13) + chr(10)).encode("utf-8"))
    print(doc["counts"])


def render_md(doc):
    s = doc["style"]
    out = ["# Missing exercise art", "",
           "The original gap-art brief, now completed with approved generated stills. Keep new illustrations in the same approved house style. Generated by "
           "`tools/exercise_art/missing.py` from `poses.json`; the machine-readable twin is `tools/exercise_art/missing.json`.", "",
           "Original brief: %d drawings: %d Codex techniques (priority 1), %d gym movements (priority 2), %d sports and classes (priority 3)." % (
               doc["counts"]["total"], doc["counts"]["priority1_codexTechniques"], doc["counts"]["priority2_movements"],
               doc["counts"]["priority3_activities"]), "",
           "## How to hand art back", "",
           "1. Generate one representative still per exercise: a full hold or clear defining repetition position.",
           "2. Save pending images in `tools/exercise_art/drafts/batch-NNN/<slug>-1.png` for local review (RGBA, transparent background, 512 px or wider, 1024 recommended).",
           "3. Only after user approval, move the still into `tools/exercise_art/owner/`, record its approval hash and list its slug in `owner/APPROVED`. Run `tools/exercise_art/.venv/Scripts/python -P tools/exercise_art/build.py --owner-only`, then `build.py --check`. Move audit JSON to `reviews/batch-NNN/` and delete all draft images. The builder traces the source PNG and regenerates the vector and Kotlin index.",
           "4. Rebuild the app. Owner-made art is the project's own and is recorded as such in `docs/ART_ATTRIBUTION.md`.", "",
           "## Style guide", "",
           "Use the approved generated Ironvellum PNGs below as the style references. Original upstream drawings and animations have been replaced.", ""]
    for k, label in (("canvas", "Canvas and framing"), ("geometry", "What the file is"), ("lineWeight", "Line weight"), ("figure", "The figure"),
                     ("equipment", "Equipment"), ("views", "Views"), ("frames", "Frames")):
        out += ["**%s.** %s" % (label, s[k]), ""]
    out += ["### Style references to attach", "",
            "Attach these approved PNGs from `tools/exercise_art/owner/` as style references:", ""]
    for r in doc["references"]:
        out.append("- `%s`: %s" % (r["file"], r["why"]))
    out += ["", "### Base prompt", "", "Paste this first in every prompt (each entry's full prompt below already includes it):", "", "```", doc["basePrompt"], "```", ""]
    groups = {1: "Priority 1: Codex techniques (calisthenics skills)", 2: "Priority 2: gym and conditioning movements",
              3: "Priority 3: sports, classes and the long tail"}
    for pr in (1, 2, 3):
        es = [e for e in doc["entries"] if e["priority"] == pr]
        out += ["## %s (%d)" % (groups[pr], len(es)), ""]
        for e in es:
            out += ["### %s%s" % (e["name"], " (done)" if e["filled"] else ""), "",
                    "- Slug: `%s`. Files: %s. Now shows: %s." % (e["slug"], ", ".join("`%s`" % f for f in e["files"]), e["currently"]),
                    "- View: %s. Equipment: %s. %s." % (e["view"], e["equipment"], "Static hold" if e["hold"] else "Repetition"),
                    *["- Frame %d: %s" % (i + 1, pose) for i, pose in enumerate(e["poses"])],
                    "- Must get right: " + e["note"], "", "Prompt:", "", "```", e["prompt"], "```", ""]
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    main()
