"""Reject workstation paths in tracked source, docs and provenance metadata."""
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKSTATION_PATH = re.compile(r"\b[A-Za-z]:[\\/]|/(?:home|Users)/[^\s/]+/")


class PortablePathsTest(unittest.TestCase):
    def test_tracked_files_do_not_depend_on_a_workstation_directory(self):
        paths = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
        failures = []
        for name in filter(None, paths):
            path = ROOT / name
            if not path.is_file():  # Git submodules are separate repositories.
                continue
            if path.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp", ".gif", ".ttf", ".woff", ".woff2", ".paths"}:
                continue
            data = path.read_bytes()
            if b"\0" in data:
                continue
            for line, value in enumerate(data.decode("utf-8", "replace").splitlines(), 1):
                if WORKSTATION_PATH.search(value):
                    failures.append(f"{name}:{line}")
        self.assertFalse(failures, "Use repository/user-relative paths or explicit configuration: " + ", ".join(failures[:20]))


if __name__ == "__main__":
    unittest.main()
