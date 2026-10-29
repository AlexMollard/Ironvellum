import { node, card, link } from './statCard.js';
import { plate } from './plate.js';
import { prTag } from './prTag.js';
import { renderBodyMap } from './bodyMap.js';
/** The "Your last workout" card, shared by the Overview and the sign-in Example. Renders what it is given. A chip is a name, or { text, path } to link it. */
export function lastWorkoutCard({ heading, profile, when, meta, xp, title, path, router, stats, prs = [], unit, chips = [], levels, names, figure }) {
  const el = card(null, path ? 'last-workout is-link' : 'last-workout');
  const head = node('div', null, 'lw-head');
  const emblem = node('span'); plate(emblem, { name: profile?.name, crestId: profile?.crestId });
  const lead = node('span', null, 'lw-lead');
  const line = node('span', null, 'lw-title-line'); line.append(node('strong', heading), node('span', ` · ${when}`, 'muted'));
  lead.append(line, node('span', meta, 'muted lw-meta'));
  head.append(emblem, lead);
  if (xp != null) head.append(node('span', `+${xp} XP`, 'lw-xp'));
  el.append(head);
  const body = node('div', null, 'lw-body'), main = node('div', null, 'lw-main');
  main.append(path ? link(title, path, router, 'lw-name stretch') : node('div', title, 'lw-name'), node('div', stats, 'muted lw-stats'));
  if (prs.length) { const row = node('div', null, 'chips'); for (const pr of prs) row.append(prTag(pr, unit)); main.append(row); }
  if (chips.length) { const row = node('div', null, 'chips'); for (const chip of chips) { const { text, path: to } = typeof chip === 'string' ? { text: chip } : chip; row.append(to && router ? link(text, to, router, 'chip') : node('span', text, 'chip')); } main.append(row); }
  body.append(main);
  if (levels) {
    const maps = node('div', null, 'body-map-pair');
    for (const view of ['front', 'back']) { const map = node('div', null, 'body-map'); renderBodyMap(map, { levels, figure, view, look: 'worked', names }); maps.append(map); }
    body.append(maps);
  }
  el.append(body);
  return el;
}
