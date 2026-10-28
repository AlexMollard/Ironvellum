import { seal } from './seal.js';
import { node, displayDate } from './statCard.js';
let serial = 0;
// The app's glossary names Epic "Fabled", whatever the Names setting.
const rarityWord = rarity => (rarity === 'Epic' ? 'Fabled' : rarity);
// A deed is a button that opens its panel in place: the description, the rarity and the date it was earned.
export function deedRow(deed, zone) {
  const el = node('div', null, 'deed-item'), id = `deed-panel-${++serial}`, rarity = `deed-rarity rarity-${String(deed.rarity).toLowerCase()}`;
  const row = node('button', null, 'deed-row'); row.type = 'button'; row.setAttribute('aria-expanded', 'false'); row.setAttribute('aria-controls', id);
  const mark = node('span', null, 'deed-mark'); seal(mark, { rarity: deed.rarity, category: deed.category, earned: true });
  const detail = node('span', null, 'deed-text'); detail.append(node('strong', deed.name), node('small', rarityWord(deed.rarity), rarity));
  const chevron = node('span', '\u203a', 'deed-chevron'); chevron.setAttribute('aria-hidden', 'true');
  row.append(mark, detail, node('span', displayDate(deed.atMs, zone), 'muted deed-date'), chevron);
  const panel = node('div', null, 'deed-panel'); panel.id = id; panel.hidden = true;
  if (deed.description) panel.append(node('p', deed.description, 'deed-description'));
  const facts = node('p', null, 'muted deed-facts'); facts.append(node('span', rarityWord(deed.rarity), rarity), ` \u00b7 Earned ${displayDate(deed.atMs, zone)}`); panel.append(facts);
  const set = open => { row.setAttribute('aria-expanded', String(open)); panel.hidden = !open; el.classList.toggle('is-open', open); };
  row.addEventListener('click', () => set(panel.hidden));
  el.addEventListener('keydown', event => { if (event.key === 'Escape' && !panel.hidden) { set(false); row.focus(); } });
  el.append(row, panel); return el;
}
