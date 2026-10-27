import { formatLoad } from '../format.js';
import { node } from './statCard.js';
export function prTag(pr, unit = 'kg') {
  const parts = [pr.label];
  if (pr.weightKg != null) parts.push(`${formatLoad(pr.weightKg, unit)} ${unit}`);
  if (pr.durationSec != null) parts.push(`${pr.durationSec} s`);
  else if (pr.reps != null) parts.push(`${pr.reps} reps`);
  return node('span', `PR · ${parts.filter(Boolean).join(' · ')}`, 'chip pr-tag');
}
