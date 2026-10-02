#!/usr/bin/env python
"""Turns the react-native-body-highlighter body shapes into the muscle-map figure data.

Source: react-native-body-highlighter (MIT, Copyright (c) 2022 ELABBASSI Hicham), "main as fetched 2026-10-02":
assets/bodyFront.ts, bodyBack.ts, bodyFemaleFront.ts, bodyFemaleBack.ts (the muscle and head shapes) and
components/SvgMaleWrapper.tsx, SvgFemaleWrapper.tsx (only their hand-drawn border paths and viewBoxes).
They are fetched by hand into .tmp/bodymap/ (gitignored) and never committed; only the shapes this tool
derives from them are, as ui/program/BodyMapShapes.kt.

What it does, per body (male, female):
  1. Parses the SVG path data (tools/bodymap_svg.py), flattens the curves and normalises into figure
     space: x is the half-width from the body midline, y runs from the crown (0) to the soles (1).
     Only the half on the positive-x side is kept; the app mirrors it.
  2. Names the parts our Muscle enum needs. Most are the source part one to one. Where the app has
     finer muscles than the source, the source part is cut (chest along a fan from the armpit, the
     deltoid into front and side, the rib slips out of the obliques, the scapular pieces out of
     upper-back, the medius out of gluteal) or inlaid (rhomboids, brachialis, hip flexors).
  3. Takes the source's own border path as the body's outline (a human skin line that passes over the
     muscles, not round each lobe of them), then keeps every region inside it by a margin. The hair is
     kept apart as decoration: it is never a muscle region.
  4. Emits ui/program/BodyMapShapes.kt: packed strings of integer pairs, decoded at load, because Kotlin
     list literals of this size overflow the JVM's 64KB method limit.

Needs shapely (pip install shapely). Usage:
    python tools/bodymap_import.py                    # both bodies -> BodyMapShapes.kt
    python tools/bodymap_import.py --bodies male      # one body
    python tools/bodymap_import.py --src .tmp/bodymap --out <file>
    python tools/bodymap_import.py --report           # measurements only, no file

The output is deterministic: running it twice gives byte-identical files.
"""
import argparse
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodymap_svg import flatten  # noqa: E402

from shapely import affinity  # noqa: E402
from shapely.geometry import MultiPoint, Polygon, box  # noqa: E402
from shapely.ops import unary_union  # noqa: E402

SOURCE_LABEL = "main as fetched 2026-10-02"
OUT = os.path.join("app", "src", "main", "kotlin", "com", "ironvellum", "app", "ui", "program", "BodyMapShapes.kt")
BODIES = {"male": ("bodyFront", "bodyBack", "SvgMaleWrapper"), "female": ("bodyFemaleFront", "bodyFemaleBack", "SvgFemaleWrapper")}
SOURCE_HAIR = {"male": True, "female": False}
# His hair is the source's: a cap that sits above his open border, so the crown is the top of the hair. Her source
# hair is a ponytail that wraps the head in lobes, so her head and hair are authored here instead (decoration, never a
# muscle): an oval face, and shoulder-length centre-parted hair whose outer edge is the body's outline.
# Her face, right half, as control points from the crown: an oval about 1.3x as tall as wide, widest at the cheekbones
# (40% down), then straight-ish jaw lines from the jaw angles (70% down) converging on a rounded chin. y is from the crown.
HEAD = {"female": dict(rx=0.038, face=[(0.0, 0.008), (0.020, 0.0135), (0.0325, 0.027), (0.038, 0.047), (0.0365, 0.064),
                                       (0.0315, 0.078), (0.0265, 0.090), (0.0175, 0.1015), (0.0085, 0.1065), (0.0, 0.1075)])}
# His jaw, only below the cheekbones: squarer and wider than hers, a flatter chin; the source's own face is a single flat piece.
JAW = {"male": [(0.0405, 0.060), (0.0405, 0.075), (0.0385, 0.088), (0.0325, 0.0985), (0.0195, 0.1035), (0.0, 0.105)]}
CHIN_SHADOW = 0.0065   # the dark crescent under the chin, where the jaw lies over the neck
# Her hair, right half of its outer edge from the crown to the ends: volume over the temples, easing in to rounded
# ends that reach the shoulder line, so the outline runs from the hair into the shoulder with no gap.
HAIR_EDGE = {"female": [(0.0, 0.0), (0.02, 0.001), (0.040, 0.009), (0.0515, 0.025), (0.057, 0.045), (0.0575, 0.065),
                        (0.0555, 0.085), (0.0525, 0.10), (0.0495, 0.115), (0.0465, 0.127), (0.041, 0.1335)]}
