import { renderLineChart } from '../components/lineChart.js';
import { renderStackedWeeks } from '../components/stackedWeeks.js';
import { renderTrainingDays } from '../components/trainingDays.js';
import { renderMusclePanel } from '../components/musclePanel.js';

const el = (tag, text, cls) => { const node = document.createElement(tag); if (text != null) node.textContent = text; if (cls) node.className = cls; return node; };

export function renderTrends(root, ctx) {
  root.replaceChildren(); root.append(el('h1', 'Trends'));
  const snapshot = ctx.snapshot, trends = snapshot?.trends;
  if (!Array.isArray(trends?.weeks) || !(snapshot.profile?.workouts > 0)) { root.append(el('p', 'No trends yet.')); return; }
  const controls = el('div', null, 'analytics-controls'); const label = el('label', 'Week range '); const select = el('select'); select.setAttribute('aria-label', 'Week range');
  for (const [value, text] of [['4', '4 weeks'], ['12', '12 weeks'], ['52', '1 year']]) { const option = el('option', text); option.value = value; option.selected = value === '52'; select.append(option); } label.append(select); controls.append(label); root.append(controls);
  const barsCard = el('section', null, 'card'); barsCard.append(el('h2', 'Hard sets by muscle group')); const bars = el('div', null, 'analytics-chart'); barsCard.append(bars); root.append(barsCard);
  const lineCard = el('section', null, 'card'); lineCard.append(el('h2', 'Hard sets per week')); const line = el('div', null, 'analytics-chart'); lineCard.append(line); root.append(lineCard);
  const activity = el('section', null, 'card'); activity.append(el('h2', 'Training days'), el('p', `${trends.daysPerWeek} a week`), el('p', `Current streak: ${snapshot.profile.streakDays} days · Longest streak: ${snapshot.profile.longestStreakDays} days`)); const grid = el('div'); renderTrainingDays(grid, trends.days || [], snapshot.builtAtMs, snapshot.zone); activity.append(grid); root.append(activity);
  const muscles = el('section', null, 'card'); renderMusclePanel(muscles, snapshot); root.append(muscles);
  function draw() {
    const weeks = trends.weeks.slice(-Number(select.value)); renderStackedWeeks(bars, weeks);
    renderLineChart(line, { series: [{ points: weeks.map(w => [Date.parse(`${w.start}T00:00:00Z`), null, w.hardSets, 0, w.start]) }], label: 'Hard sets per week', unit: 'hard sets' });
  }
  select.addEventListener('change', draw); draw();
}
