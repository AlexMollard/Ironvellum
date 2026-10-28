import { artImg } from '../components/artImg.js';
import { renderBodyMap } from '../components/bodyMap.js';
import { renderLineChart } from '../components/lineChart.js';
import { renderVariationTable, formatPeak } from '../components/variationTable.js';
import { formatLoad, formatSets, formatVolume } from '../format.js';
import { t } from '../i18n.js';

const el = (tag, text, cls) => { const node = document.createElement(tag); if (text != null) node.textContent = text; if (cls) node.className = cls; return node; };

export function renderLifts(root, ctx, name) {
  root.replaceChildren();
  const snapshot = ctx.snapshot;
  if (!snapshot) { root.append(el('p', 'Sync the app to see exercise progress.')); return; }
  const lifts = snapshot.lifts || [];
  const lift = name == null ? null : lifts.find(item => item.name === name);
  if (!lift) {
    root.append(el('h1', 'Exercises'));
    if (name != null) root.append(el('p', 'Exercise not found.'));
    const search = el('input'); search.type = 'search'; search.placeholder = 'Search exercises'; search.setAttribute('aria-label', 'Search exercises'); root.append(search);
    const list = el('div', null, 'analytics-grid'); root.append(list);
    const draw = () => { list.replaceChildren(); const term = search.value.toLocaleLowerCase();
      for (const item of lifts.filter(x => x.name.toLocaleLowerCase().includes(term))) {
        const button = el('button', null, 'card exercise-card'); button.type = 'button'; button.append(artImg(item.name, item.art), el('strong', item.name), el('span', `${item.variations.length} ${item.variations.length === 1 ? 'variation' : 'variations'}`));
        button.addEventListener('click', () => ctx.router.go(`/lifts/${encodeURIComponent(item.name)}`)); list.append(button);
      }
      if (!list.children.length) list.append(el('p', 'No exercises found.'));
    }; search.addEventListener('input', draw); draw(); return;
  }
  const back = el('button', 'All exercises'); back.type = 'button'; back.addEventListener('click', () => ctx.router.go('/lifts')); root.append(back);
  const header = el('section', null, 'card analytics-header'); header.append(artImg(lift.name, lift.art), el('h1', lift.name), el('p', lift.muscleSummary));
  for (const v of lift.variations) if (v.peak) header.append(el('span', `${v.label} · ${formatPeak(v.peak, snapshot.unit)}`, 'record-tag'));
  const map = el('div', null, 'analytics-body-map'); renderBodyMap(map, { levels: lift.muscleLevels, figure: snapshot.profile?.figure, look: 'worked' }); header.append(map); root.append(header);
  const selected = new Set(lift.variations.map(v => v.key));
  let range = 6;
  const controls = el('div', null, 'analytics-controls'); controls.append(el('label', 'Chart range '));
  const select = el('select'); select.setAttribute('aria-label', 'Chart range');
  for (const [value, label] of [['3', '3 months'], ['6', '6 months'], ['12', '1 year'], ['all', 'All']]) { const option = el('option', label); option.value = value; option.selected = value === '6'; select.append(option); }
  controls.append(select); root.append(controls);
  const chartSection = el('section', null, 'card'); chartSection.append(el('h2', 'Progress'));
  const chart = el('div', null, 'analytics-chart'); chartSection.append(chart); root.append(chartSection);
  const tableSection = el('section', null, 'card analytics-table-wrap'); tableSection.append(el('h2', 'Variations side by side')); const table = el('div', null, 'table-scroll'); tableSection.append(table); root.append(tableSection);
  const records = el('section', null, 'card'); records.append(el('h2', 'Records'));
  for (const v of lift.variations) {
    const box = el('div', null, 'analytics-record'); box.append(el('h3', v.label));
    if (v.peak) box.append(el('p', `Best set: ${formatPeak(v.peak, snapshot.unit)}`));
    if (v.peak?.e1rmKg != null) box.append(el('p', `Best est. 1RM: ${formatLoad(v.peak.e1rmKg, snapshot.unit)} ${snapshot.unit}`));
    if (v.heaviest) box.append(el('p', `Heaviest: ${formatPeak(v.heaviest, snapshot.unit)}`));
    for (const record of v.setRecords || []) box.append(el('p', `Set ${record.position + 1}: ${lift.metric === 'hold' ? `${record.reps} s` : formatPeak(record, snapshot.unit)}`));
    if (v.volumeKg != null) box.append(el('p', `Volume: ${formatVolume(v.volumeKg, snapshot.unit)}`));
    if (v.perWeek != null) box.append(el('p', `${formatSets(v.perWeek)} a week`));
    box.append(el('p', `${v.sessions} workouts`)); records.append(box);
  } root.append(records);
  function draw() {
    const cutoff = range === 'all' ? -Infinity : snapshot.builtAtMs - Number(range) * 30.4375 * 86400000;
    const date = new Intl.DateTimeFormat(undefined, { timeZone: snapshot.zone, day: 'numeric', month: 'short', year: 'numeric' });
    chart.replaceChildren();
    for (const metric of ['e1rm', 'strength']) {
      const matching = lift.variations.map((v, index) => ({ v, index })).filter(({ v }) => v.chartMetric === metric && v.chartUnit === (metric === 'e1rm' ? 'kg' : 'score') && selected.has(v.key));
      if (!matching.length) continue;
      const unit = metric === 'e1rm' ? snapshot.unit : 'score';
      const formatValue = metric === 'e1rm' ? value => formatLoad(value, snapshot.unit) : String;
      const title = metric === 'e1rm' ? 'Estimated 1RM per workout' : 'Strength score per workout';
      const series = matching.map(({ v, index }) => ({ index, name: v.label, points: v.points.filter(p => p[0] >= cutoff).map(p => [...p, date.format(p[0]), `${formatValue(p[2])} ${unit}`]) }));
      const panel = el('div', null, 'analytics-chart'); panel.append(el('h3', title));
      const plot = el('div'); panel.append(plot); chart.append(panel);
      if (series.reduce((n, s) => n + s.points.length, 0) < 2) plot.append(el('p', t('explorer_chart_one')));
      else renderLineChart(plot, { series, label: title, formatValue, unit });
    }
    if (lift.variations.some(v => selected.has(v.key) && !((v.chartMetric === 'e1rm' && v.chartUnit === 'kg') || (v.chartMetric === 'strength' && v.chartUnit === 'score')))) chart.append(el('p', 'Sync the app to see chart metrics.'));
    renderVariationTable(table, { variations: lift.variations, selected, unit: snapshot.unit, zone: snapshot.zone, builtAtMs: snapshot.builtAtMs, onToggle(key) { if (selected.has(key)) selected.delete(key); else selected.add(key); draw(); } });
  }
  select.addEventListener('change', () => { range = select.value; draw(); }); draw();
}
