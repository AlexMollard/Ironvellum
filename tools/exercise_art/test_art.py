"""Checks for the vector art pipeline: python -P tools/exercise_art/test_art.py (from the tools/exercise_art/.venv)."""
import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import art  # noqa: E402


def box(h=200, w=200, **rects):
    m = np.zeros((h, w), bool)
    for (y0, y1, x0, x1) in rects.values():
        m[y0:y1, x0:x1] = True
    return m


def ring(h, w, outer, inner):
    m = np.zeros((h, w), bool)
    y0, y1, x0, x1 = outer
    m[y0:y1, x0:x1] = True
    y0, y1, x0, x1 = inner
    m[y0:y1, x0:x1] = False
    return m


class ArtTest(unittest.TestCase):
    def test_frames_share_one_ground_and_centre(self):
        a = ("M0 0", box(a=(10, 60, 10, 30)))
        b = ("M0 0", box(b=(100, 190, 90, 150)))
        window, shifts = art.register([a, b], hanging=False)
        # both end on the first frame's lowest ink row, and are centred on its middle
        self.assertEqual(shifts[0], (0, 0))
        self.assertEqual(100 + shifts[1][1] + 90, 60)
        self.assertAlmostEqual((90 + 150) / 2 + shifts[1][0], (10 + 30) / 2)
        self.assertGreater(window[2], 0)

    def test_a_hanging_exercise_keeps_its_bar_by_the_top(self):
        a = ("M0 0", box(a=(10, 100, 50, 150)))
        b = ("M0 0", box(b=(30, 180, 50, 150)))
        _, shifts = art.register([a, b], hanging=True)
        self.assertEqual(30 + shifts[1][1], 10)

    def test_a_second_contour_is_a_halo_and_a_single_one_is_not(self):
        single = ring(200, 200, (40, 160, 40, 160), (50, 150, 50, 150))
        doubled = single | ring(200, 200, (34, 166, 34, 166), (37, 163, 37, 163))
        self.assertLess(art.halo_score(single), art.HALO)
        self.assertGreater(art.halo_score(doubled), art.halo_score(single) * 1.45)

    def test_heavy_strokes_are_noted_and_empty_frames_dropped(self):
        thin = ring(400, 400, (20, 380, 20, 380), (24, 376, 24, 376))
        heavy = ring(400, 400, (20, 380, 20, 380), (50, 350, 50, 350))
        empty = box(400, 400, e=(5, 7, 5, 30))
        ok, why, _ = art.analyse([thin, heavy, empty])
        self.assertEqual(ok, [True, True, False])
        self.assertIn("heavy", why[1])

    def test_the_file_round_trips_and_keeps_paths_verbatim(self):
        d = "M298.955 5.977c-3.049.394-12.049 2.553-20 4.798z"
        text = art.write_paths((1.5, 2, 300, 400), [(0, 0), (-3.25, 7)], [d, d])
        window, frames = art.read_paths(text)
        self.assertEqual(window, (1.5, 2.0, 300.0, 400.0))
        self.assertEqual(frames, [(0.0, 0.0, d), (-3.25, 7.0, d)])

    def test_tracing_a_pose_gives_a_path_that_renders_back_to_it(self):
        m = box(120, 120, a=(20, 100, 30, 40), b=(20, 30, 30, 90))
        d = art.trace(m)
        text = art.write_paths((0, 0, 120, 120), [(0, 0)], [d])
        img = np.asarray(art.render_paths(text, width=120, height=120, ink="#ffffff", ground="#000000")[0].convert("L")) > 127
        self.assertGreater((img & m).sum() / m.sum(), 0.97)
        self.assertLess((img & ~m).sum() / m.sum(), 0.05)


    def test_hairline_and_heavy_frames_come_out_at_one_weight(self):
        hairline = ring(400, 400, (20, 380, 20, 380), (22, 378, 22, 378))
        heavy = ring(400, 400, (20, 380, 20, 380), (34, 366, 34, 366))
        for m in (hairline, heavy):
            self.assertAlmostEqual(art.stroke_width(art.reweight(m, 6.0)), 6.0, delta=1.5)

    def test_reweighting_a_file_keeps_its_frames_and_pose(self):
        a = art.trace(box(400, 400, a=(40, 360, 100, 104), b=(40, 44, 100, 300)))
        b = art.trace(box(400, 400, a=(40, 360, 100, 110), b=(40, 50, 100, 300)))
        window, frames = art.read_paths(art.reweight_paths(art.write_paths((0, 0, 400, 400), [(0, 0), (0, 0)], [a, b])))
        self.assertEqual(len(frames), 2)
        self.assertEqual(max(window[2:]), art.WEIGHT_RES)
        widths = [art.stroke_width(np.asarray(i.convert("L")) > 127) for i in art.render_paths(
            art.write_paths(window, [(0, 0)] * 2, [d for _, _, d in frames]), ink="#ffffff", ground="#000000")]
        for w in widths:
            self.assertAlmostEqual(w, art.WEIGHT * art.WEIGHT_RES, delta=2.5)

if __name__ == "__main__":
    unittest.main()
