import { word } from '../i18n.js';
import { artImg } from './artImg.js';
import { formatLoad, splitLabel } from '../format.js';
import { node, displayDate, link } from './statCard.js';
export function recordRow(record, snapshot, router) {
  const el = node('div', null, 'record-row is-link');
  const frame = node('span', null, 'art-frame'); frame.append(artImg(record.name, record.art));
  const [name, variation] = splitLabel(record.label || record.name);
  const content = node('div', null, 'record-text');
  const title = node('div');
  title.append(link(name, `/lifts/${encodeURIComponent(record.name)}`, router, 'stretch'));
  if (variation) title.append(node('span', ` · ${variation}`, 'accent-text'));
  content.append(title, node('div', `e1RM · ${displayDate(record.atMs, snapshot.zone)} · +${formatLoad(record.gainKg, snapshot.unit)} ${snapshot.unit}`, 'muted record-sub'));
  el.append(frame, content, node('b', word('PR', 'concept_peak_u'), 'pr-mark'), node('span', `${formatLoad(record.e1rmKg, snapshot.unit)} ${snapshot.unit}`, 'record-value')); return el;
}