HAIR_BACK_END = {"female": [(0.03, 0.1345), (0.015, 0.130), (0.0, 0.126)]}   # the back mass's lower edge, rising behind the neck
# Neck and shoulder slope, right half, as control points (x, y) from the head to where the slope meets the border's
# shoulder. A smooth curve through them replaces the border there: the jaw flows into the neck, the neck narrows a
# little to its middle, and the trapezius leaves it in one long concave curve. Neck widths are 0.72x (his) and
# 0.64x (hers) of the head width; his neck runs to the shoulder line where hers meets it sooner, as her shoulders sit higher.
NECK_CURVE = {
    "male": [(0.0485, 0.070), (0.0465, 0.078), (0.042, 0.086), (0.0375, 0.093), (0.0355, 0.100), (0.0345, 0.108),
             (0.0345, 0.116), (0.0365, 0.124), (0.042, 0.131), (0.050, 0.138), (0.062, 0.146), (0.078, 0.153),
             (0.094, 0.1585), (0.107, 0.164)],
    "female": [(0.0235, 0.090), (0.0235, 0.118), (0.027, 0.124), (0.034, 0.129), (0.045, 0.133), (0.060, 0.1365),
               (0.075, 0.140), (0.091, 0.142)],
}
# The male nape hairline on the back view: a soft curve at the bottom of the ear and jaw, lowest in the middle.
NAPE = lambda x: 0.097 - 0.008 * (min(x, 0.049) / 0.049) ** 2  # noqa: E731

UNIT = 10000          # figure units -> integers
HEAD_HULL = {"male": 0.07, "female": 0.0}  # the hull of the head above this y: joins his hair to his border; fills the dip in her parting
FLATTEN_TOL = 0.05    # source units
SIMPLIFY = 0.00030    # figure units, about a third of a pixel at 360px high
MARGIN = 0.0022       # regions stay this far inside the outline
GAP = 0.0006          # regions made by cutting one part keep this gap between them
MIN_GAP = 0.0004      # every pair of regions on a view is at least this far apart
MIN_AREA = 2.0e-6     # smaller pieces are slivers and dropped

# ---------------------------------------------------------------- parsing

def read_parts(path):
    """[(slug, side, [(x, y) ...])] for every closed subpath in one source file."""
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    parts = []
    chunks = re.split(r'\bslug:\s*"', text)[1:]
    for chunk in chunks:
        slug = chunk[: chunk.index('"')]
        for side in ("left", "right", "common"):
            m = re.search(side + r"\s*:\s*\[(.*?)\]", chunk, re.S)
            if not m:
                continue
            for d in re.findall(r'"([^"]+)"', m.group(1)):
                subs = flatten(d, FLATTEN_TOL)
                moves = len(re.findall(r"[Mm]", d))
                if len(subs) != moves:
                    raise SystemExit(f"{path} {slug}.{side}: {moves} movetos but {len(subs)} subpaths parsed")
                for pts, _closed in subs:
                    parts.append((slug, side, pts))
    return parts


def read_border(path):
    """{view: points} the wrapper's hand-drawn border path for the front and the back, plus its viewBoxes."""
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    paths = re.findall(r'\sd="([^"]+)"', text)
    if len(paths) != 2:
        raise SystemExit(f"{path}: expected a front and a back border path, found {len(paths)}")
    boxes = re.findall(r'"(-?\d+ -?\d+ \d+ \d+)"', text)
    return {"front": flatten(paths[0], FLATTEN_TOL)[0][0], "back": flatten(paths[1], FLATTEN_TOL)[0][0]}, boxes


def mirror(geom):
    return affinity.scale(geom, -1, 1, origin=(0, 0))


def poly(points):
    p = Polygon(points)
    if not p.is_valid:
        p = p.buffer(0)
    return p


def pieces(geom):
    """Every Polygon in a geometry, holes dropped, slivers dropped."""
    if geom.is_empty:
        return []
    if isinstance(geom, Polygon):
        out = [Polygon(geom.exterior)]
    elif hasattr(geom, "geoms"):
        out = [q for g in geom.geoms for q in pieces(g)]
    else:
        out = []
    return [p for p in out if p.area >= MIN_AREA]


