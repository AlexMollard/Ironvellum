import { t } from '../i18n.js';
import { node, dayKey } from './statCard.js';
export function calendar(month, workouts, zone, changeMonth) {
  const el = node('section', null, 'card calendar');
  const [year, number] = month.split('-').map(Number);
  const header = node('div', null, 'button-row');
  const previous = node('button', '‹'); previous.type = 'button'; previous.setAttribute('aria-label', 'Previous month'); previous.onclick = () => changeMonth(-1);
  const next = node('button', '›'); next.type = 'button'; next.setAttribute('aria-label', 'Next month'); next.onclick = () => changeMonth(1);
  header.append(previous, node('h2', new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(Date.UTC(year, number - 1, 1)))), next);
  el.append(header);
  const grid = node('div', null, 'calendar-grid');
  for (const day of ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']) grid.append(node('span', day, 'muted'));
  const first = (new Date(Date.UTC(year, number - 1, 1)).getUTCDay() + 6) % 7;
  for (let i = 0; i < first; i++) grid.append(node('span', ''));
  const days = new Set(workouts.map(w => dayKey(w.startedAt, zone)));
  const count = new Date(Date.UTC(year, number, 0)).getUTCDate();
  for (let i = 1; i <= count; i++) {
    const key = `${month}-${String(i).padStart(2, '0')}`;
    const day = node('span', i, days.has(key) ? 'calendar-day has-workout' : 'calendar-day');
    day.dataset.day = key; if (days.has(key)) day.setAttribute('aria-label', `${key}: ${t('concept_trial_s')}`);
    grid.append(day);
  }
  el.append(grid); return el;
}
