"""Apply reviewed source-frame choices without retracing or changing line weight."""
import json

import art


def apply_selections(assets, report, selections, archive, check=False):
    pending = []
    for aid, choice in selections.items():
        source_frame = choice['sourceFrame']
        path = assets / (aid + '.paths')
        rep = report[aid]
        text = path.read_text(encoding='utf-8')
        window, frames = art.read_paths(text)
        if len(frames) != len(rep['kept']):
            raise ValueError('Frame/report mismatch: ' + aid)
        if check:
            if len(frames) != 1 or rep['kept'] != [source_frame]:
                raise ValueError('Still selection not built: ' + aid)
            continue
        if len(frames) == 1 and rep['kept'] == [source_frame]:
            continue
        original = None
        if len(frames) == 1:
            text = (archive / path.name).read_text(encoding='utf-8')
            rep = json.loads((archive / (aid + '.json')).read_text(encoding='utf-8'))
            window, frames = art.read_paths(text)
        else:
            original = (path.read_bytes(), dict(rep))
        if source_frame not in rep['kept']:
            raise ValueError('Selected source frame was dropped: ' + aid)
        dx, dy, data = frames[rep['kept'].index(source_frame)]
        selected = art.write_paths(window, [(dx, dy)], [data])
        pending.append((aid, path, selected, original, {**rep, 'kept': [source_frame], 'stillSelection': choice}))
    for aid, path, selected, original, rep in pending:
        if original is not None and not (archive / path.name).exists():
            archive.mkdir(parents=True, exist_ok=True)
            (archive / path.name).write_bytes(original[0])
            (archive / (aid + '.json')).write_bytes((json.dumps(original[1], indent=2) + '\n').replace('\n', '\r\n').encode())
        path.write_bytes(selected.encode('utf-8'))
        report[aid] = rep
    return report
