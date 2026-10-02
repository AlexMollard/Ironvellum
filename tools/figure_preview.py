#!/usr/bin/env python
"""Offline preview of the muscle-map figure, so region shapes can be looked at without an emulator.

Reads the generated ui/program/BodyMapShapes.kt (packed integer pairs, right half only) and, when it
exists, the key -> Muscle table in ui/program/BodyFigures.kt, and draws what the app draws: the outline,
every region, the right half mirrored across the midline, a seam round every region. Each region gets a
distinct colour and its muscle (or part key) name; --highlight shows only some muscles in the app's green.

Usage:
    python tools/figure_preview.py                        # large labelled front + back, both bodies -> .tmp/shots/
    python tools/figure_preview.py --body female          # one body
    python tools/figure_preview.py --height 360           # in-app size (about 120dp tall at 3x)
    python tools/figure_preview.py --silhouette           # the bare outline, no muscles
    python tools/figure_preview.py --highlight BICEPS,LATS,FOREARMS
    python tools/figure_preview.py --tag after            # file names get a suffix
    python tools/figure_preview.py --check                # inside-outline / overlap report, no images
    python tools/figure_preview.py --landmarks            # outline half-width table

Output: .tmp/shots/figure_<body>_<tag>_front.png, _back.png, _both.png (never committed; .tmp is ignored).
"""
import argparse
import colorsys
import math
import os
import re
import sys

PROGRAM = os.path.join("app", "src", "main", "kotlin", "com", "ironvellum", "app", "ui", "program")
SHAPES = os.path.join(PROGRAM, "BodyMapShapes.kt")
FIGURES = os.path.join(PROGRAM, "BodyFigures.kt")
UNIT = 10000.0


def read(path: str) -> str:
    with open(path, encoding="utf-8", newline="") as fh:
        return fh.read().replace("\r\n", "\n")


def unpack(text):
    nums = [int(n) / UNIT for n in text.split()]
    return list(zip(nums[0::2], nums[1::2]))


def parse_shapes(path: str):
    """{body: (outline_half, {view: [(key, [polygon, ...])]}, {view: [hair polygon, ...]})}"""
    text = read(path)
    bodies = {}
    for m in re.finditer(r"val (\w+) = BodyShapes\(\n(.*?)\n    \)", text, re.S):
        block = m.group(2)
        outline = unpack(re.search(r'outline = "([^"]*)"', block).group(1))
        views = {}
        for view in ("front", "back"):
            vm = re.search(view + r" = mapOf\(\n(.*?)\n        \),", block, re.S)
            entries = []
            for key, packed in re.findall(r'"([^"]+)" to "([^"]*)"', vm.group(1)):
                entries.append((key, [unpack(p) for p in packed.split("|") if p]))
            views[view] = entries
        hair = {
            view: [unpack(p) for p in re.search("hair" + view.capitalize() + r' = "([^"]*)"', block).group(1).split("|") if p]
            for view in ("front", "back")
        }
        bodies[m.group(1).lower()] = (outline, views, hair)
    return bodies


def key_muscles(path: str):
    if not os.path.exists(path):
        return {}
    return dict(re.findall(r'"([\w.\-]+)"\s+to\s+(?:Muscle\.)?([A-Z_]+)', read(path)))


def full_outline(half):
    return half + [(-x, y) for x, y in reversed(half[1:-1])]


def mirrored(poly):
    return [(-x, y) for x, y in poly]


def inside(poly, pt):
    x, y = pt
    c = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            c = not c
        j = i
    return c


def seg_cross(a, b, c, d):
    def o(p, q, r):
        return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])
    return (o(a, b, c) * o(a, b, d) < 0) and (o(c, d, a) * o(c, d, b) < 0)


def overlaps(p, q):
    for i in range(len(p)):
        for j in range(len(q)):
            if seg_cross(p[i], p[(i + 1) % len(p)], q[j], q[(j + 1) % len(q)]):
                return True
    return inside(p, q[0]) or inside(q, p[0])


def centroid(poly):
    a = cx = cy = 0.0
    for i in range(len(poly)):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % len(poly)]
        w = x0 * y1 - x1 * y0
        a += w
        cx += (x0 + x1) * w
        cy += (y0 + y1) * w
    return (cx / (3 * a), cy / (3 * a)) if a else poly[0]


