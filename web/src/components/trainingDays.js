export function renderTrainingDays(root, days, builtAtMs, zone) {
  root.replaceChildren(); root.className = 'training-days';
  const formatter = new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' });
  const parts = Object.fromEntries(formatter.formatToParts(builtAtMs).map(p => [p.type, p.value]));
  const end = Date.parse(`${parts.year}-${parts.month}-${parts.day}T00:00:00Z`);
  const trained = new Set(days);
  for (let i = 181; i >= 0; i--) {
    const day = new Date(end - i * 86400000).toISOString().slice(0, 10);
    const cell = document.createElement('span'); cell.className = `training-day${trained.has(day) ? ' trained' : ''}`; cell.dataset.day = day; cell.title = day; cell.setAttribute('aria-label', `${day}${trained.has(day) ? ' · workout' : ''}`); root.append(cell);
  }
}
