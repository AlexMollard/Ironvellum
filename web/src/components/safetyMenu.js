import { node } from './statCard.js';

/**
 * The overflow menu of a card or a row: a disclosure button and a short list of actions ({ label, run }). It closes on
 * choosing, on Escape and when focus leaves it. Only what the caller offers is shown (own comments get Delete, others Report).
 */
export function safetyMenu(items, label = 'More') {
  const wrap = node('div', null, 'safety-menu');
  const toggle = node('button', '⋯', 'icon-button safety-toggle'); toggle.type = 'button'; toggle.setAttribute('aria-label', label); toggle.setAttribute('aria-haspopup', 'true'); toggle.setAttribute('aria-expanded', 'false');
  const list = node('div', null, 'safety-list'); list.setAttribute('role', 'menu'); list.hidden = true;
  const close = () => { list.hidden = true; toggle.setAttribute('aria-expanded', 'false'); };
  for (const item of items) {
    const b = node('button', item.label, 'safety-item'); b.type = 'button'; b.setAttribute('role', 'menuitem');
    b.addEventListener('click', () => { close(); item.run(); }); list.append(b);
  }
  toggle.addEventListener('click', () => { list.hidden = !list.hidden; toggle.setAttribute('aria-expanded', String(!list.hidden)); if (!list.hidden) list.firstChild?.focus(); });
  wrap.addEventListener('keydown', e => { if (e.key === 'Escape') { close(); toggle.focus(); } });
  wrap.addEventListener('focusout', e => { if (e.relatedTarget && !wrap.contains(e.relatedTarget)) close(); });
  wrap.append(toggle, list);
  return wrap;
}
