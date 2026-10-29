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
    expect(root.querySelector('.friends-tabs')).toBeNull();
  });
  it('showsTheEmptyFeedStateAndTheFiveSubTabs', async () => {
    const root = document.createElement('div');
    await renderFriends(root, { source: source(), social: createFakeSocial({ feed: async () => [] }), router: router() }, 'feed');
    expect(root.textContent).toContain(t('feed_empty'));
    expect([...root.querySelectorAll('.friends-tab')].map(a => a.dataset.tab)).toEqual(['feed', 'inbox', 'allies', 'boards', 'circle']);
    expect(root.querySelector('[aria-current="page"]').dataset.tab).toBe('feed');
  });
  it('subTabsAreDeepLinks', async () => {
    const r = router(); const root = document.createElement('div');
    await renderFriends(root, { source: source(), social: createFakeSocial({}), router: r }, 'inbox');
    expect(root.querySelector('[aria-current="page"]').getAttribute('href')).toBe('#/friends/inbox');
    root.querySelector('[data-tab="circle"]').click();
    expect(r.go).toHaveBeenCalledWith('/friends/circle');
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
    const seen = []; const saved = SCREENS.boards; SCREENS.boards = async (body, ctx) => { seen.push([ctx.audience, ctx.allyIds && [...ctx.allyIds]]); };
    const r = router(); const root = document.createElement('div');
    await renderFriends(root, { source: source(), social, router: r }, 'boards');
    expect(seen[0]).toEqual(['everyone', undefined]);
    expect(social.calls.map(c => c.method)).toEqual([]);
    root.querySelector('[data-audience="allies"]').click();
    expect(r.go).toHaveBeenCalledWith('/friends/boards');
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
