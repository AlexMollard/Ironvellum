import { node, link } from '../components/statCard.js';
import { renderBodyMap } from '../components/bodyMap.js';
import { routineEntry } from '../components/routineEntry.js';
import { weekPlan, DAY_NAMES, DAY_LONG } from '../components/weekPlan.js';
import { word } from '../i18n.js';

const maps = (levels, look, snapshot) => {
  const row = node('div', null, 'routine-maps');
  for (const view of ['front', 'back']) { const figure = node('div', null, 'body-map'); renderBodyMap(figure, { levels, figure: snapshot.profile?.figure, view, look, names: snapshot.muscles }); row.append(figure); }
  return row;
};

/**
 * Routines, view only: the phone's saved routines (the snapshot's `routines`, worded by the phone), the week they cover, the weekly
 * plan strip and one routine's numbered entries with the muscles it works. Editing stays in the app, and the page says so.
 */
export function renderRoutines(root, ctx, id) {
  const { snapshot, router } = ctx, routines = snapshot?.routines;
  const header = node('div', null, 'screen-header'); header.append(node('h1', word('Routines', 'concept_rite_cpl'))); root.append(header);
  if (!routines) { root.append(node('p', 'Sync the app to see your routines here.', 'notice routines-hint')); return; }
  if (!routines.presets.length) { root.append(node('p', 'No routines yet. Create one in the app and it appears here.', 'empty'), node('p', 'View only. Edit routines in the app.', 'muted routines-viewonly')); return; }
  const open = routines.presets.find(p => String(p.id) === String(id)) ?? routines.presets.find(p => p.day != null) ?? routines.presets[0];
  const layout = node('div', null, 'routines-layout'), left = node('div', null, 'routines-list'), right = node('div', null, 'routines-detail'); layout.append(left, right); root.append(layout);

  const list = node('ul', null, 'routine-list');
  for (const p of routines.presets) {
    const item = node('li', null, `routine-item${p.id === open.id ? ' is-open' : ''}`);
    const a = link(p.name, `/routines/${p.id}`, router, 'routine-link'); if (p.id === open.id) a.setAttribute('aria-current', 'true');
    item.append(a, node('span', `${p.day != null ? DAY_NAMES[p.day - 1] : 'no day'} · ${p.entries.filter(e => !e.warmup).length} ${p.entries.filter(e => !e.warmup).length === 1 ? 'exercise' : 'exercises'}`, 'muted routine-meta')); list.append(item);
  }
  const covers = node('section', null, 'routines-covers'); covers.append(node('h2', 'The week covers', 'eyebrow'), node('p', routines.week.headline, 'routines-headline'), maps(routines.week.covers, 'coverage', snapshot));
  left.append(list, covers);

  right.append(weekPlan(routines.presets, routines.week, open.id, router));
  const detail = node('section', null, 'routine-detail');
  detail.append(node('h2', open.name), node('div', `${open.day != null ? DAY_LONG[open.day - 1] : 'No day'} · ~${open.minutes} min · ${open.hardSets} hard sets`, 'muted routine-figures'));
  if (open.note) detail.append(node('p', open.note, 'routine-note'));
  const entries = node('ol', null, 'routine-entries'); for (const entry of open.entries) entries.append(routineEntry(entry, snapshot.unit)); detail.append(entries);
  detail.append(node('h3', 'Works', 'eyebrow'), maps(open.levels, 'worked', snapshot));
  right.append(detail);
  if (routines.custom.length) {
    const custom = node('section', null, 'routines-custom'); custom.append(node('h3', 'Custom exercises', 'eyebrow'));
    for (const c of routines.custom) {
      const muscles = Object.entries(c.muscles).map(([m, role]) => `${snapshot.muscles?.[m] ?? m} (${role})`).join(', ');
      const row = node('div', null, 'custom-row'); row.append(node('strong', c.name), node('span', [muscles, c.needs.join(', ')].filter(Boolean).join(' · ') || '—', 'muted'));
      custom.append(row);
    }
    right.append(custom);
  }
  right.append(node('p', 'View only. Edit routines in the app.', 'muted routines-viewonly'));
}
