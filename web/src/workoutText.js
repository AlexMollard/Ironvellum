import { t, word } from './i18n.js';
import { formatDay, formatSets, formatVolume, formatWeekday, formatClock, formatSetLoad, setPerformance, setEffort, setKindName } from './format.js';
import { workoutDay } from './components/statCard.js';
// Sets of one workout grouped into exercise blocks: the same exercise logged twice stays two blocks, ordered by position.
export function groupSets(sets) {
  const groups = new Map();
  for (const set of sets) {
    const key = JSON.stringify([set.exerciseName, set.exercisePosition]);
    if (!groups.has(key)) groups.set(key, { name: set.exerciseName, position: set.exercisePosition, sets: [] });
    groups.get(key).sets.push(set);
  }
  const ordered = [...groups.values()].sort((a, b) => (a.position ?? Infinity) - (b.position ?? Infinity) || a.name.localeCompare(b.name));
  for (const group of ordered) group.sets.sort((a, b) => a.setIndex - b.setIndex);
  return ordered;
}
// One set as a plain line: "load × reps", then its kind (when not a working set), its effort and a PR mark.
function setLine(set, number, width, entry, snapshot) {
  const unit = snapshot?.unit || 'kg', performance = setPerformance(set), load = formatSetLoad(set, unit);
  const main = set.reps ? [`${load} × ${performance[0]}`, ...performance.slice(1)] : [...(load === 'BW' && performance.length ? [] : [load]), ...performance];
  const parts = [main.join(' · ') || '—'];
  if ((set.kind || 'working') !== 'working') parts.push(setKindName(set));
  const effort = setEffort(set, snapshot);
  if (effort != null) parts.push(`${snapshot?.effort === 'rir' ? 'RIR' : 'RPE'} ${effort}`);
  if (entry?.peakSets?.includes(`${set.exerciseName}#${set.setIndex}`)) parts.push(`(${word('PR', 'concept_peak_c')})`);
  return `  ${String(number).padStart(width)}  ${parts.join('  ')}`;
}
/** The workout as plain text for pasting into a chat or notes app. Only figures the page already has; unknown ones are left out. */
export function formatWorkoutText({ workout, entry, snapshot, sets }) {
  const zone = snapshot?.zone, day = workoutDay(workout, zone);
  const facts = [`${formatWeekday(day)} ${formatDay(day)}`, formatClock(workout.startedAt, zone)];
  if (entry) facts.push(`${entry.min} min`, `${formatSets(entry.hardSets)} hard sets`, formatVolume(entry.volumeKg, snapshot?.unit));
  const lines = [workout.title || workout.label || t('concept_trial_c'), facts.join(' · ')];
  for (const group of groupSets(sets)) {
    const variations = [...new Set(group.sets.map(s => s.modifiers).filter(Boolean))];
    lines.push('', variations.length ? `${group.name} · ${variations.join(' · ')}` : group.name);
    const width = String(group.sets.length).length;
    group.sets.forEach((set, index) => lines.push(setLine(set, index + 1, width, entry, snapshot)));
  }
  if (workout.note?.trim()) lines.push('', `Notes: ${workout.note.trim()}`);
  return lines.join('\n');
}
