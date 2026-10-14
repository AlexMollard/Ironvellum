"""Approved replacement drawings override upstream art, and approval is the pinned art catalogue."""
import hashlib
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

    def test_replacement_requires_a_matching_catalogue_entry_then_maps_aliases(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            png = root / 'png'
            png.mkdir()
            missing, catalog, counts, art_catalogue = (root / n for n in (
                'missing.json', 'replacements.json', 'counts.json', 'catalogue.json'))
            missing.write_text('{"entries": []}')
            catalog.write_text(json.dumps({'entries': [{'name': 'fly', 'slug': 'fly', 'kind': 'replacement',
                'aliases': ['fly', 'chest fly'], 'sourceArt': 'ek_old', 'frameCount': 1}]}))
            counts.write_text('{"fly": 1}')
            image = b'approved image bytes'

            def listed(*rows):
                art_catalogue.write_text(json.dumps({'exercises': [
                    {'id': slug, 'png': {'file': 'png/%s.png' % slug, 'sha256': digest}} for slug, digest in rows]}))

            mp = {'mapped': [], 'techniques': {'technique': {'art': 'ek_old', 'frames': 3, 'proxy': True}}}
            with patch.multiple(build, ART=root, ART_PNG=png, CATALOGUE=art_catalogue, MISSING=missing,
                                REPLACEMENTS=catalog, FRAME_COUNTS=counts):
                (png / 'fly.png').write_bytes(image)
                listed()                                          # image present, not in the catalogue
                self.assertEqual(build.apply_owner(mp), {})
                listed(('fly', 'wrong hash'))                     # in the catalogue, hash differs
                self.assertEqual(build.apply_owner(mp), {})
                listed(('fly', hashlib.sha256(image).hexdigest()))
                (png / 'fly.png').unlink()                        # in the catalogue, file absent
                self.assertEqual(build.apply_owner(mp), {})
                (png / 'fly.png').write_bytes(image)
                self.assertEqual(build.apply_owner(mp), {'own_fly': False})
            self.assertEqual(build.art_table([], mp), {'fly': ('own_fly', 1), 'chest fly': ('own_fly', 1)})
            self.assertEqual(mp['techniques']['technique']['art'], 'own_fly')
            self.assertTrue(mp['techniques']['technique']['proxy'])

    def test_technique_flag_also_draws_the_skill_node_and_a_missing_submodule_stops_the_build(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'png').mkdir()
            image = b'still'
            (root / 'png' / 'cobra-squat.png').write_bytes(image)
            (root / 'catalogue.json').write_text(json.dumps({'exercises': [
                {'id': 'cobra-squat', 'png': {'file': 'png/cobra-squat.png', 'sha256': hashlib.sha256(image).hexdigest()}}]}))
            missing, catalog, counts = (root / n for n in ('missing.json', 'replacements.json', 'counts.json'))
            missing.write_text('{"entries": []}')
            catalog.write_text(json.dumps({'entries': [{'name': 'Cobra Squat', 'slug': 'cobra-squat', 'kind': 'movement',
                'frameCount': 1, 'technique': True}]}))
            counts.write_text('{}')
            mp = {'mapped': [], 'techniques': {}}
            with patch.multiple(build, ART=root, ART_PNG=root / 'png', CATALOGUE=root / 'catalogue.json', MISSING=missing,
                                REPLACEMENTS=catalog, FRAME_COUNTS=counts):
                build.apply_owner(mp)
                self.assertEqual(mp['techniques']['Cobra Squat'], {'exercise': 'Cobra Squat', 'art': 'own_cobra-squat',
                                                                   'frames': 1, 'confidence': 'owner', 'proxy': False})
                self.assertEqual(build.art_table([], mp)['Cobra Squat'], ('own_cobra-squat', 1))
                with patch.object(build, 'CATALOGUE', root / 'absent.json'):
                    with self.assertRaises(SystemExit) as stop:
                        build.apply_owner({'mapped': [], 'techniques': {}})
                    self.assertIn('git submodule update --init art', str(stop.exception))


if __name__ == '__main__':
    unittest.main()
