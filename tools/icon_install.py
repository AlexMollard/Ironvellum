#!/usr/bin/env python
"""Install one square PNG as Monarch's adaptive launcher icon.

Takes a generated emblem and writes every mipmap the manifest actually
references: `ic_launcher_foreground`, `ic_launcher_monochrome`, and the legacy
`ic_launcher` / `ic_launcher_round` bitmaps for pre-26 launchers.

Two rules this encodes, because both are easy to get wrong by hand:

* SAFE ZONE. An adaptive foreground is 108dp but a launcher may mask it to any
  shape and only the centre 72dp is guaranteed visible — two thirds of the
  width. Art drawn edge to edge loses its outer third to a circle mask. So the
  emblem is scaled into the inner 66% and the rest is transparent padding.
* The MONOCHROME layer is themed-icon material: Android tints it, so colour
  there is meaningless and only the alpha silhouette matters. It is written as
  flat white-on-transparency from the source's own alpha.

Usage:
    python tools/icon_install.py .tmp/icon_candidate.png
    python tools/icon_install.py .tmp/icon_candidate.png --dry-run
"""
import argparse
import pathlib

# Launcher bitmap edge per density bucket, in px, for a 48dp icon.
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
# Adaptive layers are 108dp.
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
# Fraction of the adaptive canvas the art may occupy: 72dp of 108dp.
SAFE = 72.0 / 108.0

RES = pathlib.Path("app/src/main/res")


def fit_into(im, canvas_px: int, fraction: float):
    """Scale the art into `fraction` of a square canvas, centred, rest clear."""
    from PIL import Image

    art_px = max(1, int(round(canvas_px * fraction)))
    art = im.copy()
    art.thumbnail((art_px, art_px), Image.LANCZOS)
    out = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    out.paste(art, ((canvas_px - art.width) // 2, (canvas_px - art.height) // 2), art)
    return out


def monochrome(im):
    """Flat white on the source's alpha: Android tints this layer itself."""
    from PIL import Image

    flat = Image.new("RGBA", im.size, (255, 255, 255, 0))
    flat.putalpha(im.getchannel("A"))
    return flat


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("source", help="square PNG with a transparent ground")
    ap.add_argument("--dry-run", action="store_true", help="report what would be written")
    args = ap.parse_args()

    from PIL import Image

    src = Image.open(args.source).convert("RGBA")
    if src.width != src.height:
        print(f"warning: {args.source} is {src.width}x{src.height}, not square")
    alpha = src.getchannel("A")
    if alpha.getextrema()[1] == 0:
        print("refusing: the source is fully transparent")
        return 1
    opaque = sum(1 for a in alpha.getdata() if a > 24) / (src.width * src.height)
    if opaque > 0.9:
        print(f"refusing: {100 * opaque:.0f}% of the source is opaque — the ground was not keyed")
        return 1

    written = []
    for bucket, edge in ADAPTIVE.items():
        fg = fit_into(src, edge, SAFE)
        written.append((RES / f"mipmap-{bucket}" / "ic_launcher_foreground.png", fg))
        written.append((RES / f"mipmap-{bucket}" / "ic_launcher_monochrome.png", monochrome(fg)))
    for bucket, edge in LEGACY.items():
        # Legacy bitmaps have no mask, so the art may use more of the square.
        legacy = fit_into(src, edge, 0.84)
        written.append((RES / f"mipmap-{bucket}" / "ic_launcher.png", legacy))
        written.append((RES / f"mipmap-{bucket}" / "ic_launcher_round.png", legacy))

    for path, im in written:
        print(f"{'would write' if args.dry_run else 'wrote'} {path} {im.width}x{im.height}")
        if not args.dry_run:
            path.parent.mkdir(parents=True, exist_ok=True)
            im.save(path, format="PNG", optimize=True)
    print(f"\n{len(written)} files, art at {100 * SAFE:.0f}% of the adaptive canvas")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
