import { artImg } from './artImg.js';
import { formatLoad } from '../format.js';
import { node, displayDate, link } from './statCard.js';
export function recordRow(record, snapshot, router) {
  const el = node('div', null, 'record-row');
  el.append(artImg(record.name, record.art));
  const content = node('div');
  content.append(link(record.label || record.name, `/lifts/${encodeURIComponent(record.name)}`, router), node('p', `${formatLoad(record.e1rmKg, snapshot.unit)} ${snapshot.unit} · +${formatLoad(record.gainKg, snapshot.unit)} ${snapshot.unit} · ${displayDate(record.atMs, snapshot.zone)}`, 'muted'));
  el.append(content); return el;
}
