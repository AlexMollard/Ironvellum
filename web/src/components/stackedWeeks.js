import { formatDayShort, formatDay } from '../format.js';
import { showTip, hideTip, roving, clientPoint, svgPoint } from './interact.js';
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
  const hits = [];
  weeks.forEach((week, i) => {
    let y = height - bottom;
    GROUPS.forEach((group, j) => {
      const value = week.groups?.[group] || 0, h = value / max * (height - top - bottom); y -= h;
      const rect = make('rect', { x: i * slot + gap / 2, y, width: Math.max(1, slot - gap), height: h, fill: 'currentColor', class: `chart-series-${j}` });
      rect.dataset.group = group; rect.dataset.value = String(value); rect.dataset.week = week.start;
      svg.append(rect);
    });
    const left = i * slot, lines = GROUPS.map((group, j) => ({ text: `${labels[j]}: ${week.groups?.[group] || 0}`, series: j }));
    const hit = make('path', { d: `M${left} 0H${left + slot}V${height - bottom}H${left}Z`, class: 'chart-hit', tabindex: -1, role: 'img', 'aria-label': `Week of ${formatDay(week.start)}: ${lines.map(l => l.text).join(', ')} hard sets` });
    hit.dataset.week = week.start;
    hits.push({ hit, x: left + slot / 2, y, content: { title: `Week of ${formatDay(week.start)}`, value: 'Hard sets', lines } });
    svg.append(hit);
  });
  for (const [i, anchor] of [[0, 'start'], [weeks.length - 1, 'end']]) {
    if (!weeks[i] || (i === 0 && weeks.length === 1)) continue;
    const text = make('text', { x: i === 0 ? 0 : width, y: height - 8, 'text-anchor': anchor, class: 'chart-text' }); text.textContent = formatDayShort(weeks[i].start); svg.append(text);
  }
  root.append(svg);
  interact(svg, hits, width, height);
  const legend = document.createElement('div'); legend.className = 'chart-legend';
  GROUPS.forEach((group, i) => { const item = document.createElement('span'); item.className = `legend-item chart-series-${i}`; item.textContent = labels[i]; legend.append(item); }); root.append(legend);
}

// Hover or tap a week to see its groups; Tab reaches the first week and Left/Right move between weeks.
function interact(svg, hits, width, height) {
  if (!hits.length) return;
  let active = null;
  const clear = () => { active?.hit.classList.remove('is-active'); active = null; hideTip(svg); };
  const activate = target => {
    if (active === target) return;
    active?.hit.classList.remove('is-active'); active = target; target.hit.classList.add('is-active');
    showTip(target.content, { anchor: target.hit, owner: svg, point: clientPoint(svg, target.x, target.y, width, height) });
  };
  const move = event => { const at = svgPoint(svg, event, width, height); activate(hits.reduce((b, t) => Math.abs(t.x - at.x) < Math.abs(b.x - at.x) ? t : b)); };
  svg.addEventListener('pointermove', move); svg.addEventListener('pointerdown', move);
  svg.addEventListener('pointerleave', event => { if (event.pointerType === 'touch') return; const focused = hits.find(t => t.hit === document.activeElement); if (focused) activate(focused); else clear(); });
  roving(hits.map(t => t.hit), (index, key) => (key === 'ArrowRight' ? index + 1 : key === 'ArrowLeft' ? index - 1 : undefined));
  for (const t of hits) { t.hit.addEventListener('focus', () => activate(t)); t.hit.addEventListener('blur', () => { if (active === t) clear(); }); }
}
