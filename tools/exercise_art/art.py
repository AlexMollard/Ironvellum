"""Turns upstream drawings into the compact masks the app tints at runtime.

Every drawing becomes a coverage mask (0 = no ink, 255 = full ink) and an exercise's frames are laid side by side in one
lossless WebP, cropped to the union of the frames' ink so the figure keeps its place and scale from frame to frame:

    Everkinetic   dist/png/<id>-relaxation.png + -tension.png   black ink on white  -> coverage = 255 - luminance
    workout-guide assets/<slug>/frame-N.png                     white ink, alpha     -> coverage = alpha

The mask is stored as grey RGB (R = G = B = coverage), quantised to LEVELS steps, so the file carries no colour at all.
The app reads the R channel as alpha and paints it in the house ink; recolouring never touches the files.
"""
import io
from pathlib import Path

import numpy as np
from PIL import Image

MAX_SIDE = 256   # longest side of one frame cell, px
LEVELS = 4       # coverage steps kept (lossless WebP; 4 steps cost about 5 KB a frame pair)
THRESHOLD = 24   # coverage below this is background noise when finding the ink bounds


def coverage(kind, src, ident, index):
    """One frame's coverage mask as uint8, or raises FileNotFoundError."""
    src = Path(src)
    if kind == "ek":
        name = "relaxation" if index == 0 else "tension"
        im = Image.open(src / "ek" / "dist" / "png" / ("%s-%s.png" % (ident, name))).convert("L")
        a = 255 - np.asarray(im).astype(np.uint8)
    else:
        im = Image.open(src / "wg" / "packages" / "workout-guide" / "assets" / ident / ("frame-%d.png" % (index + 1))).convert("RGBA")
        a = np.asarray(im)[:, :, 3].astype(np.uint8)
    return normalise(a)


def normalise(a):
    """Some frames are drawn faint; lift each so its strong strokes reach full ink, keeping the frames comparable."""
    strong = a[a > THRESHOLD]
    if strong.size:
        p = float(np.percentile(strong, 98))
        if 40 < p < 230:
            a = np.clip(a.astype(np.float32) * (255.0 / p), 0, 255).astype(np.uint8)
    return a


def strip(frames):
    """Frames -> (PIL 'L' image of all cells side by side, cell width, cell height)."""
    ys, xs = [], []
    for a in frames:
        m = a > THRESHOLD
        rows, cols = np.where(m.any(1))[0], np.where(m.any(0))[0]
        ys += [rows[0], rows[-1]]
        xs += [cols[0], cols[-1]]
    y0, y1, x0, x1 = min(ys), max(ys) + 1, min(xs), max(xs) + 1
    w, h = x1 - x0, y1 - y0
    s = MAX_SIDE / max(w, h)
    cw, ch = max(1, round(w * s)), max(1, round(h * s))
    out = Image.new("L", (cw * len(frames), ch))
    for i, a in enumerate(frames):
        cell = Image.fromarray(a[y0:y1, x0:x1], "L").resize((cw, ch), Image.LANCZOS)
        out.paste(cell, (i * cw, 0))
    return out, cw, ch


def quantise(img):
    a = np.asarray(img).astype(np.float32)
    q = np.round(a / 255.0 * (LEVELS - 1)) / (LEVELS - 1) * 255.0
    return Image.fromarray(q.astype(np.uint8), "L")


def encode(kind, src, ident, nframes):
    """-> (webp bytes, cell width, cell height)."""
    frames = [coverage(kind, src, ident, i) for i in range(nframes)]
    img, cw, ch = strip(frames)
    buf = io.BytesIO()
    quantise(img).convert("RGB").save(buf, "WEBP", lossless=True, quality=100, method=6)
    return buf.getvalue(), cw, ch
