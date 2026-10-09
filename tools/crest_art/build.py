#!/usr/bin/env python3
"""Turns tools/crest_art/marks.txt (SVG line art, 0 0 100 100) into the Compose path table the app draws.

    python3 tools/crest_art/build.py            # rewrites CrestMarks.kt
    python3 tools/crest_art/build.py --check    # exits 1 if CrestMarks.kt is stale or a shape breaks the flat-gradient rule

Every shape is baked to one absolute path in the 100 x 100 box (circles, ellipses, rects and polygons
become arcs and lines, transforms are applied), so the app only parses `d` strings and strokes them with
a flat diagonal gradient. A shape's fill is its wash alpha: 0 for fill="none", the fill-opacity otherwise.
A metal fill without fill-opacity is a build error. Aurora permits an 18% light curtain;
Void alone permits its opaque #0B0B0D negative-space centre. Neither is metallic shading.
"""
import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "tools" / "crest_art" / "marks.txt"
OUT = ROOT / "app" / "src" / "main" / "kotlin" / "com" / "ironvellum" / "app" / "ui" / "components" / "CrestMarks.kt"
MAX_WASH = 0.18


def num(x):
    s = ("%.2f" % x).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def rot(px, py, deg, cx, cy):
    a = math.radians(deg)
    dx, dy = px - cx, py - cy
    return cx + dx * math.cos(a) - dy * math.sin(a), cy + dx * math.sin(a) + dy * math.cos(a)


def transform_point(pt, tf):
    for (deg, cx, cy) in tf:
        pt = rot(pt[0], pt[1], deg, cx, cy)
    return pt


def parse_transform(text):
    if not text:
        return []
    m = re.fullmatch(r"rotate\(\s*(-?[\d.]+)\s+(-?[\d.]+)\s+(-?[\d.]+)\s*\)", text.strip())
    if not m:
        raise SystemExit("unsupported transform: %s" % text)
    return [(float(m.group(1)), float(m.group(2)), float(m.group(3)))]


def path_d(d, tf):
    """Absolute M L H V C A Z paths pass through; with a transform only M L H V Z are supported."""
    if not tf:
        return d
    toks = re.findall(r"[MLHVZ]|-?[\d.]+", d)
    out, i, cur = [], 0, (0.0, 0.0)
    while i < len(toks):
        c = toks[i]
        i += 1
        if c == "Z":
            out.append("Z")
        elif c in "ML":
            x, y = float(toks[i]), float(toks[i + 1])
            i += 2
            cur = (x, y)
            px, py = transform_point(cur, tf)
            out.append("%s%s %s" % (c, num(px), num(py)))
        elif c == "H":
            cur = (float(toks[i]), cur[1])
            i += 1
            px, py = transform_point(cur, tf)
            out.append("L%s %s" % (num(px), num(py)))
        elif c == "V":
            cur = (cur[0], float(toks[i]))
            i += 1
            px, py = transform_point(cur, tf)
            out.append("L%s %s" % (num(px), num(py)))
        else:
            raise SystemExit("transformed path uses %s; extend path_d" % c)
    return "".join(out)


def ellipse_d(cx, cy, rx, ry, deg=0.0):
    a = math.radians(deg)
    x1, y1 = cx - rx * math.cos(a), cy - rx * math.sin(a)
    x2, y2 = cx + rx * math.cos(a), cy + rx * math.sin(a)
    return "M%s %sA%s %s %s 1 0 %s %sA%s %s %s 1 0 %s %sZ" % (
        num(x1), num(y1), num(rx), num(ry), num(deg), num(x2), num(y2), num(rx), num(ry), num(deg), num(x1), num(y1))


def rect_d(x, y, w, h, r):
    if not r:
        return "M%s %sH%sV%sH%sZ" % (num(x), num(y), num(x + w), num(y + h), num(x))
    return ("M%s %sH%sA%s %s 0 0 1 %s %sV%sA%s %s 0 0 1 %s %sH%sA%s %s 0 0 1 %s %sV%sA%s %s 0 0 1 %s %sZ" % (
        num(x + r), num(y), num(x + w - r), num(r), num(r), num(x + w), num(y + r), num(y + h - r),
        num(r), num(r), num(x + w - r), num(y + h), num(x + r), num(r), num(r), num(x), num(y + h - r),
        num(y + r), num(r), num(r), num(x + r), num(y)))


