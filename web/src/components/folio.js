import { node, displayDate, link } from './statCard.js';
import { plate } from './plate.js';
import { titleName } from './personRow.js';
import { hiddenName } from './hiddenName.js';
import { liftTier, knownLift } from './liftTier.js';
import { reasonDialog, confirmPanel } from './reasonDialog.js';
import { renderLineChart } from './lineChart.js';
import { lifterSummary } from '../lifterSummary.js';
import { formatSetLoad, setPerformance, formatDay, plural } from '../format.js';
import { explain } from '../explain.js';
import { t, format, word } from '../i18n.js';

const grouped = n => Number(n).toLocaleString('en-US');
const dayOf = ms => formatDay(new Date(ms).toISOString().slice(0, 10));

/** The "N workouts shared" / "last workout" figures of the summary line, through the phone's own strings. */
export function summaryLine(figures) {
  const trial = n => word(n === 1 ? 'workout' : 'workouts', n === 1 ? 'concept_trial_s' : 'concept_trial_pl');
  const parts = [`${figures.count} ${trial(figures.count)} shared`, format(t('lifter_xp_gain'), grouped(figures.xp)), format(t('lifter_summary_per_week'), figures.perWeek)];
  if (figures.daysSince != null) parts.push(figures.daysSince === 0 ? t('lifter_summary_last_today') : figures.daysSince === 1 ? t('lifter_summary_last_yesterday') : `last ${trial(1)} ${plural(figures.daysSince, 'day')} ago`);
  return parts.join(' · ');
}

function workoutSets(body, workout, unit) {
  body.replaceChildren();
  if (workout.note) body.append(node('p', workout.note, 'muted'));
  for (const exercise of workout.exercises) {
    const block = node('div', null, 'folio-exercise'); block.append(node('strong', exercise.name));
    for (const [index, set] of exercise.sets.entries()) {
      const line = node('div', [`Set ${index + 1}`, formatSetLoad(set, unit), setPerformance(set).join(' · ')].filter(Boolean).join(' · '), set.done ? 'folio-set' : 'folio-set muted'); block.append(line);
    }
    body.append(block);
  }
}

/**
 * One lifter's folio: what the phone's lifter screen shows, from the same reads (`social.lifter`, `social.lifterWorkout`), read-only
 * and in the viewer's unit, then the manage panel. `onGone()` runs after a block or a removal so the caller leaves the folio.
 */
