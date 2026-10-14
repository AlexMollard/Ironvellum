"""Prepare resumable owner-art replacement batches, without touching upstream clones."""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build


def write(path, value):
    path.write_bytes((json.dumps(value, indent=2, ensure_ascii=False) + '\n').replace('\n', '\r\n').encode('utf-8'))


def main():
    if (HERE / 'replacement-catalog.json').exists():
        generation = json.loads((HERE / 'generation-review.json').read_text())
        print(f'Existing replacement queue preserved; resume batch {generation["currentBatch"]}')
        return
    lib, mapping, guides = build.load()
    build.apply_owner(mapping)
    names = {}
    for name, (aid, _) in build.art_table(lib, mapping).items():
        names.setdefault(aid, []).append(name)
    selections = json.loads((HERE / 'still-selections.json').read_text())['selections']
    approved = build.approved_art()
    generation = json.loads((HERE / 'generation-review.json').read_text())
    base = generation['stylePrompt'] + ' Smooth egg-shaped blank oval head: ABSOLUTELY NO EARS or ear bumps, no eyes, nose, mouth, eyebrows or hair. Single uniform contour weight about 0.8% of canvas width; all enclosed interiors transparent. Square canvas, entire figure and necessary equipment centered, occupying 70-80%, generous clear margins. Realistic joints, neutral head aligned with chest, fingers and thumbs grip correctly. No labels, arrows, floor line, shadows, panels, border, glow or extra figures. '
    entries = []
    for aid, selection in selections.items():
        aliases = names[aid]
        if any(build.slugify(name) in approved for name in aliases):
            continue
        name = aliases[0]
        slug = build.slugify(name)
        guide = guides.get(name, {})
        description = ' '.join([guide.get('setup', ''), *guide.get('steps', []), *guide.get('cues', [])])
        pose = f'Redraw the selected source pose for {name}, source frame {selection["sourceFrame"] + 1}, with correct anatomy and equipment. One clear representative still only.'
        entries.append(dict(name=name, slug=slug, kind='replacement', frameCount=1,
                            files=[slug + '-1.png'], poses=[pose], note=description,
                            sourceArt=aid, aliases=aliases, sourceFrame=selection['sourceFrame'],
                            prompt=base + 'Exercise: ' + name + '. ' + pose + ' Movement instructions for anatomical correctness: ' + description + ' The source pose reference is ONLY for pose and equipment; replace its style entirely with the approved white-outline style. Do not copy incorrect anatomy, filled regions, sketchy or double outlines.'))
    assert len({e['slug'] for e in entries}) == len(entries)
    write(HERE / 'replacement-catalog.json', {'schema': 1, 'entries': entries})
    batches = {b['batch']: b for b in generation['batches']}
    for offset in range(0, len(entries), 50):
        number = 5 + offset // 50
        folder = HERE / 'drafts' / f'batch-{number:03d}'
        folder.mkdir(exist_ok=True)
        path = folder / 'prompts.json'
        jobs = [dict(id=e['slug'] + '-1.png', slug=e['slug'], pose=e['poses'][0], prompt=e['prompt'], sourceArt=e['sourceArt'], sourceFrame=e['sourceFrame'], status='planned') for e in entries[offset:offset+50]]
        if not path.exists():
            write(path, {'schema': 1, 'batch': number, 'status': 'planned', 'jobs': jobs})
        if number not in batches:
            batches[number] = dict(batch=number, ids=[j['id'] for j in jobs], status='planned', available=0, blocked=[])
    known = {f['id'] for f in generation['frames']}
    generation['frames'].extend(dict(id=e['slug'] + '-1.png', pose=e['poses'][0], status='planned') for e in entries if e['slug'] + '-1.png' not in known)
    generation.update(expectedTotal=len(generation['frames']), currentBatch=5, status='generating', batches=list(batches.values()))
    write(HERE / 'generation-review.json', generation)
    print(f'Prepared {len(entries)} replacements in {(len(entries)+49)//50} batches; preserved {len(approved)} approved exercises')


if __name__ == '__main__':
    main()
