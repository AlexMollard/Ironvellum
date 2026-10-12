"""Save one built-in imagegen result in the project's independent draft folder."""
import hashlib
import json
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from prepare_replacements import write

batch, name, source = sys.argv[1:4]
folder = HERE / 'drafts' / f'batch-{int(batch):03d}'
manifest = json.loads((folder / 'prompts.json').read_text())
job = next(j for j in manifest['jobs'] if j['id'] == name)
if job.get('sha256'):
    job.setdefault('previousAttempts', []).append({key: job[key] for key in ('source', 'sha256', 'updatedAt') if key in job})
shutil.copy2(source, folder / name)
job.update(status='generated', source=source, sha256=hashlib.sha256((folder / name).read_bytes()).hexdigest(), updatedAt=datetime.now(timezone.utc).isoformat())
write(folder / 'prompts.json', manifest)
generation = json.loads((HERE / 'generation-review.json').read_text())
next(f for f in generation['frames'] if f['id'] == name)['status'] = 'pending'
b = next(b for b in generation['batches'] if b['batch'] == int(batch))
b.update(available=sum(j['status'] == 'generated' for j in manifest['jobs']), status='generating')
generation['generated'] = sum(f['status'] in ('pending', 'approved') for f in generation['frames'])
write(HERE / 'generation-review.json', generation)
print(name, b['available'])
