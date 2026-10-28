import { t } from '../i18n.js';
import { renderBodyMap } from './bodyMap.js';
import { node, card } from './statCard.js';
const levelKeys = ['fatigue_level_fresh', 'fatigue_level_light', 'fatigue_level_moderate', 'fatigue_level_high', 'fatigue_level_very_high'];
export function renderFatigueCard(el, { fatigue, muscles = {}, figure = 'male', builtAtMs, zone = 'UTC', nowMs = Date.now() }) {
  el.replaceChildren();
  if (!fatigue?.hasHistory) { el.hidden = true; return; }
  el.hidden = false;
  const panel = card('Muscles right now', 'fatigue-card');
  const at = fatigue.atMs ?? builtAtMs;
  panel.append(node('p', `Estimate · as of ${new Date(at).toLocaleString(undefined, { timeZone: zone })}`, 'muted'));
  if (Number.isFinite(at) && nowMs - at > 24 * 60 * 60 * 1000) panel.append(node('p', 'Sync the app for a fresh reading.', 'muted'));
  const maps = node('div', null, 'body-map-pair');
  for (const view of ['front', 'back']) {
    const map = node('div', null, 'body-map'); renderBodyMap(map, { levels: fatigue.levels, figure, view, look: 'fatigue' }); maps.append(map);
  }
  panel.append(maps);
  const legend = node('div', null, 'fatigue-legend'); legend.setAttribute('aria-label', 'Fatigue levels');
  for (const [level, key] of levelKeys.entries()) {
    legend.append(node('span', t(key), `fatigue-legend-item level-${level}`));
  }
  panel.append(legend);
  for (const row of (fatigue.worked || []).slice(0, 4)) {
    const item = node('div', null, `fatigue-row level-${row.level}`);
    item.append(node('strong', muscles[row.muscle] || row.muscle), ' · ', node('span', levelKeys[row.level] ? t(levelKeys[row.level]) : '—', 'fatigue-level'));
    if (row.cause) item.append(node('p', row.cause));
    if (row.freshBy) item.append(node('p', row.freshBy));
    panel.append(item);
  }
  el.append(panel);
}
