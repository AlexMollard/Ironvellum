import { t } from '../i18n.js';
import tokens from '../generated/tokens.json';
import catalogue from '../generated/catalogue.json';
import { snapshotForWorkout, workoutPath } from '../source.js';
import { formatDay, formatSets, formatWeekday, formatClock, splitVolume } from '../format.js';
import { plate } from '../components/plate.js';
import { node, card, kpi, sortedWorkouts, workoutDay, weekPassed, link } from '../components/statCard.js';
import { renderFatigueCard } from '../components/fatigueCard.js';
import { lastWorkoutCard } from '../components/lastWorkoutCard.js';
import { deedRow } from '../components/deedRow.js';
import { recordRow } from '../components/recordRow.js';
const tier = level => [...tokens.ladder].reverse().find(step => step.level <= level)?.title;
function levelCard(profile, week) {
  const el = card(null, 'level-card');
  const top = node('div', null, 'level-top'), emblem = node('span'); plate(emblem, { name: profile.name, crestId: profile.crestId });
  const crest = catalogue.crests.find(c => c.id === profile.crestId);
  const lead = node('div', null, 'level-lead');
  lead.append(node('div', `Level ${profile.level}`, 'level-name'), node('div', [tier(profile.level), crest && `wears ${crest.name}`, `${profile.totalXp.toLocaleString()} XP total`].filter(Boolean).join(' · '), 'muted level-sub'));
  top.append(emblem, lead); el.append(top);
  const bar = node('div', null, 'xp-bar'), fill = node('div', null, 'xp-fill');
  fill.style.setProperty('--pct', `${Math.min(100, Math.max(0, profile.needed > 0 ? profile.intoLevel / profile.needed * 100 : 0))}%`);
  bar.append(fill); bar.setAttribute('role', 'progressbar'); bar.setAttribute('aria-valuenow', String(profile.intoLevel)); bar.setAttribute('aria-valuemax', String(profile.needed)); bar.setAttribute('aria-label', 'XP into this level');
  const caption = node('div', null, 'level-caption'); caption.append(node('span', `${profile.intoLevel.toLocaleString()} / ${profile.needed.toLocaleString()} XP`, 'muted'), node('span', `+${week.xp} this week`, 'lw-xp'));
  el.append(bar, caption); return el;
}
export async function renderOverview(root, { source, snapshot, router, nowMs = Date.now() }) {
  root.replaceChildren();
  const heading = node('header', null, 'screen-header'); heading.append(node('h1', 'Overview'));
  root.append(heading);
  if (!snapshot) root.append(node('p', `Sync the app to see your stats. ${t('concept_trial_cpl')} are still available.`, 'muted'));
  const rows = sortedWorkouts(await source.listWorkouts(), source, snapshot?.zone);
  if (!snapshot) { if (rows.length) root.append(link(`View ${t('concept_trial_pl')}`, '/workouts', router)); return; }
  const { profile, week, trends } = snapshot;
  if (!profile || !week || !trends) { root.append(node('p', 'Your phone has not synced all your stats yet. Open the app to sync.', 'muted')); if (rows.length) root.append(link(`View ${t('concept_trial_pl')}`, '/workouts', router)); return; }
  const layout = node('div', null, 'overview-layout'), main = node('div', null, 'ov-main'), side = node('div', null, 'ov-side'); layout.append(main, side); root.append(layout);
  const tiles = node('div', null, 'kpi-row');
  const [volume, volumeUnit] = splitVolume(week.volumeKg, snapshot.unit);
  tiles.append(
    kpi(weekPassed(snapshot, nowMs) ? `Week of ${formatDay(week.startDay)}` : 'This week', week.workouts, t('concept_trial_pl'), `${formatSets(week.hardSets)} hard sets${week.plannedDays != null ? ` · ${week.plannedDays} planned` : ''}`),
    kpi(t('concept_oath_c'), profile.streakDays, 'days', `Best: ${profile.longestStreakDays} days`),
    kpi('Records', trends.records30d, 'PRs', 'last 30 days'),
    kpi('Volume', volume, volumeUnit, `this week · ${splitVolume(week.prevVolumeKg, snapshot.unit).join(' ')} last`));
  main.append(tiles);
  const last = rows[0];
  if (last) {
    const entry = snapshotForWorkout(snapshot, last), day = workoutDay(last, snapshot.zone);
    const sets = await source.getSets(last), chips = [...new Set(sets.filter(s => s.exerciseName).map(s => s.modifiers ? `${s.exerciseName} · ${s.modifiers}` : s.exerciseName))];
    const volumeText = entry ? splitVolume(entry.volumeKg, snapshot.unit).join(' ') : '—';
    main.append(lastWorkoutCard({
      heading: `Your last ${t('concept_trial_s')}`, profile, when: `${formatWeekday(day)} ${formatDay(day)}`, meta: `${formatClock(last.startedAt, snapshot.zone)}${entry ? ` · ${entry.min} min` : ''}`, xp: entry?.xp,
      title: last.title || last.label || t('concept_trial_c'), path: workoutPath(last), router, unit: snapshot.unit, prs: entry?.prs, chips, levels: entry?.muscleLevels, figure: profile.figure,
      stats: entry ? `${formatSets(entry.hardSets)} hard sets · ${volumeText} · STR ${entry.strength}` : '— hard sets · — volume · — STR'
    }));
  } else main.append(node('p', `No ${t('concept_trial_pl')} yet.`));
  const pair = node('div', null, 'ov-pair');
  const records = card('Recent records', 'records-card', `${trends.records30d} in the last 30 days`);
  for (const record of trends.recent || []) records.append(recordRow(record, snapshot, router));
  const deeds = card('Deeds', 'deeds-card', `${profile.deedsEarned} of ${profile.deedsTotal}`);
  for (const deed of snapshot.deeds || []) deeds.append(deedRow(deed, snapshot.zone));
  pair.append(records, deeds); main.append(pair);
  side.append(levelCard(profile, week));
  const fatigue = node('div', null, 'fatigue-slot'); renderFatigueCard(fatigue, { fatigue: snapshot.fatigue, muscles: snapshot.muscles, figure: profile.figure, builtAtMs: snapshot.builtAtMs, zone: snapshot.zone, nowMs }); side.append(fatigue);
}
