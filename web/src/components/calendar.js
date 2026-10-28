import { t } from '../i18n.js';
import { node, card, dayKey, workoutDay, link } from './statCard.js';
import { workoutPath } from '../source.js';
// A day with one workout links to it; a day with several calls onPick(day, workouts) so the list can show them.
export function calendar(month, workouts, zone, changeMonth, nowMs = Date.now(), { router, onPick } = {}) {
  const [year, number] = month.split('-').map(Number);
  const arrows = node('span', null, 'calendar-arrows');
  const previous = node('button', '‹', 'icon-button'); previous.type = 'button'; previous.setAttribute('aria-label', 'Previous month'); previous.onclick = () => changeMonth(-1);
  const next = node('button', '›', 'icon-button'); next.type = 'button'; next.setAttribute('aria-label', 'Next month'); next.onclick = () => changeMonth(1);
  arrows.append(previous, next);
  const el = card(new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(Date.UTC(year, number - 1, 1))), 'calendar', arrows);
  const grid = node('div', null, 'calendar-grid');
  for (let i = 0; i < 7; i++) { const name = node('span', new Intl.DateTimeFormat(undefined, { weekday: 'narrow', timeZone: 'UTC' }).format(new Date(Date.UTC(2024, 0, 1 + i))), 'calendar-weekday'); name.setAttribute('aria-hidden', 'true'); grid.append(name); }
  const first = (new Date(Date.UTC(year, number - 1, 1)).getUTCDay() + 6) % 7;
  const count = new Date(Date.UTC(year, number, 0)).getUTCDate();
  const counts = new Map(), byDay = new Map();
  for (const workout of workouts) { const key = workoutDay(workout, zone); counts.set(key, (counts.get(key) || 0) + 1); byDay.set(key, [...(byDay.get(key) || []), workout]); }
  const today = dayKey(nowMs, zone);
  for (let i = 0; i < first; i++) grid.append(node('span', null, 'calendar-day blank'));
  for (let i = 1; i <= count; i++) {
    const key = `${month}-${String(i).padStart(2, '0')}`, n = counts.get(key) || 0;
    const className = `calendar-day${n ? ' has-workout' : ''}${key === today ? ' today' : ''}`;
    let day;
    if (n === 1 && router) day = link('', workoutPath(byDay.get(key)[0]), router, className);
    else if (n > 1 && onPick) { day = node('button', null, `${className} is-multiple`); day.type = 'button'; day.addEventListener('click', () => onPick(key, byDay.get(key))); }
    else day = node('span', null, className);
    day.dataset.day = key; day.append(node('span', i, 'day-number'));
    if (n) { day.setAttribute('aria-label', `${key}: ${n === 1 ? '' : `${n} `}${t(n === 1 ? 'concept_trial_s' : 'concept_trial_pl')}`); const dots = node('span', null, 'day-dots'); for (let d = 0; d < Math.min(n, 3); d++) dots.append(node('span', null, 'day-dot')); day.append(dots); }
    if (key === today) day.setAttribute('aria-current', 'date');
    grid.append(day);
  }
  for (let i = (first + count) % 7 ? 7 - (first + count) % 7 : 0; i > 0; i--) grid.append(node('span', null, 'calendar-day blank'));
  el.append(grid); return el;
}
