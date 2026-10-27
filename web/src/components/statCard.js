export function node(tag, text, className) {
  const el = document.createElement(tag);
  if (text !== undefined && text !== null) el.textContent = String(text);
  if (className) el.className = className;
  return el;
}
export function card(title, className = '') {
  const el = node('section', null, `card ${className}`.trim());
  if (title) el.append(node('h2', title));
  return el;
}
export function statCard(label, value) {
  const el = node('div', null, 'stat-card');
  el.append(node('span', label, 'muted'), node('strong', value ?? '—'));
  return el;
}
export function dayKey(value, zone = 'UTC') {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '';
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: zone || 'UTC', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(date);
  const get = key => parts.find(p => p.type === key)?.value;
  return `${get('year')}-${get('month')}-${get('day')}`;
}
export function displayDate(value, zone = 'UTC') {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '—';
  return new Intl.DateTimeFormat(undefined, { timeZone: zone || 'UTC', year: 'numeric', month: 'short', day: 'numeric' }).format(date);
}
export function owned(rows, source) {
  return rows.filter(row => (row.userId ?? row.user_id ?? source.userId) === source.userId);
}
export function sortedWorkouts(rows, source) {
  return owned(rows, source).slice().sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt));
}
export function link(text, path, router, className) {
  const el = node('a', text, className); el.href = `#${path}`;
  el.addEventListener('click', e => { e.preventDefault(); router.go(path); });
  return el;
}
