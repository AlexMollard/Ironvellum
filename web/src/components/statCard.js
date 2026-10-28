export function node(tag, text, className) {
  const el = document.createElement(tag);
  if (text !== undefined && text !== null) el.textContent = String(text);
  if (className) el.className = className;
  return el;
}
export function card(title, className = '', aside) {
  const el = node('section', null, `card ${className}`.trim());
  if (title || aside != null) {
    const head = node('div', null, 'card-head');
    if (title) head.append(node('h2', title));
    if (aside != null) head.append(aside instanceof Node ? aside : node('span', aside, 'card-aside'));
    el.append(head);
  }
  return el;
}
export function kpi(label, value, unit, sub) {
  const el = card(null, 'kpi');
  const figure = node('div', value ?? '—', 'kpi-value');
  if (unit && value != null) figure.append(node('span', ` ${unit}`, 'kpi-unit'));
  el.append(node('div', label, 'eyebrow'), figure, node('div', sub ?? '', 'kpi-sub'));
  return el;
}
export function factRow(label, value) {
  const el = node('div', null, 'fact-row');
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
export function weekPassed(snapshot, nowMs = Date.now()) {
  const end = snapshot?.week?.endDay;
  return Boolean(end) && dayKey(nowMs, snapshot.zone) > end;
}
export function workoutDay(workout, zone = 'UTC') {
  return dayKey(workout.completedAt ?? workout.startedAt, zone);
}
export function sortedWorkouts(rows, source, zone) {
  return owned(rows, source).slice().sort((a, b) => workoutDay(b, zone).localeCompare(workoutDay(a, zone)) || new Date(b.startedAt) - new Date(a.startedAt));
}
export function link(text, path, router, className) {
  const el = node('a', text, className); el.href = `#${path}`;
  el.addEventListener('click', e => { e.preventDefault(); router.go(path); });
  return el;
}