export function renderFolio(root, { social, snapshot, me, nowMs = Date.now(), onGone, data, router }) {
  const status = node('p', null, 'notice folio-status'); status.setAttribute('role', 'status');
  const say = text => { status.textContent = text; };
  const name = hiddenName(data.profile?.name);
  const head = node('div', null, 'folio-head'), emblem = node('span'); plate(emblem, { name, crestId: data.profile?.crestId });
  const who = node('div', null, 'folio-who'); const heading = node('h2', name); heading.tabIndex = -1; who.append(heading);
  who.append(node('div', [data.profile?.level != null ? `Level ${data.profile.level}` : '', titleName(data.profile?.titleId)].filter(Boolean).join(' · '), 'muted'));
  if (data.allySinceMs != null) who.append(node('div', `${word('Friends', 'concept_ally_cpl')} since ${displayDate(data.allySinceMs, 'UTC')}`, 'muted folio-since'));
  const back = link('‹ All friends', '/friends/allies', router, 'folio-back'); head.append(emblem, who); root.append(back, head);

  if (data.profile?.totalXp != null) root.append(node('div', [`${grouped(data.profile.totalXp)} XP`, data.profile.streakDays != null ? `${data.profile.streakDays} day streak` : ''].filter(Boolean).join(' · '), 'muted folio-total'));
  const sessions = data.sessions;
  if (!sessions.length) root.append(node('p', t('lifter_nothing_shared'), 'muted folio-empty'));
  else {
    const figures = lifterSummary(sessions, nowMs);
    root.append(node('p', summaryLine({ ...figures, count: data.workoutsShared }), 'muted folio-summary'));
    const strength = node('section', null, 'folio-strength'), title = node('div', null, 'folio-strength-head'); title.append(node('h3', t('lifter_strength_title')));
    const chrono = [...sessions].sort((a, b) => (a.completedAtMs ?? Infinity) - (b.completedAtMs ?? Infinity));
    if (figures.series.length >= 2) {
      title.append(node('span', format(t('lifter_strength_peak'), figures.peak), 'chip'));
      const plot = node('div', null, 'folio-chart'); strength.append(title, plot);
      const dated = chrono.every(s => s.completedAtMs != null);
      renderLineChart(plot, { series: [{ points: chrono.map((s, i) => [dated ? s.completedAtMs : i, null, s.strength, s.strength === figures.peak, s.completedAtMs != null ? dayOf(s.completedAtMs) : '', undefined]) }], label: t('lifter_strength_title'), unit: '', height: 180, formatValue: v => String(Math.round(v)) });
    } else strength.append(title, node('p', t('lifter_strength_single'), 'muted'));
    root.append(strength);

    const recent = node('section', null, 'folio-recent'); recent.append(node('h3', t('lifter_recent_header')));
    for (const s of sessions) {
      const item = node('div', null, 'folio-workout'), open = node('button', null, 'folio-open'); open.type = 'button'; open.setAttribute('aria-expanded', 'false');
      open.append(node('strong', s.title || s.label || word('workout', 'concept_trial_s')), node('span', [s.completedAtMs != null ? dayOf(s.completedAtMs) : '', plural(s.sets, 'set'), format(t('lifter_str_figure'), s.strength), format(t('lifter_xp_gain'), grouped(s.xp)), s.editedAtMs ? t('lifter_amended') : ''].filter(Boolean).join(' · '), 'muted'));
      const chevron = node('span', '›', 'folio-chevron'); chevron.setAttribute('aria-hidden', 'true'); open.append(chevron);
      const body = node('div', null, 'folio-sets'); body.hidden = true; let loaded = false;
      open.addEventListener('click', async () => {
        body.hidden = !body.hidden; open.setAttribute('aria-expanded', String(!body.hidden));
        if (body.hidden || loaded) return;
        try { workoutSets(body, await social.lifterWorkout(s.id), snapshot?.unit); loaded = true; } catch (error) { body.replaceChildren(node('p', error?.name === 'WorkoutHidden' ? t('cloud_sync_trial_hidden') : explain(error), 'notice')); }
      });
      item.append(open, body); recent.append(item);
    }
    root.append(recent);
  }
  const lifts = data.lifts.filter(l => knownLift(l.lift) && l.step > 0);
  if (lifts.length) { const boards = node('section', null, 'folio-lifts'); boards.append(node('h3', 'Lift boards')); for (const l of lifts) boards.append(liftTier(l)); root.append(boards); }

  if (data.userId !== me) {
    const manage = node('section', null, 'panel folio-manage'); manage.append(node('h3', t('lifter_manage_header')));
    const slot = node('div', null, 'folio-panel');
    const muteRow = node('label', null, 'folio-mute'), mute = node('input'); mute.type = 'checkbox'; mute.checked = data.muted;
    muteRow.append(mute, node('span', t('lifter_mute_label')), node('span', t('lifter_mute_caption'), 'muted'));
    mute.addEventListener('change', async () => {
      const want = mute.checked; mute.disabled = true;
      try { await (want ? social.mute(data.userId) : social.unmute(data.userId)); say(t(want ? 'lifter_muted' : 'lifter_unmuted')); } catch (error) { mute.checked = !want; say(explain(error)); } finally { mute.disabled = false; }
    });
    manage.append(muteRow);
    const button = (label, run) => { const b = node('button', label); b.type = 'button'; b.addEventListener('click', run); return b; };
    const actions = node('div', null, 'panel-actions'), close = () => slot.replaceChildren();
    if (data.accepted) actions.append(button(t('lifter_remove_ally_label'), () => slot.replaceChildren(confirmPanel({ title: format(t('lifter_remove_title'), name), body: t('lifter_remove_body'), confirmLabel: t('lifter_remove_button'), cancelLabel: t('lifter_keep_button'), explain,
      onConfirm: async () => { await social.removeFriend(data.userId); say(t('lifter_no_longer_allies')); onGone?.(); }, onCancel: close }))));
    actions.append(
      button(t('lifter_block_label'), () => slot.replaceChildren(confirmPanel({ title: format(t('lifter_block_title'), name), body: format(t('lifter_block_body'), name), confirmLabel: t('lifter_block_label'), explain,
        onConfirm: async () => { await social.block(data.userId); onGone?.(); }, onCancel: close }))),
      button(t('lifter_report_label'), () => slot.replaceChildren(reasonDialog({ title: format(t('comments_report_title'), name), explain,
        onSend: async ({ reason, note }) => { await social.report({ targetUserId: data.userId, reason, note }); close(); say(t('lifter_report_sent')); }, onCancel: close }))),
    );
    manage.append(actions, slot); root.append(manage);
  }
  root.append(status);
}
