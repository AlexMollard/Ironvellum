#!/usr/bin/env python3
"""Generate Monarch artwork with gemini-3.1-flash-image via omp's google-antigravity provider.

Why a script and not an MCP server: the provider, the model and the credential
already exist in omp. The only missing piece was a callable entry point, and an
MCP server would be a daemon plus a protocol schema wrapping one HTTP POST.
This keeps the prompts in version control next to the art they produced.

    python tools/art.py --style ink "a sealed stone gate, dormant rune core" -o out.png

STYLE IS A CONTRACT. Every piece of art in the app goes through one of the
named styles below so screens cannot drift apart, and every style inherits
BASE - the palette and the anti-"AI render" constraints. Never pass a bare
prompt with the style baked into the text; add or edit a style here instead.

Auth: `omp token google-antigravity` returns a live OAuth token and owns its
refresh, so no secret lives here or in the repo.

Transport: headroom (127.0.0.1:8787) -> Cloud Code Assist. Note the envelope:
the real Gemini body must be nested under "request", or the server answers
`missing request payload`.
"""

from __future__ import annotations

import argparse
import base64
import io
import json
import pathlib
import subprocess
import sys
import urllib.error
import urllib.request

ENDPOINT = "http://127.0.0.1:8787/v1internal:streamGenerateContent?alt=sse"
MODEL = "gemini-3.1-flash-image"
# The Antigravity UA is what makes the cloudcode pipeline accept the call.
USER_AGENT = "antigravity/2.8.0"

# Shared by every style: framing, the monochrome rule, and the negative
# constraints that keep output from looking like stock AI art (glow bloom, 3D
# gloss, gradient mush, stray lettering).
#
# HOUSE STYLE: black ink only. Tinted runs (green/olive scales and trees) sat
# beside untinted ones and the set stopped reading as one hand, so colour is
# banned outright rather than merely steered.
BASE = (
    "Monochrome black ink only. Pure greyscale: black and charcoal ink with bone-white "
    "paper tones and grey washes. Absolutely no colour of any kind - no green, no blue, "
    "no brown, no tint, no colour accents whatsoever. "
    "Single subject, centred, generous empty margins, on a pure white background. "
    "No text, letters, numbers, signatures or watermarks. "
    "No glow bloom, no lens flare, no vignette, no 3D render, no photorealism, "
    "no gradient mesh, no drop shadows, no busy background detail."
)

STYLES: dict[str, str] = {
    # Loose hand-painted marks. Reads as made by a person, not a renderer.
    "ink": (
        "Sumi-e ink brush painting. Loose confident brush strokes with visible dry-brush "
        "texture and slight ink bleed, imperfect hand-painted edges, tapered stroke ends, "
        "a few strokes only, flat with no shading."
    ),
    # Austere pictogram. The safest for small sizes and icon-adjacent use.
    "line": (
        "Minimal monoline pictogram. Uniform medium stroke weight, geometric construction, "
        "open unfilled shapes, hard flat colour, large areas of negative space, "
        "no texture and no shading."
    ),
    # Carved, high-contrast, deliberately crude - strongest "not AI" signal.
    "woodcut": (
        "Hand-carved woodcut relief print. Chiselled angular marks, visible gouge texture, "
        "high contrast flat ink, slightly ragged printed edges, no smooth gradients."
    ),
}


def token() -> str:
    p = subprocess.run(
        ["omp", "token", "google-antigravity"], capture_output=True, text=True
    )
    tok = p.stdout.strip()
    if p.returncode != 0 or not tok:
        sys.exit(f"could not get a google-antigravity token: {p.stderr.strip()}")
    return tok