def order(polys):
    return sorted(polys, key=lambda p: (round(p.bounds[1], 5), round(p.bounds[0], 5)))


class Body:
    """One body in figure space: views[view][slug] = right-half Polygons, plus the left side for the symmetry check."""

    def __init__(self, name, src):
        self.name = name
        raw = {v: read_parts(os.path.join(src, f + ".ts")) for v, f in zip(("front", "back"), BODIES[name][:2])}
        border, self.view_boxes = read_border(os.path.join(src, BODIES[name][2] + ".tsx"))
        self.mid = {}
        for view, parts in raw.items():
            xs = [p[0] for s, _, pts in parts if s != "hair" for p in pts]
            self.mid[view] = (min(xs) + max(xs)) / 2
        # y runs from the crown (the top of the hair that sits on the head) to the soles (the border's lowest).
        top = min(p[1] for p in border["front"])
        if SOURCE_HAIR[name]:
            top = min([top] + [p[1] for s, _, pts in raw["front"] if s == "hair" for p in pts])
        self.crown = top
        self.scale = 1.0 / (max(p[1] for p in border["front"]) - self.crown)
        self.border = {
            view: poly([((x - self.mid[view]) * self.scale, (y - self.crown) * self.scale) for x, y in pts])
            for view, pts in border.items()
        }
        self.views = {}
        self.left = {}
        for view, parts in raw.items():
            right, left = {}, {}
            for slug, side, pts in parts:
                norm = [((x - self.mid[view]) * self.scale, (y - self.crown) * self.scale) for x, y in pts]
                g = poly(norm)
                if g.is_empty:
                    continue
                if side == "common" or g.bounds[0] < -1e-9 < g.bounds[2]:
                    for q in pieces(g.intersection(box(0, -1, 5, 5))):
                        right.setdefault(slug, []).append(q)
                elif g.centroid.x > 0:
                    right.setdefault(slug, []).append(g)
                else:
                    left.setdefault(slug, []).append(affinity.scale(g, -1, 1, origin=(0, 0)))
            self.views[view] = {s: order(v) for s, v in right.items()}
            self.left[view] = {s: order(v) for s, v in left.items()}

    def symmetry(self):
        """Worst mismatch between each slug's right side and its mirrored left side, as a fraction of its area."""
        worst = []
        for view in ("front", "back"):
            for slug, rs in self.views[view].items():
                ls = self.left[view].get(slug)
                if not ls:
                    continue
                a, b = unary_union(rs), unary_union(ls)
                worst.append((a.symmetric_difference(b).area / max(a.area, 1e-9), view, slug))
        return sorted(worst, reverse=True)


# ---------------------------------------------------------------- geometry helpers

def wedge(apex, a0, a1, reach=1.0):
    """The triangle from [apex] between two rays, angles in radians measured from the medial (-x) direction, y down."""
    def ray(a):
        return (apex[0] - reach * math.cos(a), apex[1] + reach * math.sin(a))
    return Polygon([apex, ray(a0), ray(a1)])


def angle_of(apex, p):
    return math.atan2(p[1] - apex[1], apex[0] - p[0])


def fan_split(shape, apex, fractions):
    """Cut [shape] into len(fractions)+1 bands along rays from [apex], each taking about its fraction of the area."""
    angles = [angle_of(apex, p) for p in shape.exterior.coords]
    lo, hi = min(angles) - 1e-3, max(angles) + 1e-3
    cuts = [lo]
    total = shape.area
    acc = 0.0
    for f in fractions:
        acc += f
        a, b = cuts[-1], hi
        for _ in range(50):
            mid = (a + b) / 2
            if shape.intersection(wedge(apex, lo, mid)).area < acc * total:
                a = mid
            else:
                b = mid
        cuts.append((a + b) / 2)
    cuts.append(hi)
    return [shape.intersection(wedge(apex, cuts[i], cuts[i + 1])) for i in range(len(cuts) - 1)], cuts


