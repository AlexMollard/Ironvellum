import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderFeed } from '../src/screens/friends/feed.js';
import { renderOverview } from '../src/screens/overview.js';
import { renderWorkoutDetail } from '../src/screens/workoutDetail.js';
import { createSupabaseSocial, createFakeSocial } from '../src/data/socialSource.js';
import { commentProblem, commentThread } from '../src/components/commentThread.js';
import { setWordsMode, t } from '../src/i18n.js';
import { fakeClient } from './helpers/fakeClient.js';

afterEach(() => { setWordsMode(false); document.body.innerHTML = ''; });
const router = { go: vi.fn() };
const NOW = Date.parse('2026-10-28T12:00:00Z');
const snapshot = { unit: 'kg', zone: 'UTC', muscles: { LATS: 'Lats', QUADS: 'Quads' }, profile: { figure: 'male', name: 'Me' } };
const row = (over = {}) => ({
  session_id: 's1', user_id: 'u1', display_name: 'Mara', current_title_id: null, level: 12, label: 'Pull', title: 'Pull day', note: '', completed_at: '2026-10-28T11:00:00Z', started_at: '2026-10-28T10:00:00Z',
  xp_awarded: 120, strength_score: 80, sets_done: 14, like_count: 0, liked_by_me: false, top_movements: 'Back Squat · Pull-up', best_set: '5 x 120.0 kg', duration_sec: 3600, comment_count: 2,
  reactions: { iron: 2 }, my_reaction: null, edited_at: null, current_crest_id: null,
  feed_card: { v: 1, hardSets: 9, volumeKg: 4210.5, prs: [{ label: 'Back Squat', reps: 5, weightKg: 120, durationSec: null }, { label: 'Pull-up', reps: 8, weightKg: 12.5, durationSec: null }], levels: { QUADS: 3, LATS: 1 } }, ...over,
});
const mount = async (script, { me = 'me', client = fakeClient(script) } = {}) => {
  const social = createSupabaseSocial(client, me), root = document.createElement('main'); document.body.append(root);
  await renderFeed(root, { social, snapshot, router, nowMs: NOW, source: { userId: me } });
  return { root, client, social };
};
const click = el => el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
const wait = () => new Promise(resolve => setTimeout(resolve, 0));

describe('feed card', () => {
  it('feedCardShowsFeedCardFields', async () => {
    const { root } = await mount({ public_feed: [row()] });
    const text = root.textContent;
    expect(text).toContain('9 hard sets'); expect(text).toContain('4.2 t');
    expect(text).toContain('Back Squat 120 kg × 5'); expect(text).toContain('Pull-up 12.5 kg × 8');
    const levels = Object.fromEntries([...root.querySelectorAll('.body-map')].flatMap(m => [...m.querySelectorAll('.body-region')]).map(r => [r.dataset.muscle, r.dataset.level]));
    expect(levels.QUADS).toBe('3'); expect(levels.LATS).toBe('1');
    expect(text).toContain('+120 XP'); expect(text).toContain('60 min'); expect(text).toContain('Pull day');
  });
  it('feedCardNeverShowsAnE1rm', async () => {
    const { root } = await mount({ public_feed: [row()] });
    expect(root.textContent).not.toMatch(/e1rm/i);
  });
  it('cardWithoutFeedCardFallsBackToBestSet', async () => {
    const { root } = await mount({ public_feed: [row({ feed_card: null })] });
    expect(root.textContent).toContain('Best 5 x 120.0 kg'); expect(root.textContent).toContain('Back Squat'); expect(root.textContent).toContain('Pull-up');
    expect(root.textContent).not.toContain('hard sets'); expect(root.querySelector('.body-map')).toBeNull();
  });
  it('crestAndTitleComeFromTheCatalogue', async () => {
    const { root } = await mount({ public_feed: [row({ current_crest_id: 'no-such-crest', current_title_id: 'no-such-title' })] });
    expect(root.querySelector('.plate').dataset.ring).toBeUndefined();
    expect(root.textContent).toContain('Level 12');
  });
  it('feedPagesBy50AndByCompletedAt', async () => {
    const page = Array.from({ length: 50 }, (_, i) => row({ session_id: `s${i}`, completed_at: new Date(NOW - (i + 1) * 3600000).toISOString() }));
    const calls = [];
    const { root, client } = await mount(call => { calls.push(call); return call.table === 'public_feed' && !call.filters.length ? page : []; });
    const first = client.callsTo('public_feed')[0];
    expect(first.limit).toBe(50); expect(first.order).toEqual([['completed_at', false]]);
    expect(root.querySelectorAll('.feed-card')).toHaveLength(50);
    const more = root.querySelector('.feed-more'); expect(more.hidden).toBe(false); click(more); await wait(); await wait();
    const second = client.callsTo('public_feed')[1];
    expect(second.filters).toEqual([['lt', 'completed_at', page[49].completed_at]]);
    expect(root.querySelector('.feed-more').hidden).toBe(true); expect(root.textContent).toContain(t('feed_end'));
  });
  it('emptyFeedReadsPlain', async () => {
    const { root } = await mount({ public_feed: [] });
    expect(root.textContent).toContain(t('feed_empty'));
  });
});

