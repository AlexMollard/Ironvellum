import { describe, it, expect, vi, afterEach } from 'vitest';
import parity from './fixtures/parity.json';
import { renderAllies } from '../src/screens/friends/allies.js';
import { createSupabaseSocial } from '../src/data/socialSource.js';
import { lifterSummary } from '../src/lifterSummary.js';
import { summaryLine } from '../src/components/folio.js';
import { displayDate } from '../src/components/statCard.js';
import { t, format, word, setWordsMode } from '../src/i18n.js';
import { fakeClient } from './helpers/fakeClient.js';

afterEach(() => { setWordsMode(false); document.body.innerHTML = ''; });
const NOW = Date.parse('2026-10-28T12:00:00Z');
const wait = () => new Promise(resolve => setTimeout(resolve, 0));
const settle = async () => { for (let i = 0; i < 6; i++) await wait(); };
const click = el => el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
const submit = form => form.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
const person = (id, name) => ({ id, display_name: name, level: 12, current_title_id: null, current_crest_id: null, total_xp: 1200, streak_days: 3, titles_count: 2, lifetime_strength: 900 });
const sessionRow = (i, over = {}) => ({ id: `s${i}`, label: 'Pull', title: `Day ${i}`, note: '', completed_at: new Date(NOW - i * 86400000).toISOString(), xp_awarded: 100, strength_score: 500 + i, edited_at: null, session_sets: [{ count: 10 }], ...over });
const mount = async (script, arg) => {
  const client = fakeClient(script), social = createSupabaseSocial(client, 'me'), root = document.createElement('main'), router = { go: vi.fn() };
  document.body.append(root); await renderAllies(root, { social, router, source: { userId: 'me' }, snapshot: { unit: 'kg' }, nowMs: NOW }, arg); await settle();
  return { root, client, router, social };
};
const base = (extra = {}) => ({ friendships: [], profiles: [], blocks: [], 'rpc:my_inbox': [], ...extra });
const typeAndSend = async (root, name) => { const input = root.querySelector('.allies-add input'); input.value = name; submit(root.querySelector('.allies-add')); await settle(); };
const status = root => root.querySelector('.friends-notice').textContent;

describe('adding a friend', () => {
  it('findAllyByExactNameThenRequest', async () => {
    const { root, client } = await mount(base({ 'rpc:find_hunter': [{ id: 'u9', display_name: 'Odin Hart' }] }));
    await typeAndSend(root, '  Odin Hart ');
    const find = client.calls.find(c => c.name === 'find_hunter'); expect(find.args).toEqual({ name: 'Odin Hart' });
    const insert = client.callsTo('friendships').find(c => c.op === 'insert'); expect(insert.values).toEqual({ requester_id: 'me', addressee_id: 'u9', accepted: false });
    expect(client.calls.indexOf(find)).toBeLessThan(client.calls.indexOf(insert));
  });
  it('unknownNameReadsNotFound', async () => {
    const { root, client } = await mount(base({ 'rpc:find_hunter': [] }));
    await typeAndSend(root, 'Zed'); expect(status(root)).toBe(format(t('cloud_ally_not_found'), 'Zed')); expect(client.callsTo('friendships').some(c => c.op === 'insert')).toBe(false);
  });
  it('selfRequestReadsSelf', async () => {
    const { root, client } = await mount(base({ 'rpc:find_hunter': [{ id: 'me', display_name: 'Me' }] }));
    await typeAndSend(root, 'Me'); expect(status(root)).toBe(t('cloud_ally_self_request')); expect(client.callsTo('friendships').some(c => c.op === 'insert')).toBe(false);
  });
  it('emptyNameAsksForOne', async () => { const { root, client } = await mount(base()); await typeAndSend(root, '   '); expect(status(root)).toBe(t('cloud_ally_type_name')); expect(client.rpcs().size).toBe(0); });
  it('duplicateRequestReadsAlreadyRequested', async () => {
    const { root } = await mount(base({ 'rpc:find_hunter': [{ id: 'u9', display_name: 'Odin Hart' }], friendships: call => (call.op === 'insert' ? { code: '23505', message: 'duplicate key' } : []) }));
    await typeAndSend(root, 'Odin Hart'); expect(status(root)).toBe(format(t('cloud_ally_already_requested'), 'Odin Hart'));
  });
  it('serverLimitsSurfaceAsPlainMessages', async () => {
    for (const message of ['Too many ally requests today — try again tomorrow.', 'You cannot send a request to this lifter.']) {
      const { root } = await mount(base({ 'rpc:find_hunter': [{ id: 'u9', display_name: 'Odin Hart' }], friendships: call => (call.op === 'insert' ? { code: 'P0001', message } : []) }));
      await typeAndSend(root, 'Odin Hart');
      expect(status(root)).not.toMatch(/P0001|ally requests today/); expect(status(root).length).toBeGreaterThan(10);
      expect(root.querySelector('.allies-add input').value).toBe('Odin Hart');
    }
  });
});

