"""The local review site can reuse approved art from the art submodule after draft cleanup."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class ReviewSiteTest(unittest.TestCase):
    def test_art_fallback_requires_a_catalogue_match_and_preserves_new_drafts(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            art = root / "tools/exercise_art"
            site = art / "review-site"
            png = root / "art/png"
            site.mkdir(parents=True)
            png.mkdir(parents=True)
            shutil.copy2(Path(__file__).parent / "review-site/build.py", site / "build.py")
            for name in ("index.html", "style.css", "app.js"):
                (site / name).write_text("")
            image = "example-1.png"
            master = b"approved image bytes"
            (png / "example.png").write_bytes(master)

            def save(name, value):
                (art / name).write_text(json.dumps(value))

            def catalogue(*rows):
                (root / "art/catalogue.json").write_text(json.dumps({"exercises": [
                    {"id": slug, "png": {"file": "png/%s.png" % slug, "sha256": digest}} for slug, digest in rows]}))

            save("missing.json", {"entries": [{"slug": "example", "name": "Example",
                 "kind": "movement", "frameCount": 1, "poses": ["Full hold"]}]})
            save("generation-review.json", {"batchSize": 1, "frames": [{"id": image}],
                 "reference": "art/png/example.png"})
            good = hashlib.sha256(master).hexdigest()

            def build():
                save("review-decisions.json", {"frames": []})
                subprocess.run([sys.executable, "-P", str(site / "build.py"), "--batch", "1"],
                               check=True, capture_output=True, text=True)
                return json.loads((site / "dist/manifest.json").read_text())

            catalogue(("example", good))
            self.assertEqual(build()["available"], 1)
            self.assertEqual((site / "dist/frames" / image).read_bytes(), master)
            catalogue(("example", "wrong hash"))
            self.assertEqual(build()["available"], 0)
            self.assertFalse((site / "dist/frames" / image).exists())
            catalogue()
            self.assertEqual(build()["available"], 0)
            catalogue(("example", good))
            drafts = art / "drafts/batch-001"
            drafts.mkdir(parents=True)
            (drafts / image).write_bytes(b"pending replacement")
            result = build()
            self.assertEqual(result["available"], 1)
            self.assertEqual((site / "dist/frames" / image).read_bytes(), b"pending replacement")
            self.assertNotEqual(result["exercises"][0]["frames"][0]["sha256"], good)


if __name__ == "__main__":
    unittest.main()
