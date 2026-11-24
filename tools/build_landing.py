"""Stage the static landing site in .tmp/landing for preview or upload."""
from pathlib import Path
from shutil import copyfile


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    images = root / "fastlane/metadata/android/en-US/images"
    output = root / ".tmp/landing"
    output.mkdir(parents=True, exist_ok=True)
    for source, name in (
        (root / "site/index.html", "index.html"),
        (root / "site/site.css", "site.css"),
        (images / "icon.png", "icon.png"),
        (images / "phoneScreenshots/02-live-workout.png", "workout.png"),
    ):
        copyfile(source, output / name)


if __name__ == "__main__":
    main()
