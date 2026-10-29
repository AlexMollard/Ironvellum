import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import parity from './fixtures/parity.json';
import { renderBoards, resetBoards } from '../src/screens/friends/boards.js';
import { rankRows, rankLift, ordinal, metricText, METRICS } from '../src/boardRanking.js';
import { createSupabaseSocial } from '../src/data/socialSource.js';
import { t, format, setWordsMode } from '../src/i18n.js';
import { fakeClient } from './helpers/fakeClient.js';

beforeEach(resetBoards);
afterEach(() => { setWordsMode(false); document.body.innerHTML = ''; });
const wait = () => new Promise(resolve => setTimeout(resolve, 0));
const click = el => el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
const choose = (select, value) => { select.value = value; select.dispatchEvent(new Event('change')); };
const lb = (id, name, over = {}) => ({ id, display_name: name, current_title_id: null, current_crest_id: null, level: 10, total_xp: 1000, streak_days: 1, titles_count: 1, lifetime_strength: 500, sessions_last_7d: 2, ...over });
const lift = (id, name, l, step, recent = null) => ({ user_id: id, display_name: name, current_title_id: null, current_crest_id: null, level: 10, lift: l, step, recent_step: recent });
const mount = async (script, ctx = {}, kind) => {
  const client = fakeClient(script), social = createSupabaseSocial(client, 'me'), root = document.createElement('main'), router = { go: vi.fn() };
  document.body.append(root); await renderBoards(root, { social, router, source: { userId: 'me' }, audience: 'everyone', ...ctx }, kind); await wait();
  return { root, client, router };
};
const board = [lb('me', 'Fictional lifter', { total_xp: 5000 }), lb('u1', 'Mara', { total_xp: 9000 }), lb('u2', 'Jonah', { total_xp: 7000 }), lb('u3', 'Ines', { total_xp: 4000 }), lb('u4', 'Odin', { total_xp: 3000 }), lb('u5', 'Eve', { total_xp: 100 })];

describe('ranking', () => {
  it('rankingMatchesPhoneParity', () => {
    const { training, lifts } = parity.boardCases;
    expect(Object.keys(training.orders).sort()).toEqual([...METRICS].sort());
    for (const metric of METRICS) {
      const sorted = rankRows(metric, training.rows);
      expect(sorted.map(r => r.id)).toEqual(training.orders[metric].ids);
    }
    expect(rankLift(lifts.rows, 'squat', false).map(s => ({ id: s.row.id, rank: s.rank, step: s.step }))).toEqual(lifts.allTime);
    expect(rankLift(lifts.rows, 'squat', true).map(s => ({ id: s.row.id, rank: s.rank, step: s.step }))).toEqual(lifts.recent);
  });
  it('tiesShareARank', () => {
    const rows = [{ id: 'a', name: 'a', lift: 'squat', step: 6, recentStep: null }, { id: 'b', name: 'b', lift: 'squat', step: 6, recentStep: null }, { id: 'c', name: 'c', lift: 'squat', step: 3, recentStep: null }];
    expect(rankLift(rows, 'squat', false).map(s => s.rank)).toEqual([1, 1, 3]);
  });
  it('anotherLiftDoesNotRankHere', () => { expect(rankLift([{ id: 'a', name: 'a', lift: 'bench', step: 6, recentStep: 6 }], 'squat', false)).toEqual([]); });
  it('ordinalsReadLikeThePhone', () => { expect([1, 2, 3, 4, 11, 12, 13, 21, 22].map(ordinal)).toEqual(['1st', '2nd', '3rd', '4th', '11th', '12th', '13th', '21st', '22nd']); });
  it('metricTextUsesThePhonesStrings', () => {
    const row = { totalXp: 12400, level: 31, streakDays: 1, titlesCount: 12, lifetimeStrength: 14000, sessionsLast7d: 4 };
    expect(metricText('xp', row)).toBe(format(t('acct_metric_xp_value'), '12,400')); expect(metricText('level', row)).toBe(format(t('xp_level_label'), 31));
    expect(metricText('streak', row)).toBe('1 day'); expect(metricText('titles', row)).toBe('12 deeds'); expect(metricText('strength', row)).toBe(format(t('acct_metric_strength_value'), '14,000')); expect(metricText('last7', row)).toBe(format(t('acct_metric_last7_value'), 4));
  });
});