def axis_split(shape, fraction, toward):
    """Cut [shape] by a line across its long axis; the first piece takes about [fraction] of the area.

    The axis points the way [toward] does, so the first piece is the end opposite to that.
    """
    pts = list(shape.exterior.coords)
    cx, cy = shape.centroid.x, shape.centroid.y
    sxx = sum((x - cx) ** 2 for x, _ in pts)
    syy = sum((y - cy) ** 2 for _, y in pts)
    sxy = sum((x - cx) * (y - cy) for x, y in pts)
    th = 0.5 * math.atan2(2 * sxy, sxx - syy)
    ax = (math.cos(th), math.sin(th))
    if ax[0] * toward[0] + ax[1] * toward[1] < 0:
        ax = (-ax[0], -ax[1])
    t = [(x - cx) * ax[0] + (y - cy) * ax[1] for x, y in pts]
    a, b = min(t), max(t)
    nx, ny = -ax[1], ax[0]

    def half(cut, sign):
        big = 2.0
        base = (cx + ax[0] * cut, cy + ax[1] * cut)
        p0 = (base[0] + nx * big, base[1] + ny * big)
        p1 = (base[0] - nx * big, base[1] - ny * big)
        p2 = (p1[0] + sign * ax[0] * big, p1[1] + sign * ax[1] * big)
        p3 = (p0[0] + sign * ax[0] * big, p0[1] + sign * ax[1] * big)
        return Polygon([p0, p1, p2, p3])

    for _ in range(50):
        mid = (a + b) / 2
        if shape.intersection(half(mid, -1)).area < fraction * shape.area:
            a = mid
        else:
            b = mid
    cut = (a + b) / 2
    return shape.intersection(half(cut, -1)), shape.intersection(half(cut, 1))


def frame_polygon(host, uv):
    """A polygon given as (u, v) in the host's bounding box (u across, 0..1 from its inner edge; v down), in figure space."""
    x0, y0, x1, y1 = host.bounds
    return Polygon([(x0 + u * (x1 - x0), y0 + v * (y1 - y0)) for u, v in uv])


# ---------------------------------------------------------------- the parts

def build_parts(body, outline):
    """{view: {key: [Polygon]}} named for the app's muscles; see the module docstring."""
    out = {}
    for view in ("front", "back"):
        src = body.views[view]
        parts = {}

        def take(key, slug, shapes=None):
            parts[key] = list(src.get(slug, [])) if shapes is None else shapes

        if view == "front":
            chest = src["chest"][0]
            ax = max(p[0] for p in chest.exterior.coords)
            near = [p for p in chest.exterior.coords if p[0] >= ax - 0.002]
            apex = (ax, sum(p[1] for p in near) / len(near))
            bands, cuts = fan_split(chest, apex, (0.34, 0.33))
            for key, band in zip(("chest.upper", "chest.mid", "chest.lower"), bands):
                parts[key] = [b.buffer(-GAP / 2) for b in pieces(band)]
            body.apex = apex
            body.fan_cuts = cuts

            obliques = src["obliques"]
            chest_bottom = max(p[1] for p in chest.exterior.coords)
            slips = [o for o in obliques if o.bounds[1] < chest_bottom + 0.02]
            parts["serratus"] = slips
            parts["obliques"] = [o for o in obliques if o not in slips]
            take("abs", "abs")
            take("neck", "neck")
            delts = sorted(src["deltoids"], key=lambda p: p.centroid.x)
            if len(delts) == 2:  # the female source already separates the front head from the lateral cap
                parts["deltoids.front"], parts["deltoids.side"] = [delts[0]], [delts[1]]
            else:
                front, side = axis_split(delts[0], 0.55, toward=(1.0, 1.0))
                parts["deltoids.front"] = [b.buffer(-GAP / 2) for b in pieces(front)]
                parts["deltoids.side"] = [b.buffer(-GAP / 2) for b in pieces(side)]
            biceps = src["biceps"][0]
            brachialis = biceps.intersection(frame_polygon(biceps, BRACHIALIS_UV))
            rest = biceps.difference(frame_polygon(biceps, BRACHIALIS_UV))
            parts["biceps"] = [b.buffer(-GAP / 2) for b in pieces(rest)]
            parts["brachialis"] = [b.buffer(-GAP / 2) for b in pieces(brachialis)]
            take("triceps", "triceps")
            take("forearm", "forearm")
            take("trapezius", "trapezius")
            take("adductors", "adductors")
            take("quadriceps", "quadriceps")
            take("tibialis", "tibialis")
            take("calves", "calves")
            parts["hip-flexors"] = hip_flexors(parts, outline)
        else:
            traps = src["trapezius"][0]
            rhomb = traps.intersection(frame_polygon(traps, RHOMBOIDS_UV))
            rest = traps.difference(frame_polygon(traps, RHOMBOIDS_UV))
            parts["trapezius"] = [b.buffer(-GAP / 2) for b in pieces(rest)]
            parts["rhomboids"] = [b.buffer(-GAP / 2) for b in pieces(rhomb)]
            upper = sorted(src["upper-back"], key=lambda p: -p.area)
            parts["lats"] = upper[:1]
            parts["rotator-cuff"] = upper[1:]
            take("deltoids.rear", "deltoids")
            take("neck", "neck")
            take("triceps", "triceps")
            take("forearm", "forearm")
            take("lower-back", "lower-back")
            glutes = sorted(src["gluteal"], key=lambda p: -p.area)
            parts["gluteal"] = glutes[:1]
            parts["abductors"] = glutes[1:]
            take("hamstring", "hamstring")
            take("adductors", "adductors")
            take("calves", "calves")
        out[view] = {k: order([p for p in v if p.area >= MIN_AREA]) for k, v in parts.items()}
    return out


