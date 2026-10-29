import { node, link } from '../../components/statCard.js';
import { personRow } from '../../components/personRow.js';
import { hiddenName } from '../../components/hiddenName.js';
import { renderFolio } from '../../components/folio.js';
import { explain } from '../../explain.js';
import { t, format, word } from '../../i18n.js';

/**
 * Friends: add one by exact name (find_hunter, then a friendships insert, as the phone does), the list of friends and pending requests,
 * the blocked list with Unblock, and, when a lifter is open (`/friends/allies/<id>`), that lifter's folio beside the list.
 */
export async function renderAllies(root, ctx, openId) {
  const { social, router, source, snapshot, nowMs = Date.now() } = ctx, me = source.userId;
  const layout = node('div', null, `allies-layout${openId ? ' has-folio' : ''}`), left = node('div', null, 'allies-list'), right = node('div', null, 'allies-folio');
  layout.append(left, right); root.append(layout);

  const status = node('p', null, 'notice friends-notice'); status.setAttribute('role', 'status');
  const say = text => { status.textContent = text; };

  const form = node('form', null, 'panel allies-add'), label = node('label', t('acct_invite_label')), input = node('input'), send = node('button', t('acct_invite_button'), 'primary-small');
  input.type = 'text'; input.autocomplete = 'off'; input.maxLength = 40; send.type = 'submit'; label.append(input); form.append(label, send);
  form.addEventListener('submit', async event => {
    event.preventDefault(); const name = input.value.trim(); say('');
    if (!name) { say(t('cloud_ally_type_name')); return; }
    send.disabled = true;
    try {
      const found = await social.findHunter(name);
      if (!found) { say(format(t('cloud_ally_not_found'), name)); return; }
      if (found.id === me) { say(t('cloud_ally_self_request')); return; }
      await social.requestFriend(found.id); input.value = ''; say(`${t('lifter_request_pending')}: ${hiddenName(found.displayName)}`); await drawList();
    } catch (error) { say(error?.code === '23505' ? format(t('cloud_ally_already_requested'), name) : explain(error)); }
    finally { send.disabled = false; }
  });
  const list = node('div', null, 'allies-rows'), blocked = node('section', null, 'allies-blocked');
  left.append(form, status, list, blocked);

  const open = person => router.go(`/friends/allies/${encodeURIComponent(person.userId)}`);
  const drawList = async () => {
    const rows = await social.friends();
    const allies = rows.filter(r => r.accepted), incoming = rows.filter(r => r.incoming), outgoing = rows.filter(r => !r.accepted && !r.incoming);
    list.replaceChildren();
    if (incoming.length) {
      const line = node('p', null, 'allies-requests'); line.append(link(`${incoming.length} ${word('friend', 'concept_ally_s')} ${incoming.length === 1 ? 'request' : 'requests'}`, '/friends/inbox', router));
      list.append(line);
    }
    const head = node('h2', format(t('acct_allies_header'), allies.length), 'eyebrow'); list.append(head);
    if (!allies.length) list.append(node('p', t('acct_allies_empty'), 'muted'));
    for (const person of allies) { const row = personRow(person, { onOpen: open }); row.dataset.user = person.userId; if (person.userId === openId) row.classList.add('is-open'); list.append(row); }
    if (outgoing.length) {
      const pending = node('div', null, 'allies-pending');
      for (const person of outgoing) { const row = personRow(person, { trailing: node('span', t('feed_ally_pending'), 'chip chip-quiet') }); row.dataset.user = person.userId; pending.append(row); }
      list.append(pending);
    }
  };
  const drawBlocked = async () => {
    const rows = await social.blocked(); blocked.replaceChildren();
    blocked.append(node('h2', t('acct_blocked_label'), 'eyebrow'));
    if (!rows.length) { blocked.append(node('p', t('acct_blocked_empty'), 'muted')); return; }
    for (const person of rows) {
      const row = node('div', null, 'blocked-row'), undo = node('button', t('acct_unblock_button')); undo.type = 'button';
      undo.addEventListener('click', async () => { undo.disabled = true; try { await social.unblock(person.userId); await drawBlocked(); } catch (error) { say(explain(error)); undo.disabled = false; } });
      row.append(node('span', hiddenName(person.name)), undo); blocked.append(row);
    }
  };
  await drawList(); await drawBlocked();
  if (openId) {
    try {
      const data = await social.lifter(openId);
      renderFolio(right, { social, snapshot, me, nowMs, data, onGone: () => router.go('/friends/allies') });
    } catch (error) { right.append(node('p', explain(error), 'notice friends-notice')); }
  }
}
