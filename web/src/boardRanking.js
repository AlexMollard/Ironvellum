import { t, format, word } from './i18n.js';

/** The training board's metrics in the phone's order, with the id the parity cases use (`BoardRanking.Metric` lowercased). */
export const METRICS = ['xp', 'level', 'streak', 'titles', 'strength', 'last7'];
const VALUE = { xp: r => r.totalXp, level: r => r.level, streak: r => r.streakDays, titles: r => r.titlesCount, strength: r => r.lifetimeStrength, last7: r => r.sessionsLast7d };
/** The raw value a metric ranks by. */
export const metricValue = (metric, row) => VALUE[metric](row);

// Kotlin compares strings by UTF-16 code unit, which is what < and > do here (localeCompare would order differently).
const byName = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

/**
 * The phone's `BoardRanking.sort`: the metric's value, then XP, then name, best first (a sorted copy). `rankLift` is its
 * `liftStandings`: one lift for a window, best tier first, ties sharing a rank (1, 1, 3), and the week window dropping lifters with no
 * recent step. Both are pinned by `boardCases` in test/fixtures/parity.json, which the phone generates.
 */
export const rankRows = (metric, rows) => [...rows].sort((a, b) => metricValue(metric, b) - metricValue(metric, a) || b.totalXp - a.totalXp || byName(a.name, b.name));

export function rankLift(rows, lift, recent) {
  const ranked = rows.filter(r => r.lift === lift).map(r => ({ row: r, step: recent ? r.recentStep : r.step })).filter(x => x.step != null)
    .sort((a, b) => b.step - a.step || byName(a.row.name.toLowerCase(), b.row.name.toLowerCase()));
  let rank = 0;
  return ranked.map((x, index) => { if (index === 0 || x.step !== ranked[index - 1].step) rank = index + 1; return { rank, row: x.row, step: x.step }; });
}

export const ordinal = n => `${n}${n % 100 >= 11 && n % 100 <= 13 ? 'th' : ({ 1: 'st', 2: 'nd', 3: 'rd' })[n % 10] ?? 'th'}`;
const grouped = n => Number(n).toLocaleString('en-US');

export const metricLabel = metric => ({ xp: () => t('acct_metric_xp'), level: () => t('acct_metric_level'), streak: () => word('Streak', 'concept_oath_c'), titles: () => word('Deeds', 'always_deed_cpl'), strength: () => t('acct_metric_strength'), last7: () => t('acct_metric_last7') })[metric]();

/** The big per-row figure of a metric, in the phone's own strings. */
export function metricText(metric, row) {
  switch (metric) {
    case 'xp': return format(t('acct_metric_xp_value'), grouped(row.totalXp));
    case 'level': return format(t('xp_level_label'), row.level);
    case 'streak': return `${row.streakDays} ${row.streakDays === 1 ? 'day' : 'days'}`;
    case 'titles': return `${row.titlesCount} ${word(row.titlesCount === 1 ? 'deed' : 'deeds', row.titlesCount === 1 ? 'always_deed_s' : 'always_deed_pl')}`;
    case 'strength': return format(t('acct_metric_strength_value'), grouped(row.lifetimeStrength));
    default: return format(t('acct_metric_last7_value'), row.sessionsLast7d);
  }
}