def edge_distance(poly, pt):
    best = 1e9
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
        dx, dy = x1 - x0, y1 - y0
        t = 0.0 if dx == dy == 0 else max(0.0, min(1.0, ((pt[0] - x0) * dx + (pt[1] - y0) * dy) / (dx * dx + dy * dy)))
        best = min(best, math.dist(pt, (x0 + t * dx, y0 + t * dy)))
    return best


def check(name, half, views, margin=0.0015, gap=0.0003):
    body = full_outline(half)
    bad = 0
    for view, entries in views.items():
        flat = [(k, i, p) for k, polys in entries for i, p in enumerate(polys)]
        for k, i, p in flat:
            out = [q for q in p if not inside(body, q) or edge_distance(body, q) < margin]
            if out:
                bad += 1
                print(f"{name} {view} {k}#{i}: {len(out)} points outside or within the margin of the outline, first {out[0]}")
        for a in range(len(flat)):
            for b in range(a + 1, len(flat)):
                pa, pb = flat[a][2], flat[b][2]
                if overlaps(pa, pb):
                    bad += 1
                    print(f"{name} {view} {flat[a][0]}#{flat[a][1]} overlaps {flat[b][0]}#{flat[b][1]}")
                elif min(edge_distance(pb, q) for q in pa) < gap:
                    bad += 1
                    print(f"{name} {view} {flat[a][0]}#{flat[a][1]} and {flat[b][0]}#{flat[b][1]} are closer than {gap}")
    print(f"{name}: " + ("OK" if not bad else f"{bad} problems"))
    return bad


def landmarks(half):
    body = full_outline(half)
    print("y      outline crossings right of the midline (every 0.01)")
    for k in range(0, 100):
        y = k / 100
        xs = []
        for i in range(len(body)):
            (x0, y0), (x1, y1) = body[i], body[(i + 1) % len(body)]
            if (y0 > y) != (y1 > y):
                xs.append(x0 + (y - y0) * (x1 - x0) / (y1 - y0))
        print(f"{y:.2f}  " + "  ".join(f"{x:.3f}" for x in sorted(x for x in xs if x >= 0)))


def colour(i):
    r, g, b = colorsys.hsv_to_rgb((i * 0.61803398875) % 1.0, 0.55, 0.95)
    return int(r * 255), int(g * 255), int(b * 255)


