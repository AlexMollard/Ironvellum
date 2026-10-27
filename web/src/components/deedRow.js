import { seal } from './seal.js';
import { node, displayDate } from './statCard.js';
export function deedRow(deed, zone) {
  const el = node('div', null, 'deed-row');
  const mark = node('span', null, 'deed-seal'); seal(mark, { rarity: deed.rarity, category: deed.category, earned: true });
  const detail = node('div'); detail.append(node('strong', deed.name), node('p', deed.description), node('small', `${deed.rarity} · ${displayDate(deed.atMs, zone)}`, 'muted'));
  el.append(mark, detail); return el;
}