def generate(prompt: str, aspect: str | None) -> list[tuple[str, bytes]]:
    gen_cfg: dict = {"responseModalities": ["IMAGE"]}
    if aspect:
        gen_cfg["imageConfig"] = {"aspectRatio": aspect}
    body = {
        "model": MODEL,
        "request": {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": gen_cfg,
        },
    }
    req = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(body).encode(),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token()}",
            "User-Agent": USER_AGENT,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=180) as r:
            stream = r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        sys.exit(f"HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:400]}")

    images: list[tuple[str, bytes]] = []
    for line in stream.splitlines():
        if not line.startswith("data: "):
            continue
        try:
            chunk = json.loads(line[6:])
        except json.JSONDecodeError:
            continue
        for cand in chunk.get("response", {}).get("candidates", []):
            for part in cand.get("content", {}).get("parts", []):
                inline = part.get("inlineData") or part.get("inline_data")
                if inline and inline.get("data"):
                    mime = inline.get("mimeType") or inline.get("mime_type") or ""
                    images.append((mime, base64.b64decode(inline["data"])))
    if not images:
        sys.exit(f"no image parts came back; first 400 chars: {stream[:400]}")
    return images


def key_backdrop(im, tol: int = 24):
    """Make the backdrop transparent, whatever colour the model actually used.

    The prompt asks for pure black, but the model does not always comply - one
    run of the quest gate came back on opaque white (254,254,254), which would
    have shipped a white slab onto a near-black panel. So the backdrop colour
    is read off the corners instead of assumed.

    Flood fill from the corners, not a global colour match: the palette has
    bone-white accents INSIDE the artwork, and a global match would punch holes
    through them.
    """
    from PIL import Image, ImageDraw, ImageFilter

    MAGIC = (255, 0, 255)
    rgb = im.convert("RGB")
    w, h = rgb.size
    for corner in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        if rgb.getpixel(corner) == MAGIC:
            continue
        ImageDraw.floodfill(rgb, corner, MAGIC, thresh=tol)

    filled = rgb.load()
    alpha = Image.new("L", (w, h), 255)
    ap = alpha.load()
    for y in range(h):
        for x in range(w):
            if filled[x, y] == MAGIC:
                ap[x, y] = 0
    # Soften the cut so brush edges do not alias against the panel.
    alpha = alpha.filter(ImageFilter.GaussianBlur(0.6))
    out = im.convert("RGBA")
    out.putalpha(alpha)
    return out


def postprocess(
    raw: bytes, size: int | None, key: bool, colors: int | None, on_dark: bool
) -> bytes:
    """Convert to PNG, optionally downscale, key the backdrop out, and quantise.

    The model always returns an opaque JPEG, so transparency has to be keyed
    here; without it a generated tile sits on screen as a visible box.
    Quantising matters for shipping: raw PNGs are ~350 KB each against a
    7 MB release APK.
    """
    from PIL import Image

    im = Image.open(io.BytesIO(raw)).convert("RGBA")
    if size:
        im.thumbnail((size, size), Image.LANCZOS)
    if key:
        # Density-based, not region-based: see ink_to_alpha for why keying the
        # ground by flood fill was not good enough.
        im = ink_to_alpha(im)
    elif on_dark:
        im = invert_for_dark(im) if ink_is_dark(im) else im
    if colors:
        # Quantise colour only; RGBA quantisation would flatten the alpha cut.
        rgb = im.convert("RGB").quantize(colors=colors, method=Image.FASTOCTREE)
        rgb = rgb.convert("RGB")
        rgb.putalpha(im.getchannel("A"))
        im = rgb
    out = io.BytesIO()
    im.save(out, format="PNG", optimize=True)
    return out.getvalue()

# One bone tone for every piece, so screens cannot drift apart on colour.
INK_TINT = (232, 232, 228)


