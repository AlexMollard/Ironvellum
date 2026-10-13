"""Validate a fully inspected replacement batch and queue it for user approval."""
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from prepare_replacements import write

batch = int(sys.argv[1])
folder = HERE / 'drafts' / f'batch-{batch:03d}'
manifest = json.loads((folder / 'prompts.json').read_text())
jobs = manifest['jobs']
generation = json.loads((HERE / 'generation-review.json').read_text())
configured_batch = next(b for b in generation['batches'] if b['batch'] == batch)
assert jobs and len(jobs) == len(configured_batch['ids'])
assert {j['id'] for j in jobs} == set(configured_batch['ids'])
for job in jobs:
    path = folder / job['id']
    assert job['status'] == 'generated', job['id']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == job['sha256'], job['id']
    with Image.open(path) as im:
        im.verify()
    with Image.open(path) as im:
        assert im.mode == 'RGBA' and im.width == im.height and im.width >= 512, job['id']
        assert im.getextrema()[3] == (0, 255), job['id']
        hist = im.getchannel('A').histogram()
        assert hist[0] / sum(hist) > 0.5, job['id']
        assert 0.005 < sum(hist[128:]) / sum(hist) < 0.3, job['id']
manifest.update(status='awaiting-user-review', completedAt=datetime.now(timezone.utc).isoformat(),
                qa={'visuallyInspected': len(jobs), 'headStyle': 'Smooth ear-free blank oval',
                    'method': 'Contrasting-background contact sheets and PNG/hash/transparency checks',
                    'approval': 'User approval required for every replacement'})
write(folder / 'prompts.json', manifest)
generation = json.loads((HERE / 'generation-review.json').read_text())
b = next(b for b in generation['batches'] if b['batch'] == batch)
b.update(status='awaiting-user-review', available=len(jobs), pending=len(jobs), approved=0, blocked=[])
generation.update(currentBatch=batch, status='awaiting-user-review')
generation['correctionStatus'] = {
    'batch': batch,
    'correctedAwaitingReview': [j['id'] for j in jobs if j.get('previousAttempts')],
    'remainingRedo': 0,
    'missing': [],
    'note': 'All new images, including ear corrections, await user approval.'
}
write(HERE / 'generation-review.json', generation)
print(f'Validated {len(jobs)} PNGs and hashes; batch {batch} awaits user approval')
