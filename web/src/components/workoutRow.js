import { t } from '../i18n.js';
import { workoutPath } from '../source.js';
import { formatDay, formatSets, formatVolume, formatWeekday } from '../format.js';
import { node, workoutDay, link } from './statCard.js';
import { muscleStrip } from './muscleStrip.js';
import { copyButton } from './copyWorkout.js';
import { formatWorkoutText } from '../workoutText.js';
import { prTag } from './prTag.js';
export function workoutRow(workout, entry, snapshot, router, source) {
  const el = node('article', null, 'workout-row is-link');
  const day = workoutDay(workout, snapshot?.zone); el.dataset.day = day;
  const when = node('span', null, 'row-date'); when.append(node('span', formatWeekday(day)), node('span', formatDay(day)));
  const text = node('div', null, 'row-text');
  const heading = link(workout.title || workout.label || t('concept_trial_c'), workoutPath(workout), router, 'workout-title');
  const titleLine = node('div', null, 'row-title'); titleLine.append(heading);
  if (workout.origin === 'imported') titleLine.append(node('span', 'Imported', 'chip chip-quiet'));
  const figures = entry ? `${entry.min} min · ${formatSets(entry.hardSets)} hard sets · ${formatVolume(entry.volumeKg, snapshot?.unit)} · +${entry.xp} XP` : '— min · — hard sets · — volume · — XP';
  text.append(titleLine, node('div', figures, 'muted row-figures'));
  el.append(when, text);
  if (entry?.prs?.length) el.append(prTag(entry.prs[0], snapshot?.unit));
  // The strip sits above the row's stretched link so it can show its tooltips; a click on it still opens the workout.
  const strip = muscleStrip(entry?.groupLevels); strip.addEventListener('click', () => heading.click());
  el.append(strip);
  if (source) el.append(copyButton({ iconOnly: true, label: `Copy ${workout.title || workout.label || t('concept_trial_s')}`, getText: async () => formatWorkoutText({ workout, entry, snapshot, sets: await source.getSets(workout) }) }));
  el.append(node('span', '›', 'row-chevron'));
  el.lastChild.setAttribute('aria-hidden', 'true');
  return el;
}
