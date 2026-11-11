import { formatWeight } from '../format.js';

export function formatPeak(peak, unit, addsToBody = false, assisted = false, defaultUnit = unit) {
  if (!peak) return '—';
  if (peak.durationSec != null) return `${peak.reps || 1} × 1 at ${peak.durationSec} s`;
  const load = assisted ? (peak.weightKg == null ? '—' : `${formatWeight(peak.weightKg, unit, defaultUnit)} assistance`) : peak.weightKg == null || peak.weightKg === 0 ? 'BW' : `${addsToBody && peak.weightKg > 0 ? '+' : ''}${formatWeight(peak.weightKg, unit, defaultUnit)}`;
  return `${load} × ${peak.reps}`;
}

function lastDone(ms, builtAtMs, zone) {
  if (ms == null) return '—';
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' });
  const iso = value => { const parts = Object.fromEntries(day.formatToParts(value).map(p => [p.type, p.value])); return `${parts.year}-${parts.month}-${parts.day}`; };
  const current = iso(builtAtMs), date = iso(ms);
  const currentDay = new Date(`${current}T00:00:00Z`);
  const monday = new Date(currentDay.getTime() - ((currentDay.getUTCDay() + 6) % 7) * 86400000).toISOString().slice(0, 10);
  return date >= monday && date <= current ? new Intl.DateTimeFormat(undefined, { timeZone: zone, weekday: 'long' }).format(ms) : new Intl.DateTimeFormat(undefined, { timeZone: zone, day: 'numeric', month: 'short', year: 'numeric' }).format(ms);
}

export function renderVariationTable(root, { variations, selected, onToggle, unit, defaultUnit = unit, zone, builtAtMs, addsToBody = false, assisted = false }) {
  root.replaceChildren();
  const table = document.createElement('table');
  table.className = 'variation-table';
  const head = document.createElement('thead');
  const header = document.createElement('tr');
  for (const label of ['Variation', 'Best est. 1RM', 'Best set', 'Last done']) { const th = document.createElement('th'); th.textContent = label; header.append(th); }
  head.append(header); table.append(head);
  const body = document.createElement('tbody');
  variations.forEach((v, index) => {
    const tr = document.createElement('tr'); tr.className = 'is-link';
    const first = document.createElement('td');
    const button = document.createElement('button'); button.type = 'button'; button.className = `variation-toggle stretch chart-series-${index % 8}`;
    button.setAttribute('aria-pressed', String(selected.has(v.key))); button.textContent = v.label;
    if (selected.has(v.key) && selected.size === 1) button.setAttribute('aria-disabled', 'true');
    button.addEventListener('click', () => onToggle(v.key)); first.append(button); tr.append(first);
    const values = v.sets ? [v.peak?.e1rmKg == null ? '—' : formatWeight(v.peak.e1rmKg, unit, defaultUnit), formatPeak(v.peak, unit, addsToBody, assisted, defaultUnit), lastDone(v.lastMs, builtAtMs, zone)] : ['Not yet', 'Not yet', 'Not yet'];
    for (const value of values) { const td = document.createElement('td'); td.textContent = value; tr.append(td); }
    body.append(tr);
  });
  table.append(body); root.append(table);
}
