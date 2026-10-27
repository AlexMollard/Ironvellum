#!/usr/bin/env python3
"""Export the phone's packed figure geometry for the web, without approximating it."""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROGRAM = ROOT / 'app/src/main/kotlin/com/ironvellum/app/ui/program'
OUTPUT = ROOT / 'web/src/generated/bodyMapShapes.js'


def pieces(packed):
    result = []
    for piece in packed.split('|'):
        numbers = [int(n) for n in piece.split()]
        if not numbers or len(numbers) % 2:
            raise ValueError('Invalid packed body polygon')
        result.append([numbers[i:i + 2] for i in range(0, len(numbers), 2)])
    return result


def generate():
    shapes = (PROGRAM / 'BodyMapShapes.kt').read_text(encoding='utf-8')
    figures = (PROGRAM / 'BodyFigures.kt').read_text(encoding='utf-8')
    tables = {}
    for view in ('FRONT', 'BACK'):
        match = re.search(r'private val ' + view + r'_PARTS = listOf\((.*?)\n\)', figures, re.S)
        if not match:
            raise ValueError('Missing part table: ' + view)
        tables[view.lower()] = re.findall(r'"([^"]+)"\s+to\s+([A-Z_]+)', match.group(1))
        if not tables[view.lower()]:
            raise ValueError('Empty part table: ' + view)
    result = {}
    for sex in ('MALE', 'FEMALE'):
        match = re.search(r'val ' + sex + r' = BodyShapes\((.*?)\n    \)', shapes, re.S)
        if not match:
            raise ValueError('Missing figure: ' + sex)
        body = match.group(1)
        figure = {}
        for name in ('outline', 'hairFront', 'hairBack'):
            found = re.search(r'\b' + name + r' = "([^"]*)"', body)
            if not found:
                raise ValueError('Missing ' + sex + '.' + name)
            decoded = pieces(found.group(1))
            figure[name] = decoded[0] if name == 'outline' and len(decoded) == 1 else decoded
        for view in ('front', 'back'):
            found = re.search(r'\b' + view + r' = mapOf\((.*?)\n        \)', body, re.S)
            if not found:
                raise ValueError('Missing ' + sex + '.' + view)
            parts = dict(re.findall(r'"([^"]+)"\s+to\s+"([^"]*)"', found.group(1)))
            table = tables[view]
            if set(parts) != {key for key, _ in table}:
                raise ValueError('Body part mismatch: ' + sex + '.' + view)
            figure[view] = [{'muscle': muscle, 'polygons': pieces(parts[key])} for key, muscle in table]
        result[sex.lower()] = figure
    return ('// Generated from BodyMapShapes.kt and BodyFigures.kt; do not edit.\r\n'
            'export const bodyMapShapes = ' + json.dumps(result, ensure_ascii=False, separators=(',', ':')) + ';\r\n').encode('utf-8')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data = generate()
    if args.check:
        if not OUTPUT.is_file() or OUTPUT.read_bytes() != data:
            raise SystemExit('Body map is stale; run python tools/bodymap_web.py')
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_bytes(data)


if __name__ == '__main__':
    main()
