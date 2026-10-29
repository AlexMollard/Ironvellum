import { node } from './statCard.js';

// The keys that sit under a body map pair: what each colour means. Shared by the muscle panel, the workout detail and Routines.
export function coverageLegend() {
  const legend = node('div', null, 'chart-legend coverage-legend');
  for (const [state, text] of [['covered', 'Covered'], ['below', 'Below'], ['heavy', 'Heavy'], ['none', 'None']]) legend.append(node('span', text, `legend-item coverage-${state}`));
  return legend;
}
export function workedLegend() {
  const legend = node('div', null, 'fatigue-legend worked-legend');
  for (const [level, label] of [[3, 'Most'], [2, 'Some'], [1, 'A little']]) legend.append(node('span', label, `fatigue-legend-item worked-${level}`));
  return legend;
}
