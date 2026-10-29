import catalogue from '../../generated/catalogue.json';
import { node } from '../../components/statCard.js';
import { personRow } from '../../components/personRow.js';
import { podium } from '../../components/podium.js';
import { stepLabel, MAX_STEP, knownLift } from '../../components/liftTier.js';
import { rankRows, rankLift, ordinal, METRICS, metricLabel, metricText } from '../../boardRanking.js';
import { t, format, word } from '../../i18n.js';

export const BOARD_KINDS = [['training', () => t('acct_board_training')], ['lifts', () => t('acct_board_lifts')], ['muster', () => word('The Veil', 'always_the_veil_c')]];
// The viewer's picks survive a re-render (the Friends | Everyone switch) for the session, like the phone's remembered ones.
let metric = 'xp', lift = 'squat', recent = false;
export const resetBoards = () => { metric = 'xp'; lift = 'squat'; recent = false; };

// The board kind is the one tab row on this screen (a segmented control); every other choice is a labelled native select in `.board-filters`.
const segmented = (label, options, current, pick) => {
  const group = node('div', null, 'segmented'); group.setAttribute('role', 'group'); group.setAttribute('aria-label', label);
  for (const [value, text] of options) { const b = node('button', text); b.type = 'button'; b.dataset.value = value; b.setAttribute('aria-pressed', String(value === current)); b.addEventListener('click', () => pick(value)); group.append(b); }
  return group;
};
const field = (label, options, current, className, pick) => {
  const wrap = node('label', null, 'board-field'), select = node('select', null, className);
  for (const [value, text] of options) { const o = node('option', text); o.value = value; o.selected = value === current; select.append(o); }
  select.addEventListener('change', () => pick(select.value)); wrap.append(node('span', label), select);
  return wrap;
};
const mine = (rows, me) => rows.findIndex(r => r.id === me);
const scoped = (rows, ctx, me) => (ctx.audience === 'allies' && ctx.allyIds ? rows.filter(r => r.id === me || ctx.allyIds.has(r.id)) : rows);

function trainingBoard(body, rows, me, redraw, filters) {
  filters.append(field(t('acct_ranked_by_label'), METRICS.map(m => [m, metricLabel(m)]), metric, 'board-metric', value => { metric = value; redraw(); }));
  const sorted = rankRows(metric, rows);
  if (!sorted.length) { body.append(node('p', t('acct_board_empty'), 'empty')); return; }
  body.append(podium(sorted.slice(0, 3).map((row, i) => ({ rank: i + 1, row, figure: metricText(metric, row) })), me));
  if (sorted.length === 1) body.append(node('p', t('acct_board_alone'), 'muted'));
  const list = node('div', null, 'board-rows');
  for (const [index, row] of sorted.entries()) {
    if (index < 3) continue;
    const sub = node('span', null, 'board-figure'); sub.append(node('strong', metricText(metric, row)));
    const line = personRow(row, { trailing: sub }); line.classList.add('board-row'); line.dataset.user = row.id; if (row.id === me) line.classList.add('is-me');
    line.prepend(node('span', String(index + 1), 'board-rank'));
    line.querySelector('.person-sub').textContent = [metric === 'level' ? format(t('acct_metric_xp_value'), Number(row.totalXp).toLocaleString('en-US')) : format(t('xp_level_label'), row.level), `${row.sessionsLast7d} ${word(row.sessionsLast7d === 1 ? 'workout' : 'workouts', row.sessionsLast7d === 1 ? 'concept_trial_s' : 'concept_trial_pl')} this week`].join(' · ');
    list.append(line);
  }
  body.append(list);
  const place = mine(sorted, me); if (place >= 0) { body.append(node('p', `${t('acct_board_your_standing')}: ${place + 1}`, 'muted board-you')); }
}