def walk(el, inherited, tf, out, cid):
    style = dict(inherited)
    for k in ("stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "fill", "fill-opacity", "stroke-opacity"):
        if el.get(k) is not None:
            style[k] = el.get(k)
    style["opacity"] = float(inherited.get("opacity", 1)) * float(el.get("opacity", 1))
    tf = tf + parse_transform(el.get("transform"))
    tag = el.tag.split("}")[1]
    f = lambda k, d=0.0: float(el.get(k, d))
    d = None
    if tag == "path":
        d = path_d(el.get("d"), tf)
    elif tag == "circle":
        # A circle is an ellipse; a transform moves its centre (none of the marks rotate one, but be exact).
        c = transform_point((f("cx"), f("cy")), tf)
        d = ellipse_d(c[0], c[1], f("r"), f("r"))
    elif tag == "ellipse":
        c = transform_point((f("cx"), f("cy")), tf)
        deg = sum(t[0] for t in tf) + 0.0
        d = ellipse_d(c[0], c[1], f("rx"), f("ry"), deg)
    elif tag == "rect":
        if tf:
            raise SystemExit("%s: transformed rect unsupported" % cid)
        d = rect_d(f("x"), f("y"), f("width"), f("height"), f("rx"))
    elif tag == "polygon":
        pts = [tuple(map(float, p.split(","))) for p in el.get("points").split()]
        pts = [transform_point(p, tf) for p in pts]
        d = "M" + "L".join("%s %s" % (num(x), num(y)) for x, y in pts) + "Z"
    elif tag == "g":
        for ch in el:
            walk(ch, style, tf, out, cid)
        return
    else:
        raise SystemExit("%s: unsupported element <%s>" % (cid, tag))
    fill = style.get("fill")
    ground = fill == "#0B0B0D"
    if ground and (cid != "void" or style["opacity"] != 1):
        raise SystemExit("only Void may use the opaque dark centre")
    if fill == "none" or ground:
        wash = 0.0
    else:
        wash = style.get("fill-opacity")
        if wash is None:
            raise SystemExit("%s: <%s> has a solid fill; marks are line art with a wash of at most %s" % (cid, tag, MAX_WASH))
        wash = float(wash) * style["opacity"]
        if wash > MAX_WASH + 1e-9:
            raise SystemExit("%s: wash %s is over %s" % (cid, wash, MAX_WASH))
    dash = el.get("stroke-dasharray")
    stroke_alpha = 0 if style.get("stroke") == "none" else float(style.get("stroke-opacity", 1)) * style["opacity"]
    out.append((d, wash, stroke_alpha, float(style.get("stroke-width", 1)),
                style.get("stroke-linecap", "butt") == "round", style.get("stroke-linejoin", "miter") == "round",
                [float(v) for v in dash.split()] if dash else None, ground))


def build():
    marks = []
    for line in SRC.read_text(encoding="utf8").splitlines():
        if not line or line.startswith("#"):
            continue
        cid, frag = line.split("\t", 1)
        root = ET.fromstring('<svg xmlns="http://www.w3.org/2000/svg">%s</svg>' % frag)
        shapes = []
        for ch in root:
            walk(ch, {}, [], shapes, cid)
        marks.append((cid, shapes))
    return marks


def kotlin(marks):
    lines = [
        "package com.ironvellum.app.ui.components",
        "",
        "// GENERATED by tools/crest_art/build.py from tools/crest_art/marks.txt. Do not edit by hand.",
        "// Flat line art in a 100 x 100 box, with a translucent wash and Void's dark negative space.",
        "",
        "/**",
        " * One shape of a crest mark: an absolute path [d] in the 100 x 100 box, its [wash] (the fill's alpha, 0 for",
        " * none, at most [MAX_WASH]), its stroke alpha, width and caps, and an optional dash.",
        " */",
        "internal class CrestShape(",
        "    val d: String,",
        "    val wash: Float,",
        "    val strokeAlpha: Float,",
        "    val width: Float,",
        "    val roundCap: Boolean,",
        "    val roundJoin: Boolean,",
        "    val dash: FloatArray? = null,",
        "    val darkGround: Boolean = false,",
        ")",
        "",
        "internal const val MAX_WASH = 0.18f",
        "",
        "/** Every crest mark by catalogue id. */",
        "internal val CREST_SHAPES: Map<String, List<CrestShape>> = mapOf(",
    ]
    for cid, shapes in marks:
        lines.append('    "%s" to listOf(' % cid)
        for d, wash, sa, w, rc, rj, dash, ground in shapes:
            dash_s = ", dash = floatArrayOf(%s)" % ", ".join("%sf" % num(v) for v in dash) if dash else ""
            ground_s = ", darkGround = true" if ground else ""
            lines.append('        CrestShape("%s", %sf, %sf, %sf, %s, %s%s%s),' % (
                d, num(wash), num(sa), num(w), str(rc).lower(), str(rj).lower(), dash_s, ground_s))
        lines.append("    ),")
    lines.append(")")
    return "\r\n".join(lines) + "\r\n"


def main():
    text = kotlin(build())
    if "--check" in sys.argv:
        ok = OUT.exists() and OUT.read_bytes() == text.encode("utf8")
        print("CrestMarks.kt is %s" % ("current" if ok else "STALE"))
        sys.exit(0 if ok else 1)
    OUT.write_bytes(text.encode("utf8"))
    print("wrote %s (%d marks)" % (OUT.relative_to(ROOT), len(build())))


if __name__ == "__main__":
    main()
