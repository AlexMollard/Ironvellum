import { renderLineChart } from '../components/lineChart.js';
import { renderStackedWeeks } from '../components/stackedWeeks.js';
import { renderTrainingDays } from '../components/trainingDays.js';
import { renderMusclePanel } from '../components/musclePanel.js';
import { card } from '../components/statCard.js';
import { formatDay, formatDayShort } from '../format.js';

const el = (tag, text, cls) => { const node = document.createElement(tag); if (text != null) node.textContent = text; if (cls) node.className = cls; return node; };

export function renderTrends(root, ctx) {
  root.replaceChildren(); const header = el('header', null, 'screen-header'); header.append(el('h1', 'Trends')); root.append(header);
  const snapshot = ctx.snapshot, trends = snapshot?.trends;
  if (!Array.isArray(trends?.weeks) || !(snapshot.profile?.workouts > 0)) { root.append(el('p', 'No trends yet.', 'muted')); return; }
  let range = '12';
  const controls = el('div', null, 'range-pills'); controls.setAttribute('role', 'group'); controls.setAttribute('aria-label', 'Week range');
  for (const [value, text] of [['4', '4 weeks'], ['12', '12 weeks'], ['52', '1 year']]) { const button = el('button', text, 'pill'); button.type = 'button'; button.dataset.range = value; button.addEventListener('click', () => { range = value; draw(); }); controls.append(button); } header.append(controls);
  const layout = el('div', null, 'trends-layout');
  const barsAside = el('span', null, 'card-aside'), barsCard = card('Where your hard sets went', 'bars-card', barsAside); const bars = el('div', null, 'analytics-chart'); barsCard.append(bars);
  const lineCard = card('Hard sets per week', 'line-card'); const line = el('div', null, 'analytics-chart'); lineCard.append(line);
  const activity = card('Training days', 'days-card', `${trends.daysPerWeek} a week`); const grid = el('div'); renderTrainingDays(grid, trends.days || [], snapshot.builtAtMs, snapshot.zone); activity.append(grid, el('p', `Longest streak: ${snapshot.profile.longestStreakDays} days · now: ${snapshot.profile.streakDays}`, 'muted'));
  const muscles = card(null, 'trend-muscles'); renderMusclePanel(muscles, snapshot);
  layout.append(barsCard, lineCard, activity, muscles); root.append(layout);
  function draw() {
    for (const button of controls.querySelectorAll('button')) button.setAttribute('aria-pressed', String(button.dataset.range === range));
    const weeks = trends.weeks.slice(-Number(range));
    barsAside.textContent = range === '52' ? '1 year' : `${range} weeks`;
    renderStackedWeeks(bars, weeks);
    renderLineChart(line, { series: [{ points: weeks.map(w => [Date.parse(`${w.start}T00:00:00Z`), null, w.hardSets, 0, w.start, undefined, formatDayShort(w.start)]) }], label: 'Hard sets per week', unit: 'hard sets', height: 300, pointTitle: p => `Week of ${formatDay(p[4])}` });
  }
  draw();
}
