import argparse, hashlib, json, shutil
from pathlib import Path
from datetime import datetime, timezone

p=argparse.ArgumentParser()
p.add_argument('--limit', type=int)
p.add_argument('--batch', type=int, default=1)
a=p.parse_args()
root=Path(__file__).resolve().parents[3]
dist=Path(__file__).resolve().parent/'dist'
(dist/'frames').mkdir(parents=True,exist_ok=True)
for name in ('index.html','style.css','app.js'):
    shutil.copy2(Path(__file__).resolve().parent/name,dist/name)
source=json.loads((root/'tools/exercise_art/missing.json').read_text(encoding='utf-8'))
art=root/'tools/exercise_art'
replacement_path=art/'replacement-catalog.json'
if replacement_path.exists():
    source['entries'].extend(json.loads(replacement_path.read_text(encoding='utf-8'))['entries'])
counts_path=art/'frame-counts.json'
counts=json.loads(counts_path.read_text(encoding='utf-8')) if counts_path.exists() else {}
generation=json.loads((art/'generation-review.json').read_text(encoding='utf-8'))
if a.limit is None:
    a.limit=generation.get('batchSize',50)
batch_ids={f['id'] for f in generation['frames'][(a.batch-1)*a.limit:a.batch*a.limit]}
for batch in generation.get('batches',[]):
    if batch['batch']==a.batch:
        batch_ids=set(batch['ids'])
overrides_path=art/'pose-overrides.json'
overrides=json.loads(overrides_path.read_text(encoding='utf-8')) if overrides_path.exists() else {}
reviews_path=art/'review-decisions.json'
reviews=json.loads(reviews_path.read_text(encoding='utf-8')) if reviews_path.exists() else {'frames':[]}
anchors_path=art/'frame-anchors.json'
anchors=json.loads(anchors_path.read_text(encoding='utf-8'))['sequences'] if anchors_path.exists() else {}
entries=[]; available=0
for e in source['entries']:
    frames=[]
    frame_count=counts.get(e['slug'],e.get('frameCount',3))
    for i in range(1,frame_count+1):
        name=f"{e['slug']}-{i}.png"
        path=root/'tools/exercise_art/drafts'/f'batch-{a.batch:03d}'/name
        in_batch=name in batch_ids
        f={'id':name,'number':i,'pose':overrides.get(name,e['poses'][i-1]),'available':False}
        if path.exists() and in_batch:
            shutil.copy2(path,dist/'frames'/name)
            f.update(available=True,src='frames/'+name,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
            available+=1
        frames.append(f)
    alignment=anchors.get(e['slug'])
    if alignment and any(f['available'] and f['sha256'] != expected for f,expected in zip(frames,alignment.get('hashes',[]))):
        raise ValueError(f"Source image changed; recalibrate anchors for {e['slug']}")
    if alignment:
        if len(alignment['points']) != frame_count:
            raise ValueError(f"Anchor count mismatch: {e['slug']}")
        for f, point in zip(frames,alignment['points']):
            f['anchor']=point
    entries.append({'slug':e['slug'],'name':e['name'],'group':e.get('line',e['kind']),'note':e.get('note',''),'frames':frames,'alignment':alignment})
shutil.copy2(root/'docs/art/style-options/approved.png',dist/'approved.png')
active_ids={f['id'] for e in entries for f in e['frames']}
packaged_ids={f['id'] for e in entries for f in e['frames'] if f['available']}
for stale in (dist/'frames').glob('*.png'):
    if stale.name not in packaged_ids:
        stale.unlink()
manifest={'version':1,'batch':a.batch,'batchSize':a.limit,'batchExpected':len(batch_ids & active_ids),'generatedAt':datetime.now(timezone.utc).isoformat(),'totalExpected':len(active_ids),'available':available,'exercises':entries,'reviewDecisions':reviews.get('frames',[])}
(dist/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(f'Built batch {a.batch}: {available}/{a.limit} available, {len(entries)} exercises')
