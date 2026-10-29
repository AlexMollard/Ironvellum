import { node } from './statCard.js';
import { plate } from './plate.js';
import { hiddenName } from './hiddenName.js';

/**
 * The top three of a board: first in the middle and taller, then second and third beside it (the phone's podium). Each entry is
 * { rank, row: { id, name, crestId }, figure }; `mine` is the viewer's id. Fewer than three just leaves the other places empty.
 */
export function podium(entries, mine) {
  const el = node('div', null, 'podium');
  for (const place of [2, 1, 3]) {
    const entry = entries[place - 1];
    if (!entry) continue;
    const step = node('div', null, `podium-place place-${place}${entry.row.id === mine ? ' is-me' : ''}`); step.dataset.rank = String(place); step.dataset.user = entry.row.id;
    const emblem = node('span'); plate(emblem, { name: hiddenName(entry.row.name), crestId: entry.row.crestId });
    step.append(node('span', String(place), 'podium-rank'), emblem, node('strong', hiddenName(entry.row.name), 'podium-name'), node('span', entry.figure, 'muted podium-figure'));
    el.append(step);
  }
  return el;
}
