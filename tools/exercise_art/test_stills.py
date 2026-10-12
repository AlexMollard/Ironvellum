"""Selected stills retain their source pose and survive rebuilds and reselection."""
import tempfile
import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from stills import apply_selections


class StillTest(unittest.TestCase):
    def test_reselect_from_preserved_animation_after_first_conversion(self):
        with tempfile.TemporaryDirectory() as tmp:
            assets = Path(tmp) / 'assets'
            archive = Path(tmp) / 'archive'
            assets.mkdir()
            path = assets / 'wg_example.paths'
            original = 'v1 0 0 100 100\r\n0 0 M1 1L2 2Z\r\n3 4 M5 5L6 6Z\r\n'
            path.write_bytes(original.encode())
            report = {'wg_example': {'source': 3, 'kept': [0, 2], 'why': ['', 'broken', '']}}
            apply_selections(assets, report, {'wg_example': {'sourceFrame': 2}}, archive)
            self.assertEqual(report['wg_example']['kept'], [2])
            self.assertIn('3 4 M5 5L6 6Z', path.read_text())
            self.assertEqual((archive / path.name).read_bytes(), original.encode())
            apply_selections(assets, report, {'wg_example': {'sourceFrame': 0}}, archive)
            self.assertEqual(report['wg_example']['kept'], [0])
            self.assertIn('0 0 M1 1L2 2Z', path.read_text())
            before = path.read_bytes()
            apply_selections(assets, report, {'wg_example': {'sourceFrame': 0}}, archive, check=True)
            self.assertEqual(path.read_bytes(), before)

    def test_reject_dropped_frame_without_changing_asset(self):
        with tempfile.TemporaryDirectory() as tmp:
            assets = Path(tmp)
            path = assets / 'ek_example.paths'
            original = b'v1 0 0 10 10\r\n0 0 M1 1Z\r\n0 0 M2 2Z\r\n'
            path.write_bytes(original)
            report = {'ek_example': {'source': 3, 'kept': [0, 2]}}
            with self.assertRaises(ValueError):
                apply_selections(assets, report, {'ek_example': {'sourceFrame': 1}}, assets / 'archive')
            self.assertEqual(path.read_bytes(), original)


if __name__ == '__main__':
    unittest.main()
