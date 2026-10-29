import { node, link } from './statCard.js';
import { plural } from '../format.js';

export const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
export const DAY_LONG = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

/**
 * The weekly plan strip, Monday to Sunday: each scheduled routine on its day, a rest day reading Rest, the open routine's day marked
 * and today's day labelled. A day with one routine is one link over the whole cell; a day with two stacks a full-width link per routine.
 * `week` is the snapshot's { days, hardSets }; `today` is the weekday number (1 is Monday) or null.
 */
export function weekPlan(presets, week, openId, router, today = null) {
  const el = node('section', null, 'week-plan');
  el.append(node('div', `${plural(week.days, 'day')} · ${plural(week.hardSets, 'hard set')}`, 'muted week-plan-head'));
  const strip = node('ol', null, 'week-strip');
  for (const [index, name] of DAY_NAMES.entries()) {
    const here = presets.filter(p => p.day === index + 1), open = here.some(p => p.id === openId);
    const cell = node('li', null, `week-day${open ? ' is-open' : ''}${here.length ? '' : ' is-rest'}${here.length === 1 ? ' is-link' : ''}${here.length > 1 ? ' is-multi' : ''}${today === index + 1 ? ' is-today' : ''}`); cell.dataset.day = String(index + 1);
    cell.append(node('span', name, 'week-day-name'));
    if (today === index + 1) cell.append(node('span', 'Today', 'week-today'));
    if (!here.length) cell.append(node('span', 'Rest', 'week-rest'));
    for (const p of here) {
      const a = link(p.name, `/routines/${p.id}`, router, 'week-routine stretch'); a.setAttribute('aria-label', `${DAY_LONG[index]}, ${p.name}`); a.title = p.name;
      if (p.id === openId) a.setAttribute('aria-current', 'true');
      if (here.length === 1) cell.append(a);
      else { const slot = node('div', null, `week-slot is-link${p.id === openId ? ' is-open' : ''}`); slot.append(a); cell.append(slot); }
    }
    strip.append(cell);
  }
  el.append(strip);
  return el;
}
