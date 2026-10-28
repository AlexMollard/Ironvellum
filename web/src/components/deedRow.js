import { seal } from './seal.js';
import { node, displayDate } from './statCard.js';
export function deedRow(deed, zone) {
  const el = node('div', null, 'deed-row');
  const mark = node('span', null, 'deed-mark'); seal(mark, { rarity: deed.rarity, category: deed.category, earned: true });
  const detail = node('div', null, 'deed-text'); detail.append(node('strong', deed.name), node('small', deed.rarity, `deed-rarity rarity-${String(deed.rarity).toLowerCase()}`));
  if (deed.description) detail.setAttribute('title', deed.description);
  el.append(mark, detail, node('span', displayDate(deed.atMs, zone), 'muted deed-date')); return el;
}
