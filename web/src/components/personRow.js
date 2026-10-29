import catalogue from '../generated/catalogue.json';
import { plate } from './plate.js';
import { node } from './statCard.js';
import { hiddenName } from './hiddenName.js';

/** The worn title's name from the generated catalogue; an unknown id reads as no title, as on the phone. */
export const titleName = id => catalogue.deeds.find(d => d.id === id)?.name ?? '';

/**
 * One lifter as a row: plate with the worn crest, name, worn title and level. `person` is
 * { id, name, level, titleId, crestId }; `onOpen` makes the whole row one button (the app opens the folio from it): the name is the
 * button and stretches over the row, so there is one tab stop and the avatar and level line are part of the target.
 */
export function personRow(person, { onOpen, trailing } = {}) {
  const row = node('div', null, `person-row${onOpen ? ' is-link' : ''}`);
  const name = hiddenName(person.name);
  const emblem = node('span'); plate(emblem, { name, crestId: person.crestId }); row.append(emblem);
  const text = node('span', null, 'person-text');
  if (onOpen) {
    const open = node('button', name, 'link-button person-name stretch'); open.type = 'button'; open.dataset.focusKey = `person:${person.userId ?? person.id}`;
    open.addEventListener('click', () => onOpen(person)); text.append(open);
  } else text.append(node('strong', name, 'person-name'));
  const title = titleName(person.titleId);
  text.append(node('span', [person.level != null ? `Level ${person.level}` : '', title].filter(Boolean).join(' · '), 'muted person-sub'));
  row.append(text);
  if (trailing) row.append(trailing);
  return row;
}
