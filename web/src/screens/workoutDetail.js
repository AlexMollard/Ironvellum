import { t } from '../i18n.js';
import { formatLoad, formatSets } from '../format.js';
import { artImg } from '../components/artImg.js';
import { renderBodyMap } from '../components/bodyMap.js';
import { node, card, statCard, sortedWorkouts, displayDate, link } from '../components/statCard.js';
import { setTable } from '../components/setTable.js';
import { prTag } from '../components/prTag.js';
export async function renderWorkoutDetail(root, { source, snapshot, router }, localId) {
  root.replaceChildren();
  const workouts = sortedWorkouts(await source.listWorkouts(), source);
  const index = workouts.findIndex(w => String(w.localId) === String(localId));
  if (index < 0) { root.append(node('h1', `${t('concept_trial_c')} not found`), link(`Back to ${t('concept_trial_pl')}`, '/workouts', router)); return; }
  const workout = workouts[index], entry = snapshot?.sessions?.[String(workout.localId)];
  const header = node('header', null, 'screen-header'); header.append(link(t('concept_trial_cpl'), '/workouts', router), node('h1', workout.title || workout.label || t('concept_trial_c')), node('p', displayDate(workout.startedAt, snapshot?.zone), 'muted'));
  if (workout.origin === 'imported') header.append(node('span', 'Imported', 'chip')); root.append(header);
  if (!snapshot) root.append(node('p', 'Sync the app to see your stats.', 'muted'));
  const summary = card('Summary'); const figures = node('div', null, 'grid');
  figures.append(statCard('Records', entry?.peaks), statCard('Strength', entry?.strength), statCard('XP', entry?.xp), statCard('Hard sets', entry ? formatSets(entry.hardSets) : null), statCard('Volume', entry ? `${formatLoad(entry.volumeKg, snapshot.unit)} ${snapshot.unit}` : null), statCard('Minutes', entry?.min)); summary.append(figures); root.append(summary);
  if (entry) {
    const muscles = card('Muscles worked'); const maps = node('div', null, 'body-map-pair');
    for (const view of ['front', 'back']) { const map = node('div', null, 'body-map'); renderBodyMap(map, { levels: entry.muscleLevels, figure: snapshot.profile?.figure || 'male', view, look: 'worked' }); maps.append(map); }
    muscles.append(maps); root.append(muscles);
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
    const panel = card(); const lift = snapshot?.lifts?.find(l => l.name === group.name);
    const title = node('div', null, 'exercise-header'); title.append(artImg(group.name, lift?.art), node('h2', group.name));
    const modifiers = [...new Set(group.sets.map(s => s.modifiers).filter(Boolean))];
    for (const variation of modifiers) title.append(node('span', variation, 'chip'));
    for (const pr of entry?.prs || []) if (pr.label === group.name || pr.label.startsWith(`${group.name} .`)) title.append(prTag(pr, snapshot?.unit));
    panel.append(title); const scroll = node('div', null, 'table-scroll'); scroll.append(setTable(group.sets, entry, snapshot)); panel.append(scroll); root.append(panel);
  }
  if (workout.note) { const note = card('Your note'); note.append(node('p', workout.note)); root.append(note); }
  const nav = node('nav', null, 'button-row'); nav.setAttribute('aria-label', `Adjacent ${t('concept_trial_pl')}`);
  if (index + 1 < workouts.length) nav.append(link(`Previous ${t('concept_trial_s')}`, `/workouts/${encodeURIComponent(workouts[index + 1].localId)}`, router));
  if (index > 0) nav.append(link(`Next ${t('concept_trial_s')}`, `/workouts/${encodeURIComponent(workouts[index - 1].localId)}`, router)); root.append(nav);
  const footer = node('footer'); const credit = node('a', 'Body map license'); credit.href = `${import.meta.env.BASE_URL}licenses/MIT-react-native-body-highlighter.txt`; footer.append(credit); root.append(footer);
}
