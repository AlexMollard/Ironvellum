import { renderBodyMap } from './bodyMap.js';
import { renderFatigueCard } from './fatigueCard.js';
import { t } from '../i18n.js';

export function renderMusclePanel(root, snapshot) {
  root.replaceChildren();
  const header = document.createElement('div'); header.className = 'analytics-controls';
  const heading = document.createElement('h2'); heading.textContent = 'Muscles'; header.append(heading);
  const label = document.createElement('label'); label.textContent = 'View ';
  const select = document.createElement('select'); select.setAttribute('aria-label', 'Muscle view');
  for (const name of ['Fatigue', 'Coverage']) { const option = document.createElement('option'); option.value = name.toLowerCase(); option.textContent = name; select.append(option); }
  label.append(select); header.append(label); root.append(header);
  const content = document.createElement('div'); root.append(content);
  function draw() {
    content.replaceChildren(); content.hidden = false;
    if (select.value === 'fatigue') {
      renderFatigueCard(content, { fatigue: snapshot.fatigue, muscles: snapshot.muscles, figure: snapshot.profile?.figure, builtAtMs: snapshot.builtAtMs, zone: snapshot.zone });
      return;
    }
    const window = snapshot.trends?.windows?.['7'];
    if (!window) { content.textContent = 'No coverage yet.'; return; }
    const map = document.createElement('div'); map.className = 'analytics-body-map'; renderBodyMap(map, { levels: window.state, figure: snapshot.profile?.figure, look: 'coverage' }); content.append(map);
    const legend = document.createElement('div'); legend.className = 'chart-legend';
    for (const [state, text] of [['covered', 'Covered'], ['below', 'Below'], ['heavy', 'Heavy'], ['none', 'None']]) { const item = document.createElement('span'); item.className = `coverage-${state}`; item.textContent = `● ${text}`; legend.append(item); } content.append(legend);
    const caption = document.createElement('p'); caption.textContent = `${t('fatigue_window_caption')}. ${t('coverage_reason').replace('%1$d', String(snapshot.trends.floor))}`; content.append(caption);
    const thresholds = document.createElement('p'); thresholds.textContent = `Floor: ${snapshot.trends.floor} · Heavy: ${snapshot.trends.heavy}`; content.append(thresholds);
    const rows = document.createElement('div'); rows.className = 'muscle-rows';
    for (const [muscle, state] of Object.entries(window.state)) { const row = document.createElement('p'); row.className = `coverage-${state}`; row.textContent = `${snapshot.muscles?.[muscle] || muscle}: ${window.perWeek?.[muscle] ?? '—'} · ${state}`; rows.append(row); } content.append(rows);
  }
  select.addEventListener('change', draw); draw();
}
