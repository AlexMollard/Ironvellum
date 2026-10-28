import { t } from '../i18n.js';
import { renderBodyMap } from './bodyMap.js';
import { node, card, dayKey } from './statCard.js';
import { formatDay, formatClock } from '../format.js';
const levelKeys = ['fatigue_level_fresh', 'fatigue_level_light', 'fatigue_level_moderate', 'fatigue_level_high', 'fatigue_level_very_high'];
export function renderFatigueCard(el, { fatigue, muscles = {}, figure = 'male', builtAtMs, zone = 'UTC', nowMs = Date.now() }) {
  el.replaceChildren();
  if (!fatigue?.hasHistory) { el.hidden = true; return; }
  el.hidden = false;
  const panel = card('Muscles right now', 'fatigue-card', 'estimate');
  const at = fatigue.atMs ?? builtAtMs;
  panel.append(node('p', `As of ${formatDay(dayKey(at, zone))} ${formatClock(at, zone)}`, 'muted fatigue-asof'));
  if (Number.isFinite(at) && nowMs - at > 24 * 60 * 60 * 1000) panel.append(node('p', 'Sync the app for a fresh reading.', 'muted fatigue-asof'));
  const maps = node('div', null, 'body-map-pair');
  for (const [view, label] of [['front', 'Front'], ['back', 'Back']]) {
    const map = node('div', null, 'body-map'); renderBodyMap(map, { levels: fatigue.levels, figure, view, look: 'fatigue' });
    const wrap = node('div', null, 'body-view'); wrap.append(node('span', label, 'body-view-label'), map); maps.append(wrap);
  }
  panel.append(maps);
  const legend = node('div', null, 'fatigue-legend'); legend.setAttribute('aria-label', 'Fatigue levels');
  for (const [level, key] of levelKeys.entries()) {
    legend.append(node('span', t(key), `fatigue-legend-item level-${level}`));
  }
  panel.append(legend);
  for (const row of (fatigue.worked || []).slice(0, 4)) {
    const item = node('div', null, `fatigue-row level-${row.level}`);
    const text = node('div', null, 'fatigue-text');
    const line = node('div'); line.append(node('strong', muscles[row.muscle] || row.muscle), ' · ', node('span', levelKeys[row.level] ? t(levelKeys[row.level]) : '—', 'fatigue-level muted'));
    text.append(line);
    if (row.cause) text.append(node('p', row.cause, 'muted'));
    item.append(node('span', null, 'fatigue-swatch'), text);
    if (row.freshBy) item.append(node('span', row.freshBy, 'muted fatigue-fresh'));
    panel.append(item);
  }
  el.append(panel);
}