describe('reactions', () => {
  it('reactingUpsertsTheKind', async () => {
    const { root, client } = await mount({ public_feed: [row()], session_likes: [] });
    click(root.querySelector('[data-kind="iron"]')); await wait(); await wait();
    const call = client.callsTo('session_likes').find(c => c.op === 'upsert');
    expect(call.values).toEqual({ session_id: 's1', user_id: 'me', kind: 'iron' }); expect(call.options).toEqual({ onConflict: 'session_id,user_id' });
    expect(root.querySelector('[data-kind="iron"]').getAttribute('aria-pressed')).toBe('true'); expect(root.querySelector('[data-kind="iron"]').textContent).toContain('3');
  });
  it('switchingKindUpdatesNotDuplicates', async () => {
    const { root, client } = await mount({ public_feed: [row({ my_reaction: 'iron', reactions: { iron: 2 } })], session_likes: [] });
    click(root.querySelector('[data-kind="flame"]')); await wait(); await wait();
    const writes = client.callsTo('session_likes'); expect(writes).toHaveLength(1); expect(writes[0].op).toBe('upsert'); expect(writes[0].values.kind).toBe('flame');
    expect(root.querySelector('[data-kind="iron"]').textContent).toContain('1'); expect(root.querySelector('[data-kind="flame"]').getAttribute('aria-pressed')).toBe('true');
    expect(root.querySelector('[data-kind="iron"]').getAttribute('aria-pressed')).toBe('false');
  });
  it('clearingDeletesMyRow', async () => {
    const { root, client } = await mount({ public_feed: [row({ my_reaction: 'iron' })], session_likes: [] });
    click(root.querySelector('[data-kind="iron"]')); await wait(); await wait();
    const call = client.callsTo('session_likes')[0]; expect(call.op).toBe('delete'); expect(call.filters).toEqual([['eq', 'session_id', 's1'], ['eq', 'user_id', 'me']]);
    expect(root.querySelector('[data-kind="iron"]').getAttribute('aria-pressed')).toBe('false');
  });
  it('aRefusedReactionShowsItsSentenceAndChangesNothing', async () => {
    const { root } = await mount({ public_feed: [row()], session_likes: () => ({ code: '42501', message: 'rls' }) });
    click(root.querySelector('[data-kind="flame"]')); await wait(); await wait();
    expect(root.querySelector('.reaction-status').textContent).toBe(t('cloud_error_not_allowed')); expect(root.querySelector('[data-kind="flame"]').getAttribute('aria-pressed')).toBe('false');
  });
  it('reactorsListShowsNamesOrHidden', async () => {
    const client = fakeClient({ session_likes: [{ user_id: 'a', kind: 'iron', created_at: '2026-10-28T10:00:00Z', profiles: { display_name: 'Ines' } }, { user_id: 'b', kind: 'flame', created_at: '2026-10-28T09:00:00Z', profiles: null }] });
    const social = createSupabaseSocial(client, 'me'); const people = await social.reactors('s1');
    expect(people.map(p => p.name)).toEqual(['Ines', null]);
    const call = client.callsTo('session_likes')[0]; expect(call.limit).toBe(50); expect(call.columns).toBe('user_id,kind,created_at,profiles(display_name)');
  });
});

