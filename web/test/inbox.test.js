import { describe, it, expect, vi, afterEach } from 'vitest';
import parity from './fixtures/parity.json';
import { renderInbox, unreadCount, itemLine } from '../src/screens/friends/inbox.js';
import { createSupabaseSocial, createFakeSocial, inboxItem } from '../src/data/socialSource.js';
import { startPolling, INBOX_REFRESH_MS } from '../src/data/poll.js';
import { renderShell } from '../src/shell.js';
import { setWordsMode, t } from '../src/i18n.js';
import { fakeClient } from './helpers/fakeClient.js';

afterEach(() => { setWordsMode(false); document.body.innerHTML = ''; vi.useRealTimers(); });
const NOW = Date.parse('2026-10-28T12:00:00Z');
const row = (kind, over = {}) => ({ kind, occurred_at: '2026-10-28T10:00:00Z', actor_id: 'u1', actor_name: 'Mara', session_id: 's1', session_headline: 'Heavy pull day', comment_id: 'c1', body: 'Nice one', reaction: 'iron', ...over });
const wait = () => new Promise(resolve => setTimeout(resolve, 0));
const click = el => el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
const open = async (script, ctx = {}) => {
  const client = fakeClient(script), social = createSupabaseSocial(client, 'me'), root = document.createElement('main'); document.body.append(root);
  const setInboxUnread = vi.fn(); await renderInbox(root, { social, nowMs: NOW, setInboxUnread, ...ctx });
  return { root, client, social, setInboxUnread };
};

describe('inbox rules', () => {
  it('unreadCountMatchesPhoneParity', () => {
    expect(parity.inboxUnreadCases.length).toBeGreaterThanOrEqual(6);
    for (const c of parity.inboxUnreadCases) expect(unreadCount(c.itemsAtMs.map(occurredAtMs => ({ occurredAtMs })), c.seenAtMs)).toBe(c.unread);
  });
  it('everyKnownKindMaps', () => {
    const rows = ['request', 'accepted', 'comment', 'reply', 'reaction', 'band_join', 'circle_join', 'band_goal', 'circle_goal', 'circle_left', 'circle_keeper', 'circle_removed'];
    for (const kind of rows) expect(inboxItem(row(kind, { body: kind.includes('circle') || kind.startsWith('band') ? 'Iron Tide' : 'Nice one' }))).not.toBeNull();
    const text = kind => itemLine(inboxItem(row(kind, { body: 'Iron Tide' })));
    expect(text('request')).toBe(t('inbox_line_friend_request')); expect(text('accepted')).toBe(t('inbox_line_request_accepted'));
    expect(text('comment')).toBe('left a comment on Heavy pull day'); expect(text('reply')).toBe('replied on Heavy pull day');
    expect(text('reaction')).toBe('paid Iron kudos on Heavy pull day');
    expect(text('band_join')).toBe('joined your group Iron Tide'); expect(text('circle_join')).toBe('joined your group Iron Tide');
    expect(text('band_goal')).toBe(t('inbox_line_circle_goal_met')); expect(text('circle_left')).toBe('left your group Iron Tide');
    expect(text('circle_keeper')).toBe('passed you the keys of Iron Tide'); expect(text('circle_removed')).toBe('removed you from Iron Tide');
    const blank = itemLine(inboxItem(row('comment', { session_headline: '' }))); expect(blank).toBe(t('inbox_line_comment_untitled'));
    setWordsMode(true); expect(itemLine(inboxItem(row('reaction')))).toContain('tribute');
  });
  it('unknownKindIsDropped', () => { expect(inboxItem(row('shout'))).toBeNull(); });
  it('reactionWithoutSessionIsDropped', () => {
    expect(inboxItem(row('reaction', { session_id: null }))).toBeNull(); expect(inboxItem(row('reaction', { reaction: 'laugh' }))).toBeNull();
    expect(inboxItem(row('comment', { session_id: null }))).toBeNull(); expect(inboxItem(row('comment', { comment_id: null }))).toBeNull();
  });
  it('anAwkwardRowNeverFailsTheList', async () => {
    const { root } = await open({ 'rpc:my_inbox': [row('shout'), row('reaction', { session_id: null }), row('request')], inbox_seen: [] });
    expect(root.querySelectorAll('.inbox-item')).toHaveLength(1);
  });
});