def hip_flexors(parts, outline):
    """The groove between the lower obliques, the abs and the inner thigh, where the hip flexors run.

    There is no source part for it: it is the free space inside the silhouette beside the abs tip, between
    the slab of the obliques and the adductors, kept off everything around it.
    """
    slab = max(parts["obliques"], key=lambda p: p.bounds[3])
    abs_bottom = max(p.bounds[3] for p in parts["abs"])
    zone = box(0.012, slab.bounds[3] - 0.012, slab.bounds[2] + 0.012, abs_bottom + 0.012)
    used = unary_union([p for v in parts.values() for p in v]).buffer(0.003)
    free = zone.intersection(outline.buffer(-MARGIN)).difference(used)
    free = free.buffer(-0.002).buffer(0.002)
    found = pieces(free)
    if not found:
        raise SystemExit("no room for the hip flexors")
    return [max(found, key=lambda p: p.area)]


# Hand shapes for what the source has no part for, in the bounding box of the part they sit in
# (u across from the inner edge, v down). Refitted to every body by construction.
BRACHIALIS_UV = [(1.2, 0.34), (0.66, 0.50), (0.60, 0.76), (0.72, 1.2), (1.2, 1.2)]
RHOMBOIDS_UV = [(0.24, 0.40), (1.2, 0.34), (1.2, 0.72), (0.30, 0.76)]


# ---------------------------------------------------------------- the outline

def border_outline(body):
    """The body's right-half outline Polygon: the source's hand-drawn skin line, front and back together.

    The male border is an open path (it stops at the hairline), so it is closed with a straight line. The
    hair that sits on the head, above the crown line, joins both: the hair is part of the person's outline.
    The back border of the female has no head, so the front's head is what both views share.
    """
    shapes = [body.border["front"], body.border["back"]]
    half = unary_union(shapes).intersection(box(0, -1, 5, 5))
    hair = [h.intersection(box(0, 0, 5, HAIR_BOTTOM[body.name])) for h in body.views["front"].get("hair", [])] if SOURCE_HAIR[body.name] else []
    whole = unary_union([half] + hair)
    full = unary_union([whole, mirror(whole)])
    if body.name in NECK_CURVE:
        curve = smooth_curve(NECK_CURVE[body.name])
        slab = Polygon(curve + [(0, curve[-1][1]), (0, curve[0][1])])
        parts = [full.intersection(box(-5, curve[-1][1], 5, 5)), slab, mirror(slab)]
        if body.name in HEAD:
            parts += [head_shape(body.name), hair_mass(body.name)]
        else:
            parts.append(full.intersection(box(-5, -1, 5, curve[0][1])))
        full = unary_union(parts)
        if body.name in HEAD:   # close the small crease where the hair ends meet the shoulder slope
            band = full.intersection(box(-5, 0.1, 5, 0.15))
            full = unary_union([full, band.buffer(0.006, 16).buffer(-0.006, 16)])
    # The female border dips at the parting, so the crown is the hull of the top of the head; and a skull
    # is not a flat-topped box, so the head's corners are rounded. Both are done on the whole body, not
    # the half, because opening a half would also erode its cut edge on the midline.
    top = [p for p in full.exterior.coords if p[1] <= HEAD_HULL[body.name]]
    if len(top) > 2:
        full = unary_union([full, MultiPoint(top).convex_hull])
    head = box(-5, -1, 5, 0.085)
    rounded = full.intersection(head).buffer(-0.006, 16).buffer(0.006, 16)
    full = unary_union([full.intersection(box(-5, 0.075, 5, 5)), rounded])
    half = full.intersection(box(0, -1, 5, 5))
    return max(pieces(half), key=lambda p: p.area)