def ink_to_alpha(im, tint: tuple[int, int, int] = INK_TINT):
    """Turn a monochrome ink drawing into bone strokes on transparency.

    This replaces backdrop keying for ink art, because keying only removes the
    ground it can REACH: one run came back as a paper square inside a ragged
    dark border, so flood fill from the corners left the whole white block
    opaque and shipped a white slab. Corner sampling reported "transparent"
    and was wrong.

    Density, not region: ink darkness becomes opacity, and the RGB is replaced
    by a single bone tint. Brush texture survives in the alpha channel, the
    paper disappears by construction, and every piece gets the same colour.
    """
    from PIL import Image

    grey = im.convert("L")
    px, (w, h) = grey.load(), grey.size
    # Which end is the ink? Sample the border: paper dominates the edges.
    border = [px[x, y] for x in range(0, w, 4) for y in (0, h - 1)]
    border += [px[x, y] for y in range(0, h, 4) for x in (0, w - 1)]
    paper_is_light = sum(border) / len(border) > 127

    alpha = Image.new("L", (w, h))
    ap = alpha.load()
    for y in range(h):
        for x in range(w):
            v = px[x, y]
            ap[x, y] = (255 - v) if paper_is_light else v
    flat = Image.new("RGB", (w, h), tint)
    out = flat.convert("RGBA")
    out.putalpha(alpha)
    return out



def lift_shadows(im, floor: int = 70):
    """Raise the darkest ink off the panel floor without flattening texture.

    Even correct-polarity ink washes keep near-black outline strokes, and on a
    panel at luminance 23 those strokes simply vanish - measured at 36% of the
    ink in the skill-tree piece. Remapping the ramp to [floor, 255] keeps every
    relative value (so the brushwork survives) while guaranteeing the faintest
    mark still clears the background.

    Also drops near-transparent fringe pixels, which otherwise leave a faint
    rectangular halo where keying feathered the edge.
    """
    from PIL import Image

    px, (w, h) = im.load(), im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 48:
                px[x, y] = (r, g, b, 0)
                continue
            lum = int(0.2126 * r + 0.7152 * g + 0.0722 * b)
            scaled = floor + (lum * (255 - floor)) // 255
            px[x, y] = (scaled, scaled, scaled, a)
    return im


def ink_is_dark(im, threshold: int = 110) -> bool:
    """True when the surviving marks sit at the dark end of the ramp.

    Only keyed-in (opaque) pixels count: the paper has already been cut away,
    so what is left is the artwork itself.
    """
    px, (w, h) = im.load(), im.size
    lums = [
        0.2126 * px[x, y][0] + 0.7152 * px[x, y][1] + 0.0722 * px[x, y][2]
        for y in range(0, h, 3)
        for x in range(0, w, 3)
        if px[x, y][3] > 180
    ]
    if not lums:
        return False
    lums.sort()
    return lums[len(lums) // 2] < threshold


def invert_for_dark(im):
    """Turn black-on-paper ink into bone ink on transparency.

    Monochrome ink is drawn dark on white paper, which is the wrong polarity
    for this app: keying the paper away leaves near-black strokes on a
    near-black panel (panel luminance is ~23/255). Inverting luminance keeps
    every brush mark, the dry-brush texture and the greyscale palette, and
    simply flips which end of the ramp the ink sits on.
    """
    from PIL import Image, ImageOps

    alpha = im.getchannel("A")
    grey = ImageOps.invert(im.convert("L"))
    out = Image.merge("RGBA", (grey, grey, grey, alpha))
    return out


def chroma_audit(png: bytes) -> str:
    """Fail chromatic output: the house style is black ink only.

    Replaces an earlier green-vs-blue check. Steering colour was the wrong
    contract - tinted pieces sat next to untinted ones and the set stopped
    looking like one hand.
    """
    from PIL import Image

    im = Image.open(io.BytesIO(png)).convert("RGBA")
    px, (w, h) = im.load(), im.size
    tinted = total = 0
    for y in range(0, h, 3):
        for x in range(0, w, 3):
            r, g, b, a = px[x, y]
            if a < 150:
                continue
            total += 1
            # Chroma as max-min across channels; pure greyscale is 0.
            if max(r, g, b) - min(r, g, b) > 24:
                tinted += 1
    if not total:
        return "no opaque ink"
    pct = 100 * tinted / total
    verdict = "OK" if pct < 5 else "COLOUR DRIFT - not black ink only"
    return f"{pct:.1f}% tinted px -> {verdict}"


def contrast_audit(png: bytes, panel=(20, 23, 27)) -> str:
    """Report how much ink would disappear into the app's panel colour."""
    from PIL import Image

    def lum(c):
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]

    im = Image.open(io.BytesIO(png)).convert("RGBA")
    px, (w, h) = im.load(), im.size
    inks = [lum(px[x, y][:3]) for y in range(0, h, 3) for x in range(0, w, 3) if px[x, y][3] > 180]
    if not inks:
        return "no opaque ink"
    inks.sort()
    buried = sum(1 for v in inks if v <= lum(panel) + 6) / len(inks)
    verdict = "OK" if buried < 0.25 else "LOW CONTRAST on the dark panel"
    return (
        f"ink median {inks[len(inks) // 2]:.0f} vs panel {lum(panel):.0f}, "
        f"{100 * buried:.0f}% buried -> {verdict}"
    )


