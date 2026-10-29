import { artImg } from '../components/artImg.js';
import { renderBodyMap } from '../components/bodyMap.js';
import { renderLineChart } from '../components/lineChart.js';
import { renderVariationTable, formatPeak } from '../components/variationTable.js';
import { formatLoad, formatSets, formatVolume } from '../format.js';
import { card } from '../components/statCard.js';
import { t, word } from '../i18n.js';

const el = (tag, text, cls) => { const node = document.createElement(tag); if (text != null) node.textContent = text; if (cls) node.className = cls; return node; };
const tile = (label, value, sub) => { const item = el('div', null, 'tile'); item.append(el('span', label, 'muted'), el('strong', value)); if (sub) item.append(el('span', sub, 'muted')); return item; };

function liftList(lifts, selected, router) {
  const panel = card(null, 'lift-list-card');
  const search = el('input'); search.type = 'search'; search.placeholder = 'Search lifts'; search.setAttribute('aria-label', 'Search exercises'); panel.append(search);
  const list = el('div', null, 'lift-list'); panel.append(list);
  const draw = () => {
    list.replaceChildren(); const term = search.value.toLocaleLowerCase();
    for (const item of lifts.filter(x => x.name.toLocaleLowerCase().includes(term))) {
      const button = el('button', null, 'lift-item'); button.type = 'button'; if (item === selected) button.setAttribute('aria-current', 'true');
      const frame = el('span', null, 'art-frame'); frame.append(artImg(item.name, item.art));
      button.append(frame, el('strong', item.name), el('span', `${item.variations.length} ${item.variations.length === 1 ? 'variation' : 'variations'}`, 'muted'));
      button.addEventListener('click', () => router.go(`/lifts/${encodeURIComponent(item.name)}`)); list.append(button);
    }
    if (!list.children.length) list.append(el('p', 'No exercises found.', 'muted'));
  };
  search.addEventListener('input', draw); draw(); return panel;
}

