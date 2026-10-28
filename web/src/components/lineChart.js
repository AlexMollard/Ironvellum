import { word } from '../i18n.js';
import { showTip, hideTip, roving, clientPoint, svgPoint } from './interact.js';
const NS = 'http://www.w3.org/2000/svg';
const make = (tag, attrs = {}) => { const el = document.createElementNS(NS, tag); for (const [key, value] of Object.entries(attrs)) el.setAttribute(key, String(value)); return el; };

/** Plot the supplied values without deriving training figures. */
export function renderLineChart(root, { series, label = 'Progress', formatValue = String, unit = '', height = 220, details = true, pointTitle = p => p[4] }) {
  root.replaceChildren();
  const all = series.flatMap(s => s.points);
  if (!all.length) return;
  const width = Math.max(300, Math.round(root.clientWidth || 600)), right = 14, top = 14, bottom = 28;
  const widest = Math.max(...[Math.min(...all.map(p => p[2])), Math.max(...all.map(p => p[2]))].map(v => `${formatValue(v)}${unit ? ` ${unit}` : ''}`.length));
  const left = Math.max(48, Math.ceil(widest * 6.4) + 16);
  const svg = make('svg', { viewBox: `0 0 ${width} ${height}`, role: 'img', 'aria-label': label, class: 'line-chart' });
  const xs = all.map(p => p[0]), ys = all.map(p => p[2]);
  const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
  const x = v => left + (maxX === minX ? (width - left - right) / 2 : (v - minX) / (maxX - minX) * (width - left - right));
  const y = v => height - bottom - (maxY === minY ? (height - top - bottom) / 2 : (v - minY) / (maxY - minY) * (height - top - bottom));
  const span = maxY - minY, ticks = minY === maxY ? [minY] : [0, 1, 2, 3, 4].map(i => i === 0 ? minY : i === 4 ? maxY : minY + span * i / 4);
  const tidy = value => value === minY || value === maxY ? value : Number(value.toFixed(span >= 8 ? 0 : 1));
  for (const value of ticks) {
    svg.append(make('line', { x1: left, x2: width - right, y1: y(value), y2: y(value), class: 'chart-grid' }));
    const tick = make('text', { x: left - 8, y: y(value), 'text-anchor': 'end', 'dominant-baseline': 'middle', class: 'chart-text' });
    tick.textContent = `${formatValue(tidy(value))}${unit ? ` ${unit}` : ''}`; svg.append(tick);
  }
  const cursor = make('line', { x1: 0, x2: 0, y1: top, y2: height - bottom, class: 'chart-cursor', visibility: 'hidden' }); svg.append(cursor);
  const groups = [];
  series.forEach((s, index) => {
    const marks = []; groups.push(marks);
    const group = make('g', { class: `chart-series chart-series-${(s.index ?? index) % 8}` });
    group.append(make('polyline', { points: s.points.map(p => `${x(p[0])},${y(p[2])}`).join(' '), fill: 'none', stroke: 'currentColor', 'stroke-width': 2, 'stroke-linejoin': 'round' }));
    for (const p of s.points) {
      const px = x(p[0]), py = y(p[2]);
      const mark = p[3] ? make('polygon', { points: `${px},${py - 6} ${px + 6},${py} ${px},${py + 6} ${px - 6},${py}`, class: 'chart-pr' }) : make('circle', { cx: px, cy: py, r: 4 });
      mark.setAttribute('fill', 'currentColor');
      mark.dataset.value = String(p[2]);
      mark.dataset.at = String(p[0]);
      mark.setAttribute('aria-label', `${p[4] ?? label}: ${p[5] ?? `${formatValue(p[2])}${unit ? ` ${unit}` : ''}`}`);
      mark.setAttribute('role', 'img'); mark.classList.add('chart-point');
      marks.push({ mark, x: px, y: py, content: { title: pointTitle(p), value: p[5] ?? `${formatValue(p[2])}${unit ? ` ${unit}` : ''}`, pr: Boolean(p[3]), lines: s.name ? [{ text: s.name, series: s.index ?? index }] : [] } });
      group.append(mark);
    }
    svg.append(group);
  });
  const labelled = all.filter(p => p[4] != null).sort((a, b) => a[0] - b[0]);
  for (const p of labelled.length > 1 ? [labelled[0], labelled[labelled.length - 1]] : labelled) {
    const text = make('text', { x: x(p[0]), y: height - 8, 'text-anchor': p === labelled[0] ? 'start' : 'end', class: 'chart-text' });
    text.textContent = p[6] ?? p[4]; svg.append(text);
  }
  root.append(svg);
  interact(svg, cursor, groups, width, height);
  if (!details) return;
  const values = document.createElement('details');
  const summary = document.createElement('summary'); summary.textContent = 'Chart values'; values.append(summary);
  const list = document.createElement('ul');
  for (const s of series) for (const p of s.points) {
    const item = document.createElement('li');
    item.textContent = `${s.name ? `${s.name} · ` : ''}${p[4] ?? ''}: ${p[5] ?? `${formatValue(p[2])}${unit ? ` ${unit}` : ''}`}${p[3] ? ` · ${word('PR', 'concept_peak_u')}` : ''}`;
    list.append(item);
  }
  values.append(list); root.append(values);
}

