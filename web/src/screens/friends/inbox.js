import { node, link } from '../../components/statCard.js';
import { hiddenName } from '../../components/hiddenName.js';
import { explain } from '../../explain.js';
import { t, format, word } from '../../i18n.js';
import { timeAgo } from '../../components/feedCard.js';

/** The phone's `Inbox.unread` rule, pinned by parity cases it generated: unread when never seen, or newer than the last visit. */
export const unreadCount = (items, seenAtMs) => items.filter(i => seenAtMs == null || i.occurredAtMs > seenAtMs).length;

const REACTION_WORD = { salute: 'feed_reaction_honour', iron: 'feed_reaction_iron', flame: 'feed_reaction_flame' };
const SECTIONS = [['requests', item => item.kind === 'request', () => `${word('Friend', 'concept_ally_c')} requests`],
  ['yours', item => ['comment', 'reply', 'reaction'].includes(item.kind), () => 'On your workouts'],
  ['circle', item => item.kind.startsWith('circle_'), () => word('Group', 'concept_circle_c')],
  ['friends', item => item.kind === 'accepted', () => word('Friends', 'concept_ally_cpl')]];

/** The phone's line for one item, through its own strings: "<name> <this>". */
export function itemLine(item) {
  const headline = item.headline || null;
  switch (item.kind) {
    case 'request': return t('inbox_line_friend_request');
    case 'accepted': return t('inbox_line_request_accepted');
    case 'comment': return headline ? format(t('inbox_line_comment'), headline) : t('inbox_line_comment_untitled');
    case 'reply': return headline ? format(t('inbox_line_reply'), headline) : t('inbox_line_reply_untitled');
    case 'reaction': { const name = t(REACTION_WORD[item.reaction]); return headline ? format(t('inbox_line_reaction'), name, headline) : format(t('inbox_line_reaction_untitled'), name); }
    case 'circle_join': return format(t('inbox_line_circle_joined'), item.body);
    case 'circle_goal': return t('inbox_line_circle_goal_met');
    case 'circle_left': return format(t('inbox_line_circle_left'), item.body);
    case 'circle_keeper': return format(t('inbox_line_circle_keys'), item.body);
    case 'circle_removed': return format(t('inbox_line_circle_removed'), item.body);
    default: return '';
  }
}

/**
 * The inbox: the same one the phone shows, derived on the server (`my_inbox`), so opening it here clears the phone's badge too (the
 * page says so). Opening calls `mark_inbox_seen` once, after the unread markers are drawn; a background refresh never does.
 */
export async function renderInbox(root, ctx) {
  const { social, router, nowMs = Date.now(), setInboxUnread } = ctx;
  const body = node('div', null, 'inbox'), status = node('p', null, 'notice friends-notice'); status.setAttribute('role', 'status');
  root.append(body, status, node('p', 'One inbox, two windows: reading it here clears it in the app too. There are no push messages or emails.', 'muted inbox-note'));
  let answered = new Set();
  const load = async () => {
    const { items, seenAtMs } = await social.inbox();
    draw(items, seenAtMs); return { items, seenAtMs };
  };
  const answer = async (item, run, done) => {
    status.textContent = '';
    try { await run(); answered.add(item.actorId); status.textContent = done; await load(); } catch (error) { status.textContent = error?.name === 'NoPending' ? t('cloud_ally_no_pending') : explain(error); }
  };
  const row = (item, seenAtMs) => {
    const el = node('div', null, 'inbox-item'); el.dataset.kind = item.kind;
    const unread = seenAtMs == null || item.occurredAtMs > seenAtMs;
    if (unread) { const dot = node('span', null, 'inbox-dot'); dot.setAttribute('role', 'img'); dot.setAttribute('aria-label', t('inbox_unread_description')); el.append(dot); }
    // Where the item points: your workout for a comment or kudos on it, the folio of someone who accepted, the Group page for its own news.
    const path = item.sessionId ? `/workouts/synced/${encodeURIComponent(item.sessionId)}` : item.kind === 'accepted' ? `/friends/allies/${encodeURIComponent(item.actorId)}` : item.kind.startsWith('circle_') ? '/friends/circle' : null;
    if (path && router) el.classList.add('is-link');
    const text = node('div', null, 'inbox-text'); const line = path && router ? link('', path, router, 'inbox-link stretch') : node('div'); line.append(node('strong', hiddenName(item.actorName)), ` ${itemLine(item)}`);
    text.append(line);
    if (item.body && ['comment', 'reply'].includes(item.kind)) text.append(node('div', format(t('inbox_remark_quote'), item.body), 'muted inbox-quote'));
    text.append(node('div', timeAgo(item.occurredAtMs, nowMs), 'faint inbox-when')); el.append(text);
    if (item.kind === 'request' && !answered.has(item.actorId)) {
      const actions = node('div', null, 'panel-actions');
      const accept = node('button', t('inbox_accept_button'), 'primary-small'), decline = node('button', t('inbox_decline_button'));
      accept.type = decline.type = 'button';
      accept.addEventListener('click', () => answer(item, () => social.acceptFriend(item.actorId), t('inbox_answer_accepted')));
      decline.addEventListener('click', () => answer(item, () => social.removeFriend(item.actorId), format(t('inbox_declined_message'), hiddenName(item.actorName))));
      actions.append(accept, decline); el.append(actions);
    }
    return el;
  };
  const draw = (items, seenAtMs) => {
    body.replaceChildren();
    if (!items.length) { body.append(node('p', t('inbox_empty_title'), 'empty'), node('p', t('inbox_empty_body'), 'muted')); return; }
    for (const [id, test, title] of SECTIONS) {
      const mine = items.filter(test).sort((a, b) => b.occurredAtMs - a.occurredAtMs); if (!mine.length) continue;
      const section = node('section', null, `inbox-section inbox-${id}`), head = node('h2', title(), 'eyebrow'); section.append(head);
      for (const item of mine) section.append(row(item, seenAtMs));
      body.append(section);
    }
  };
  const first = await load();
  try { await social.markInboxSeen(); setInboxUnread?.(0); } catch (error) { status.textContent = explain(error); }
  return first;
}
