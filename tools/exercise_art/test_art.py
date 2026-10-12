"""Checks for the mask encoder: python3 -P tools/exercise_art/test_art.py."""
import io
import sys
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import art  # noqa: E402


class ArtTest(unittest.TestCase):
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
