import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import web_art
import bodymap_web


class WebArtTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for path in ('art/svg', 'web/src/generated'):
            (self.root / path).mkdir(parents=True)
        (self.root / 'art/svg/a.svg').write_bytes(b'<svg>a</svg>')
        (self.root / 'art/svg/b.svg').write_bytes(b'<svg>b</svg>')
        (self.root / 'art/ATTRIBUTION.md').write_text('CC BY-SA 4.0', encoding='utf-8')
        (self.root / 'web/src/generated/art-index.json').write_text(json.dumps({'one': 'a'}), encoding='utf-8')

    def test_copy_is_deterministic_and_only_indexed(self):
        web_art.sync(self.root)
        first = {p.name: p.read_bytes() for p in (self.root / 'web/public/art').iterdir()}
        web_art.sync(self.root)
        self.assertEqual(first, {p.name: p.read_bytes() for p in (self.root / 'web/public/art').iterdir()})
        self.assertEqual(set(first), {'a.svg', 'CREDIT.txt'})
        self.assertIn(b'Colours changed', first['CREDIT.txt'])

    def test_check_fails_on_stale_copy(self):
        web_art.sync(self.root)
        (self.root / 'web/public/art/a.svg').write_bytes(b'changed')
        with patch.object(web_art, 'check_pin'), self.assertRaises(ValueError):
            web_art.sync(self.root, check=True)

    def test_check_rejects_extra_files(self):
        web_art.sync(self.root)
        (self.root / 'web/public/art/extra.svg').write_bytes(b'extra')
        with patch.object(web_art, 'check_pin'), self.assertRaises(ValueError):
            web_art.sync(self.root, check=True)


class BodyMapTest(unittest.TestCase):
    def test_deterministic_and_both_figures(self):
        first = bodymap_web.generate()
        self.assertEqual(first, bodymap_web.generate())
        data = json.loads(first.decode().split(' = ', 1)[1].split(';', 1)[0])
        self.assertEqual(set(data), {'male', 'female'})
        for figure in data.values():
            self.assertTrue(figure['outline'])
            for view in ('front', 'back'):
                self.assertTrue(all(row['muscle'] and row['polygons'] for row in figure[view]))


if __name__ == '__main__':
    unittest.main()
