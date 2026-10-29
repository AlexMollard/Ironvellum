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
export function kpi(label, value, unit, sub, target) {
  const el = card(null, `kpi${target ? ' is-link' : ''}`);
  const figure = node('div', value ?? '—', 'kpi-value');
  if (unit && value != null) figure.append(node('span', ` ${unit}`, 'kpi-unit'));
  el.append(node('div', null, 'eyebrow'), figure, node('div', sub ?? '', 'kpi-sub'));
  el.firstChild.append(target ? link(label, target.path, target.router, 'kpi-link stretch') : label);
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
  // A modified or non-primary click is the browser's own (new tab, new window, download); only a plain click is routed in place.
  el.addEventListener('click', e => { if (e.defaultPrevented || e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return; e.preventDefault(); router.go(path); });
  return el;
}
// The lift page for an exercise name, or null when the snapshot has no such lift (a custom exercise, or a friend's lift this lifter never logged).
export function liftPath(snapshot, name) {
  const lift = snapshot?.lifts?.find(l => String(l.name).trim().toLowerCase() === String(name ?? '').trim().toLowerCase());
  return lift ? `/lifts/${encodeURIComponent(lift.name)}` : null;
}
// An exercise name: a link to its lift page where one exists, plain text where not. `className` goes on either.
export function liftName(name, snapshot, router, className) {
  const path = router ? liftPath(snapshot, name) : null;
  return path ? link(name, path, router, className) : node('span', name, className);
}
