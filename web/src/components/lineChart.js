const NS = 'http://www.w3.org/2000/svg';

/** Plot the supplied values without deriving training figures. */
export function renderLineChart(root, { series, label = 'Progress', formatValue = String, unit = '' }) {
  root.replaceChildren();
  const all = series.flatMap(s => s.points);
  if (!all.length) return;
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('viewBox', '0 0 600 220');
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', label);
  svg.classList.add('line-chart');
  const xs = all.map(p => p[0]);
  const ys = all.map(p => p[2]);
  const minX = Math.min(...xs), maxX = Math.max(...xs);
  const minY = Math.min(...ys), maxY = Math.max(...ys);
  const x = v => 80 + (maxX === minX ? 245 : (v - minX) / (maxX - minX) * 490);
  const y = v => 190 - (maxY === minY ? 80 : (v - minY) / (maxY - minY) * 160);
  for (const value of minY === maxY ? [minY] : [minY, maxY]) {
    const tick = document.createElementNS(NS, 'text');
    tick.setAttribute('x', '74'); tick.setAttribute('y', String(y(value)));
    tick.setAttribute('text-anchor', 'end'); tick.setAttribute('dominant-baseline', 'middle');
    tick.setAttribute('fill', 'currentColor'); tick.setAttribute('font-size', '10');
    tick.textContent = `${formatValue(value)}${unit ? ` ${unit}` : ''}`; svg.append(tick);
  }
  series.forEach((s, index) => {
    const group = document.createElementNS(NS, 'g');
    group.setAttribute('class', `chart-series chart-series-${(s.index ?? index) % 6}`);
    const line = document.createElementNS(NS, 'polyline');
    line.setAttribute('points', s.points.map(p => `${x(p[0])},${y(p[2])}`).join(' '));
    line.setAttribute('fill', 'none');
    line.setAttribute('stroke', 'currentColor');
    line.setAttribute('stroke-width', '2');
    group.append(line);
    for (const p of s.points) {
      const mark = document.createElementNS(NS, p[3] ? 'polygon' : 'circle');
      const px = x(p[0]), py = y(p[2]);
      if (p[3]) mark.setAttribute('points', `${px},${py - 5} ${px + 5},${py} ${px},${py + 5} ${px - 5},${py}`);
      else { mark.setAttribute('cx', px); mark.setAttribute('cy', py); mark.setAttribute('r', '4'); }
      mark.setAttribute('fill', 'currentColor');
      mark.dataset.value = String(p[2]);
      mark.dataset.at = String(p[0]);
      mark.setAttribute('aria-label', `${p[4] ?? label}: ${p[5] ?? `${formatValue(p[2])}${unit ? ` ${unit}` : ''}`}`);
      const title = document.createElementNS(NS, 'title'); title.textContent = `${p[4] ?? ''}: ${p[5] ?? p[2]}`; mark.append(title);
      group.append(mark);
    }
    svg.append(group);
  });
  const labelled = all.filter(p => p[4] != null).sort((a, b) => a[0] - b[0]);
  for (const p of labelled.length > 1 ? [labelled[0], labelled[labelled.length - 1]] : labelled) {
    const text = document.createElementNS(NS, 'text');
    text.setAttribute('x', String(x(p[0]))); text.setAttribute('y', '215');
    text.setAttribute('text-anchor', p === labelled[0] ? 'start' : 'end');
    text.setAttribute('fill', 'currentColor'); text.setAttribute('font-size', '10');
    text.textContent = p[4]; svg.append(text);
  }
  root.append(svg);
  const details = document.createElement('details');
  const summary = document.createElement('summary'); summary.textContent = 'Chart values'; details.append(summary);
  const list = document.createElement('ul');
  for (const s of series) for (const p of s.points) {
    const item = document.createElement('li');
    item.textContent = `${s.name ? `${s.name} · ` : ''}${p[4] ?? ''}: ${p[5] ?? `${formatValue(p[2])}${unit ? ` ${unit}` : ''}`}${p[3] ? ' · PR' : ''}`;
    list.append(item);
  }
  details.append(list); root.append(details);
}