describe('the list', () => {
  const rows = [
    { requester_id: 'me', addressee_id: 'u1', accepted: true, accepted_at: '2026-03-14T00:00:00Z' },
    { requester_id: 'u2', addressee_id: 'me', accepted: false, accepted_at: null },
    { requester_id: 'me', addressee_id: 'u3', accepted: false, accepted_at: null },
  ];
  it('pendingAndAcceptedAreSeparated', async () => {
    const { root } = await mount(base({ friendships: rows, profiles: [person('u1', 'Mara Quill'), person('u3', 'Odin Hart')], 'rpc:my_inbox': [{ kind: 'request', actor_id: 'u2', actor_name: 'Ines Vale' }] }));
    expect([...root.querySelectorAll('.allies-rows > .person-row')].map(r => r.dataset.user)).toEqual(['u1']);
    expect([...root.querySelectorAll('.allies-pending .person-row')].map(r => r.dataset.user)).toEqual(['u3']);
    expect(root.querySelector('.allies-requests a').getAttribute('href')).toBe('#/friends/inbox'); expect(root.querySelector('.allies-requests').textContent).toBe('1 friend request');
    expect(root.querySelector('.allies-rows').textContent).toContain(format(t('acct_allies_header'), 1));
  });
  it('hiddenProfileUsesNeutralName', async () => {
    const { root } = await mount(base({ friendships: [{ requester_id: 'me', addressee_id: 'u1', accepted: true }] }));
    expect(root.querySelector('.allies-rows .person-row').textContent).toContain(t('cloud_hidden_lifter'));
  });
  it('anIncomingRequestBorrowsTheInboxName', async () => {
    const { root } = await mount(base({ friendships: [rows[1]], 'rpc:my_inbox': [{ kind: 'request', actor_id: 'u2', actor_name: 'Ines Vale' }] }));
    expect(root.textContent).toContain('1 friend request'); expect(root.querySelector('.allies-pending')).toBeNull();
  });
  it('openingAnAllyGoesToTheirFolio', async () => {
    const { root, router } = await mount(base({ friendships: rows, profiles: [person('u1', 'Mara Quill')] }));
    click(root.querySelector('.allies-rows .person-name')); expect(router.go).toHaveBeenCalledWith('/friends/allies/u1');
  });
  it('blockedListOffersUnblock', async () => {
    const { root, client } = await mount(base({ blocks: [{ blocked_id: 'ux', blocked_name: 'Rook Danner' }] }));
    expect(root.querySelector('.allies-blocked').textContent).toContain('Rook Danner');
    click(root.querySelector('.blocked-row button')); await settle();
    const call = client.callsTo('blocks').find(c => c.op === 'delete'); expect(call.filters).toEqual([['eq', 'blocker_id', 'me'], ['eq', 'blocked_id', 'ux']]);
  });
  it('emptyBlockedListReadsPlain', async () => { const { root } = await mount(base()); expect(root.querySelector('.allies-blocked').textContent).toContain(t('acct_blocked_empty')); });
});