describe('comments', () => {
  const thread = async (script, { ownWorkout = false } = {}) => {
    const client = fakeClient(script), social = createSupabaseSocial(client, 'me'), root = document.createElement('div'); document.body.append(root);
    await commentThread(root, { social, sessionId: 's1', me: 'me', ownWorkout, explain: e => `E:${e.code ?? e.name}` });
    return { root, client };
  };
  const send = async (root, text) => { const box = root.querySelector('textarea'); box.value = text; box.dispatchEvent(new Event('input')); root.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })); await wait(); await wait(); };
  const comment = (over = {}) => ({ id: 'c1', session_id: 's1', user_id: 'u1', author_name: 'Mara', body: 'Strong', created_at: '2026-10-28T11:30:00Z', ...over });

  it('postingACommentSendsOnlyThreeColumns', async () => {
    const { root, client } = await thread({ session_comments: call => (call.op === 'insert' ? comment({ id: 'c9', user_id: 'me', author_name: 'Me', body: 'Nice' }) : []) });
    await send(root, '  Nice  ');
    const insert = client.callsTo('session_comments').find(c => c.op === 'insert'); expect(Object.keys(insert.values).sort()).toEqual(['body', 'session_id', 'user_id']); expect(insert.values.body).toBe('Nice');
    expect(insert.returning).toContain('author_name');
  });
  it('commentReadBackShowsServerNameAndTime', async () => {
    const { root } = await thread({ session_comments: call => (call.op === 'insert' ? comment({ id: 'c9', user_id: 'u2', author_name: 'Server Name', body: 'Hi', created_at: '2026-10-28T11:45:00Z' }) : []) });
    await send(root, 'Hi'); expect(root.textContent).toContain('Server Name'); expect(root.textContent).toContain('Hi');
  });
  it('emptyCommentIsRefusedLocally', async () => {
    const { root, client } = await thread({ session_comments: [] }); await send(root, '   ');
    expect(root.querySelector('.form-status').textContent).toBe(t('cloud_remark_empty')); expect(client.callsTo('session_comments').some(c => c.op === 'insert')).toBe(false);
  });
  it('commentOver280IsRefusedBeforeSending', async () => {
    const { root, client } = await thread({ session_comments: [] }); await send(root, 'x'.repeat(281));
    expect(root.querySelector('.form-status').textContent).toBe('Comments are at most 280 characters'); expect(client.callsTo('session_comments').some(c => c.op === 'insert')).toBe(false);
    setWordsMode(true); expect(commentProblem('x'.repeat(281))).toBe('Remarks are at most 280 characters');
  });
  it('exactly280IsAccepted', async () => {
    const { root, client } = await thread({ session_comments: call => (call.op === 'insert' ? comment({ id: 'c9', user_id: 'me', body: 'x'.repeat(280) }) : []) }); await send(root, 'x'.repeat(280));
    expect(client.callsTo('session_comments').some(c => c.op === 'insert')).toBe(true); expect(root.querySelector('.comment-counter').textContent).toBe('0 / 280');
  });
  it('counterIsLive', async () => {
    const { root } = await thread({ session_comments: [] }); const box = root.querySelector('textarea'); box.value = 'abc'; box.dispatchEvent(new Event('input'));
    expect(root.querySelector('.comment-counter').textContent).toBe('3 / 280');
  });
  it('deleteOwnCommentAsksRowsBack', async () => {
    const { root, client } = await thread({ session_comments: call => (call.op === 'delete' ? [{ id: 'c1' }] : [comment({ user_id: 'me' })]) });
    click(root.querySelector('.comment-delete')); click(root.querySelector('.confirm-panel .primary-small')); await wait(); await wait();
    const del = client.callsTo('session_comments').find(c => c.op === 'delete'); expect(del.filters).toEqual([['eq', 'id', 'c1']]); expect(del.returning).toBe('id');
    expect(root.querySelector('.comment')).toBeNull();
  });
  it('deleteRefusedReadsGone', async () => {
    const { root } = await thread({ session_comments: call => (call.op === 'delete' ? [] : [comment({ user_id: 'me' })]) });
    click(root.querySelector('.comment-delete')); click(root.querySelector('.confirm-panel .primary-small')); await wait(); await wait();
    expect(root.querySelector('.confirm-panel .form-status').textContent).toBe(t('cloud_remark_gone')); expect(root.querySelector('.comment')).not.toBeNull();
  });
  it('ownerCanDeleteOthersCommentsOnOwnWorkout', async () => {
    const own = await thread({ session_comments: [comment()] }, { ownWorkout: true }); expect(own.root.querySelector('.comment-delete')).not.toBeNull();
    document.body.innerHTML = '';
    const other = await thread({ session_comments: [comment()] }); expect(other.root.querySelector('.comment-delete')).toBeNull(); expect(other.root.querySelector('.comment-report')).not.toBeNull();
  });
  it('threadStopsAt200', async () => {
    const { client } = await thread({ session_comments: [] }); const read = client.callsTo('session_comments')[0];
    expect(read.limit).toBe(200); expect(read.order).toEqual([['created_at', true]]);
  });
  it('serverLimitsSurfaceAsPlainMessages', async () => {
    for (const sentence of ['Too many remarks — wait a minute.', 'Daily remark limit reached — try again tomorrow.', 'This trial has reached its remark limit.']) {
      document.body.innerHTML = '';
      const client = fakeClient({ session_comments: call => (call.op === 'insert' ? { code: 'P0001', message: sentence } : []) });
      const social = createSupabaseSocial(client, 'me'), root = document.createElement('div'); document.body.append(root);
      await commentThread(root, { social, sessionId: 's1', me: 'me', ownWorkout: false, explain: (await import('../src/explain.js')).explain });
      await send(root, 'Hello there');
      expect(root.querySelector('.form-status').textContent).not.toMatch(/P0001/); expect(root.querySelector('.form-status').textContent).toContain(sentence.split(' ')[0]);
      expect(root.querySelector('textarea').value).toBe('Hello there'); expect(root.querySelector('button[type="submit"]').disabled).toBe(false);
    }
  });
});

