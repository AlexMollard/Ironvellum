import { createElement, Copy } from 'lucide';
import { node, card } from './statCard.js';
let serial = 0;
// Last resort when the clipboard is unavailable or refuses: the text in a dialog, selected, for the user to copy by hand.
function showFallback(text, opener) {
  const id = `copy-dialog-${++serial}`, backdrop = node('div', null, 'copy-backdrop'), dialog = card('Copy by hand', 'copy-dialog');
  dialog.setAttribute('role', 'dialog'); dialog.setAttribute('aria-modal', 'true'); dialog.setAttribute('aria-labelledby', id); dialog.querySelector('h2').id = id;
  const area = node('textarea'); area.readOnly = true; area.value = text; area.rows = 14; area.setAttribute('aria-label', 'Workout text');
  const close = node('button', 'Close'); close.type = 'button';
  dialog.append(node('p', 'Your browser would not copy it for you. Select all the text and press Ctrl+C (Cmd+C on a Mac).', 'muted'), area, close);
  const done = () => { backdrop.remove(); opener?.focus(); };
  close.addEventListener('click', done);
  backdrop.addEventListener('click', event => { if (event.target === backdrop) done(); });
  backdrop.addEventListener('keydown', event => {
    if (event.key === 'Escape') { event.preventDefault(); done(); }
    else if (event.key === 'Tab') { const first = area, last = close, target = event.target; if (event.shiftKey && target === first) { event.preventDefault(); last.focus(); } else if (!event.shiftKey && target === last) { event.preventDefault(); first.focus(); } }
  });
  backdrop.append(dialog); document.body.append(backdrop); area.focus(); area.select();
  return backdrop;
}
/** A "Copy" button with a polite live "Copied" confirmation. getText may be async; label names the button for assistive tech when it is icon only. */
export function copyButton({ getText, label = 'Copy', iconOnly = false }) {
  const wrap = node('span', null, 'copy-wrap'), button = node('button', null, `copy-button${iconOnly ? ' icon-only' : ''}`); button.type = 'button'; button.setAttribute('aria-label', label);
  const icon = node('span', null, 'copy-icon'); icon.append(createElement(Copy, { width: 16, height: 16, 'stroke-width': 1.75, 'aria-hidden': 'true' })); button.append(icon);
  if (!iconOnly) button.append(node('span', 'Copy'));
  const status = node('span', null, 'copy-status'); status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
  let timer, busy = false;
  const say = text => { status.textContent = text; clearTimeout(timer); if (text) timer = setTimeout(() => { status.textContent = ''; }, 2000); };
  button.addEventListener('click', async event => {
    event.stopPropagation(); if (busy) return; busy = true;
    try {
      let text; try { text = await getText(); } catch { say('Could not copy'); return; }
      try { await navigator.clipboard.writeText(text); say('Copied'); } catch { say(''); showFallback(text, button); }
    } finally { busy = false; }
  });
  wrap.append(button, status); return wrap;
}
