import { node } from './statCard.js';
const groups = ['BACK', 'CHEST', 'LEGS', 'SHOULDERS', 'ARMS', 'CORE'];
// Without a snapshot entry (levels undefined) every cell is unknown; with one, a group the workout did not train is level 0.
export function muscleStrip(levels) {
  const el = node('div', null, 'muscle-strip');
  el.setAttribute('aria-label', 'Muscle groups worked');
  for (const group of groups) {
    const level = levels ? (levels[group] ?? 0) : null;
    const cell = node('span', null, `muscle-cell level-${level ?? 0}`);
    cell.title = `${group}: ${level ?? '—'}`;
    cell.dataset.level = level ?? '';
    el.append(cell);
  }
  return el;
}
