import { node } from '../../components/statCard.js';
import { feedCard } from '../../components/feedCard.js';
import { commentThread } from '../../components/commentThread.js';
import { reasonDialog, confirmPanel } from '../../components/reasonDialog.js';
import { hiddenName } from '../../components/hiddenName.js';
import { explain } from '../../explain.js';
import { t, format } from '../../i18n.js';
import { FEED_PAGE } from '../../data/socialSource.js';

/**
 * The Feed: every lifter's workouts the server lets this account read, newest first, 50 a page by `completed_at`. A muted
 * lifter's workouts are absent (the view hides them) and a mute or block removes that lifter's cards at once.
 */
export async function renderFeed(root, ctx) {
  const { social, snapshot, router, nowMs = Date.now(), source } = ctx, me = source.userId;
  const list = node('div', null, 'feed-list'), more = node('button', null, 'pill feed-more'), status = node('p', null, 'notice friends-notice'); more.type = 'button'; more.hidden = true; more.textContent = 'Older workouts';
  root.append(node('p', t('feed_header'), 'muted'), list, status, more);
  let rows = [], last = null;

  const say = text => { status.textContent = text; };
  const removeLifter = userId => { rows = rows.filter(r => r.userId !== userId); for (const el of [...list.querySelectorAll('.feed-card')]) if (el.dataset.user === userId) el.remove(); if (!rows.length) say(t('feed_empty')); };
  const panelIn = (el, panel) => { el.querySelector('.panel')?.remove(); el.append(panel); };
  const menuFor = (row, el) => (row.userId === me ? [] : [
    { label: t('comments_report_button'), run: () => panelIn(el, reasonDialog({ title: format(t('comments_report_title'), hiddenName(row.name)), explain,
      onSend: async ({ reason, note }) => { await social.report({ targetUserId: row.userId, sessionId: row.sessionId, reason, note }); el.querySelector('.panel')?.remove(); say(t('comments_report_sent')); }, onCancel: () => el.querySelector('.panel')?.remove() })) },
    { label: t('lifter_mute_label'), run: async () => { try { await social.mute(row.userId); removeLifter(row.userId); say(t('lifter_muted')); } catch (error) { say(explain(error)); } } },
    { label: t('lifter_block_label'), run: () => panelIn(el, confirmPanel({ title: format(t('lifter_block_title'), hiddenName(row.name)), body: format(t('lifter_block_body'), hiddenName(row.name)), confirmLabel: t('lifter_block_label'), explain,
      onConfirm: async () => { await social.block(row.userId); removeLifter(row.userId); }, onCancel: () => el.querySelector('.panel')?.remove() })) },
  ]);
  const openComments = (row, el, toggle) => {
    const open = el.querySelector('.comment-thread'); if (open) { open.remove(); return; }
    const holder = node('div'); el.append(holder);
    commentThread(holder, { social, sessionId: row.sessionId, me, ownWorkout: row.userId === me, explain, onCount: n => { row.commentCount = n; toggle.textContent = `${n} ${t(n === 1 ? 'concept_remark_s' : 'concept_remark_pl')}`; } });
  };
  const add = page => {
    for (const row of page) { rows.push(row); list.append(feedCard(row, { router, snapshot, nowMs, social, explain, onComments: openComments, menu: menuFor })); }
    last = page.at(-1)?.completedAtMs ?? last;
    more.hidden = page.length < FEED_PAGE;
    if (page.length < FEED_PAGE && rows.length) say(t('feed_end'));
  };
  const load = async before => {
    more.disabled = true;
    try { add(await social.feed({ beforeMs: before, limit: FEED_PAGE })); }
    catch (error) { say(before == null ? explain(error) : t('feed_paging_error')); }
    finally { more.disabled = false; }
  };
  more.addEventListener('click', () => { say(''); load(last); });
  await load(null);
  if (!rows.length && !status.textContent) say(t('feed_empty'));
}
