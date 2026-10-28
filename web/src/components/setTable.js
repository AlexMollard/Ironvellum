import { word } from '../i18n.js';
import { formatLoad, formatSetLoad, setPerformance, setEffort, setKindName } from '../format.js';
import { node } from './statCard.js';
export function setTable(sets, entry, snapshot) {
  const table = node('table', null, 'set-table');
  const head = node('thead'); const header = node('tr');
  header.append(...['Set', 'Kind', 'Load', 'Reps', snapshot?.effort === 'rir' ? 'Reps left' : 'RPE', 'e1RM'].map(label => node('th', label)));
  head.append(header); table.append(head);
  const body = node('tbody');
  for (const [position, set] of sets.entries()) {
    const key = `${set.exerciseName}#${set.setIndex}`; const row = node('tr', null, `kind-${set.kind || 'working'}`);
    const unit = snapshot?.unit || 'kg';
    const load = formatSetLoad(set, unit), performance = setPerformance(set).join(' · ') || '—', effort = setEffort(set, snapshot) ?? '—';
    const e1rm = entry?.e1rm?.[key], peak = entry?.peakSets?.includes(key);
    for (const value of [position + 1, setKindName(set), load, performance, effort]) row.append(node('td', value));
    const last = node('td', null, 'e1rm-cell'); if (peak) last.append(node('b', word('PR', 'concept_peak_u'), 'pr-mark')); last.append(e1rm == null ? '' : `${formatLoad(e1rm, unit)} ${unit}`); row.append(last);
    body.append(row);
  }
  table.append(body); return table;
}
