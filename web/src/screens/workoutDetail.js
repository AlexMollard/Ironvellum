import { t } from '../i18n.js';
import { snapshotForWorkout, workoutPath } from '../source.js';
import { formatDay, formatDayShort, formatSets, formatVolume, formatWeekday, formatClock } from '../format.js';
import { artImg } from '../components/artImg.js';
import { renderBodyMap } from '../components/bodyMap.js';
import { node, card, factRow, sortedWorkouts, workoutDay, link } from '../components/statCard.js';
import { setTable } from '../components/setTable.js';
import { prTag } from '../components/prTag.js';
const adjacent = (workout, day, router, previous) => {
  const a = link('', workoutPath(workout), router, 'adjacent'); a.append(node('span', previous ? `Previous ${t('concept_trial_s')}: ` : `Next ${t('concept_trial_s')}: `, 'sr-only'), `${previous ? '‹ ' : ''}${formatWeekday(day)} ${formatDayShort(day)}${previous ? '' : ' ›'}`);
  return a;
};
export async function renderWorkoutDetail(root, { source, snapshot, router }, origin, id) {
  root.replaceChildren();
  const workouts = sortedWorkouts(await source.listWorkouts(), source, snapshot?.zone);
  const index = workouts.findIndex(w => w.origin === origin && String(w.id) === String(id));
  if (index < 0) { root.append(node('h1', `${t('concept_trial_c')} not found`), link(`Back to ${t('concept_trial_pl')}`, '/workouts', router)); return; }
  const workout = workouts[index], entry = snapshotForWorkout(snapshot, workout), day = workoutDay(workout, snapshot?.zone);
  const header = node('header', null, 'screen-header'), title = node('div', null, 'header-title');
  title.append(link(`${t('concept_trial_cpl')} ›`, '/workouts', router, 'crumb'), ' ', node('h1', workout.title || workout.label || t('concept_trial_c')));
  if (workout.origin === 'imported') title.append(' ', node('span', 'Imported', 'chip chip-quiet'));
  header.append(title);
  if (index + 1 < workouts.length) header.append(adjacent(workouts[index + 1], workoutDay(workouts[index + 1], snapshot?.zone), router, true));
  if (index > 0) header.append(adjacent(workouts[index - 1], workoutDay(workouts[index - 1], snapshot?.zone), router, false));
  root.append(header);
  const layout = node('div', null, 'detail-layout'), main = card(null, 'sets-card'), side = node('div', null, 'detail-side'); layout.append(main, side); root.append(layout);
  if (!snapshot) side.append(node('p', 'Sync the app to see your stats.', 'muted'));
  const summary = card(null, 'summary-card'), top = node('div', null, 'summary-top');
  top.append(node('span', `${formatWeekday(day)} ${formatDay(day)} · ${formatClock(workout.startedAt, snapshot?.zone)} · ${entry?.min ?? '—'} min`, 'muted'));
  if (entry) top.append(node('span', `+${entry.xp} XP`, 'lw-xp'));
  summary.append(top, factRow('Hard sets', entry ? formatSets(entry.hardSets) : null), factRow('Volume', entry ? formatVolume(entry.volumeKg, snapshot.unit) : null), factRow('Records', entry?.peaks), factRow('Strength', entry?.strength)); side.append(summary);
  if (entry) {
    const muscles = card('Muscles worked', 'muscles-card'); const maps = node('div', null, 'body-map-pair');
    for (const view of ['front', 'back']) { const map = node('div', null, 'body-map'); renderBodyMap(map, { levels: entry.muscleLevels, figure: snapshot.profile?.figure || 'male', view, look: 'worked' }); maps.append(map); }
    const legend = node('div', null, 'fatigue-legend worked-legend');
    for (const [level, label] of [[3, 'Most'], [2, 'Some'], [1, 'A little']]) legend.append(node('span', label, `fatigue-legend-item worked-${level}`));
    muscles.append(maps, legend); side.append(muscles);
  }
  const sets = await source.getSets(workout);
  const groups = new Map();
  for (const set of sets) {
    const key = JSON.stringify([set.exerciseName, set.exercisePosition]);
    if (!groups.has(key)) groups.set(key, { name: set.exerciseName, position: set.exercisePosition, sets: [] });
    groups.get(key).sets.push(set);
  }
  const ordered = [...groups.values()].sort((a, b) => (a.position ?? Infinity) - (b.position ?? Infinity) || a.name.localeCompare(b.name));
  for (const group of ordered) {
    group.sets.sort((a, b) => a.setIndex - b.setIndex);
    const block = node('section', null, 'exercise-block'); const lift = snapshot?.lifts?.find(l => l.name === group.name);
    const head = node('div', null, 'exercise-header'), frame = node('span', null, 'art-frame large'); frame.append(artImg(group.name, lift?.art));
    const line = node('div', null, 'exercise-line'); line.append(node('h2', group.name));
    const modifiers = [...new Set(group.sets.map(s => s.modifiers).filter(Boolean))];
    for (const variation of modifiers) line.append(node('span', variation, 'chip chip-accent'));
    for (const pr of entry?.prs || []) if (pr.label === group.name || pr.label.startsWith(`${group.name} .`)) line.append(prTag(pr, snapshot?.unit));
    head.append(frame, line); block.append(head); const scroll = node('div', null, 'table-scroll'); scroll.append(setTable(group.sets, entry, snapshot)); block.append(scroll); main.append(block);
  }
  if (workout.note) { const note = node('p', null, 'note'); note.append(node('span', 'Your note: ', 'muted'), workout.note); side.append(note); }
  const footer = node('footer'); const credit = node('a', 'Body map license'); credit.href = `${import.meta.env.BASE_URL}licenses/MIT-react-native-body-highlighter.txt`; footer.append(credit); root.append(footer);
}
