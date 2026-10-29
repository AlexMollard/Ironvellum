import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderFriends, SCREENS, TABS, allyIdsOf, filterByAllies, resetAudience } from '../src/screens/friends/index.js';
import { createSupabaseSocial, createFakeSocial, NotYet } from '../src/data/socialSource.js';
import { personRow } from '../src/components/personRow.js';
import { hiddenName } from '../src/components/hiddenName.js';
import { renderShell } from '../src/shell.js';
import { setWordsMode, t } from '../src/i18n.js';
import { NEEDED_SCHEMA_VERSION, FRIENDS_SCHEMA_VERSION } from '../src/schema.js';
import { fakeClient } from './helpers/fakeClient.js';

const router = () => ({ go: vi.fn() });
const source = (version = 32) => ({ userId: 'me', schemaVersion: async () => version });
afterEach(() => { setWordsMode(false); resetAudience(); });

// The tables and RPCs a Friends screen may read: friends' rows through the existing read policies, never an owner-only table.
const ALLOWED_TABLES = new Set(['public_feed', 'session_likes', 'session_comments', 'friendships', 'profiles', 'sessions', 'session_sets', 'lift_board', 'leaderboard',
  'shadow_board', 'blocks', 'mutes', 'inbox_seen', 'earned_titles', 'level_ups', 'reports']);
const ALLOWED_RPCS = new Set(['my_inbox', 'mark_inbox_seen', 'find_hunter', 'my_circle', 'circle_bonuses', 'create_circle', 'join_circle', 'leave_circle', 'set_circle_goal', 'set_circle_name', 'rotate_circle_code', 'kick_circle_member']);
const FORBIDDEN = ['web_snapshots', 'imported_sessions', 'imported_session_sets', 'session_set_details', 'cloud_archives'];

