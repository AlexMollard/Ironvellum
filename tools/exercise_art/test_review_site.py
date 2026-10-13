"""The local review site can reuse approved masters after draft cleanup."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class ReviewSiteTest(unittest.TestCase):
    def test_owner_fallback_requires_approval_and_preserves_new_drafts(self):
        with tempfile.TemporaryDirectory() as temporary:
            art = Path(temporary) / "tools/exercise_art"
            site = art / "review-site"
            owner = art / "owner"
            site.mkdir(parents=True)
            owner.mkdir()
            shutil.copy2(Path(__file__).parent / "review-site/build.py", site / "build.py")
            for name in ("index.html", "style.css", "app.js"):
                (site / name).write_text("")
            image = "example-1.png"
            master = b"approved image bytes"
            (owner / image).write_bytes(master)
            (owner / "APPROVED").write_text("example\n")

            def save(name, value):
                (art / name).write_text(json.dumps(value))

            save("missing.json", {"entries": [{"slug": "example", "name": "Example",
                 "kind": "movement", "frameCount": 1, "poses": ["Full hold"]}]})
            save("generation-review.json", {"batchSize": 1, "frames": [{"id": image}],
                 "reference": "tools/exercise_art/owner/" + image})
            decision = {"id": image, "status": "approved", "sha256": hashlib.sha256(master).hexdigest()}

            def build():
                save("review-decisions.json", {"frames": [decision]})
                subprocess.run([sys.executable, "-P", str(site / "build.py"), "--batch", "1"],
                               check=True, capture_output=True, text=True)
                return json.loads((site / "dist/manifest.json").read_text())

            self.assertEqual(build()["available"], 1)
            self.assertEqual((site / "dist/frames" / image).read_bytes(), master)
            decision["status"] = "redo"
            self.assertEqual(build()["available"], 0)
            self.assertFalse((site / "dist/frames" / image).exists())
            decision["status"] = "approved"
            decision["sha256"] = "wrong hash"
            self.assertEqual(build()["available"], 0)
            decision["sha256"] = hashlib.sha256(master).hexdigest()
            (owner / "APPROVED").write_text("")
            self.assertEqual(build()["available"], 0)
            (owner / "APPROVED").write_text("example\n")
            drafts = art / "drafts/batch-001"
            drafts.mkdir(parents=True)
            (drafts / image).write_bytes(b"pending replacement")
            result = build()
            self.assertEqual(result["available"], 1)
            self.assertEqual((site / "dist/frames" / image).read_bytes(), b"pending replacement")
            self.assertNotEqual(result["exercises"][0]["frames"][0]["sha256"], decision["sha256"])


if __name__ == "__main__":
    unittest.main()
