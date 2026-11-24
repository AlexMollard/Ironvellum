"""Stage the static landing site in .tmp/landing for preview or upload."""
from pathlib import Path
from shutil import copyfile
from xml.etree import ElementTree as ET


def stage_art_wall(root: Path, output: Path) -> tuple[int, int]:
    drawings = sorted((root / "app/src/main/assets/exercise_art").glob("own_*.paths"))
    if not drawings:
        raise ValueError("No exercise drawings found")
    wall = output / "exercise-wall"
    wall.mkdir(exist_ok=True)
    per_sheet = 18
    sheets = (len(drawings) + per_sheet - 1) // per_sheet
    for page in range(sheets):
        sheet = ET.Element("svg", xmlns="http://www.w3.org/2000/svg", viewBox="0 0 960 480")
        ET.SubElement(sheet, "metadata").text = (
            "Open Exercise Art / Alex Mollard, CC BY-SA 4.0. Resized, recoloured and tiled. "
            "https://github.com/AlexMollard/Open-Exercise-Art"
        )
        for cell in range(per_sheet):
            source = drawings[(page * per_sheet + cell) % len(drawings)]
            header, *paths = source.read_text(encoding="utf-8").splitlines()
            version, *bounds = header.split()
            if version != "v1" or len(bounds) != 4 or not paths:
                raise ValueError(f"Unsupported drawing: {source}")
            x, y = (cell % 6) * 160, (cell // 6) * 160
            ET.SubElement(sheet, "rect", x=str(x), y=str(y), width="160", height="160",
                          fill="#cbd5c5" if cell % 2 else "#d6dfcf", stroke="#aab9a9")
            tile = ET.SubElement(sheet, "svg", x=str(x + 12), y=str(y + 12), width="136",
                                 height="136", viewBox=" ".join(bounds), fill="#3d5745")
            ET.SubElement(tile, "title").text = source.stem.removeprefix("own_")
            for line in paths:
                dx, dy, path = line.split(maxsplit=2)
                ET.SubElement(tile, "path", d=path, transform=f"translate({dx} {dy})",
                              attrib={"fill-rule": "evenodd"})
        ET.ElementTree(sheet).write(wall / f"{page}.svg", encoding="utf-8")
    return len(drawings), sheets


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    images = root / "fastlane/metadata/android/en-US/images"
    output = root / ".tmp/landing"
    output.mkdir(parents=True, exist_ok=True)
    for source, name in (
        (root / "site/art-wall.js", "art-wall.js"),
        (root / "site/site.css", "site.css"),
        (root / "site/planner.js", "planner.js"),
        (images / "icon.png", "icon.png"),
        (root / "site/screens/pull-up.webp", "pull-up.webp"),
        (root / "site/screens/bench-sets.webp", "bench-sets.webp"),
        (root / "site/screens/weekly-recap.webp", "weekly-recap.webp"),
        (root / "app/src/main/res/font/chakra_petch_bold.ttf", "chakra-bold.ttf"),
        (root / "app/src/main/res/font/chakra_petch_medium.ttf", "chakra-medium.ttf"),
        (root / "licenses/OFL-ChakraPetch.txt", "OFL-ChakraPetch.txt"),
        (root / "licenses/CC-BY-SA-4.0-exercise-art.txt", "CC-BY-SA-4.0-exercise-art.txt"),
    ):
        copyfile(source, output / name)
    count, sheets = stage_art_wall(root, output)
    page = (root / "site/index.html").read_text(encoding="utf-8")
    (output / "index.html").write_text(
        page.replace("{{art_count}}", str(count)).replace("{{art_sheets}}", str(sheets)),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
