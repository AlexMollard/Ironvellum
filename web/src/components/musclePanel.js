import { renderBodyMap } from './bodyMap.js';
import { renderFatigueCard } from './fatigueCard.js';
import { t } from '../i18n.js';

export function renderMusclePanel(root, snapshot) {
  root.replaceChildren(); root.classList.add('muscle-panel');
  const header = document.createElement('div'); header.className = 'card-head';
  const heading = document.createElement('h2'); heading.textContent = 'Muscles'; header.append(heading);
  const switcher = document.createElement('div'); switcher.className = 'segmented'; switcher.setAttribute('role', 'group'); switcher.setAttribute('aria-label', 'Muscle view');
  let view = 'fatigue';
  for (const name of ['Fatigue', 'Coverage']) { const button = document.createElement('button'); button.type = 'button'; button.dataset.view = name.toLowerCase(); button.textContent = name; button.addEventListener('click', () => { view = button.dataset.view; draw(); }); switcher.append(button); }
  header.append(switcher); root.append(header);
  const content = document.createElement('div'); root.append(content);
  function draw() {
    for (const button of switcher.querySelectorAll('button')) button.setAttribute('aria-pressed', String(button.dataset.view === view));
    content.replaceChildren(); content.hidden = false;
    if (view === 'fatigue') {
      renderFatigueCard(content, { fatigue: snapshot.fatigue, muscles: snapshot.muscles, figure: snapshot.profile?.figure, builtAtMs: snapshot.builtAtMs, zone: snapshot.zone });
      content.firstElementChild?.classList.add('bare');
      return;
    }
    const window = snapshot.trends?.windows?.['7'];
    if (!window) { content.textContent = 'No coverage yet.'; return; }
    const maps = document.createElement('div'); maps.className = 'body-map-pair';
    for (const side of ['front', 'back']) { const wrap = document.createElement('div'); wrap.className = 'body-view'; const label = document.createElement('span'); label.className = 'body-view-label'; label.textContent = side === 'front' ? 'Front' : 'Back'; const map = document.createElement('div'); map.className = 'body-map'; renderBodyMap(map, { levels: window.state, figure: snapshot.profile?.figure, view: side, look: 'coverage' }); wrap.append(label, map); maps.append(wrap); }
    content.append(maps);
    const legend = document.createElement('div'); legend.className = 'chart-legend coverage-legend';
    for (const [state, text] of [['covered', 'Covered'], ['below', 'Below'], ['heavy', 'Heavy'], ['none', 'None']]) { const item = document.createElement('span'); item.className = `legend-item coverage-${state}`; item.textContent = text; legend.append(item); } content.append(legend);
    const caption = document.createElement('p'); caption.className = 'muted'; caption.textContent = `${t('fatigue_window_caption')}. ${t('coverage_reason').replace('%1$d', String(snapshot.trends.floor))}`; content.append(caption);
    const thresholds = document.createElement('p'); thresholds.className = 'muted'; thresholds.textContent = `Floor: ${snapshot.trends.floor} · Heavy: ${snapshot.trends.heavy}`; content.append(thresholds);
    const rows = document.createElement('div'); rows.className = 'muscle-rows';
    for (const [muscle, state] of Object.entries(window.state)) { const row = document.createElement('p'); row.className = `coverage-${state}`; row.textContent = `${snapshot.muscles?.[muscle] || muscle}: ${window.perWeek?.[muscle] ?? '—'} · ${state}`; rows.append(row); } content.append(rows);
  }
  draw();
}
