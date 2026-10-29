import { node, link, liftPath } from './statCard.js';
import { artImg } from './artImg.js';
import { formatLoad } from '../format.js';

/**
 * One numbered entry of a routine, read-only, exactly as the snapshot carries it: the art, name and variation chip, the scheme line
 * and the phone's own figure, the Warm-up tag, the superset marker and the load (or BW). When the lifter has a lift page for the
 * exercise (`context.snapshot.lifts`) the whole entry is one link to it; a custom exercise has none and stays plain.
 */
export function routineEntry(entry, unit = 'kg', { snapshot, router } = {}) {
  const path = router ? liftPath(snapshot, entry.name) : null;
  const row = node('li', null, `routine-entry${entry.warmup ? ' is-warmup' : ''}${path ? ' is-link' : ''}`); row.dataset.position = String(entry.position);
  const art = node('span', null, 'art-frame'); art.append(artImg(entry.name, entry.art)); art.setAttribute('aria-hidden', 'true');
  const text = node('div', null, 'routine-entry-text'), title = node('div', null, 'routine-entry-title');
  title.append(path ? link(entry.name, path, router, 'routine-name stretch') : node('strong', entry.name));
  if (entry.label) title.append(node('span', entry.label, 'chip chip-quiet'));
  if (entry.warmup) title.append(node('span', 'Warm-up', 'chip chip-quiet routine-warmup'));
  if (entry.supersetGroup != null) title.append(node('span', `Superset ${entry.supersetGroup}`, 'chip chip-quiet routine-superset'));
  text.append(title);
  if (entry.scheme) text.append(node('div', entry.scheme, 'muted routine-scheme'));
  text.append(node('div', entry.figure, 'routine-figure'));
  row.append(node('span', String(entry.position + 1), 'routine-number'), art, text, node('span', entry.loadKg == null ? 'BW' : `${formatLoad(entry.loadKg, unit)} ${unit}`, 'routine-load'));
  return row;
}