def backdrop_audit(png: bytes) -> str:
    """Confirm the ground is really gone, not just at the corners.

    Corner sampling alone shipped a defect: one piece came back as a white
    paper square inside a ragged dark border, so all four corners keyed
    transparent while the entire interior stayed opaque white. An ink drawing
    is mostly empty space, so total opaque coverage is the honest test.
    """
    from PIL import Image

    im = Image.open(io.BytesIO(png)).convert("RGBA")
    px, (w, h) = im.load(), im.size
    corners = [px[2, 2], px[w - 3, 2], px[2, h - 3], px[w - 3, h - 3]]
    opaque_corners = [c for c in corners if c[3] > 32]
    sampled = [
        px[x, y][3] for y in range(0, h, 3) for x in range(0, w, 3)
    ]
    coverage = sum(1 for a in sampled if a > 180) / len(sampled)
    problems = []
    if opaque_corners:
        problems.append(f"{len(opaque_corners)}/4 corners opaque")
    if coverage > 0.55:
        problems.append(f"{100 * coverage:.0f}% of the image is opaque (paper slab)")
    if problems:
        return "; ".join(problems) + " -> NOT KEYED"
    return f"transparent ({100 * coverage:.0f}% ink coverage) -> OK"


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("prompt", help="the subject only; style comes from --style")
    ap.add_argument("-o", "--out", required=True, help="output .png path")
    ap.add_argument(
        "--style", default="ink", choices=sorted(STYLES), help="named house style"
    )
    ap.add_argument("--aspect", default="1:1", help='e.g. "1:1", "16:9"')
    ap.add_argument("--size", type=int, help="max edge in px after generation")
    ap.add_argument(
        "--alpha",
        action="store_true",
        help="key the backdrop out, whatever colour the model used",
    )
    ap.add_argument(
        "--colors", type=int, help="quantise to N colours to shrink the PNG"
    )
    ap.add_argument(
        "--no-invert",
        action="store_true",
        help="keep black-on-paper polarity instead of bone ink for dark panels",
    )
    args = ap.parse_args()

    prompt = f"{STYLES[args.style]} {BASE} Subject: {args.prompt}"
    # Always keep the untouched model output. Postprocessing is destructive and
    # the quota is finite: one bad local transform wiped three finished pieces
    # that then could not be regenerated for hours.
    raw_dir = pathlib.Path(".tmp/art-raw")
    raw_dir.mkdir(parents=True, exist_ok=True)
    mime, raw = generate(prompt, args.aspect)[0]
    raw_path = raw_dir / f"{pathlib.Path(args.out).stem}.raw.jpg"
    raw_path.write_bytes(raw)
    png = postprocess(
        raw, args.size, args.alpha, args.colors, on_dark=not args.no_invert
    )
    with open(args.out, "wb") as f:
        f.write(png)
    print(f"{args.out}: {len(png)} bytes PNG ({args.style}, from {mime})")
    print(f"  chroma:   {chroma_audit(png)}")
    print(f"  contrast: {contrast_audit(png)}")
    backdrop = backdrop_audit(png)
    print(f"  backdrop: {backdrop}")
    # Shipping an unkeyed tile onto a dark panel is a visible defect, so this
    # fails the command rather than printing a warning nobody reads.
    if args.alpha and "NOT KEYED" in backdrop:
        sys.exit("keying failed - refusing to pass this off as usable art")


if __name__ == "__main__":
    main()
