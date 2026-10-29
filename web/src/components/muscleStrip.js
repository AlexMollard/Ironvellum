import { node } from './statCard.js';
import { bindTips, roving } from './interact.js';
const groups = ['BACK', 'CHEST', 'LEGS', 'SHOULDERS', 'ARMS', 'CORE'];
const names = { BACK: 'Back', CHEST: 'Chest', LEGS: 'Legs', SHOULDERS: 'Shoulders', ARMS: 'Arms', CORE: 'Core' };
export const workedLevel = level => (level == null ? '\u2014' : ['Not worked', 'A little', 'Some', 'Most'][level] ?? String(level));
// Without a snapshot entry (levels undefined) every cell is unknown; with one, a group the workout did not train is level 0.
// Each cell shows its group and level on hover, focus or tap; Tab reaches the strip once and the arrow keys move along it.
// `own: false` leaves the cells out of the tab order for a parent (a workout row) that roves over them with its other controls.
export function muscleStrip(levels, { own = true } = {}) {
  const el = node('div', null, 'muscle-strip');
  el.setAttribute('role', 'group'); el.setAttribute('aria-label', 'Muscle groups worked');
  const cells = [];
  for (const group of groups) {
    const level = levels ? (levels[group] ?? 0) : null;
    const cell = node('span', null, `muscle-cell level-${level ?? 0}`);
    cell.dataset.level = level ?? ''; cell.dataset.group = group;
    cell.setAttribute('role', 'img'); cell.setAttribute('aria-label', `${names[group]}: ${workedLevel(level)}`);
    el.append(cell); cells.push(cell);
  }
  if (own) roving(cells); else cells.forEach(cell => cell.setAttribute('tabindex', '-1'));
  bindTips(el, '.muscle-cell', cell => ({ title: names[cell.dataset.group], value: workedLevel(cell.dataset.level === '' ? null : Number(cell.dataset.level)) }), { onShow: c => c.classList.add('is-active'), onHide: c => c.classList.remove('is-active') });
  return el;
}