describe('friends shell', () => {
  it('friendScreensReadNoOwnerOnlyTable', async () => {
    const client = fakeClient({});
    const social = createSupabaseSocial(client, 'me');
    for (const [tab] of TABS) {
      const root = document.createElement('div');
      await renderFriends(root, { source: source(), social, router: router() }, tab);
    }
    for (const table of client.tables()) { expect(ALLOWED_TABLES.has(table)).toBe(true); expect(FORBIDDEN).not.toContain(table); }
    for (const name of client.rpcs()) expect(ALLOWED_RPCS.has(name)).toBe(true);
    for (const call of client.calls.filter(c => c.table === 'reports')) expect(call.op).toBe('insert');
  });
  it('needsSchema32OnAnOlderBackend', async () => {
    expect(NEEDED_SCHEMA_VERSION).toBe(31); expect(FRIENDS_SCHEMA_VERSION).toBe(32);
    for (const version of [30, 31]) {
      const root = document.createElement('div'); const social = createFakeSocial({});
      await renderFriends(root, { source: source(version), social, router: router() }, 'feed');
      expect(root.textContent).toContain(`needs schema 32; this backend reports ${version}`);
      expect(social.calls).toEqual([]);
    }
    const root = document.createElement('div');
    await renderFriends(root, { source: source(32), social: createFakeSocial({}), router: router() }, 'feed');
    expect(root.textContent).not.toContain('needs schema');
  });
  it('signedOutShowsAPlainMessage', async () => {
    const root = document.createElement('div');
    await renderFriends(root, { source: source(), social: null, router: router() }, 'feed');
    expect(root.textContent).toContain('Sign in');
    expect(root.querySelector('.friends-section')).toBeNull();
  });
  it('showsTheEmptyFeedStateAndTheFivePages', async () => {
    const root = document.createElement('div');
    await renderFriends(root, { source: source(), social: createFakeSocial({ feed: async () => [] }), router: router() }, 'feed');
    expect(root.textContent).toContain(t('feed_empty'));
    // No in-page tab row: the page carries one labelled select (shown only at the bottom-bar width) and no links.
    expect(root.querySelector('.friends-tabs, .friends-tab')).toBeNull(); expect(root.querySelector('a')).toBeNull();
    const pick = root.querySelector('label.friends-section select'); expect(root.querySelector('label.friends-section').textContent).toContain('Section');
    expect([...pick.options].map(o => o.value)).toEqual(['feed', 'inbox', 'allies', 'boards', 'circle']); expect(pick.value).toBe('feed');
  });
  it('pagesAreSidebarDeepLinksAndTheSelectNavigates', async () => {
    const r = router(); const root = document.createElement('div');
    await renderFriends(root, { source: source(), social: createFakeSocial({}), router: r }, 'inbox');
    const pick = root.querySelector('.friends-section-pick'); expect(pick.value).toBe('inbox');
    pick.value = 'circle'; pick.dispatchEvent(new Event('change'));
    expect(r.go).toHaveBeenCalledWith('/friends/circle');
    // the sidebar holds the links: nested under Friends, shown only while Friends is open, the current page marked
    const open = async hash => {
      document.body.innerHTML = '<div id="r"></div>'; location.hash = hash;
      const shell = renderShell(document.querySelector('#r'), { source: { ...source(), listWorkouts: async () => [] }, snapshot: null, social: createFakeSocial({}), onSignOut() {} });
      await new Promise(resolve => setTimeout(resolve, 20)); return shell;
    };
    let shell = await open('#/friends/inbox');
    const sub = document.querySelector('.nav-sub'); expect(sub.hidden).toBe(false);
    expect([...sub.querySelectorAll('a')].map(a => [a.dataset.tab, a.getAttribute('href')])).toEqual(TABS.map(([id]) => [id, `#/friends/${id}`]));
    expect([...sub.querySelectorAll('[aria-current]')].map(a => a.dataset.tab)).toEqual(['inbox']);
    expect(document.querySelector('[data-friends]').getAttribute('aria-current')).toBe('true');
    expect(document.querySelectorAll('.sidebar [aria-current="page"]')).toHaveLength(1);
    expect(document.querySelector('.content .friends-section-pick').value).toBe('inbox');
    shell.dispose(); shell = await open('#/workouts'); expect(document.querySelector('.nav-sub').hidden).toBe(true); expect(document.querySelector('.nav-sub [aria-current]')).toBeNull(); shell.dispose();
    shell = await open('#/friends'); expect(document.querySelector('.nav-sub [aria-current]').dataset.tab).toBe('feed'); shell.dispose();
    location.hash = '';
    // and the shell routes each path to its tab
    for (const tab of ['feed', 'inbox', 'allies', 'boards', 'circle']) {
      const seen = vi.fn(); const saved = SCREENS[tab]; SCREENS[tab] = async (body, ctx, arg) => { seen(tab, arg); }; document.body.innerHTML = '<div id="r"></div>';
      location.hash = tab === 'allies' ? '#/friends/allies/u-7' : `#/friends/${tab}`;
      const shell = renderShell(document.querySelector('#r'), { source: { ...source(), listWorkouts: async () => [] }, snapshot: null, social: createFakeSocial({}), onSignOut() {} });
      await new Promise(resolve => setTimeout(resolve, 20)); shell.dispose();
      expect(seen).toHaveBeenCalledWith(tab, tab === 'allies' ? 'u-7' : undefined); SCREENS[tab] = saved;
    }
    location.hash = '';
  });
  it('audienceFilterIsAClientFilterByAllyIds', async () => {
    const friends = [{ userId: 'a', accepted: true }, { userId: 'b', accepted: false }, { userId: 'c', accepted: true }];
    expect([...allyIdsOf(friends)]).toEqual(['a', 'c']);
    const rows = [{ id: 'a' }, { id: 'b' }, { id: 'c' }, { id: 'me' }];
    expect(filterByAllies(rows, allyIdsOf(friends))).toEqual([{ id: 'a' }, { id: 'c' }]);
    const social = createFakeSocial({ friends: async () => friends, leaderboard: async () => rows });
    const seen = []; let last; const saved = SCREENS.boards; SCREENS.boards = async (body, ctx) => { last = ctx; seen.push([ctx.audience, ctx.allyIds && [...ctx.allyIds]]); };
    const r = router(); const root = document.createElement('div');
    await renderFriends(root, { source: source(), social, router: r }, 'boards');
    expect(seen[0]).toEqual(['everyone', undefined]);
    expect(social.calls.map(c => c.method)).toEqual([]);
    last.setAudience('allies');
    const again = document.createElement('div');
    await renderFriends(again, { source: source(), social, router: r }, 'boards');
    expect(seen[1]).toEqual(['allies', ['a', 'c']]);
    expect(social.calls.map(c => c.method)).toEqual(['friends']);
    SCREENS.boards = saved;
  });
  it('hiddenProfileUsesNeutralName', () => {
    expect(hiddenName(null)).toBe(t('cloud_hidden_lifter')); expect(hiddenName('  ')).toBe(t('cloud_hidden_lifter')); expect(hiddenName('Mara')).toBe('Mara');
    const row = personRow({ id: 'u', name: null, level: 4 });
    expect(row.textContent).toContain(t('cloud_hidden_lifter'));
    setWordsMode(true); expect(hiddenName(undefined)).toBe(t('cloud_hidden_lifter')); expect(t('cloud_hidden_lifter')).toMatch(/Ironbound/);
  });
  it('aScreenThatHasNotLandedReadsAsAPlainSentence', async () => {
    const root = document.createElement('div');
    await renderFriends(root, { source: source(), social: createFakeSocial({}), router: router() }, 'inbox');
    expect(root.textContent).toContain(t('cloud_error_unavailable'));
    expect(new NotYet('x').message).not.toMatch(/stack/);
  });
});
