"""Checks for the mask encoder: python3 -P tools/exercise_art/test_art.py."""
import io
import sys
import unittest
import tempfile
import json
from unittest.mock import patch
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import art  # noqa: E402
import build  # noqa: E402


class ArtTest(unittest.TestCase):
    def test_owner_encode_preserves_soft_alpha_and_three_cells(self):
        with tempfile.TemporaryDirectory() as tmp:
            alpha = np.tile(np.arange(256, dtype=np.uint8), (256, 1))
            rgba = np.full((256, 256, 4), 255, np.uint8)
            rgba[:, :, 3] = alpha
            for n in (1, 2, 3):
                Image.fromarray(rgba).save(Path(tmp) / f"pose-{n}.png")
            np.testing.assert_array_equal(art.coverage("own", tmp, "pose", 0), alpha)
            data, cw, ch = art.encode("own", tmp, "pose", 3)
            result = np.asarray(Image.open(io.BytesIO(data)))
            self.assertEqual(result.shape, (ch, cw * 3, 3))
            self.assertGreater(len(np.unique(result)), 200)
            np.testing.assert_array_equal(result[:, :cw], result[:, cw:2*cw])

    def test_owner_only_preserves_upstream_assets(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            upstream = folder / "ek_existing.webp"
            upstream.write_bytes(b"keep upstream unchanged")
            with patch.object(build, "ASSETS", folder), patch.object(build, "KT", folder), \
                    patch.object(build, "load", return_value=([], {"mapped": [], "techniques": {}}, {})), \
                    patch.object(build, "apply_owner", return_value=set()), \
                    patch.object(sys, "argv", ["build.py", "--owner-only"]):
                build.main()
            self.assertEqual(upstream.read_bytes(), b"keep upstream unchanged")

    def test_owner_mapping_requires_complete_three_frame_set(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            brief = folder / "missing.json"
            brief.write_text(json.dumps({"entries": [
                {"slug": "frog-stand", "name": "Frog Stand", "kind": "technique"},
                {"slug": "row", "name": "Row", "kind": "exercise"},
            ]}))
            for slug in ("frog-stand", "row"):
                for n in (1, 2):
                    (folder / f"{slug}-{n}.png").touch()
            mp = {"mapped": [], "techniques": {}}
            with patch.object(build, "OWNER", folder), patch.object(build, "MISSING", brief):
                self.assertEqual(build.apply_owner(mp), set())
                self.assertEqual(mp["techniques"], {})
                for slug in ("frog-stand", "row"):
                    (folder / f"{slug}-3.png").touch()
                self.assertEqual(build.apply_owner(mp), {"own_frog-stand", "own_row"})
            self.assertEqual(build.art_table([], mp)["Frog Stand"], ("own_frog-stand", 3))
            self.assertEqual(build.art_table([], mp)["Row"], ("own_row", 3))

    def test_strip_keeps_frames_in_one_crop(self):
        a = np.zeros((100, 100), np.uint8)
        b = np.zeros((100, 100), np.uint8)
        a[10:20, 10:20] = 255
        b[60:80, 40:90] = 255
        img, cw, ch = art.strip([a, b])
        self.assertEqual(img.size, (cw * 2, ch))
        self.assertEqual(max(cw, ch), art.MAX_SIDE)

    def test_quantise_leaves_only_the_declared_levels(self):
        img = Image.fromarray(np.arange(256, dtype=np.uint8).reshape(16, 16), "L")
        levels = set(np.asarray(art.quantise(img)).ravel().tolist())
        self.assertEqual(len(levels), art.LEVELS)

    def test_faint_frames_are_lifted(self):
        a = np.zeros((50, 50), np.uint8)
        a[5:40, 5:40] = 100
        self.assertGreater(int(art.normalise(a).max()), 200)

    def test_output_is_lossless_webp_without_colour(self):
        a = np.zeros((60, 60), np.uint8)
        a[10:50, 20:30] = 255
        img, _, _ = art.strip([a, a])
        buf = io.BytesIO()
        art.quantise(img).convert("RGB").save(buf, "WEBP", lossless=True)
        data = buf.getvalue()
        self.assertEqual(data[:4], b"RIFF")
        self.assertEqual(data[12:16], b"VP8L")


if __name__ == "__main__":
    unittest.main()
