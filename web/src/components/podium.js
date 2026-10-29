import { node } from './statCard.js';
import { plate } from './plate.js';
import { hiddenName } from './hiddenName.js';

/**
 * The top three of a board: first in the middle and taller, then second and third beside it (the phone's podium). Each entry is
 * { rank, row: { id, name, crestId }, figure }; `mine` is the viewer's id. Fewer than three just leaves the other places empty.
 * `openerOf(id)` gives the function that opens that lifter's folio, or nothing: only a place with one is a link; the rest stay flat.
 */
export function podium(entries, mine, openerOf) {
  const el = node('div', null, 'podium');
  for (const place of [2, 1, 3]) {
    const entry = entries[place - 1];
    if (!entry) continue;
    const open = openerOf?.(entry.row.id), step = node('div', null, `podium-place place-${place}${entry.row.id === mine ? ' is-me' : ''}${open ? ' is-link' : ''}`); step.dataset.rank = String(place); step.dataset.user = entry.row.id;
    const emblem = node('span'); plate(emblem, { name: hiddenName(entry.row.name), crestId: entry.row.crestId });
    const name = node('strong', null, 'podium-name'), label = hiddenName(entry.row.name);
    if (open) { const button = node('button', label, 'link-button stretch'); button.type = 'button'; button.dataset.focusKey = `person:${entry.row.id}`; button.addEventListener('click', () => open(entry.row)); name.append(button); } else name.textContent = label;
    step.append(node('span', String(place), 'podium-rank'), emblem, name, node('span', entry.figure, 'muted podium-figure'));
    el.append(step);
  }
  return el;
}
