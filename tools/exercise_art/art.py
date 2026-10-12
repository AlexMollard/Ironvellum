"""Turns upstream drawings into the vector files the app draws.

An exercise's art is one text file, `exercise_art/<id>.paths`:

    v1 <x> <y> <w> <h>            the shared window, in source units, that every frame is drawn inside
    <dx> <dy> <d>                 one line per kept frame: translation that registers it, then SVG path data (even-odd)

The window is the union of the frames once each is moved by its own (dx, dy), so the figure keeps one scale and one
ground line from pose to pose. The app scales the window to its slot with Fit and fills the paths in the house ink.

    workout-guide   assets/<slug>/frame-N.svg   one 512 x 512 <path>; its `d` is copied verbatim, never edited
    Everkinetic     dist/svg/<id>-relaxation|tension.svg   rendered into ONE shared box, thresholded, vector-traced (potrace)
    owner-made      owner/<slug>-N.svg|png                  the same as Everkinetic, see owner/README.md

The free sets are used as drawn: a frame is dropped only when it is broken (see `analyse`); an exercise left with one
frame is shipped as a still.
"""
import io
import re
from pathlib import Path

import numpy as np
import resvg_py
from PIL import Image
from scipy import ndimage

WG_SIZE = 512          # workout-guide canvas
EK_ZOOM = 3            # Everkinetic is rasterised at this multiple of its own viewBox before tracing
MIN_INK = 40           # px of ink below which a speck is noise when measuring bounds

# Movements hung from a fixed bar or handle are registered by their top; everything else by its ground line.
HANGING = re.compile(r"(?i)pull-?up|chin-?up|pulldown|hang|muscle-up|toes-to-bar|leg raise|knee raise|lever|flag|dead hang|"
                     r"L-sit|skin the cat|ring row|inverted row|australian|front row")


def _render(svg_text, width=None, zoom=None):
    b = resvg_py.svg_to_bytes(svg_string=svg_text, background="#000000", width=width, zoom=zoom)
    return np.asarray(Image.open(io.BytesIO(b)).convert("L"))


# --------------------------------------------------------------------------------------------- sources

def _sized(svg):
    """Everkinetic's width/height are in pt; give resvg plain px that match the viewBox."""
    vb = re.search(r'viewBox="[\d.\-]+ [\d.\-]+ ([\d.]+) ([\d.]+)"', svg)
    return re.sub(r'width="[^"]*" height="[^"]*"', 'width="%s" height="%s"' % (vb.group(1), vb.group(2)), svg, count=1)


def wg_frames(src, slug):
    """[(d, ink mask)] for each workout-guide frame; `d` is exactly the file's path data."""
    base = Path(src) / "wg" / "packages" / "workout-guide" / "assets" / slug
    out = []
    for i in range(1, 9):
        f = base / ("frame-%d.svg" % i)
        if not f.exists():
            break
        text = f.read_text(encoding="utf-8")
        d = re.search(r'<path[^>]* d="([^"]*)"', text).group(1)
        out.append((d, _render(text, width=WG_SIZE) > 127))
    return out


def ek_frames(src, ident, tracer):
    """[(d, ink mask)] for the relaxation and tension poses, both rendered into one shared box."""
    base = Path(src) / "ek" / "dist" / "svg"
    texts = [(base / ("%s-%s.svg" % (ident, n))).read_text(encoding="utf-8") for n in ("relaxation", "tension")]
    masks = [_render(_sized(t), zoom=EK_ZOOM) < 128 for t in texts]
    h, w = max(m.shape[0] for m in masks), max(m.shape[1] for m in masks)
    masks = [np.pad(m, ((0, h - m.shape[0]), (0, w - m.shape[1]))) for m in masks]
    return [(tracer(m), m) for m in masks]


OWNER_WIDTH = 1024     # owner-made drawings are rendered or resampled to this width before tracing


def owner_files(folder, slug):
    """The owner's frames for one movement, in order: <slug>-1.svg|png, <slug>-2..., contiguous from 1."""
    out = []
    for i in range(1, 9):
        found = [p for ext in ("svg", "png") for p in [Path(folder) / ("%s-%d.%s" % (slug, i, ext))] if p.exists()]
        if not found:
            break
        out.append(found[0])
    return out