describe('inbox screen', () => {
  const script = (items, seen = null, extra = {}) => ({ 'rpc:my_inbox': items, inbox_seen: seen ? [{ seen_at: seen }] : [], 'rpc:mark_inbox_seen': null, friendships: [{ requester_id: 'u1' }], ...extra });
  it('showsTheSectionsAndMarksNewItems', async () => {
    const { root } = await open(script([row('request'), row('comment'), row('circle_join', { body: 'Iron Tide' }), row('accepted', { actor_name: 'Jonah' })], '2026-10-28T10:30:00Z'));
    expect([...root.querySelectorAll('.inbox-section h2')].map(h => h.textContent)).toEqual(['Friend requests', 'On your workouts', 'Group', 'Friends']);
    expect(root.querySelectorAll('.inbox-dot')).toHaveLength(0);
    const fresh = await open(script([row('request')], '2026-10-28T09:00:00Z')); expect(fresh.root.querySelectorAll('.inbox-dot')).toHaveLength(1);
    expect(root.textContent).toContain('“Nice one”'); expect(root.textContent).toContain('2h ago');
    expect(root.querySelector('.inbox-note').textContent).toContain('One inbox, two windows');
  });
  it('emptyInboxReadsPlain', async () => { const { root } = await open(script([])); expect(root.textContent).toContain(t('inbox_empty_title')); });
  it('openingCallsMarkSeenOnce', async () => {
    const { client, setInboxUnread } = await open(script([row('request')]));
    expect(client.calls.filter(c => c.name === 'mark_inbox_seen')).toHaveLength(1); expect(setInboxUnread).toHaveBeenCalledWith(0);
  });
  it('inboxSayingSeenAtIsServerTime', async () => {
    const { client } = await open(script([row('request')]));
    const call = client.calls.find(c => c.name === 'mark_inbox_seen'); expect(call.args).toBeUndefined();
    expect(JSON.stringify(client.calls)).not.toMatch(/seen_at.*\d{4}-\d\d-\d\dT.*insert/);
    expect(client.calls.filter(c => c.table === 'inbox_seen').every(c => c.op === 'select')).toBe(true);
  });
  it('acceptRequestFlipsAcceptedAndReloads', async () => {
    const { root, client } = await open(script([row('request')]));
    click(root.querySelector('.inbox-item .primary-small')); await wait(); await wait();
    const call = client.callsTo('friendships')[0];
    expect(call.op).toBe('update'); expect(call.values).toEqual({ accepted: true }); expect(call.filters).toEqual([['eq', 'requester_id', 'u1'], ['eq', 'addressee_id', 'me']]); expect(call.returning).toBe('requester_id');
    expect(client.calls.filter(c => c.name === 'my_inbox').length).toBe(2); expect(root.querySelector('.friends-notice').textContent).toBe(t('inbox_answer_accepted'));
  });
  it('acceptWithNothingPendingReadsPlain', async () => {
    const { root } = await open(script([row('request')], null, { friendships: [] }));
    click(root.querySelector('.inbox-item .primary-small')); await wait(); await wait();
    expect(root.querySelector('.friends-notice').textContent).toBe(t('cloud_ally_no_pending'));
  });
  it('declineDeletesTheRow', async () => {
    const { root, client } = await open(script([row('request')]));
    click(root.querySelector('.inbox-item .panel-actions button:last-child')); await wait(); await wait();
    const call = client.callsTo('friendships')[0]; expect(call.op).toBe('delete'); expect(call.filters[0][0]).toBe('or');
    expect(call.filters[0][1]).toContain('requester_id.eq.me,addressee_id.eq.u1'); expect(root.querySelector('.friends-notice').textContent).toBe('Declined Mara');
  });
  it('serverMessageSurfacesPlain', async () => {
    const { root } = await open(script([row('request')], null, { friendships: { code: 'P0001', message: 'Too many ally requests today — try again tomorrow.' } }));
    click(root.querySelector('.inbox-item .primary-small')); await wait(); await wait();
    expect(root.querySelector('.friends-notice').textContent).toBe('Too many friend requests today — try again tomorrow.');
  });
});

