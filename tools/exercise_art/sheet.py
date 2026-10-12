"""Contact sheets of the shipped drawings: python -P tools/exercise_art/sheet.py --out sheet.png [--before DIR] [ids...]

Each row is one drawing: its frames as the app draws them (fitted into a square cell on the house ground), optionally preceded
by the previous .webp strip from --before for comparison. Names come from mapping/library data when known.
"""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import art  # noqa: E402

ASSETS = HERE.parents[1] / "app" / "src" / "main" / "assets" / "exercise_art"
GROUND = (18, 17, 16)


def fit(img, cell):
    img = img.copy()
    img.thumbnail((cell - 8, cell - 8), Image.LANCZOS)
    out = Image.new("RGB", (cell, cell), GROUND)
    out.paste(img, ((cell - img.width) // 2, (cell - img.height) // 2))
    return out


def old_strip(path, frames, cell):
    im = Image.open(path).convert("L")
    w = im.width // frames
    out = []
    for i in range(frames):
        c = im.crop((i * w, 0, (i + 1) * w, im.height))
        rgb = Image.merge("RGB", [c.point(lambda v, k=k: GROUND[k] + v * (232 - GROUND[k]) // 255) for k in range(3)])
        out.append(fit(rgb, cell))
    return out


def sheet(ids, out, before=None, cell=150, cols=2, labels=None):
    rows = []
    for aid in ids:
        if not (ASSETS / (aid + ".paths")).exists():
            continue
        text = (ASSETS / (aid + ".paths")).read_text(encoding="utf-8")
        frames = [fit(i, cell) for i in art.render_paths(text, height=cell * 2)]
        cells = []
        if before:
            p = Path(before) / (aid + ".webp")
            if p.exists():
                n = 3 if aid.startswith("wg_") else 2
                cells += old_strip(p, n, cell) + [Image.new("RGB", (14, cell), (60, 20, 20))]
        cells += frames
        w = sum(c.width for c in cells)
        row = Image.new("RGB", (w, cell + 14), GROUND)
        x = 0
        for c in cells:
            row.paste(c, (x, 0))
            x += c.width
        ImageDraw.Draw(row).text((4, cell), (labels or {}).get(aid, aid), fill=(200, 196, 184))
        rows.append(row)
    cw = max(r.width for r in rows) + 10
    per = (len(rows) + cols - 1) // cols
    img = Image.new("RGB", (cw * cols, (cell + 14) * per), GROUND)
    for k, r in enumerate(rows):
        img.paste(r, ((k // per) * cw, (k % per) * (cell + 14)))
    img.save(out)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--before")
    ap.add_argument("--cols", type=int, default=2)
    ap.add_argument("--cell", type=int, default=150)
    ap.add_argument("ids", nargs="+")
    a = ap.parse_args()
    sheet(a.ids, a.out, a.before, a.cell, a.cols)