describe('training board', () => {
  it('podiumShowsTopThreeAndRowsFromFour', async () => {
    const { root } = await mount({ leaderboard: board });
    expect([...root.querySelectorAll('.podium-place')].map(p => [p.dataset.rank, p.dataset.user])).toEqual([['2', 'u2'], ['1', 'u1'], ['3', 'me']]);
    expect([...root.querySelectorAll('.board-row')].map(r => [r.querySelector('.board-rank').textContent, r.dataset.user])).toEqual([['4', 'u3'], ['5', 'u4'], ['6', 'u5']]);
    expect(root.querySelector('.board-row .person-sub').textContent).toContain('2 workouts this week');
  });
  it('youAreHighlighted', async () => {
    const { root } = await mount({ leaderboard: board }); expect(root.querySelector('.podium-place.is-me').dataset.user).toBe('me'); expect(root.querySelector('.board-you').textContent).toContain('3');
    const low = await mount({ leaderboard: [...board.slice(1), lb('me', 'Fictional lifter', { total_xp: 50 })] }); expect(low.root.querySelector('.board-row.is-me').dataset.user).toBe('me');
  });
  it('metricSwitchReorders', async () => {
    const rows = [lb('u1', 'Mara', { total_xp: 9000, streak_days: 1 }), lb('u2', 'Jonah', { total_xp: 100, streak_days: 20 }), lb('u3', 'Ines', { total_xp: 50, streak_days: 5 })];
    const { root, router } = await mount({ leaderboard: rows }); expect(root.querySelector('.podium-place.place-1').dataset.user).toBe('u1');
    choose(root.querySelector('.board-metric'), 'streak'); expect(root.querySelector('.podium-place.place-1').dataset.user).toBe('u2'); expect(root.querySelector('.podium-place.place-1').textContent).toContain('20 days');
    expect(root.querySelector('.board-metric').value).toBe('streak'); expect(router.go).not.toHaveBeenCalled();
  });
  it('levelBoardShowsXpAsTheSubline', async () => {
    const { root } = await mount({ leaderboard: [...board, lb('u6', 'Zed', { total_xp: 10 })] }); choose(root.querySelector('.board-metric'), 'level');
    expect(root.querySelector('.board-row .person-sub').textContent).toContain(format(t('acct_metric_xp_value'), '4,000'));
  });
  it('aloneOnTheBoardSaysSo', async () => { const { root } = await mount({ leaderboard: [lb('me', 'Fictional lifter')] }); expect(root.textContent).toContain(t('acct_board_alone')); });
  it('emptyBoardReadsPlain', async () => { const { root } = await mount({ leaderboard: [] }); expect(root.querySelector('.empty').textContent).toBe(t('acct_board_empty')); });
  it('friendsAudienceFiltersClientSideOnly', async () => {
    const { root, client } = await mount({ leaderboard: board }, { audience: 'allies', allyIds: new Set(['u2']) });
    expect([...root.querySelectorAll('.podium-place')].map(p => p.dataset.user)).toEqual(['me', 'u2']); expect(client.calls).toHaveLength(1);
  });
  it('blockedLifterIsAbsentAsServed', async () => {
    const { root } = await mount({ leaderboard: board.filter(r => r.id !== 'u1') }); expect(root.querySelector('[data-user="u1"]')).toBeNull();
  });
  it('requestsTheTopHundredByXp', async () => {
    const { client } = await mount({ leaderboard: board }); const call = client.callsTo('leaderboard')[0]; expect(call.limit).toBe(100); expect(call.order).toEqual([['total_xp', false]]);
  });
});

describe('lift board', () => {
  const rows = [lift('me', 'Fictional lifter', 'squat', 8, 7), lift('u1', 'Mara', 'squat', 7, 6), lift('u2', 'Jonah', 'squat', 7, null), lift('u3', 'Ines', 'squat', 5, 5), lift('u1', 'Mara', 'bench', 4, null)];
  it('liftBoardTiersAndWindows', async () => {
    const { root } = await mount({ lift_board: rows }, {}, 'lifts');
    expect([...root.querySelectorAll('.board-row')].map(r => [r.dataset.rank, r.dataset.user])).toEqual([['1', 'me'], ['2', 'u2'], ['2', 'u1'], ['4', 'u3']]);
    expect(root.querySelector('.board-row .board-figure').textContent).toContain('Gold II'); expect(root.querySelector('.board-row .board-figure').textContent).toContain('tier 8 of 10');
    expect(root.querySelector('.board-you').textContent).toBe('You: Gold II · 1st');
    choose(root.querySelector('.board-window'), 'week'); expect(root.querySelector('.board-window').value).toBe('week');
    expect([...root.querySelectorAll('.board-row')].map(r => [r.dataset.rank, r.dataset.user])).toEqual([['1', 'me'], ['2', 'u1'], ['3', 'u3']]);
    expect(root.querySelector('.board-row .board-figure').textContent).toContain('Gold I');
  });
  it('anotherLiftSwitchesTheBoard', async () => {
    const { root } = await mount({ lift_board: rows }, {}, 'lifts'); choose(root.querySelector('.lift-pick'), 'bench');
    expect([...root.querySelectorAll('.board-row')].map(r => r.dataset.user)).toEqual(['u1']); expect(root.querySelector('.lift-pick').value).toBe('bench');
  });
  it('anEmptyLiftReadsNoneYet', async () => { const { root } = await mount({ lift_board: [] }, {}, 'lifts'); expect(root.querySelector('.empty').textContent).toBe(t('acct_lift_none_ranked')); });
  it('theNoteKeepsTiersNeverWeights', async () => { const { root } = await mount({ lift_board: rows }, {}, 'lifts'); expect(root.textContent).toContain(t('acct_lifts_note')); });
  it('anUnknownLiftIsSkipped', async () => { const { root } = await mount({ lift_board: [lift('u1', 'Mara', 'mystery', 5, 5)] }, {}, 'lifts'); expect(root.querySelector('.board-row')).toBeNull(); });
});

