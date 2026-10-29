import { node, link, dayKey } from '../components/statCard.js';
import { renderBodyMap } from '../components/bodyMap.js';
import { routineEntry } from '../components/routineEntry.js';
import { weekPlan, DAY_NAMES, DAY_LONG } from '../components/weekPlan.js';
import { coverageLegend, workedLegend } from '../components/mapLegend.js';
import { scrollToElement } from '../components/interact.js';
import { plural } from '../format.js';
import { word } from '../i18n.js';

const maps = (levels, look, snapshot) => {
  const row = node('div', null, 'routine-maps');
  for (const view of ['front', 'back']) { const figure = node('div', null, 'body-map'); renderBodyMap(figure, { levels, figure: snapshot.profile?.figure, view, look, names: snapshot.muscles }); row.append(figure); }
  return row;
};

// Today's weekday, 1 (Monday) to 7, in the snapshot's zone.
const weekdayNumber = (nowMs, zone) => (new Date(`${dayKey(nowMs, zone)}T12:00:00Z`).getUTCDay() + 6) % 7 + 1;
// With no routine chosen: the one for today, else the next scheduled day (wrapping past Sunday), else the first.
export function defaultRoutine(presets, today) {
  for (let step = 0; step < 7; step++) { const found = presets.find(p => p.day === (today - 1 + step) % 7 + 1); if (found) return found; }
  return presets[0];
}

/**
 * Routines, view only: the phone's saved routines (the snapshot's `routines`, worded by the phone), the week they cover, the weekly
 * plan strip and one routine's numbered entries with the muscles it works. Editing stays in the app, and the page says so.
 * A choice made on a narrow screen scrolls the routine's detail into view and moves focus to its heading.
 */
export function renderRoutines(root, ctx, id) {
  const { snapshot, router, nowMs = Date.now(), sameScreen } = ctx, routines = snapshot?.routines;
  const header = node('div', null, 'screen-header'); header.append(node('h1', word('Routines', 'concept_rite_cpl'))); root.append(header);
  if (!routines) { root.append(node('p', 'Sync the app to see your routines here.', 'notice routines-hint')); return; }
  const viewOnly = node('p', 'View only. Edit routines in the app.', 'muted routines-viewonly');
  if (!routines.presets.length) { root.append(node('p', 'No routines yet. Create one in the app and it appears here.', 'empty'), viewOnly); return; }
  root.append(viewOnly);
  const today = weekdayNumber(nowMs, snapshot.zone), picked = routines.presets.find(p => String(p.id) === String(id));
  if (id != null && !picked) { const missing = node('p', 'Routine not found. ', 'notice routines-missing'); missing.append(link('Show all routines', '/routines', router)); root.append(missing); }
  const open = picked ?? defaultRoutine(routines.presets, today);
  const layout = node('div', null, 'routines-layout'), left = node('div', null, 'routines-list'), right = node('div', null, 'routines-detail'); layout.append(left, right); root.append(layout);

  const list = node('ul', null, 'routine-list');
  for (const p of routines.presets) {
    const item = node('li', null, `routine-item is-link${p.id === open.id ? ' is-open' : ''}`);
    const a = link(p.name, `/routines/${p.id}`, router, 'routine-link stretch'); if (p.id === open.id) a.setAttribute('aria-current', 'true');
    const count = p.entries.filter(e => !e.warmup).length;
    item.append(a, node('span', `${p.day != null ? DAY_NAMES[p.day - 1] : 'Unscheduled'} · ${plural(count, 'exercise')}`, 'muted routine-meta')); list.append(item);
  }
  const covers = node('section', null, 'routines-covers'); covers.append(node('h2', 'The week covers', 'eyebrow'), node('p', routines.week.headline, 'routines-headline'), maps(routines.week.covers, 'coverage', snapshot), coverageLegend());
  left.append(list, covers);

  right.append(weekPlan(routines.presets, routines.week, open.id, router, today));
  const detail = node('section', null, 'routine-detail'), heading = node('h2', open.name); heading.tabIndex = -1;
  detail.append(heading, node('div', `${open.day != null ? DAY_LONG[open.day - 1] : 'No day'} · ~${open.minutes} min · ${plural(open.hardSets, 'hard set')}`, 'muted routine-figures'));
  if (open.note) detail.append(node('p', open.note, 'routine-note'));
  const entries = node('ol', null, 'routine-entries'); for (const entry of open.entries) entries.append(routineEntry(entry, snapshot.unit, { snapshot, router })); detail.append(entries);
  detail.append(node('h3', 'Works', 'eyebrow'), maps(open.levels, 'worked', snapshot), workedLegend());
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
  root.dataset.title = `${open.name} · ${word('Routines', 'concept_rite_cpl')}`;
  // A pick on a narrow screen opens the detail below the list and week strip; bring it into view and move focus to it.
  if (sameScreen && id != null && (globalThis.innerWidth || 1024) <= 720) { scrollToElement(detail); heading.focus({ preventScroll: true }); }
}
