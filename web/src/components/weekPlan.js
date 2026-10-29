import { node, link } from './statCard.js';

export const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
export const DAY_LONG = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

/**
 * The weekly plan strip, Monday to Sunday: each scheduled routine on its day (a link to it), a rest day reading Rest, and the open
 * routine's day marked. `week` is the snapshot's { days, hardSets }.
 */
export function weekPlan(presets, week, openId, router) {
  const el = node('section', null, 'week-plan');
  el.append(node('div', `${week.days} ${week.days === 1 ? 'day' : 'days'} · ${week.hardSets} hard sets`, 'muted week-plan-head'));
  const strip = node('ol', null, 'week-strip');
  for (const [index, name] of DAY_NAMES.entries()) {
    const here = presets.filter(p => p.day === index + 1), cell = node('li', null, `week-day${here.some(p => p.id === openId) ? ' is-open' : ''}`); cell.dataset.day = String(index + 1);
    cell.append(node('span', name, 'week-day-name'));
    if (!here.length) cell.append(node('span', 'Rest', 'muted week-rest'));
    for (const p of here) cell.append(link(p.name, `/routines/${p.id}`, router, 'week-routine'));
    strip.append(cell);
  }
  el.append(strip);
  return el;
}
