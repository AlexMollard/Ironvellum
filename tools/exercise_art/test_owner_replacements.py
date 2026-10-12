"""Approved replacement drawings override upstream art without bypassing approval."""
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build


class OwnerReplacementTest(unittest.TestCase):
    def test_owner_mapping_overrides_library_source(self):
        lib = [{'name': 'fly', 'art': 'ek_old', 'frames': 3}]
        mp = {'mapped': [{'exercise': 'fly', 'art': 'own_fly', 'frames': 1,
                          'used': True, 'confidence': 'owner'}], 'techniques': {}}
        self.assertEqual(build.art_table(lib, mp)['fly'], ('own_fly', 1))

    def test_replacement_requires_approval_and_all_frames_then_maps_aliases(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            missing, catalog, counts = (root / n for n in ('missing.json', 'replacements.json', 'counts.json'))
            missing.write_text('{"entries": []}')
            catalog.write_text(json.dumps({'entries': [{'name': 'fly', 'slug': 'fly', 'kind': 'replacement',
                'aliases': ['fly', 'chest fly'], 'sourceArt': 'ek_old', 'frameCount': 1}]}))
            counts.write_text('{"fly": 1}')
            mp = {'mapped': [], 'techniques': {'technique': {'art': 'ek_old', 'frames': 3, 'proxy': True}}}
            with patch.multiple(build, OWNER=root, MISSING=missing, REPLACEMENTS=catalog, FRAME_COUNTS=counts):
                (root / 'fly-1.png').touch()
                self.assertEqual(build.apply_owner(mp), {})
                (root / 'APPROVED').write_text('fly\n')
                (root / 'fly-1.png').unlink()
                self.assertEqual(build.apply_owner(mp), {})
                (root / 'fly-1.png').touch()
                self.assertEqual(build.apply_owner(mp), {'own_fly': False})
            self.assertEqual(build.art_table([], mp), {'fly': ('own_fly', 1), 'chest fly': ('own_fly', 1)})
            self.assertEqual(mp['techniques']['technique']['art'], 'own_fly')
            self.assertTrue(mp['techniques']['technique']['proxy'])


if __name__ == '__main__':
    unittest.main()
