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