def _owner_mask(path):
    """One owner drawing -> ink mask at OWNER_WIDTH. Ink is the alpha channel; an opaque image is read as dark-on-light
    or light-on-dark by its corners."""
    if path.suffix == ".svg":
        b = resvg_py.svg_to_bytes(svg_path=str(path), width=OWNER_WIDTH)
        im = Image.open(io.BytesIO(b)).convert("RGBA")
    else:
        im = Image.open(path).convert("RGBA")
        im = im.resize((OWNER_WIDTH, max(1, round(im.height * OWNER_WIDTH / im.width))), Image.LANCZOS)
    a = np.asarray(im)
    if a[:, :, 3].min() < 250:
        return a[:, :, 3] > 127
    lum = np.asarray(im.convert("L"))
    corner = float(np.median([lum[0, 0], lum[0, -1], lum[-1, 0], lum[-1, -1]]))
    return lum < 128 if corner > 127 else lum > 127


def owner_frames(folder, slug, tracer):
    """[(d, ink mask)] for an owner-made movement, traced exactly as the Everkinetic poses are."""
    masks = [_owner_mask(p) for p in owner_files(folder, slug)]
    if not masks:
        raise FileNotFoundError("no owner frames for " + slug)
    h, w = max(m.shape[0] for m in masks), max(m.shape[1] for m in masks)
    masks = [np.pad(m, ((0, h - m.shape[0]), (0, w - m.shape[1]))) for m in masks]
    return [(tracer(m), m) for m in masks]


def trace(mask, turdsize=10, tolerance=0.9):
    """Ink mask -> compact SVG path data, one path for the whole pose (potrace; holes by even-odd)."""
    import potrace
    plist = potrace.Bitmap(~mask).trace(turdsize=turdsize, alphamax=1.0, opticurve=True, opttolerance=tolerance)

    def p(v):
        return str(int(round(v)))

    parts = []
    for curve in plist:
        s = curve.start_point
        parts.append("M%s %s" % (p(s.x), p(s.y)))
        for seg in curve.segments:
            if seg.is_corner:
                parts.append("L%s %s L%s %s" % (p(seg.c.x), p(seg.c.y), p(seg.end_point.x), p(seg.end_point.y)))
            else:
                parts.append("C%s %s %s %s %s %s" % (p(seg.c1.x), p(seg.c1.y), p(seg.c2.x), p(seg.c2.y),
                                                     p(seg.end_point.x), p(seg.end_point.y)))
        parts.append("Z")
    return "".join(parts)


# --------------------------------------------------------------------------------------------- analysis

def bounds(mask):
    """(x0, y0, x1, y1) of the ink, ignoring specks; x1 and y1 are exclusive."""
    lab, n = ndimage.label(mask, structure=np.ones((3, 3)))
    if n == 0:
        return None
    sizes = ndimage.sum(mask, lab, range(1, n + 1))
    keep = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s >= MIN_INK]) if n else mask
    ys, xs = np.where(keep)
    if xs.size == 0:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def stroke_width(mask):
    """Median stroke width in px: twice the median distance-to-edge along the ink's skeleton ridge."""
    from skimage.morphology import skeletonize
    dist = ndimage.distance_transform_edt(mask)
    sk = skeletonize(mask)
    return float(2 * np.median(dist[sk])) if sk.any() else 0.0


def halo_score(mask, gap=3):
    """How much of the figure's outer contour is doubled: a hairline lying just outside the main outline.

    Closing the mask by the hairline's gap welds the two contours into one band. The ink that closing adds within a
    short way of the silhouette's edge, per unit of edge length, is the score: about 0.5 for a single outline, and
    1.5 or more when a second contour runs parallel to it.
    """
    from skimage.morphology import disk
    closed = ndimage.binary_closing(np.pad(mask, 8), disk(gap))[8:-8, 8:-8]
    filled = ndimage.binary_fill_holes(closed)
    edge = filled & ~ndimage.binary_erosion(filled)
    band = ndimage.binary_dilation(edge, disk(14)) & filled
    return float((closed & ~mask & band).sum() / max(1, edge.sum()))


FAT = 6.0      # strokes wider than this, at a 400 px figure, are noted as the heavy style (noted, never dropped)
HALO = 1.4     # a halo score above this is noted as a doubled outline (noted, never dropped)