describe('muster board', () => {
  it('shadowBoardOrdersByEssence', async () => {
    const { root, client } = await mount({ shadow_board: [{ id: 'u1', display_name: 'Mara', level: 5, shadow_essence: 12480, shadow_count: 6, shadow_rate: 218.2 }, { id: 'u2', display_name: 'Jonah', level: 5, shadow_essence: 40, shadow_count: 1, shadow_rate: 1 }] }, {}, 'muster');
    expect([...root.querySelectorAll('.board-row')].map(r => r.dataset.user)).toEqual(['u1', 'u2']); expect(root.querySelector('.board-row').textContent).toContain('12,480 essence'); expect(root.querySelector('.board-row').textContent).toContain('6 echoes · 218.2/h');
    expect(root.querySelectorAll('.board-row')[1].textContent).toContain('1 echo · 1.0/h'); expect(client.callsTo('shadow_board')[0].order).toEqual([['shadow_essence', false]]);
  });
  it('emptyMusterReadsPlain', async () => { const { root } = await mount({ shadow_board: [] }, {}, 'muster'); expect(root.querySelector('.empty').textContent).toBe(t('line_no_veil_echoes')); });
});

describe('one tab row', () => {
  it('everyBoardHasOnlyTheBoardKindAsTabsAndLabelledSelectsBelow', async () => {
    for (const kind of ['training', 'lifts', 'muster']) {
      const { root } = await mount({ leaderboard: board, lift_board: [], shadow_board: [] }, {}, kind);
      // the only group of buttons is the board kind row, and it comes first
      expect([...root.querySelectorAll('.segmented')].map(g => g.getAttribute('aria-label'))).toEqual(['Board']);
      expect(root.firstElementChild.classList.contains('segmented')).toBe(true); expect(root.firstElementChild.nextElementSibling.classList.contains('board-filters')).toBe(true);
      // every control in the filter row is a native select with a visible label, so a screen reader and a click both land on it
      const fields = [...root.querySelectorAll('.board-filters > label')];
      expect(fields.length).toBe(kind === 'training' ? 2 : kind === 'lifts' ? 3 : 1);
      for (const f of fields) { expect(f.querySelector('select')).not.toBeNull(); expect(f.querySelector('span').textContent.length).toBeGreaterThan(0); }
      expect(root.querySelectorAll('.board-filters button')).toHaveLength(0);
    }
  });
  it('theShowSelectSetsTheAudienceAndReloadsTheSameBoard', async () => {
    const setAudience = vi.fn(); const a = await mount({ leaderboard: board }, { setAudience, audience: 'allies' });
    expect(a.root.querySelector('.board-audience').value).toBe('allies'); expect([...a.root.querySelector('.board-audience').options].map(o => o.value)).toEqual(['allies', 'everyone']);
    choose(a.root.querySelector('.board-audience'), 'everyone'); expect(setAudience).toHaveBeenCalledWith('everyone'); expect(a.router.go).toHaveBeenCalledWith('/friends/boards');
    const b = await mount({ lift_board: [] }, { setAudience }, 'lifts'); choose(b.root.querySelector('.board-audience'), 'allies'); expect(b.router.go).toHaveBeenCalledWith('/friends/boards/lifts');
  });
});

describe('every board', () => {
  it('boardsReadOnlyViewsOnly', async () => {
    for (const kind of ['training', 'lifts', 'muster']) {
      const { client } = await mount({ leaderboard: board, lift_board: [], shadow_board: [] }, {}, kind);
      expect(client.calls.every(c => c.op === 'select')).toBe(true); for (const table of client.tables()) expect(['leaderboard', 'shadow_board', 'lift_board', 'friendships']).toContain(table);
    }
  });
  it('theBoardSwitchGoesThroughTheRouter', async () => {
    const { root, router } = await mount({ leaderboard: board }); click(root.querySelector('[aria-label="Board"] [data-value="lifts"]')); expect(router.go).toHaveBeenCalledWith('/friends/boards/lifts');
  });
  it('anUnknownKindFallsBackToTraining', async () => { const { root } = await mount({ leaderboard: board }, {}, 'nonsense'); expect(root.querySelector('.podium')).not.toBeNull(); });
});