describe('the folio figures', () => {
  it('folioFiguresMatchPhoneParity', () => {
    expect(parity.lifterSummaryCases.length).toBeGreaterThanOrEqual(4);
    for (const c of parity.lifterSummaryCases) {
      const f = lifterSummary(c.sessions, c.nowMs);
      expect({ count: f.count, xp: f.xp, perWeek: f.perWeek, daysSince: f.daysSince, series: f.series, peak: f.peak }).toEqual({ count: c.count, xp: c.xp, perWeek: c.perWeek, daysSince: c.daysSince, series: c.series, peak: c.peak });
    }
  });
  it('summaryLineUsesThePhonesWording', () => {
    expect(summaryLine({ count: 1, xp: 12400, perWeek: '0.3', daysSince: 0 })).toBe(`1 workout shared · ${format(t('lifter_xp_gain'), '12,400')} · ${format(t('lifter_summary_per_week'), '0.3')} · ${t('lifter_summary_last_today')}`);
    expect(summaryLine({ count: 3, xp: 5, perWeek: '1.0', daysSince: 1 })).toContain(t('lifter_summary_last_yesterday'));
    expect(summaryLine({ count: 3, xp: 5, perWeek: '1.0', daysSince: 9 })).toContain('last workout 9 days ago');
    setWordsMode(true); expect(summaryLine({ count: 3, xp: 5, perWeek: '1.0', daysSince: 9 })).toContain('last trial 9 days ago');
  });
  const folio = (extra = {}) => base({ friendships: [{ requester_id: 'me', addressee_id: 'u1', accepted: true, accepted_at: '2026-03-14T00:00:00Z' }], profiles: [person('u1', 'Mara Quill')],
    sessions: { data: Array.from({ length: 20 }, (_, i) => sessionRow(i + 1)), count: 214 }, lift_board: [{ user_id: 'u1', lift: 'squat', step: 7, recent_step: 6 }, { user_id: 'u1', lift: 'muscle_up', step: 2, recent_step: null }, { user_id: 'u1', lift: 'mystery', step: 4 }], mutes: [], ...extra });
  it('workoutsSharedUsesTheServerCount', async () => {
    const { root, client } = await mount(folio(), 'u1');
    expect(root.querySelector('.folio-summary').textContent).toContain('214 workouts shared');
    const count = client.callsTo('sessions').find(c => c.options?.count === 'exact'); expect(count.options.head).toBe(true); expect(count.filters).toContainEqual(['eq', 'user_id', 'u1']);
  });
  it('allySinceReadsAcceptedAt', async () => {
    const { root } = await mount(folio(), 'u1'); expect(root.querySelector('.folio-since').textContent).toBe(`${word('Friends', 'concept_ally_cpl')} since ${displayDate(Date.parse('2026-03-14T00:00:00Z'), 'UTC')}`);
  });
  it('folioReadsNoOwnerOnlyTable', async () => {
    const { client } = await mount(folio(), 'u1');
    for (const table of client.tables()) expect(['profiles', 'sessions', 'lift_board', 'friendships', 'mutes', 'blocks']).toContain(table);
    expect(client.tables().has('web_snapshots')).toBe(false);
  });
  it('strengthLineShowsItsPeak', async () => {
    const { root } = await mount(folio(), 'u1'); expect(root.querySelector('.folio-strength .chip').textContent).toBe(format(t('lifter_strength_peak'), 520));
    expect(root.querySelector('.folio-strength svg')).not.toBeNull();
  });
  it('oneWorkoutSaysTheLineBeginsLater', async () => {
    const { root } = await mount(folio({ sessions: { data: [sessionRow(1)], count: 1 } }), 'u1'); expect(root.querySelector('.folio-strength').textContent).toContain(t('lifter_strength_single'));
  });
  it('noWorkoutsReadsNothingShared', async () => { const { root } = await mount(folio({ sessions: { data: [], count: 0 } }), 'u1'); expect(root.querySelector('.folio-empty').textContent).toBe(t('lifter_nothing_shared')); });
  it('liftBoardTiersUseCatalogueNames', async () => {
    const { root } = await mount(folio(), 'u1');
    const squat = root.querySelector('[data-lift="squat"]'); expect(squat.textContent).toContain('Gold I'); expect(squat.textContent).toContain('tier 7 of 10');
    const ladder = root.querySelector('[data-lift="muscle_up"]'); expect(ladder.textContent).toContain(`${word('tier', 'concept_rung_s')} 2`);
    expect(root.querySelector('[data-lift="mystery"]')).toBeNull();
  });
  it('recentWorkoutOpensItsSetsReadOnly', async () => {
    const { root, client } = await mount(folio({ session_sets: [{ session_id: 's1', exercise_name: 'Back Squat', exercise_position: 0, set_index: 0, reps: 5, weight_kg: 100, done: true }] }), 'u1');
    click(root.querySelector('.folio-open')); await settle();
    expect(root.querySelector('.folio-sets').textContent).toContain('Back Squat'); expect(root.querySelector('.folio-sets').textContent).toContain('100 kg');
    expect(client.callsTo('session_sets').every(c => c.op === 'select')).toBe(true);
  });
  it('anInvisibleWorkoutReadsPlain', async () => {
    const { root } = await mount(folio({ sessions: call => (call.options?.count ? { data: [], count: 1 } : call.single ? null : [sessionRow(1)]) }), 'u1');
    click(root.querySelector('.folio-open')); await settle(); expect(root.querySelector('.folio-sets').textContent).toBe(t('cloud_sync_trial_hidden'));
  });
  it('lifterWorkoutOrdersExercisesLikeTheApp', async () => {
    const set = (name, position, index) => ({ exercise_name: name, exercise_position: position, set_index: index, reps: 5, weight_kg: 50, done: true });
    const { social } = await mount({ sessions: { id: 's1', user_id: 'u1', label: 'Pull', title: '', completed_at: '2026-10-01T00:00:00Z' }, session_sets: [set('B', 1, 1), set('B', 1, 0), set('A', null, 0), set('C', 0, 0), set('Z', 1, 0)] });
    const workout = await social.lifterWorkout('s1'); expect(workout.exercises.map(e => e.name)).toEqual(['C', 'B', 'Z', 'A']); expect(workout.headline).toBe('Pull');
    expect(workout.exercises[1].sets.map(s => s.setIndex)).toEqual([0, 1]);
  });
});

