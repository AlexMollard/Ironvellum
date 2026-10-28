const NS = 'http://www.w3.org/2000/svg';
const make = (tag, attrs = {}) => { const el = document.createElementNS(NS, tag); for (const [key, value] of Object.entries(attrs)) el.setAttribute(key, String(value)); return el; };

/** Plot the supplied values without deriving training figures. */
export function renderLineChart(root, { series, label = 'Progress', formatValue = String, unit = '', height = 220, details = true }) {
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
  series.forEach((s, index) => {
    const group = make('g', { class: `chart-series chart-series-${(s.index ?? index) % 6}` });
    group.append(make('polyline', { points: s.points.map(p => `${x(p[0])},${y(p[2])}`).join(' '), fill: 'none', stroke: 'currentColor', 'stroke-width': 2, 'stroke-linejoin': 'round' }));
    for (const p of s.points) {
      const px = x(p[0]), py = y(p[2]);
      const mark = p[3] ? make('polygon', { points: `${px},${py - 6} ${px + 6},${py} ${px},${py + 6} ${px - 6},${py}`, class: 'chart-pr' }) : make('circle', { cx: px, cy: py, r: 3.5 });
      mark.setAttribute('fill', 'currentColor');
      mark.dataset.value = String(p[2]);
      mark.dataset.at = String(p[0]);
      mark.setAttribute('aria-label', `${p[4] ?? label}: ${p[5] ?? `${formatValue(p[2])}${unit ? ` ${unit}` : ''}`}`);
      const title = make('title'); title.textContent = `${p[4] ?? ''}: ${p[5] ?? p[2]}`; mark.append(title);
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
  if (!details) return;
  const values = document.createElement('details');
  const summary = document.createElement('summary'); summary.textContent = 'Chart values'; values.append(summary);
  const list = document.createElement('ul');
  for (const s of series) for (const p of s.points) {
    const item = document.createElement('li');
    item.textContent = `${s.name ? `${s.name} · ` : ''}${p[4] ?? ''}: ${p[5] ?? `${formatValue(p[2])}${unit ? ` ${unit}` : ''}`}${p[3] ? ' · PR' : ''}`;
    list.append(item);
  }
  values.append(list); root.append(values);
}