describe('badge and refresh', () => {
  const snapshot = null, base = { source: { userId: 'me', schemaVersion: async () => 32, listWorkouts: async () => [] }, snapshot, onSignOut() {} };
  it('badgeShowsUnreadAndClearsAfterOpen', async () => {
    const items = [{ kind: 'request', occurredAtMs: 5 }, { kind: 'request', occurredAtMs: 6 }, { kind: 'accepted', occurredAtMs: 7 }];
    const social = createFakeSocial({ inbox: async () => ({ items, seenAtMs: null }), markInboxSeen: async () => {} });
    location.hash = '#/'; document.body.innerHTML = '<div id="r"></div>';
    const shell = renderShell(document.querySelector('#r'), { ...base, social });
    await vi.waitFor(() => expect(document.querySelector('.inbox-badge').hidden).toBe(false)); expect(document.querySelector('.inbox-badge').textContent).toBe('3');
    shell.dispose();
  });
  it('noBadgeOnAnOlderBackend', async () => {
    const social = createFakeSocial({ inbox: async () => ({ items: [{ occurredAtMs: 1 }], seenAtMs: null }) });
    location.hash = '#/'; document.body.innerHTML = '<div id="r"></div>';
    const shell = renderShell(document.querySelector('#r'), { ...base, source: { ...base.source, schemaVersion: async () => 31 }, social });
    await wait(); await wait(); expect(document.querySelector('.inbox-badge').hidden).toBe(true); expect(social.calls).toEqual([]); shell.dispose();
  });
  it('refreshesEvery30MinutesAndPausesWhenHidden', () => {
    vi.useFakeTimers(); const run = vi.fn(); const stop = startPolling(run);
    expect(INBOX_REFRESH_MS).toBe(30 * 60 * 1000);
    vi.advanceTimersByTime(INBOX_REFRESH_MS); expect(run).toHaveBeenCalledTimes(1);
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => true });
    vi.advanceTimersByTime(INBOX_REFRESH_MS * 2); expect(run).toHaveBeenCalledTimes(1);
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => false }); document.dispatchEvent(new Event('visibilitychange')); expect(run).toHaveBeenCalledTimes(2);
    stop(); vi.advanceTimersByTime(INBOX_REFRESH_MS * 3); expect(run).toHaveBeenCalledTimes(2);
    delete document.hidden;
  });
  it('aBackgroundRefreshNeverMarksSeen', async () => {
    const client = fakeClient({ 'rpc:my_inbox': [row('request')], inbox_seen: [] }), social = createSupabaseSocial(client, 'me');
    await social.inbox(); expect(client.calls.some(c => c.name === 'mark_inbox_seen')).toBe(false);
  });
});

describe('inbox items are targets', () => {
  const script = items => ({ 'rpc:my_inbox': items, inbox_seen: [], 'rpc:mark_inbox_seen': null, friendships: [{ requester_id: 'u1' }] });
  it('eachItemLinksToWhatItIsAboutAndARequestLinksNowhere', async () => {
    const { root } = await open(script([row('request'), row('comment'), row('reaction'), row('accepted', { actor_name: 'Jonah' }), row('circle_join', { body: 'Iron Tide' })]), { router: { go: vi.fn() } });
    const by = kind => root.querySelector(`.inbox-item[data-kind="${kind}"]`);
    const stretch = kind => { const found = by(kind).querySelectorAll('.stretch'); expect(found.length).toBeLessThanOrEqual(1); return found[0]; };
    expect(by('request').classList.contains('is-link')).toBe(false); expect(stretch('request')).toBeUndefined(); expect(by('request').querySelector('.primary-small')).not.toBeNull();
    expect(stretch('comment').getAttribute('href')).toBe('#/workouts/synced/s1'); expect(stretch('reaction').getAttribute('href')).toBe('#/workouts/synced/s1');
    expect(stretch('accepted').getAttribute('href')).toBe('#/friends/allies/u1'); expect(stretch('circle_join').getAttribute('href')).toBe('#/friends/circle');
    expect(by('comment').classList.contains('is-link')).toBe(true);
  });
  it('theFeedAndBoardsTabsTakeTheThemedNames', async () => {
    const { TABS } = await import('../src/screens/friends/index.js');
    expect(TABS.map(([, label]) => label())).toEqual(['Feed', 'Inbox', 'Friends', 'Boards', 'Group']);
    setWordsMode(true); expect(TABS.map(([, label]) => label())).toEqual([t('concept_tidings_c'), t('concept_inbox_c'), t('concept_ally_cpl'), t('concept_reckoning_c'), t('concept_circle_c')]);
    expect(t('concept_tidings_c')).toBe('Tidings');
  });
});