describe('safety', () => {
  const menuItem = (root, label) => [...root.querySelectorAll('.safety-item')].find(b => b.textContent === label);
  it('reportSendsReasonAndNote', async () => {
    const { root, client } = await mount({ public_feed: [row()], reports: [] });
    click(root.querySelector('.safety-toggle')); click(menuItem(root, t('comments_report_button')));
    click(root.querySelector('[data-reason="abuse"]')); const note = root.querySelector('.reason-dialog input'); note.value = 'rude';
    expect(note.maxLength).toBe(280); expect([...root.querySelectorAll('[role="radio"]')].map(b => b.textContent)).toEqual(['Spam', 'Abuse', 'Cheating', 'Other']);
    root.querySelector('.reason-dialog').dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })); await wait(); await wait();
    const call = client.callsTo('reports')[0]; expect(call.op).toBe('insert'); expect(call.values).toEqual({ reporter_id: 'me', target_user_id: 'u1', reason: 'abuse', note: 'rude', session_id: 's1' }); expect(call.returning).toBeUndefined();
  });
  it('reportLimitReadsPlain', async () => {
    const { root } = await mount({ public_feed: [row()], reports: { code: 'P0001', message: 'Too many reports today — try again tomorrow.' } });
    click(root.querySelector('.safety-toggle')); click(menuItem(root, t('comments_report_button')));
    root.querySelector('.reason-dialog').dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })); await wait(); await wait();
    expect(root.querySelector('.reason-dialog .form-status').textContent).toBe('Too many reports today — try again tomorrow.');
  });
  it('mutedLifterNotInFeed', async () => {
    const { root, client } = await mount({ public_feed: [row(), row({ session_id: 's2' }), row({ session_id: 's3', user_id: 'u9', display_name: 'Jonah' })], mutes: [] });
    click(root.querySelector('.safety-toggle')); click(menuItem(root, t('lifter_mute_label'))); await wait(); await wait();
    const call = client.callsTo('mutes')[0]; expect(call.op).toBe('insert'); expect(call.values).toEqual({ muter_id: 'me', muted_id: 'u1' });
    expect(root.querySelectorAll('.feed-card')).toHaveLength(1); expect(root.querySelector('.feed-card').dataset.user).toBe('u9');
  });
  it('blockConfirmsThenInsertsAndRemovesTheirCards', async () => {
    const { root, client } = await mount({ public_feed: [row()], blocks: [] });
    click(root.querySelector('.safety-toggle')); click(menuItem(root, t('lifter_block_label')));
    expect(client.callsTo('blocks')).toHaveLength(0); click(root.querySelector('.confirm-panel .primary-small')); await wait(); await wait();
    expect(client.callsTo('blocks')[0].values).toEqual({ blocker_id: 'me', blocked_id: 'u1' }); expect(root.querySelectorAll('.feed-card')).toHaveLength(0);
  });
  it('ownWorkoutsOfferNoSafetyMenu', async () => {
    const { root } = await mount({ public_feed: [row({ user_id: 'me' })] }); expect(root.querySelector('.safety-toggle')).toBeNull();
  });
});

