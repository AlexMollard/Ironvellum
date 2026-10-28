import { formatLoad } from '../format.js';
import { node } from './statCard.js';
const kinds = { working: 'Working', warmup: 'Warm-up', top: 'Top', backoff: 'Back-off', drop: 'Drop', failure: 'Failure', rest_pause: 'Rest-pause' };
export function setTable(sets, entry, snapshot) {
  const table = node('table', null, 'set-table');
  const head = node('thead'); const header = node('tr');
  header.append(...['Set', 'Kind', 'Load', 'Reps', snapshot?.effort === 'rir' ? 'Reps left' : 'RPE', 'e1RM'].map(label => node('th', label)));
  head.append(header); table.append(head);
  const body = node('tbody');
  for (const [position, set] of sets.entries()) {
    const key = `${set.exerciseName}#${set.setIndex}`; const row = node('tr', null, `kind-${set.kind || 'working'}`);
    const unit = snapshot?.unit || 'kg';
    const load = set.weightKg == null || set.weightKg === 0 ? 'BW' : `${formatLoad(set.weightKg, unit)} ${unit}`;
    const distance = set.distanceM == null ? null : set.distanceM >= 1000
      ? `${Number((set.distanceM / 1000).toFixed(5))} km`
      : `${Number(Number(set.distanceM).toFixed(2))} m`;
    const performance = [set.reps ? `${set.reps}` : null, set.durationSec != null ? `${set.durationSec} s` : null, distance, set.grade || null].filter(Boolean).join(' · ') || '—';
    const effort = set.rpe == null ? '—' : snapshot?.effort === 'rir' ? (snapshot?.rir?.[String(set.rpe)] ?? '—') : set.rpe;
    const e1rm = entry?.e1rm?.[key], peak = entry?.peakSets?.includes(key);
    for (const value of [position + 1, kinds[set.kind] || 'Working', load, performance, effort]) row.append(node('td', value));
    const last = node('td', null, 'e1rm-cell'); if (peak) last.append(node('b', 'PR', 'pr-mark')); last.append(e1rm == null ? '' : `${formatLoad(e1rm, unit)} ${unit}`); row.append(last);
    body.append(row);
  }
  table.append(body); return table;
}
