import { t } from '../i18n.js';
import { formatLoad, formatSets } from '../format.js';
import { renderBodyMap } from '../components/bodyMap.js';
import { plate } from '../components/plate.js';
import { node, card, statCard, sortedWorkouts, displayDate, link } from '../components/statCard.js';
import { renderFatigueCard } from '../components/fatigueCard.js';
import { deedRow } from '../components/deedRow.js';
import { recordRow } from '../components/recordRow.js';
import { prTag } from '../components/prTag.js';
export async function renderOverview(root, { source, snapshot, router }) {
  root.replaceChildren();
  const heading = node('header', null, 'screen-header'); heading.append(node('h1', snapshot?.profile?.name || 'Overview'));
  if (snapshot?.builtAtMs != null) heading.append(node('p', `Stats as of ${new Date(snapshot.builtAtMs).toLocaleString()}`, 'muted'));
  root.append(heading);
  if (!snapshot) root.append(node('p', `Sync the app to see your stats. ${t('concept_trial_cpl')} are still available.`, 'muted'));
  const rows = sortedWorkouts(await source.listWorkouts(), source);
  if (!snapshot) { if (rows.length) root.append(link(`View ${t('concept_trial_pl')}`, '/workouts', router)); return; }
  const { profile, week, trends } = snapshot;
  const layout = node('div', null, 'overview-layout'); root.append(layout);
  const summary = card('This week'); const stats = node('div', null, 'grid');
  stats.append(statCard(t('concept_trial_cpl'), week.workouts), statCard('Hard sets', formatSets(week.hardSets)), statCard('Volume', `${formatLoad(week.volumeKg, snapshot.unit)} ${snapshot.unit}`), statCard('Previous week', `${formatLoad(week.prevVolumeKg, snapshot.unit)} ${snapshot.unit}`), statCard('XP this week', week.xp));
  if (week.plannedDays != null) stats.append(statCard('Planned days', week.plannedDays));
  summary.append(stats); layout.append(summary);
  const profileCard = card('Your progress'); const emblem = node('div'); plate(emblem, { name: profile.name, level: profile.level, crestId: profile.crestId });
  const progressStats = node('div', null, 'grid');
  progressStats.append(statCard('Level', profile.level), statCard('XP', `${profile.intoLevel} / ${profile.needed}`), statCard('Total XP', profile.totalXp), statCard(t('concept_oath_c'), `${profile.streakDays} days`), statCard(`Best ${t('concept_oath_s')}`, `${profile.longestStreakDays} days`));
  profileCard.append(emblem, progressStats); layout.append(profileCard);
  const last = rows[0];
  if (last) {
    const entry = snapshot.sessions?.[String(last.localId)]; const panel = card(`Last ${t('concept_trial_s')}`);
    panel.append(link(last.title || last.label || t('concept_trial_c'), `/workouts/${encodeURIComponent(last.localId)}`, router), node('p', displayDate(last.startedAt, snapshot.zone), 'muted'));
    if (entry) {
      panel.append(node('p', `${formatSets(entry.hardSets)} hard sets · ${entry.strength} STR · ${formatLoad(entry.volumeKg, snapshot.unit)} ${snapshot.unit}`));
      for (const pr of entry.prs || []) panel.append(prTag(pr, snapshot.unit));
      const maps = node('div', null, 'body-map-pair');
      for (const view of ['front', 'back']) { const map = node('div', null, 'body-map'); renderBodyMap(map, { levels: entry.muscleLevels, figure: profile.figure, view, look: 'worked' }); maps.append(map); }
      panel.append(maps);
    } else panel.append(node('p', '— hard sets · — STR · — volume'));
    const sets = await source.getSets(last); const names = [...new Set(sets.map(s => s.exerciseName).filter(Boolean))];
    const chips = node('div', null, 'button-row'); for (const name of names) chips.append(node('span', name, 'chip')); panel.append(chips); layout.append(panel);
  } else layout.append(node('p', `No ${t('concept_trial_pl')} yet.`));
  const fatigue = node('div'); renderFatigueCard(fatigue, { fatigue: snapshot.fatigue, muscles: snapshot.muscles, figure: profile.figure, builtAtMs: snapshot.builtAtMs, zone: snapshot.zone }); layout.append(fatigue);
  const records = card(`Records · ${trends.records30d} in the last 30 days`);
  for (const record of trends.recent || []) records.append(recordRow(record, snapshot, router)); layout.append(records);
  const deeds = card(`Deeds · ${profile.deedsEarned} / ${profile.deedsTotal}`);
  for (const deed of snapshot.deeds || []) deeds.append(deedRow(deed, snapshot.zone)); layout.append(deeds);
}