def label_font(size):
    from PIL import ImageFont
    for name in ("arialbd.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default(size)


def area(q):
    return abs(sum(q[i][0] * q[(i + 1) % len(q)][1] - q[(i + 1) % len(q)][0] * q[i][1] for i in range(len(q)))) / 2


def pole(poly):
    """The point of a polygon (figure units) furthest from its edge, and that distance: where a label fits best."""
    from shapely.geometry import Polygon
    from shapely.ops import polylabel
    g = Polygon(poly)
    if not g.is_valid:
        g = g.buffer(0)
    pt = polylabel(g, 1e-4)
    return pt.x, pt.y, g.exterior.distance(pt)


def place_labels(d, found, at, font, S, ox, W, side, H):
    """One label per muscle: inside its largest piece when the text fits, else on a leader line out in the margin.

    Leader labels alternate between the right and the left column (the right half is drawn on both sides, so
    either is honest), and each column spreads its rows so no two texts collide.
    """
    inside, lead = [], []
    scale = at((1, 0))[0] - at((0, 0))[0]
    for m, polys in found.items():
        best = max(polys, key=area)
        x, y, r = pole(best)
        text = m.replace("_", " ").title()
        tw = d.textlength(text, font=font)
        px, py = at((x, y))
        if math.hypot(tw / 2, font.size * 0.55) <= r * scale * 1.15:
            inside.append((px, py, text, tw))
        else:
            lead.append([text, px, py])
    for px, py, text, tw in inside:
        d.text((px - tw / 2, py - font.size / 2), text, font=font, fill=(0, 0, 0), stroke_width=S, stroke_fill=(255, 255, 255))
    lead.sort(key=lambda e: e[2])
    gap = font.size * 1.35
    ink = (235, 235, 240)
    for col in (1, -1):
        mine = [e for k, e in enumerate(lead) if (k % 2 == 0) == (col == 1)]
        ys = [e[2] for e in mine]
        for k in range(1, len(ys)):
            ys[k] = max(ys[k], ys[k - 1] + gap)
        lift = max(0.0, ys[-1] - (H - gap)) if ys else 0.0
        for (text, px, py), ty in zip(mine, ys):
            ty -= lift
            ax = ox + col * (px - ox)
            edge = ox + col * (W / 2 - side + 6 * S)
            tw = d.textlength(text, font=font)
            d.line([(ax, py), (edge, ty)], fill=ink, width=max(1, S // 2 + 1))
            d.ellipse([ax - 3 * S, py - 3 * S, ax + 3 * S, py + 3 * S], fill=ink, outline=(0, 0, 0))
            d.text((edge + 3 * S if col == 1 else edge - 3 * S - tw, ty - font.size / 2), text, font=font, fill=ink,
                   stroke_width=max(1, S // 2), stroke_fill=(0, 0, 0))


def render(half, entries, hair, height, highlight, labels, names, silhouette=False, ss=3, margin=0.03, halfwidth=0.26):
    from PIL import Image, ImageDraw
    lane = int(height * 0.22) if labels and not silhouette else 0   # room for the leader-line labels beside the figure
    width = int(height * (2 * halfwidth + 0.04)) + 2 * lane
    S = ss
    H, W = height * S, width * S
    img = Image.new("RGB", (W, H), (14, 14, 16))
    d = ImageDraw.Draw(img)
    ox, oy, sc = W / 2, margin * H, H * (1 - 2 * margin)

    def at(p):
        return (ox + p[0] * sc, oy + p[1] * sc)

    line = max(1, int(1.2 * S * height / 360))
    body = [at(p) for p in full_outline(half)]
    d.polygon(body, fill=(44, 44, 50) if not silhouette else (70, 70, 78))
    muscles = sorted({names.get(k, k) for k, _ in entries})
    found = {}
    for poly in hair:  # decoration in the darkest ink, never a muscle
        for side in (poly, mirrored(poly)):
            d.polygon([at(p) for p in side], fill=(12, 12, 11))
    if not silhouette:
        for key, polys in entries:
            m = names.get(key, key)
            if highlight:
                col = (46, 160, 110) if m in highlight else (62, 62, 68)
            else:
                col = colour(muscles.index(m))
            for poly in polys:
                for side in (poly, mirrored(poly)):
                    pts = [at(p) for p in side]
                    d.polygon(pts, fill=col)
                    d.line(pts + [pts[0]], fill=(10, 10, 12), width=line)
            if labels and (not highlight or m in highlight):
                found[m] = found.get(m, []) + [p for p in polys]
    d.line(body + [body[0]], fill=(170, 170, 180), width=max(1, int(1.4 * S * height / 360)))
    if labels and not silhouette:
        place_labels(d, found, at, label_font(max(8, int(height / 70)) * S), S, ox, W, lane * S, H)
    return img.resize((width, height), Image.LANCZOS)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--body", default="")
    ap.add_argument("--height", type=int, default=1800)
    ap.add_argument("--highlight", default="")
    ap.add_argument("--tag", default="")
    ap.add_argument("--no-labels", action="store_true")
    ap.add_argument("--silhouette", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--landmarks", action="store_true")
    ap.add_argument("--out", default=os.path.join(".tmp", "shots"))
    ap.add_argument("--src", default=SHAPES)
    a = ap.parse_args()
    bodies = parse_shapes(a.src)
    wanted = [b.strip().lower() for b in a.body.split(",") if b.strip()] or list(bodies)
    names = key_muscles(FIGURES)
    if a.landmarks:
        for b in wanted:
            print(b)
            landmarks(bodies[b][0])
        return 0
    if a.check:
        return 1 if sum(check(b, bodies[b][0], bodies[b][1]) for b in wanted) else 0
    from PIL import Image
    os.makedirs(a.out, exist_ok=True)
    hl = {h.strip().upper() for h in a.highlight.split(",") if h.strip()}
    for b in wanted:
        half, views, hair = bodies[b]
        tag = f"_{a.tag}" if a.tag else ""
        if a.silhouette:
            tag += "_silhouette"
        if hl:
            tag += "_hl"
        imgs = []
        for view in ("front", "back"):
            im = render(half, views[view], hair[view], a.height, hl, not a.no_labels and a.height >= 700, names, a.silhouette)
            im.save(os.path.join(a.out, f"figure_{b}{tag}_{view}.png"))
            imgs.append(im)
        both = Image.new("RGB", (imgs[0].width * 2, imgs[0].height))
        both.paste(imgs[0], (0, 0))
        both.paste(imgs[1], (imgs[0].width, 0))
        path = os.path.join(a.out, f"figure_{b}{tag}_both.png")
        both.save(path)
        print(path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