# ---------------------------------------------------------------- hair

def smooth_curve(pts, per=10):
    """A Catmull-Rom curve through the control points (the ends are repeated), as a list of points."""
    q = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(q) - 2):
        p0, p1, p2, p3 = q[i - 1], q[i], q[i + 1], q[i + 2]
        for k in range(per):
            s = k / per
            out.append(tuple(0.5 * (2 * p1[j] + (-p0[j] + p2[j]) * s + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * s * s
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * s ** 3) for j in (0, 1)))
    out.append(tuple(pts[-1]))
    return out


def head_shape(name):
    """Her face, whole: an oval with a clear jaw line and a rounded chin."""
    half = Polygon(smooth_curve(HEAD[name]["face"]) + [(0, HEAD[name]["face"][-1][1]), (0, HEAD[name]["face"][0][1])])
    return unary_union([half, mirror(half)])


def hair_mass(name, view="front"):
    """Her whole hair, both sides: one smooth mass from above the crown to the ends. Its edge is the outline."""
    half = Polygon(HAIR_EDGE[name] + HAIR_BACK_END[name])
    whole = unary_union([half, mirror(half)])
    return whole.buffer(0.01, 32).buffer(-0.01, 32).buffer(-0.014, 32).buffer(0.014, 32)


def authored_hair(name, view):
    """Her hair, right half, as a decoration. Behind the figure it is the whole mass; in front it frames the face.

    From the front the hairline sweeps from the forehead down to the temples and two panels fall behind the jaw and
    beside the neck to the ends, hiding the ears. Authored, not from the source.
    """
    mass = hair_mass(name).intersection(box(0, -1, 5, 5))
    if view == "back":
        return [mass]
    rx = HEAD[name]["rx"]
    xs = [rx * 1.3 * k / 40 for k in range(41)]
    line = [(x, 0.026 + 0.032 * (min(x, rx) / rx) ** 2.2) for x in xs]
    below = Polygon(line + [(xs[-1], 0.3), (0, 0.3)])
    face = head_shape(name).intersection(box(0, -1, 5, 5)).intersection(below)
    nw = NECK_CURVE[name][0][0]   # the panels' inner edge leaves the jaw and widens a little toward the ends, which taper
    neck = Polygon([(0, 0.09), (nw, 0.09), (nw, 0.10), (nw + 0.009, 0.136), (nw + 0.009, 0.3), (0, 0.3)])
    return [mass.difference(unary_union([face, neck]))]


def chin_shadow(body):
    """The dark crescent just under the chin, front view: the jaw is the lower edge of the face and lies over the
    neck, which is narrower and starts under it. Both skin, so this is what shows the jaw edge crossing the neck."""
    if body.name in HEAD:
        face = head_shape(body.name).intersection(box(0, -1, 5, 5))
    else:
        half = Polygon(smooth_curve(JAW[body.name]) + [(0, JAW[body.name][-1][1]), (0, JAW[body.name][0][1])])
        face = half
    neck = min(x for x, y in NECK_CURVE[body.name] if y >= 0.1)   # the shadow lies on the neck, so no wider than it
    lower = affinity.translate(face, 0, CHIN_SHADOW).difference(face).intersection(box(0, 0.075, neck, 0.2))
    return pieces(lower)


HAIR_BOTTOM = {"male": 0.105, "female": 0.138}   # his hair stops at the nape; her ends are above the shoulders


