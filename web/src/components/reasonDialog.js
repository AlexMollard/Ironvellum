import { t } from '../i18n.js';
import { node } from './statCard.js';

const REASONS = [['spam', 'comments_report_reason_spam'], ['abuse', 'comments_report_reason_abuse'], ['cheating', 'comments_report_reason_cheating'], ['other', 'comments_report_reason_other']];
export const REPORT_NOTE_MAX = 280;
const sentence = text => text.charAt(0) + text.slice(1).toLowerCase();

/**
 * The report form, inline (no modal): a reason from the phone's four, an optional note of at most 280 characters, Send and
 * Cancel. `onSend({reason, note})` may throw; the form shows the sentence `explain` gives back and stays open so nothing is lost.
 */
export function reasonDialog({ title, onSend, onCancel, explain }) {
  const panel = node('form', null, 'panel reason-dialog'); panel.setAttribute('role', 'group'); panel.setAttribute('aria-label', title);
  panel.append(node('h3', title), node('p', t('comments_report_note'), 'muted'));
  const group = node('div', null, 'segmented'); group.setAttribute('role', 'radiogroup'); group.setAttribute('aria-label', title);
  let reason = 'spam';
  const buttons = REASONS.map(([value, key]) => {
    const b = node('button', sentence(t(key))); b.type = 'button'; b.setAttribute('role', 'radio'); b.dataset.reason = value;
    b.setAttribute('aria-pressed', String(value === reason)); b.setAttribute('aria-checked', String(value === reason));
    b.addEventListener('click', () => { reason = value; for (const o of buttons) { o.setAttribute('aria-pressed', String(o === b)); o.setAttribute('aria-checked', String(o === b)); } });
    return b;
  });
  group.append(...buttons);
  const label = node('label', t('comments_report_note_label')); const note = node('input'); note.type = 'text'; note.maxLength = REPORT_NOTE_MAX; label.append(note);
  const status = node('p', null, 'notice form-status'); status.setAttribute('role', 'status');
  const send = node('button', t('comments_report_send_button'), 'primary-small'); send.type = 'submit';
  const cancel = node('button', 'Cancel'); cancel.type = 'button'; cancel.addEventListener('click', () => onCancel?.());
  const actions = node('div', null, 'panel-actions'); actions.append(send, cancel);
  panel.append(group, label, status, actions);
  panel.addEventListener('submit', async event => {
    event.preventDefault(); send.disabled = true; status.textContent = '';
    try { await onSend({ reason, note: note.value.trim().slice(0, REPORT_NOTE_MAX) }); }
    catch (error) { status.textContent = explain(error); }
    finally { send.disabled = false; }
  });
  return panel;
}

/** A yes/no panel for the destructive ones (remove an ally, block, leave a circle): the phone's own title and body. */
export function confirmPanel({ title, body, confirmLabel, cancelLabel = 'Cancel', onConfirm, onCancel, explain }) {
  const panel = node('div', null, 'panel confirm-panel'); panel.setAttribute('role', 'alertdialog'); panel.setAttribute('aria-label', title);
  const status = node('p', null, 'notice form-status'); status.setAttribute('role', 'status');
  const yes = node('button', confirmLabel, 'primary-small'); yes.type = 'button';
  const no = node('button', cancelLabel); no.type = 'button'; no.addEventListener('click', () => onCancel?.());
  yes.addEventListener('click', async () => { yes.disabled = true; status.textContent = ''; try { await onConfirm(); } catch (error) { status.textContent = explain(error); } finally { yes.disabled = false; } });
  const actions = node('div', null, 'panel-actions'); actions.append(yes, no);
  panel.append(node('h3', title), node('p', body, 'muted'), status, actions);
  return panel;
}
