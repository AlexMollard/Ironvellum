import { node } from './statCard.js';
const groups = ['BACK', 'CHEST', 'LEGS', 'SHOULDERS', 'ARMS', 'CORE'];
export function muscleStrip(levels) {
  const el = node('div', null, 'muscle-strip');
  el.setAttribute('aria-label', 'Muscle groups worked');
  for (const group of groups) {
    const cell = node('span', null, `muscle-cell level-${levels?.[group] ?? 0}`);
    cell.title = `${group}: ${levels?.[group] ?? '—'}`;
    cell.dataset.level = levels?.[group] ?? '';
    el.append(cell);
  }
  return el;
}