describe('allies line on the owner screens', () => {
  const ownRow = { reactions: { salute: 3, iron: 1 }, commentCount: 2, sessionId: 's1' };
  const sessionsSnapshot = { builtAtMs: NOW, zone: 'UTC', unit: 'kg', profile: { name: 'Me', level: 3, streakDays: 1, longestStreakDays: 1, deedsEarned: 0, deedsTotal: 0, figure: 'male', workouts: 1, totalXp: 1, intoLevel: 1, needed: 2 }, week: { workouts: 1, hardSets: 1, volumeKg: 1, prevVolumeKg: 0, startDay: '2026-10-26', endDay: '2026-11-01' }, trends: { records30d: 0, recent: [], weeks: [], windows: {}, days: [] }, deeds: [], muscles: {}, fatigue: null, sessions: { 1: { origin: 'synced', min: 40, hardSets: 3, volumeKg: 100, xp: 5, strength: 5, peaks: 0, muscleLevels: {}, groupLevels: {}, prs: [], peakSets: [], e1rm: {} } }, lifts: [] };
  const workout = { id: 'cloud-1', localId: 1, origin: 'synced', userId: 'me', title: 'Pull', label: 'Pull', startedAt: '2026-10-28T10:00:00Z', completedAt: '2026-10-28T11:00:00Z' };
  const source = { userId: 'me', listWorkouts: async () => [workout], getSets: async () => [] };
  it('overviewShowsAlliesLineFromOwnFeedRow', async () => {
    const social = createFakeSocial({ workoutFeedRow: async id => (id === 'cloud-1' ? ownRow : null) });
    const root = document.createElement('main'); document.body.append(root);
    await renderOverview(root, { source, snapshot: sessionsSnapshot, router, nowMs: NOW, social });
    await vi.waitFor(() => expect(root.querySelector('.allies-line')).not.toBeNull());
    expect(root.querySelector('.allies-line').textContent).toContain('Honour 3'); expect(root.querySelector('.allies-line').textContent).toContain('2 comments');
    expect(social.calls.map(c => c.method)).toEqual(['workoutFeedRow']);
  });
  it('overviewHidesTheLineWhenTheRowIsAbsent', async () => {
    const social = createFakeSocial({ workoutFeedRow: async () => null });
    const root = document.createElement('main'); document.body.append(root);
    await renderOverview(root, { source, snapshot: sessionsSnapshot, router, nowMs: NOW, social }); await wait(); await wait();
    expect(root.querySelector('.allies-line')).toBeNull();
  });
  it('detailShowsAlliesLine', async () => {
    const social = createFakeSocial({ workoutFeedRow: async () => ownRow, reactors: async () => [{ name: 'Mara' }, { name: 'Jonah' }, { name: null }] });
    const root = document.createElement('main'); document.body.append(root);
    await renderWorkoutDetail(root, { source, snapshot: sessionsSnapshot, router, social }, 'synced', 'cloud-1');
    await vi.waitFor(() => expect(root.querySelector('.allies-line')).not.toBeNull());
    expect(root.querySelector('.allies-line').textContent).toContain('Mara, Jonah, '); expect(root.querySelector('.allies-line').textContent).toContain(t('cloud_hidden_lifter'));
  });
  it('aFailingFeedReadLeavesTheScreenIntact', async () => {
    const social = createFakeSocial({ workoutFeedRow: async () => { throw new Error('offline'); } });
    const root = document.createElement('main'); document.body.append(root);
    await renderOverview(root, { source, snapshot: sessionsSnapshot, router, nowMs: NOW, social }); await wait(); await wait();
    expect(root.querySelector('.allies-line')).toBeNull(); expect(root.textContent).toContain('Pull');
  });
});