function liftBoard(body, rows, me, redraw, filters) {
  const known = catalogue.lifts.filter(l => knownLift(l.wire));
  filters.append(
    field('Lift', known.map(l => [l.wire, `${l.label} (${rankLift(rows, l.wire, recent).length})`]), lift, 'lift-pick', value => { lift = value; redraw(); }),
    field('Window', [['all', t('acct_window_all_time')], ['week', t('acct_window_week')]], recent ? 'week' : 'all', 'board-window', value => { recent = value === 'week'; redraw(); }));
  body.append(node('p', t('acct_lifts_note'), 'muted'));
  const standings = rankLift(rows, lift, recent);
  if (!standings.length) { body.append(node('p', t('acct_lift_none_ranked'), 'empty')); return; }
  const you = standings.find(s => s.row.id === me); if (you) body.append(node('p', `You: ${stepLabel(lift, you.step)} · ${ordinal(you.rank)}`, 'board-you'));
  const list = node('div', null, 'board-rows');
  for (const s of standings) {
    const bar = node('span', null, 'tier-bar'); bar.setAttribute('role', 'img'); bar.setAttribute('aria-label', `tier ${s.step} of ${MAX_STEP}`); for (let i = 1; i <= MAX_STEP; i++) bar.append(node('span', null, i <= s.step ? 'tier-cell is-on' : 'tier-cell'));
    const side = node('span', null, 'board-figure'); side.append(node('strong', stepLabel(lift, s.step)), bar, node('span', `tier ${s.step} of ${MAX_STEP}`, 'muted'));
    const line = personRow(s.row, { trailing: side }); line.classList.add('board-row'); line.dataset.user = s.row.id; line.dataset.rank = String(s.rank); if (s.row.id === me) line.classList.add('is-me');
    line.prepend(node('span', String(s.rank), 'board-rank')); list.append(line);
  }
  body.append(list);
}

function musterBoard(body, rows, me, redraw, filters) {
  body.append(node('p', t('line_veil_board_note'), 'muted'));
  if (!rows.length) { body.append(node('p', t('line_no_veil_echoes'), 'empty')); return; }
  const list = node('div', null, 'board-rows');
  for (const [index, row] of rows.entries()) {
    const side = node('span', null, 'board-figure'); side.append(node('strong', format(t('always_acct_essence_value'), Number(row.essence).toLocaleString('en-US'))));
    const line = personRow(row, { trailing: side }); line.classList.add('board-row'); line.dataset.user = row.id; if (row.id === me) line.classList.add('is-me');
    line.prepend(node('span', String(index + 1), 'board-rank'));
    line.querySelector('.person-sub').textContent = `${row.shadows} ${row.shadows === 1 ? 'echo' : 'echoes'} · ${format(t('acct_rate_per_hour'), Number(row.ratePerHour).toFixed(1))}`;
    list.append(line);
  }
  body.append(list);
}

/**
 * The boards, read-only: Training (the phone's metrics and its ranking rule, a podium for the top three), Lifts (tiers per lift, Week or
 * All time, ties sharing a rank) and the muster roll (banked essence). Rows are shown as the views serve them: a blocked lifter is
 * already absent. Friends | Everyone is the client filter the Friends shell passes in `ctx.allyIds`; picking it stores the choice
 * through `ctx.setAudience` and reloads the same board.
 */
export async function renderBoards(root, ctx, kind) {
  const { social, router, source } = ctx, me = source.userId;
  const active = BOARD_KINDS.some(([id]) => id === kind) ? kind : 'training';
  const nav = segmented('Board', BOARD_KINDS.map(([id, label]) => [id, label()]), active, id => router.go(`/friends/boards/${id}`));
  const filters = node('div', null, 'board-filters');
  filters.append(field('Show', [['allies', word('Friends', 'concept_ally_cpl')], ['everyone', 'Everyone']], ctx.audience, 'board-audience', value => { ctx.setAudience?.(value); router.go(kind ? `/friends/boards/${kind}` : '/friends/boards'); }));
  const body = node('div', null, 'board'); root.append(nav, filters, body);
  const rows = scoped(active === 'training' ? await social.leaderboard() : active === 'lifts' ? await social.liftBoard() : await social.shadowBoard(), ctx, me);
  const draw = () => {
    body.replaceChildren(); for (const extra of [...filters.children].slice(1)) extra.remove();
    (active === 'training' ? trainingBoard : active === 'lifts' ? liftBoard : musterBoard)(body, rows, me, draw, filters);
  };
  draw();
}
