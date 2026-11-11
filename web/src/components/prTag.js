import { exerciseUnit, formatWeight, splitLabel, isAssistedMachine } from '../format.js';
import { word } from '../i18n.js';
import { node } from './statCard.js';
export function prTag(pr, unit = 'kg', snapshot) {
  const [legacyName, legacyVariation] = splitLabel(pr.label);
  // A legacy label with a delimiter cannot distinguish a custom name from a variation.
  const name = pr.exerciseName || (legacyVariation ? null : legacyName);
  unit = exerciseUnit(snapshot, name, unit);
  const parts = [legacyVariation ? `${legacyName} · ${legacyVariation}` : legacyName];
  const assisted = isAssistedMachine(name || legacyName);
  if (pr.weightKg != null) parts.push(`${formatWeight(pr.weightKg, unit, snapshot?.unit ?? unit)}${assisted ? ' assistance' : ''}`);
  else if (pr.durationSec == null) parts.push(assisted ? '\u2014' : 'BW');
  if (pr.durationSec != null) parts.push(`${pr.durationSec} s`);
  else if (pr.reps != null) parts.push(`× ${pr.reps}`);
  const el = node('span', null, 'chip pr-tag');
  el.append(node('b', word('PR', 'concept_peak_u')), parts.filter(Boolean).join(' '));
  return el;
}