describe('manage', () => {
  const folio = (extra = {}) => base({ friendships: [{ requester_id: 'me', addressee_id: 'u1', accepted: true, accepted_at: '2026-03-14T00:00:00Z' }], profiles: [person('u1', 'Mara Quill')], sessions: { data: [], count: 0 }, lift_board: [], mutes: [], ...extra });
  const button = (root, label) => [...root.querySelectorAll('.folio-manage .panel-actions > button')].find(b => b.textContent === label);
  it('muteToggleInsertsAndDeletes', async () => {
    const { root, client } = await mount(folio(), 'u1'); const box = root.querySelector('.folio-mute input');
    box.checked = true; box.dispatchEvent(new Event('change')); await settle();
    expect(client.callsTo('mutes').find(c => c.op === 'insert').values).toEqual({ muter_id: 'me', muted_id: 'u1' }); expect(root.querySelector('.folio-status').textContent).toBe(t('lifter_muted'));
    box.checked = false; box.dispatchEvent(new Event('change')); await settle();
    const del = client.callsTo('mutes').find(c => c.op === 'delete'); expect(del.filters).toEqual([['eq', 'muter_id', 'me'], ['eq', 'muted_id', 'u1']]); expect(root.querySelector('.folio-status').textContent).toBe(t('lifter_unmuted'));
  });
  it('aMutedLifterShowsTheSwitchOn', async () => { const { root } = await mount(folio({ mutes: [{ muted_id: 'u1' }] }), 'u1'); expect(root.querySelector('.folio-mute input').checked).toBe(true); });
  it('removeAllyConfirmsThenDeletes', async () => {
    const { root, client, router } = await mount(folio(), 'u1');
    click(button(root, t('lifter_remove_ally_label'))); expect(root.querySelector('.confirm-panel h3').textContent).toBe(format(t('lifter_remove_title'), 'Mara Quill'));
    expect(client.callsTo('friendships').some(c => c.op === 'delete')).toBe(false);
    click(root.querySelector('.confirm-panel .primary-small')); await settle();
    expect(client.callsTo('friendships').some(c => c.op === 'delete')).toBe(true); expect(router.go).toHaveBeenCalledWith('/friends/allies');
  });
  it('keepLeavesTheAllianceAlone', async () => {
    const { root, client } = await mount(folio(), 'u1'); click(button(root, t('lifter_remove_ally_label'))); click(root.querySelector('.confirm-panel .panel-actions button:last-child'));
    expect(root.querySelector('.confirm-panel')).toBeNull(); expect(client.callsTo('friendships').some(c => c.op === 'delete')).toBe(false);
  });
  it('blockConfirmsThenInserts', async () => {
    const { root, client, router } = await mount(folio(), 'u1');
    click(button(root, t('lifter_block_label'))); expect(root.querySelector('.confirm-panel').textContent).toContain(format(t('lifter_block_body'), 'Mara Quill'));
    expect(client.callsTo('blocks').some(c => c.op === 'insert')).toBe(false);
    click(root.querySelector('.confirm-panel .primary-small')); await settle();
    const insert = client.callsTo('blocks').find(c => c.op === 'insert'); expect(Object.keys(insert.values).sort()).toEqual(['blocked_id', 'blocker_id']); expect(insert.values).toEqual({ blocker_id: 'me', blocked_id: 'u1' });
    expect(router.go).toHaveBeenCalledWith('/friends/allies');
  });
  it('blockedLifterLeavesTheList', async () => {
    const { root } = await mount(base({ friendships: [], blocks: [{ blocked_id: 'u1', blocked_name: 'Mara Quill' }] }));
    expect(root.querySelector('.allies-rows .person-row')).toBeNull(); expect(root.querySelector('.allies-blocked').textContent).toContain('Mara Quill');
  });
  it('reportSendsReasonNoteAndNeverReadsBack', async () => {
    const { root, client } = await mount(folio(), 'u1');
    click(button(root, t('lifter_report_label'))); const form = root.querySelector('.reason-dialog'); form.querySelector('input').value = ' rude '; click(form.querySelector('[data-reason="abuse"]')); submit(form); await settle();
    const insert = client.callsTo('reports').find(c => c.op === 'insert'); expect(insert.values).toEqual({ reporter_id: 'me', target_user_id: 'u1', reason: 'abuse', note: 'rude' });
    expect(client.callsTo('reports').every(c => c.op === 'insert' && c.returning === undefined)).toBe(true); expect(root.querySelector('.folio-status').textContent).toBe(t('lifter_report_sent'));
  });
  it('reportDailyLimitReadsPlain', async () => {
    const { root } = await mount(folio({ reports: { code: 'P0001', message: 'Too many reports today — try again tomorrow.' } }), 'u1');
    click(button(root, t('lifter_report_label'))); const form = root.querySelector('.reason-dialog'); submit(form); await settle();
    expect(form.querySelector('.form-status').textContent).toBe('Too many reports today — try again tomorrow.'); expect(root.querySelector('.reason-dialog')).not.toBeNull();
  });
  it('yourOwnFolioHasNoManagePanel', async () => { const { root } = await mount(folio(), 'me'); expect(root.querySelector('.folio-manage')).toBeNull(); });
  it('aFolioThatWillNotOpenSaysWhy', async () => {
    const { root } = await mount(folio({ profiles: call => (call.single ? { code: 'PGRST301', message: 'x' } : [person('u1', 'Mara Quill')]) }), 'u1'); expect(root.querySelector('.allies-folio').textContent).not.toBe('');
  });
});
