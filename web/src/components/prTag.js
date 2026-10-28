import { formatLoad, splitLabel } from '../format.js';
import { node } from './statCard.js';
export function prTag(pr, unit = 'kg') {
  const parts = [splitLabel(pr.label).filter(Boolean).join(' · ')];
  if (pr.weightKg != null) parts.push(`${formatLoad(pr.weightKg, unit)} ${unit}`);
  else if (pr.durationSec == null) parts.push('BW');
  if (pr.durationSec != null) parts.push(`${pr.durationSec} s`);
  else if (pr.reps != null) parts.push(`× ${pr.reps}`);
  const el = node('span', null, 'chip pr-tag');
  el.append(node('b', 'PR'), parts.filter(Boolean).join(' '));
  return el;
}
