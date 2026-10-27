import { t } from '../i18n.js';
import { workoutPath } from '../source.js';
import { formatLoad, formatSets } from '../format.js';
import { node, displayDate, link } from './statCard.js';
import { muscleStrip } from './muscleStrip.js';
import { prTag } from './prTag.js';
export function workoutRow(workout, entry, snapshot, router) {
  const el = node('article', null, 'card workout-row');
  const heading = link(workout.title || workout.label || t('concept_trial_c'), workoutPath(workout), router);
  heading.className = 'workout-title';
  el.append(heading, node('p', displayDate(workout.startedAt, snapshot?.zone), 'muted'));
  if (workout.origin === 'imported') el.append(node('span', 'Imported', 'chip'));
  const figures = entry ? `${entry.min} min · ${formatSets(entry.hardSets)} hard sets · ${formatLoad(entry.volumeKg, snapshot?.unit || 'kg')} ${snapshot?.unit || 'kg'} · ${entry.xp} XP` : '— min · — hard sets · — volume · — XP';
  el.append(node('p', figures), muscleStrip(entry?.groupLevels));
  if (entry?.prs?.length) el.append(prTag(entry.prs[0], snapshot?.unit));
  return el;
}
