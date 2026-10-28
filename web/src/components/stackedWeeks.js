import { formatDayShort } from '../format.js';
const GROUPS = ['BACK', 'CHEST', 'LEGS', 'SHOULDERS', 'ARMS', 'CORE'];
const labels = ['Back', 'Chest', 'Legs', 'Shoulders', 'Arms', 'Core'];
const NS = 'http://www.w3.org/2000/svg';
const make = (tag, attrs = {}) => { const el = document.createElementNS(NS, tag); for (const [key, value] of Object.entries(attrs)) el.setAttribute(key, String(value)); return el; };

export function renderStackedWeeks(root, weeks, height = 220) {
  root.replaceChildren();
  const width = Math.max(300, Math.round(root.clientWidth || 600)), top = 8, bottom = 26;
  const svg = make('svg', { viewBox: `0 0 ${width} ${height}`, role: 'img', 'aria-label': 'Hard sets by muscle group and week', class: 'stacked-weeks' });
  const totals = weeks.map(w => GROUPS.reduce((n, g) => n + (w.groups?.[g] || 0), 0));
  const max = Math.max(1, ...totals), slot = width / Math.max(weeks.length, 1), gap = Math.min(6, slot * 0.2);
  weeks.forEach((week, i) => {
    let y = height - bottom;
    GROUPS.forEach((group, j) => {
      const value = week.groups?.[group] || 0, h = value / max * (height - top - bottom); y -= h;
      const rect = make('rect', { x: i * slot + gap / 2, y, width: Math.max(1, slot - gap), height: h, fill: 'currentColor', class: `chart-series-${j}` });
      rect.dataset.group = group; rect.dataset.value = String(value); rect.dataset.week = week.start;
      const title = make('title'); title.textContent = `${week.start} · ${labels[j]}: ${value}`; rect.append(title); svg.append(rect);
    });
  });
  for (const [i, anchor] of [[0, 'start'], [weeks.length - 1, 'end']]) {
    if (!weeks[i] || (i === 0 && weeks.length === 1)) continue;
    const text = make('text', { x: i === 0 ? 0 : width, y: height - 8, 'text-anchor': anchor, class: 'chart-text' }); text.textContent = formatDayShort(weeks[i].start); svg.append(text);
  }
  root.append(svg);
  const legend = document.createElement('div'); legend.className = 'chart-legend';
  GROUPS.forEach((group, i) => { const item = document.createElement('span'); item.className = `legend-item chart-series-${i}`; item.textContent = labels[i]; legend.append(item); }); root.append(legend);
}