def analyse(masks):
    """Which of an exercise's frames are usable: ([bool], [note], [stroke width, halo score]).

    The free sets are used as drawn: a frame is dropped only when it is broken, meaning (nearly) empty next to its
    siblings. A frame in a heavier stroke, or with a doubled outline, is kept and only noted, so the report says where the
    set's own styles differ.
    """
    sizes = []
    for m in masks:
        b = bounds(m) or (0, 0, 1, 1)
        sizes.append(max(b[2] - b[0], b[3] - b[1]))
    widths = [stroke_width(m) * 400.0 / s for m, s in zip(masks, sizes)]
    halos = [halo_score(m) for m in masks]
    areas = [int(m.sum()) for m in masks]
    floor = 0.25 * float(np.median(areas))
    ok, why = [], []
    for a, w, h in zip(areas, widths, halos):
        ok.append(a >= floor and a >= MIN_INK)
        note = []
        if a < floor or a < MIN_INK:
            note.append("broken: nearly empty")
        if w > FAT:
            note.append("heavy strokes %.1f" % w)
        if h > HALO:
            note.append("doubled outline %.2f" % h)
        why.append("; ".join(note))
    return ok, why, [(round(w, 1), round(h, 2)) for w, h in zip(widths, halos)]


# --------------------------------------------------------------------------------------------- registration

def register(items, hanging):
    """items: [(d, mask)] of the kept frames -> (window, [(dx, dy)]).

    Every frame is shifted so its ground line (lowest ink row; the top for hanging movements) and the horizontal centre of
    its ink agree with the first frame's. Only translation: scale is shared by construction, the geometry untouched.
    """
    bs = [bounds(m) for _, m in items]
    ref = bs[0]
    shifts = []
    for b in bs:
        dx = (ref[0] + ref[2]) / 2 - (b[0] + b[2]) / 2
        dy = (ref[1] - b[1]) if hanging else (ref[3] - b[3])
        shifts.append((dx, dy))
    x0 = min(b[0] + s[0] for b, s in zip(bs, shifts))
    y0 = min(b[1] + s[1] for b, s in zip(bs, shifts))
    x1 = max(b[2] + s[0] for b, s in zip(bs, shifts))
    y1 = max(b[3] + s[1] for b, s in zip(bs, shifts))
    pad = 0.015 * max(x1 - x0, y1 - y0)
    window = (x0 - pad, y0 - pad, x1 - x0 + 2 * pad, y1 - y0 + 2 * pad)
    return window, shifts


def fmt(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def write_paths(window, shifts, ds):
    lines = ["v1 " + " ".join(fmt(v) for v in window)]
    for (dx, dy), d in zip(shifts, ds):
        lines.append("%s %s %s" % (fmt(dx), fmt(dy), d))
    return "\r\n".join(lines) + "\r\n"


def read_paths(text):
    """-> (window, [(dx, dy, d)])"""
    rows = [r for r in text.splitlines() if r.strip()]
    head = rows[0].split()
    assert head[0] == "v1"
    window = tuple(float(v) for v in head[1:5])
    frames = []
    for r in rows[1:]:
        a, b, d = r.split(" ", 2)
        frames.append((float(a), float(b), d))
    return window, frames


def build_one(kind, src, ident, hanging, tracer=trace):
    """-> (file text, kept source frame indices, report dict) for one drawing."""
    if kind == "wg":
        items = wg_frames(src, ident)
    elif kind == "own":
        items = owner_frames(src, ident, tracer)
    else:
        items = ek_frames(src, ident, tracer)
    ok, why, widths = analyse([m for _, m in items])
    keep = [i for i, g in enumerate(ok) if g]
    if not keep:
        keep = [max(range(len(items)), key=lambda i: int(items[i][1].sum()))]
    sel = [items[i] for i in keep]
    window, shifts = register(sel, hanging)
    text = write_paths(window, shifts, [d for d, _ in sel])
    return text, keep, {"source": len(items), "kept": keep, "why": why, "stroke": [list(w) for w in widths],
                        "window": [round(v, 1) for v in window]}


def render_paths(text, height=None, width=None, ink="#e8e0cf", ground="#121110"):
    """Render a .paths file's frames to PIL images through resvg (the same path strings the app parses)."""
    (x, y, w, h), frames = read_paths(text)
    out = []
    for dx, dy, d in frames:
        svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="%s %s %s %s"><rect x="%s" y="%s" width="%s" height="%s" fill="%s"/>'
               '<path fill="%s" fill-rule="evenodd" transform="translate(%s %s)" d="%s"/></svg>'
               % (x, y, w, h, x, y, w, h, ground, ink, dx, dy, d))
        b = resvg_py.svg_to_bytes(svg_string=svg, width=width, height=height)
        out.append(Image.open(io.BytesIO(b)).convert("RGB"))
    return out