// Hover or tap shows the nearest point; Tab reaches the first point, Left/Right walk a series, Up/Down hop to another series.
function interact(svg, cursor, groups, width, height) {
  const targets = groups.flat(), byMark = new Map(targets.map(t => [t.mark, t]));
  let active = null;
  const clear = () => { active?.mark.classList.remove('is-active'); active = null; cursor.setAttribute('visibility', 'hidden'); hideTip(svg); };
  const activate = target => {
    if (active === target) return;
    active?.mark.classList.remove('is-active'); active = target; target.mark.classList.add('is-active');
    cursor.setAttribute('x1', target.x); cursor.setAttribute('x2', target.x); cursor.removeAttribute('visibility');
    showTip(target.content, { anchor: target.mark, owner: svg, point: clientPoint(svg, target.x, target.y, width, height) });
  };
  const nearest = event => {
    const at = svgPoint(svg, event, width, height); let best = null, bestScore = Infinity;
    for (const t of targets) { const score = Math.abs(t.x - at.x) + Math.abs(t.y - at.y) * .25; if (score < bestScore) { best = t; bestScore = score; } }
    return best;
  };
  const move = event => { const t = nearest(event); if (t) activate(t); };
  svg.addEventListener('pointermove', move);
  svg.addEventListener('pointerdown', move);
  svg.addEventListener('pointerleave', event => { if (event.pointerType === 'touch') return; const focused = byMark.get(document.activeElement); if (focused) activate(focused); else clear(); });
  const items = groups.flatMap(g => g.map(t => t.mark)), position = new Map(); // item -> [group, index]
  groups.forEach((g, gi) => g.forEach((t, i) => position.set(t.mark, [gi, i])));
  const offsets = groups.reduce((list, g) => [...list, list.length ? list[list.length - 1] + groups[list.length - 1].length : 0], []);
  roving(items, (index, key) => {
    const [gi, i] = position.get(items[index]);
    if (key === 'ArrowRight') return i + 1 < groups[gi].length ? index + 1 : undefined;
    if (key === 'ArrowLeft') return i > 0 ? index - 1 : undefined;
    const other = gi + (key === 'ArrowDown' ? 1 : key === 'ArrowUp' ? -1 : 0);
    if (other === gi || !groups[other]?.length) return undefined;
    const x = groups[gi][i].x, near = groups[other].reduce((b, t, j) => Math.abs(t.x - x) < Math.abs(groups[other][b].x - x) ? j : b, 0);
    return offsets[other] + near;
  });
  for (const t of targets) {
    t.mark.addEventListener('focus', () => activate(t));
    t.mark.addEventListener('blur', () => { if (active === t) clear(); });
  }
}