function liftDetail(pane, lift, snapshot) {
  const header = card(null, 'lift-header');
  const frame = el('span', null, 'art-frame hero'); frame.append(artImg(lift.name, lift.art));
  const text = el('div', null, 'lift-intro'); text.append(el('h2', lift.name, 'lift-name'), el('p', `${lift.muscleSummary} · ${lift.variations.length} ${lift.variations.length === 1 ? 'variation' : 'variations'} logged`, 'muted'));
  const chips = el('div', null, 'chips');
  for (const v of lift.variations) if (v.peak) { const chip = el('span', null, 'chip pr-tag'); chip.append(el('b', word('PR', 'concept_peak_u')), `${v.label} ${formatPeak(v.peak, snapshot.unit, lift.addsToBody)}`); chips.append(chip); }
  text.append(chips);
  const map = el('div', null, 'analytics-body-map body-map-pair');
  for (const view of ['front', 'back']) { const figure = el('div', null, 'body-map'); renderBodyMap(figure, { levels: lift.muscleLevels, figure: snapshot.profile?.figure, view, look: 'worked', names: snapshot.muscles }); map.append(figure); }
  header.append(frame, text, map); pane.append(header);
  const selected = new Set(lift.variations.map(v => v.key));
  let range = 6;
  const select = el('select', null, 'pill-select'); select.setAttribute('aria-label', 'Chart range');
  for (const [value, label] of [['3', '3 months'], ['6', '6 months'], ['12', '1 year'], ['all', 'All']]) { const option = el('option', label); option.value = value; option.selected = value === '6'; select.append(option); }
  const chartCard = card(null, 'chart-card'), columns = el('div', null, 'chart-columns'), chartCol = el('div', null, 'chart-col'), variationCol = el('div', null, 'variation-col');
  const chartHead = el('div', null, 'card-head'); chartHead.append(el('h2', 'Progress'), select); chartCol.append(chartHead);
  const chart = el('div', null, 'analytics-chart'); chartCol.append(chart);
  const tableHead = el('div', null, 'card-head'); tableHead.append(el('h2', 'Variations side by side')); const table = el('div', null, 'table-scroll'); variationCol.append(tableHead, table, el('p', 'Each keeps its own records. Tap one to show or hide it on the chart.', 'muted note'));
  columns.append(chartCol, variationCol); chartCard.append(columns); pane.append(chartCard);
  const records = card(word('Records', 'concept_peak_cpl'), 'records-panel');
  for (const v of lift.variations) {
    const box = el('div', null, 'analytics-record'); box.append(el('h3', v.label)); const tiles = el('div', null, 'tiles');
    if (v.peak?.e1rmKg != null) tiles.append(tile('Best est. 1RM', `${formatLoad(v.peak.e1rmKg, snapshot.unit)} ${snapshot.unit}`));
    if (v.peak) tiles.append(tile('Best set', formatPeak(v.peak, snapshot.unit, lift.addsToBody)));
    if (v.heaviest) tiles.append(tile('Heaviest', formatPeak(v.heaviest, snapshot.unit, lift.addsToBody)));
    for (const record of v.setRecords || []) tiles.append(tile('Set record', `Set ${record.position + 1}: ${lift.metric === 'hold' ? `${record.reps} s` : formatPeak(record, snapshot.unit, lift.addsToBody)}`));
    if (v.volumeKg != null) tiles.append(tile('Volume', formatVolume(v.volumeKg, snapshot.unit)));
    if (v.perWeek != null) tiles.append(tile('Frequency', `${formatSets(v.perWeek)} a week`));
    tiles.append(tile('Sessions', `${v.sessions} ${t('concept_trial_pl')}`)); box.append(tiles); records.append(box);
  } pane.append(records);
  function draw() {
    const cutoff = range === 'all' ? -Infinity : snapshot.builtAtMs - Number(range) * 30.4375 * 86400000;
    const date = new Intl.DateTimeFormat(undefined, { timeZone: snapshot.zone, day: 'numeric', month: 'short', year: 'numeric' });
    chart.replaceChildren();
    for (const metric of ['e1rm', 'strength']) {
      const matching = lift.variations.map((v, index) => ({ v, index })).filter(({ v }) => v.chartMetric === metric && v.chartUnit === (metric === 'e1rm' ? 'kg' : 'score') && selected.has(v.key));
      if (!matching.length) continue;
      const unit = metric === 'e1rm' ? snapshot.unit : 'score';
      const formatValue = metric === 'e1rm' ? value => formatLoad(value, snapshot.unit) : String;
      const title = metric === 'e1rm' ? `Estimated 1RM per ${t('concept_trial_s')}` : `Strength score per ${t('concept_trial_s')}`;
      const series = matching.map(({ v, index }) => ({ index, name: v.label, points: v.points.filter(p => p[0] >= cutoff).map(p => [...p, date.format(p[0]), `${formatValue(p[2])} ${unit}`]) }));
      const panel = el('div', null, 'analytics-chart'); panel.append(el('h3', title, 'chart-title'));
      const plot = el('div'); panel.append(plot); chart.append(panel);
      if (series.reduce((n, s) => n + s.points.length, 0) < 2) plot.append(el('p', t('explorer_chart_one')));
      else renderLineChart(plot, { series, label: title, formatValue, unit, factor: metric === 'e1rm' && snapshot.unit === 'lb' ? 1 / 0.45359237 : 1 });
    }
    if (lift.variations.some(v => selected.has(v.key) && !((v.chartMetric === 'e1rm' && v.chartUnit === 'kg') || (v.chartMetric === 'strength' && v.chartUnit === 'score')))) chart.append(el('p', 'Sync the app to see chart metrics.'));
    renderVariationTable(table, { variations: lift.variations, selected, unit: snapshot.unit, zone: snapshot.zone, builtAtMs: snapshot.builtAtMs, addsToBody: lift.addsToBody, onToggle(key) { if (selected.has(key)) selected.delete(key); else selected.add(key); draw(); } });
  }
  select.addEventListener('change', () => { range = select.value; draw(); }); draw();
}

export function renderLifts(root, ctx, name) {
  root.replaceChildren();
  const header = el('header', null, 'screen-header'); header.append(el('h1', 'Lifts')); root.append(header);
  const snapshot = ctx.snapshot;
  if (!snapshot) { root.append(el('p', 'Sync the app to see exercise progress.', 'muted')); return; }
  const lifts = snapshot.lifts || [];
  const lift = name == null ? lifts[0] : lifts.find(item => item.name === name);
  const layout = el('div', null, 'lifts-layout'); layout.dataset.selected = name == null ? '' : name;
  layout.append(liftList(lifts, lift, ctx.router));
  const pane = el('div', null, 'lift-detail'); layout.append(pane); root.append(layout);
  if (lift) liftDetail(pane, lift, snapshot);
  else pane.append(el('p', lifts.length ? 'Exercise not found.' : 'No exercises yet.', 'muted'));
}
