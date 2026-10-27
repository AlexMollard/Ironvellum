export function formatLoad(kg, unit = 'kg') {
  const value = unit === 'lb' ? Math.round(kg / 0.45359237 * 2) / 2 : Math.round(kg * 100) / 100;
  return String(Object.is(value, -0) ? 0 : value);
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