def hair_shapes(body, outline, fitted):
    """The source's hair, per view: the part on the head, inside the outline, off the face and every region.

    It is decoration, not a muscle: the app draws it in the darkest ink and never colours it.
    """
    out = {}
    for view in ("front", "back"):
        face = unary_union(body.views[view].get("head", [])) if SOURCE_HAIR[body.name] else Polygon()
        sources = body.views[view].get("hair", []) if SOURCE_HAIR[body.name] else authored_hair(body.name, view)
        if body.name == "male" and view == "back":   # his nape hairline is authored: down to the earlobe, a soft curve
            xs = [0.05 * k / 20 for k in range(21)]
            above = Polygon([(0, -1), (0.05, -1)] + [(x, NAPE(x)) for x in reversed(xs)])
            sources, face = [outline.intersection(above)], Polygon()
        # Hers is drawn behind the muscles (the neck and traps draw over its ends), so nothing is cut from it.
        used = unary_union([p for ps in fitted[view].values() for p in ps] + [face]).buffer(MIN_GAP) if SOURCE_HAIR[body.name] else Polygon()
        shapes = []
        for h in sources:
            shapes += pieces(h.intersection(outline).intersection(box(0, 0, 5, HAIR_BOTTOM[body.name])).difference(used))
        if view == "front":
            muscles = unary_union([p for ps in fitted[view].values() for p in ps]).buffer(MIN_GAP)
            for c in chin_shadow(body):
                shapes += pieces(c.intersection(outline).difference(muscles).difference(unary_union(shapes) if shapes else Polygon()))
        out[view] = [s.simplify(SIMPLIFY, preserve_topology=True) for s in order(shapes)]
    return out


# ---------------------------------------------------------------- fitting

def fit(parts, outline, report):
    """Trim every part inside the outline's margin and keep a gap between every pair on a view."""
    inner = outline.buffer(-MARGIN)
    fitted = {}
    for view, keyed in parts.items():
        flat = []
        for key in sorted(keyed):
            before = sum(p.area for p in keyed[key])
            kept = 0.0
            for p in keyed[key]:
                for q in pieces(p.intersection(inner)):
                    flat.append([key, q])
                    kept += q.area
            if before and kept < 0.97 * before:
                report.append(f"{view}: {key} lost {100 * (1 - kept / before):.1f}% of its area to the outline margin")
        for i in range(len(flat)):
            for j in range(i):
                a, b = flat[j][1], flat[i][1]
                if a.is_empty or b.is_empty or a.distance(b) >= MIN_GAP:
                    continue
                overlap = a.intersection(b).area
                if overlap > 1e-6:
                    report.append(f"{view}: {flat[j][0]} and {flat[i][0]} overlap by {overlap:.6f}")
                left = pieces(b.difference(a.buffer(MIN_GAP)))
                flat[i][1] = max(left, key=lambda p: p.area) if left else Polygon()
        out = {}
        for key, p in flat:
            if p.is_empty:
                report.append(f"{view}: a piece of {key} vanished")
                continue
            out.setdefault(key, []).append(p.simplify(SIMPLIFY, preserve_topology=True))
        fitted[view] = {k: order(v) for k, v in out.items()}
    return fitted


def half_chain(outline):
    """The right half of the outline as an open chain from the crown to the crotch, both on the midline."""
    ring = list(outline.exterior.coords)[:-1]
    on = [i for i, (x, _) in enumerate(ring) if x <= 5e-4]
    top = min(on, key=lambda i: ring[i][1])
    bottom = max(on, key=lambda i: ring[i][1])
    n = len(ring)
    for step in (1, -1):
        chain = [ring[top]]
        i = (top + step) % n
        while i != bottom:
            chain.append(ring[i])
            i = (i + step) % n
        chain.append(ring[bottom])
        if len(chain) > 2 and all(x > 5e-4 for x, _ in chain[1:-1]):
            return chain
    raise SystemExit("could not walk the outline half")


# ---------------------------------------------------------------- measuring

def armpit(outline):
    """(y, x) where the arm first parts from the torso: the first row with three edges right of the midline."""
    ring = list(outline.exterior.coords)
    for k in range(190, 600):
        y = k / 1000
        xs = sorted(
            x0 + (y - y0) * (x1 - x0) / (y1 - y0)
            for (x0, y0), (x1, y1) in zip(ring, ring[1:])
            if (y0 > y) != (y1 > y) and max(x0, x1) > 1e-6
        )
        xs = [x for x in xs if x > 1e-6]
        if len(xs) >= 3:
            return y, xs[1]
    return None


