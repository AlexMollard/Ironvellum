"""Render the GitHub social preview from checked-in app screenshots (Pillow)."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs/images/social-preview.png"
SCALE = 2
BACKGROUND = "#0C0C0B"
INK = "#E8E8E4"
MUTED = "#A3A099"
BORDER = "#32302B"
ACCENT = "#34D399"


def font(size, weight="medium"):
    return ImageFont.truetype(
        str(ROOT / f"app/src/main/res/font/chakra_petch_{weight}.ttf"),
        size * SCALE,
    )


def render():
    canvas = Image.new("RGB", (1280 * SCALE, 640 * SCALE), BACKGROUND)
    draw = ImageDraw.Draw(canvas)

    def text(x, y, value, size, fill=INK, weight="medium"):
        draw.text((x * SCALE, y * SCALE), value, font=font(size, weight), fill=fill)

    def line(points, fill=BORDER):
        draw.line([(x * SCALE, y * SCALE) for x, y in points], fill=fill, width=SCALE)

    icon = Image.open(ROOT / "fastlane/metadata/android/en-US/images/icon.png")
    icon = icon.convert("RGB").resize((64 * SCALE, 64 * SCALE), Image.Resampling.LANCZOS)
    canvas.paste(icon, (64 * SCALE, 60 * SCALE))
    text(145, 78, "IRONVELLUM", 24, weight="bold")
    text(64, 176, "Train hard.", 62, weight="bold")
    text(64, 250, "Log every set.", 62, weight="bold")
    line([(64, 351), (128, 351)], ACCENT)
    text(64, 378, "Strength training. On your terms.", 27)
    text(64, 425, "Plan your week. Track your progress.", 22, MUTED)
    text(64, 526, "ANDROID  /  FREE & OPEN SOURCE", 17, ACCENT, "semibold")
    text(64, 559, "No ads. No subscription. Works offline.", 20, MUTED)

    def screen(name, x, y, width, label):
        source = ROOT / "fastlane/metadata/android/en-US/images/phoneScreenshots" / name
        # Keep real app pixels; remove the store heading and Android status bar.
        image = Image.open(source).convert("RGB").crop((82, 430, 998, 1855))
        height = round(image.height * width / image.width)
        image = image.resize((width * SCALE, height * SCALE), Image.Resampling.LANCZOS)
        draw.rounded_rectangle(
            ((x - 2) * SCALE, (y - 2) * SCALE,
             (x + width + 2) * SCALE, (y + height + 2) * SCALE),
            radius=12 * SCALE, fill=BORDER,
        )
        mask = Image.new("L", image.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle(
            (0, 0, image.width - 1, image.height - 1), radius=10 * SCALE, fill=255,
        )
        canvas.paste(image, (x * SCALE, y * SCALE), mask)
        text(x, y - 31, label, 15, MUTED, "semibold")

    screen("02-live-workout.png", 674, 113, 248, "LOG THE WORK")
    screen("05-lift-progress.png", 950, 163, 248, "SEE THE PROGRESS")
    canvas = canvas.resize((1280, 640), Image.Resampling.LANCZOS)
    canvas.save(OUTPUT, optimize=True)
    assert OUTPUT.stat().st_size < 1_000_000, "GitHub requires an image under 1 MB"
    print(f"{OUTPUT.relative_to(ROOT)}: 1280 x 640, {OUTPUT.stat().st_size:,} bytes")
    return canvas


if __name__ == "__main__":
    render()
