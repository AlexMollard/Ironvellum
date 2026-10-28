import { formatLoad } from '../format.js';

export function formatPeak(peak, unit, addsToBody = false) {
  if (!peak) return '—';
  if (peak.durationSec != null) return `${peak.reps || 1} × 1 at ${peak.durationSec} s`;
  const load = peak.weightKg == null || peak.weightKg === 0 ? 'BW' : `${addsToBody && peak.weightKg > 0 ? '+' : ''}${formatLoad(peak.weightKg, unit)} ${unit}`;
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

export function renderVariationTable(root, { variations, selected, onToggle, unit, zone, builtAtMs, addsToBody = false }) {
  root.replaceChildren();
  const table = document.createElement('table');
  table.className = 'variation-table';
  const head = document.createElement('thead');
  const header = document.createElement('tr');
  for (const label of ['Variation', 'Best est. 1RM', 'Best set', 'Last done']) { const th = document.createElement('th'); th.textContent = label; header.append(th); }
  head.append(header); table.append(head);
  const body = document.createElement('tbody');
  variations.forEach((v, index) => {
    const tr = document.createElement('tr');
    const first = document.createElement('td');
    const button = document.createElement('button'); button.type = 'button'; button.className = `variation-toggle chart-series-${index % 8}`;
    button.setAttribute('aria-pressed', String(selected.has(v.key))); button.textContent = v.label;
    button.addEventListener('click', () => onToggle(v.key)); first.append(button); tr.append(first);
    const values = v.sets ? [v.peak?.e1rmKg == null ? '—' : `${formatLoad(v.peak.e1rmKg, unit)} ${unit}`, formatPeak(v.peak, unit, addsToBody), lastDone(v.lastMs, builtAtMs, zone)] : ['Not yet', 'Not yet', 'Not yet'];
    for (const value of values) { const td = document.createElement('td'); td.textContent = value; tr.append(td); }
    body.append(tr);
  });
  table.append(body); root.append(table);
}
