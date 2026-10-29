export function formatLoad(kg, unit = 'kg') {
  const value = unit === 'lb' ? Math.round(kg / 0.45359237 * 2) / 2 : Math.round(kg * 100) / 100;
  return String(Object.is(value, -0) ? 0 : value);
}
export function formatVolume(kg, unit = 'kg') {
  const amount = unit === 'lb' ? kg / 0.45359237 : kg;
  const value = Math.round(amount / 100) / 10;
  return `${Object.is(value, -0) ? 0 : value.toFixed(1)} ${unit === 'lb' ? 'k lb' : 't'}`;
}
export function formatSets(n) {
  if (Array.isArray(n)) return n.length === 0 ? '' : n.length === 1 ? String(n[0]) : n.every(x => x === n[0]) ? `${n.length} × ${n[0]}` : n.join('/');
  return String(Number(Number(n).toFixed(1)));
}
// "1 hard set", "2 hard sets": the noun agrees with the figure. `n` may be a number or a numeric string; "1.0" counts as one.
export function plural(n, one, other = `${one}s`) { return `${formatSets(n)} ${Number(n) === 1 ? one : other}`; }
export function formatDay(isoDay, zoneNote) {
  if (!isoDay) return '—';
  const date = new Date(/^\d{4}-\d\d-\d\d$/.test(isoDay) ? `${isoDay}T12:00:00Z` : isoDay);
  if (Number.isNaN(date.valueOf())) return '—';
  return new Intl.DateTimeFormat(undefined, {day:'numeric', month:'short', year:'numeric', timeZone: /^\d{4}-\d\d-\d\d$/.test(isoDay) ? 'UTC' : (zoneNote || 'UTC')}).format(date);
}
export function formatDayShort(isoDay) {
  if (!/^\d{4}-\d\d-\d\d$/.test(isoDay ?? '')) return '—';
  return new Intl.DateTimeFormat(undefined, {day:'numeric', month:'short', timeZone:'UTC'}).format(new Date(`${isoDay}T12:00:00Z`));
}
export function formatWeekday(isoDay, long = false) {
  if (!/^\d{4}-\d\d-\d\d$/.test(isoDay ?? '')) return '';
  return new Intl.DateTimeFormat(undefined, {weekday: long ? 'long' : 'short', timeZone: 'UTC'}).format(new Date(`${isoDay}T12:00:00Z`));
}
export function formatClock(ms, zone = 'UTC') {
  const date = new Date(ms);
  if (Number.isNaN(date.valueOf())) return '—';
  return new Intl.DateTimeFormat(undefined, {hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: zone || 'UTC'}).format(date);
}
export function splitVolume(kg, unit = 'kg') {
  const text = formatVolume(kg, unit), space = text.indexOf(' ');
  return [text.slice(0, space), text.slice(space + 1)];
}
export function splitLabel(label = '') {
  const [name, ...rest] = String(label).split(/\s+[·.]\s+/);
  return [name, rest.join(' · ')];
}
export const SET_KINDS = { working: 'Working', warmup: 'Warm-up', top: 'Top', backoff: 'Back-off', drop: 'Drop', failure: 'Failure', rest_pause: 'Rest-pause' };
export const setKindName = set => SET_KINDS[set.kind] || 'Working';
// Whether the load logged on an exercise is added to the body (a pull-up's plates), as the app's BodyShare.addsToBody
// decides it. The snapshot carries that per lift; an exercise it does not list reads as a plain load.
export function addsToBody(snapshot, exerciseName) {
  const name = String(exerciseName ?? '').trim().toLowerCase();
  return !!snapshot?.lifts?.find(l => String(l.name).trim().toLowerCase() === name)?.addsToBody;
}
// Load of one set: bodyweight reads BW, an assisted set keeps its minus sign, and weight added to the body gets a plus.
export function formatSetLoad(set, unit = 'kg', addsToBody = false) {
  if (set.weightKg == null || set.weightKg === 0) return 'BW';
  return `${addsToBody && set.weightKg > 0 ? '+' : ''}${formatLoad(set.weightKg, unit)} ${unit}`;
}
// Reps, hold time, distance and grade of one set, as the parts the set table joins with " · ".
export function setPerformance(set) {
  const distance = set.distanceM == null ? null : set.distanceM >= 1000
    ? `${Number((set.distanceM / 1000).toFixed(5))} km`
    : `${Number(Number(set.distanceM).toFixed(2))} m`;
  return [set.reps ? `${set.reps}` : null, set.durationSec != null ? `${set.durationSec} s` : null, distance, set.grade || null].filter(Boolean);
}
// The effort figure to show (reps left in RIR mode, the stored RPE otherwise); null when unknown.
export function setEffort(set, snapshot) {
  if (set.rpe == null) return null;
  return snapshot?.effort === 'rir' ? (snapshot?.rir?.[String(set.rpe)] ?? null) : set.rpe;
}
