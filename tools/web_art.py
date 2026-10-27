#!/usr/bin/env python3
"""Copy the exact indexed exercise SVGs and their attribution to the web site."""
import argparse
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def expected_files(root=ROOT):
    index = json.loads((root / 'web/src/generated/art-index.json').read_text(encoding='utf-8'))
    if not isinstance(index, dict) or any(not isinstance(v, str) for v in index.values()):
        raise ValueError('art-index.json must map names to SVG IDs')
    result = {}
    for art_id in sorted(set(index.values())):
        if not art_id or Path(art_id).name != art_id or art_id in ('.', '..'):
            raise ValueError('Invalid art ID: ' + art_id)
        result[art_id + '.svg'] = (root / 'art/svg' / (art_id + '.svg')).read_bytes()
    credit = (root / 'art/ATTRIBUTION.md').read_bytes().rstrip(b'\r\n')
    result['CREDIT.txt'] = credit + b'\r\n\r\nColours changed to follow the page theme.\r\n'
    return result


def check_pin(root=ROOT):
    pinned = subprocess.run(['git', 'ls-tree', 'HEAD', 'art'], cwd=root, check=True, capture_output=True, text=True).stdout.split()
    actual = subprocess.run(['git', '-C', str(root / 'art'), 'rev-parse', 'HEAD'], cwd=root, check=True, capture_output=True, text=True).stdout.strip()
    if len(pinned) < 3 or pinned[1] != 'commit' or pinned[2] != actual:
        raise ValueError('art checkout does not match the pinned submodule commit')


def sync(root=ROOT, check=False):
    expected = expected_files(root)
    dest = root / 'web/public/art'
    actual = {p.name for p in dest.iterdir() if p.is_file()} if dest.is_dir() else set()
    if check:
        check_pin(root)
        if actual != set(expected) or any((dest / name).read_bytes() != data for name, data in expected.items() if name in actual):
            raise ValueError('web art is stale; run python tools/web_art.py')
        return
    dest.mkdir(parents=True, exist_ok=True)
    for name in actual - set(expected):
        (dest / name).unlink()
    for name, data in expected.items():
        (dest / name).write_bytes(data)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    sync(check=args.check)


if __name__ == '__main__':
    main()
