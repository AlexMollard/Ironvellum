const GROUPS = ['BACK', 'CHEST', 'LEGS', 'SHOULDERS', 'ARMS', 'CORE'];
const labels = ['Back', 'Chest', 'Legs', 'Shoulders', 'Arms', 'Core'];
const NS = 'http://www.w3.org/2000/svg';

export function renderStackedWeeks(root, weeks) {
  root.replaceChildren();
  const legend = document.createElement('div'); legend.className = 'chart-legend';
  GROUPS.forEach((group, i) => { const item = document.createElement('span'); item.className = `chart-series-${i}`; item.textContent = `● ${labels[i]}`; legend.append(item); }); root.append(legend);
  const svg = document.createElementNS(NS, 'svg'); svg.setAttribute('viewBox', '0 0 600 220'); svg.setAttribute('role', 'img'); svg.setAttribute('aria-label', 'Hard sets by muscle group and week'); svg.classList.add('stacked-weeks');
  const totals = weeks.map(w => GROUPS.reduce((n, g) => n + (w.groups?.[g] || 0), 0));
  const max = Math.max(1, ...totals);
  weeks.forEach((week, i) => { let y = 200; const width = 560 / Math.max(weeks.length, 1); GROUPS.forEach((group, j) => {
    const value = week.groups?.[group] || 0, height = value / max * 180; y -= height;
    const rect = document.createElementNS(NS, 'rect'); rect.setAttribute('x', String(20 + i * width)); rect.setAttribute('y', String(y)); rect.setAttribute('width', String(Math.max(1, width - 1))); rect.setAttribute('height', String(height)); rect.setAttribute('fill', 'currentColor'); rect.setAttribute('class', `chart-series-${j}`); rect.dataset.group = group; rect.dataset.value = String(value); rect.dataset.week = week.start;
    const title = document.createElementNS(NS, 'title'); title.textContent = `${week.start} · ${labels[j]}: ${value}`; rect.append(title); svg.append(rect);
  }); }); root.append(svg);
}
