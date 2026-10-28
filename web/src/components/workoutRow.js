import { t } from '../i18n.js';
import { workoutPath } from '../source.js';
import { formatDay, formatSets, formatVolume, formatWeekday } from '../format.js';
import { node, workoutDay, link } from './statCard.js';
import { muscleStrip } from './muscleStrip.js';
import { prTag } from './prTag.js';
export function workoutRow(workout, entry, snapshot, router) {
  const el = node('article', null, 'workout-row');
  const day = workoutDay(workout, snapshot?.zone);
  const when = node('span', null, 'row-date'); when.append(node('span', formatWeekday(day)), node('span', formatDay(day)));
  const text = node('div', null, 'row-text');
  const heading = link(workout.title || workout.label || t('concept_trial_c'), workoutPath(workout), router, 'workout-title');
  const titleLine = node('div', null, 'row-title'); titleLine.append(heading);
  if (workout.origin === 'imported') titleLine.append(node('span', 'Imported', 'chip chip-quiet'));
  const figures = entry ? `${entry.min} min · ${formatSets(entry.hardSets)} hard sets · ${formatVolume(entry.volumeKg, snapshot?.unit)} · +${entry.xp} XP` : '— min · — hard sets · — volume · — XP';
  text.append(titleLine, node('div', figures, 'muted row-figures'));
  el.append(when, text);
  if (entry?.prs?.length) el.append(prTag(entry.prs[0], snapshot?.unit));
  el.append(muscleStrip(entry?.groupLevels), node('span', '›', 'row-chevron'));
  el.lastChild.setAttribute('aria-hidden', 'true');
  return el;
}
