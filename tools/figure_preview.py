#!/usr/bin/env python
"""Offline preview of the muscle-map figure, so region shapes can be iterated without an emulator.

Parses HALF_OUTLINE and the FRONT / BACK Region lists out of MuscleHeatMap.kt and draws them exactly
as the app does: the same quadratic-midpoint closed curve (smoothSamples / smoothClosed), the right
half mirrored across the midline, a seam round every region. Each region gets a distinct colour and
its Muscle name; --highlight shows only some muscles in the app's green and greys the rest.

Usage:
    python tools/figure_preview.py                       # large labelled front + back + both -> .tmp/shots/
    python tools/figure_preview.py --height 360          # in-app size (about 120dp tall at 3x)
    python tools/figure_preview.py --highlight BICEPS,LATS,FOREARMS
    python tools/figure_preview.py --tag after           # file names get a suffix
    python tools/figure_preview.py --check               # inside-outline / overlap report, no images
    python tools/figure_preview.py --landmarks           # outline half-width table and canon landmarks

Output: .tmp/shots/figure_<tag>_front.png, _back.png, _both.png (never committed; .tmp is ignored).
"""
import argparse
import colorsys
import math
import os
import re
import sys

KT = os.path.join("app", "src", "main", "kotlin", "com", "ironvellum", "app", "ui", "program", "MuscleHeatMap.kt")
PAIR = re.compile(r"(-?\d*\.?\d+)f\s+to\s+(-?\d*\.?\d+)f")
STEPS = 8


def read_source(path: str) -> str:
    with open(path, encoding="utf-8", newline="") as fh:
        text = fh.read().replace("\r\n", "\n")
    return re.sub(r"//[^\n]*", "", text)


def block(text: str, header: str) -> str:
    start = text.index(header)
    end = text.index("\n)\n", start)
    return text[start:end]


def parse(path: str):
    text = read_source(path)
    outline = [(float(a), float(b)) for a, b in PAIR.findall(block(text, "val HALF_OUTLINE"))]
    views = {}
    for name in ("FRONT", "BACK"):
        regions = []
        for chunk in block(text, f"val {name} = listOf(").split("Region(")[1:]:
            muscle = re.match(r"\s*Muscle\.(\w+)", chunk).group(1)
            regions.append((muscle, [(float(a), float(b)) for a, b in PAIR.findall(chunk)]))
        views[name] = regions
    return outline, views


def smooth_samples(points, steps=STEPS):
    """Copy of smoothSamples in MuscleHeatMap.kt: quadratic curves between edge midpoints, vertex as control."""
    def mid(a, b):
        return ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)

    out = []
    frm = mid(points[-1], points[0])
    out.append(frm)
    n = len(points)
    for i, p in enumerate(points):
        to = mid(p, points[(i + 1) % n])
        for k in range(1, steps + 1):
            t = k / steps
            u = 1 - t
            s = (u * u * frm[0] + 2 * u * t * p[0] + t * t * to[0], u * u * frm[1] + 2 * u * t * p[1] + t * t * to[1])
            if math.dist(s, out[-1]) > 1e-5:
                out.append(s)
        frm = to
    return out


def full_outline(half):
    return half + [(-x, y) for x, y in reversed(half)]


def region_curve(points, side=1.0):
    return smooth_samples([(x * side, y) for x, y in points])


def outline_curve(half):
    return smooth_samples(full_outline(half))


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


def check(outline, views, margin=0.0015, gap=0.0004):
    """Region samples must be inside the outline by at least `margin` (float32 in the app tests is not exact)."""
    body = outline_curve(outline)
    bad = 0
    for view, regions in views.items():
        curves = [(m, region_curve(pts)) for m, pts in regions]
        for idx, (m, c) in enumerate(curves):
            out = [p for p in c if not inside(body, p) or edge_distance(body, p) < margin]
            if out:
                bad += 1
                print(f"{view} {m}#{idx}: {len(out)} samples outside (or within the margin of) the outline, first {out[0][0]:.3f},{out[0][1]:.3f}")
        for i in range(len(curves)):
            for j in range(i + 1, len(curves)):
                if overlaps(curves[i][1], curves[j][1]):
                    bad += 1
                    print(f"{view} {curves[i][0]}#{i} overlaps {curves[j][0]}#{j}")
                elif min(edge_distance(curves[j][1], p) for p in curves[i][1]) < gap:
                    bad += 1
                    print(f"{view} {curves[i][0]}#{i} and {curves[j][0]}#{j} are closer than {gap}")
    print("OK" if not bad else f"{bad} problems")
    return bad