def measure(body, outline, fitted):
    lines = []
    chain = half_chain(outline)
    crotch = chain[-1][1]
    head = crotch / 4
    lines.append(f"{body.name}: crown {body.crown:.1f} scale 1/{1 / body.scale:.1f} mid {body.mid}")
    lines.append(f"  crotch {crotch:.4f} head {head:.4f} nipple {2 * head:.4f} navel {3 * head:.4f} armpit {armpit(outline)}")
    for view, keyed in fitted.items():
        for key, ps in keyed.items():
            b = unary_union(ps).bounds
            lines.append(f"  {view:5} {key:16} x {b[0]:.3f}-{b[2]:.3f} y {b[1]:.3f}-{b[3]:.3f} pieces {len(ps)}")
    return lines


# ---------------------------------------------------------------- emitting

def pack(polys):
    """Polygons as 'x y x y ...' integer pairs in figure units, joined with '|'."""
    out = []
    for g in polys:
        pts = []
        for x, y in list(g.exterior.coords)[:-1]:
            q = (max(0, round(x * UNIT)), round(y * UNIT))
            if not pts or pts[-1] != q:
                pts.append(q)
        if len(pts) > 1 and pts[0] == pts[-1]:
            pts.pop()
        if len(pts) >= 3:
            out.append(" ".join(f"{x} {y}" for x, y in pts))
    return "|".join(out)


def pack_chain(chain):
    return " ".join(f"{max(0, round(x * UNIT))} {round(y * UNIT)}" for x, y in chain)


HEADER = """// Muscle-map body shapes. GENERATED by tools/bodymap_import.py - do not edit.
//
// Derived from the body shapes of react-native-body-highlighter
// (https://github.com/HichamELBSI/react-native-body-highlighter), "{label}".
// MIT License, Copyright (c) 2022 ELABBASSI Hicham. The licence text ships with the app
// (see OpenSourceNotices) and in THIRD_PARTY_NOTICES.md.
//
// Figure space: x is the half-width from the body midline, y runs from the crown (0) to the soles (1),
// both in units of 1/{unit} of the figure's height. Only the right half is stored; the app mirrors it.
// Each part is polygons of "x y x y ..." integer pairs, joined by '|'. Packed strings, because list
// literals this size overflow the JVM's 64KB method limit.
package com.ironvellum.app.ui.program
"""


def emit(bodies):
    lines = [HEADER.format(label=SOURCE_LABEL, unit=UNIT).rstrip("\n"), "", "internal object BodyMapShapes {"]
    names = list(bodies)
    for n, name in enumerate(names):
        chain, fitted, hair = bodies[name]
        lines.append(f"    val {name.upper()} = BodyShapes(")
        lines.append(f'        outline = "{pack_chain(chain)}",')
        lines.append(f'        hairFront = "{pack(hair["front"])}",')
        lines.append(f'        hairBack = "{pack(hair["back"])}",')
        for view in ("front", "back"):
            lines.append(f"        {view} = mapOf(")
            for key in sorted(fitted[view]):
                lines.append(f'            "{key}" to "{pack(fitted[view][key])}",')
            lines.append("        ),")
        lines.append("    )")
        if n < len(names) - 1:
            lines.append("")
    lines.append("}")
    return "\r\n".join(lines).replace("\r\n", "\n").replace("\n", "\r\n") + "\r\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=os.path.join(".tmp", "bodymap"))
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--bodies", default="male,female")
    ap.add_argument("--report", action="store_true")
    a = ap.parse_args()
    done = {}
    for name in [b.strip() for b in a.bodies.split(",") if b.strip()]:
        body = Body(name, a.src)
        worst = body.symmetry()[:3]
        print(f"{name}: worst left/right mismatch " + ", ".join(f"{s} {v} {r:.3f}" for r, v, s in worst))
        outline = border_outline(body).simplify(SIMPLIFY, preserve_topology=True)
        parts = build_parts(body, outline)
        notes = []
        fitted = fit(parts, outline, notes)
        for n in notes:
            print("  note:", n)
        for line in measure(body, outline, fitted):
            print(line)
        done[name] = (half_chain(outline), fitted, hair_shapes(body, outline, fitted))
    if not a.report:
        text = emit(done)
        with open(a.out, "w", encoding="utf-8", newline="") as fh:
            fh.write(text)
        print(f"wrote {a.out} ({len(text)} bytes)")


if __name__ == "__main__":
    main()