def landmarks(outline):
    body = outline_curve(outline)
    print("y      outer-x  (outline half-widths; the smoothed curve, right half, rows are every 0.01)")
    for k in range(0, 100, 1):
        y = k / 100
        xs = sorted(x for x in (_cross(body, y)) if x >= 0)
        print(f"{y:.2f}  " + "  ".join(f"{x:.3f}" for x in xs))


def _cross(poly, y):
    xs = []
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
        if (y0 > y) != (y1 > y):
            xs.append(x0 + (y - y0) * (x1 - x0) / (y1 - y0))
    return xs


def colour(i, n):
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


def render(outline, regions, height, highlight, labels, ss=3):
    from PIL import Image, ImageDraw
    margin = 0.03
    width = int(height * 0.50)
    S = ss
    H = height * S
    W = width * S
    img = Image.new("RGB", (W, H), (14, 14, 16))
    d = ImageDraw.Draw(img)
    ox, oy, sc = W / 2, margin * H, H * (1 - 2 * margin)

    def at(p):
        return (ox + p[0] * sc, oy + p[1] * sc)

    d.polygon([at(p) for p in outline_curve(outline)], fill=(44, 44, 50))
    muscles = sorted({m for m, _ in regions})
    texts = []
    for idx, (m, pts) in enumerate(regions):
        if highlight:
            col = (46, 160, 110) if m in highlight else (62, 62, 68)
        else:
            col = colour(muscles.index(m), len(muscles))
        for side in (1.0, -1.0):
            poly = [at(p) for p in region_curve(pts, side)]
            d.polygon(poly, fill=col)
            d.line(poly + [poly[0]], fill=(10, 10, 12), width=max(1, int(1.2 * S * height / 360)))
        if labels and (not highlight or m in highlight):
            cx, cy = centroid([at(p) for p in region_curve(pts)])
            texts.append((cx, cy, m.replace("_", " ").title()))
    d.line([at(p) for p in outline_curve(outline)] + [at(outline_curve(outline)[0])], fill=(150, 150, 160), width=max(1, int(1.4 * S * height / 360)))
    if labels:
        font = label_font(max(8, int(height / 62)) * S)
        for cx, cy, t in texts:
            tw = d.textlength(t, font=font)
            d.text((cx - tw / 2, cy - font.size / 2), t, font=font, fill=(0, 0, 0), stroke_width=S, stroke_fill=(255, 255, 255))
    return img.resize((width, height), Image.LANCZOS)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--height", type=int, default=1800)
    ap.add_argument("--highlight", default="")
    ap.add_argument("--tag", default="")
    ap.add_argument("--no-labels", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--landmarks", action="store_true")
    ap.add_argument("--out", default=os.path.join(".tmp", "shots"))
    ap.add_argument("--src", default=KT)
    a = ap.parse_args()
    outline, views = parse(a.src)
    if a.landmarks:
        landmarks(outline)
        return 0
    if a.check:
        return 1 if check(outline, views) else 0
    from PIL import Image
    os.makedirs(a.out, exist_ok=True)
    hl = {h.strip().upper() for h in a.highlight.split(",") if h.strip()}
    tag = f"_{a.tag}" if a.tag else ""
    if hl:
        tag += "_hl"
    imgs = []
    for name in ("FRONT", "BACK"):
        im = render(outline, views[name], a.height, hl, not a.no_labels and a.height >= 700)
        im.save(os.path.join(a.out, f"figure{tag}_{name.lower()}.png"))
        imgs.append(im)
    both = Image.new("RGB", (imgs[0].width * 2, imgs[0].height))
    both.paste(imgs[0], (0, 0))
    both.paste(imgs[1], (imgs[0].width, 0))
    path = os.path.join(a.out, f"figure{tag}_both.png")
    both.save(path)
    print(path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
