import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderCircle, joinRefusal, resetLabel } from '../src/screens/friends/circle.js';
import { createSupabaseSocial } from '../src/data/socialSource.js';
import { t, format, word, setWordsMode } from '../src/i18n.js';
import { fakeClient } from './helpers/fakeClient.js';

afterEach(() => { setWordsMode(false); document.body.innerHTML = ''; });
const NOW = Date.parse('2026-10-28T12:00:00Z');
const wait = () => new Promise(resolve => setTimeout(resolve, 0));
const settle = async () => { for (let i = 0; i < 6; i++) await wait(); };
const click = el => el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
const submit = form => form.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
const member = (id, name, days, over = {}) => ({ user_id: id, display_name: name, level: 20, current_title_id: null, current_crest_id: null, days_this_week: days, counts: true, last_workout_at: '2026-10-27T08:00:00Z', ...over });
const members = [member('me', 'Fictional lifter', 3), member('u2', 'Mara Quill', 2), member('u3', 'Jonah Reed', 3), member('u4', 'Ines Vale', 3), member('u5', 'Odin Hart', 0, { counts: false })];
const row = (over = {}) => ({ id: 'c1', name: 'Iron Tide', code: 'K7M4Q9XA', owner_id: 'u2', week: '2026-10-26', per_member: 3, pending_per_member: null, goal: 15, circle_total: 11, roster: 5, weeks_met: 4, members, ...over });
const mount = async (script, me = 'me') => {
  const client = fakeClient(script), social = createSupabaseSocial(client, me), root = document.createElement('main'); document.body.append(root);
  await renderCircle(root, { social, source: { userId: me }, nowMs: NOW }); await settle();
  return { root, client };
};
const status = root => root.querySelector('.friends-notice').textContent;
const rpcCalls = (client, name) => client.calls.filter(c => c.name === name);
const withCircle = (extra = {}, over = {}) => ({ 'rpc:my_circle': [row(over)], 'rpc:circle_bonuses': [], ...extra });
const button = (root, label) => [...root.querySelectorAll('button')].find(b => b.textContent === label);

describe('circle screen', () => {
  it('circleHeaderAndRosterFromMyCircle', async () => {
    const { root } = await mount(withCircle());
    expect(root.querySelector('.circle-title h2').textContent).toBe('Iron Tide'); expect(root.querySelector('.circle-progress').textContent).toBe(format(t('circle_progress_week'), 11, 15));
    expect(root.querySelector('.circle-count').textContent).toBe('5 of 8 members');
    const second = root.querySelector('[data-user="u2"] .day-boxes'); expect(second.querySelectorAll('.day-box')).toHaveLength(3); expect(second.querySelectorAll('.is-on')).toHaveLength(2);
    expect(root.querySelector('.circle-code').textContent).toContain(format(t('circle_code_label'), 'K7M4Q9XA'));
    expect(root.querySelector('[data-user="u2"]').textContent).toContain(word('Keeper', 'concept_keeper_c'));
  });
  it('metGoalReadsGoalMet', async () => { const { root } = await mount(withCircle({}, { circle_total: 15 })); expect(root.querySelector('.circle-progress').textContent).toBe(t('circle_goal_met')); });
  it('noGoalReadsWarmupWeek', async () => { const { root } = await mount(withCircle({}, { goal: 0, circle_total: 0 })); expect(root.querySelector('.circle-progress').textContent).toBe(t('circle_warmup_week')); });
  it('memberNotOnRosterReadsFromNextMonday', async () => {
    const { root } = await mount(withCircle()); const odin = root.querySelector('[data-user="u5"]'); expect(odin.textContent).toContain('counts from next Monday'); expect(odin.querySelector('.day-boxes')).toBeNull();
  });
  it('keeperPanelOnlyForTheKeeper', async () => {
    const { root } = await mount(withCircle()); expect(root.querySelector('.circle-goal')).toBeNull(); expect(root.querySelector('.circle-rename')).toBeNull();
    expect(button(root, t('circle_new_code_button'))).toBeUndefined(); expect(button(root, t('circle_remove_button'))).toBeUndefined(); expect(button(root, t('circle_leave_circle_button'))).toBeDefined();
    const keeper = await mount(withCircle({}, { owner_id: 'me' })); expect(keeper.root.querySelector('.circle-goal')).not.toBeNull(); expect(keeper.root.querySelector('.circle-rename')).not.toBeNull(); expect(button(keeper.root, t('circle_new_code_button'))).toBeDefined();
  });
  it('manageLineShowsPendingGoalAndWeeksMet', async () => {
    const { root } = await mount(withCircle({}, { owner_id: 'me', pending_per_member: 4 })); const facts = root.querySelector('.circle-facts').textContent;
    expect(facts).toContain('3 days each'); expect(facts).toContain('4 from next week'); expect(facts).toContain(format(t('circle_manage_weeks_met'), 4)); expect(facts).toContain(format(t('circle_manage_resets'), 'Mon 2 Nov 00:00 UTC'));
    expect(root.querySelector('.circle-footer').textContent).toContain('Monday to Sunday, UTC');
  });
  it('resetLabelIsTheNextMondayUtc', () => {
    expect(resetLabel(Date.parse('2026-10-26T00:00:00Z'))).toBe('Mon 2 Nov 00:00 UTC'); expect(resetLabel(Date.parse('2026-11-01T23:59:00Z'))).toBe('Mon 2 Nov 00:00 UTC'); expect(resetLabel(Date.parse('2026-10-28T12:00:00Z'))).toBe('Mon 2 Nov 00:00 UTC');
  });
  it('kickNeverOffersTheKeeper', async () => {
    const { root } = await mount(withCircle({}, { owner_id: 'me' }));
    expect(root.querySelector('[data-user="me"] .danger-small')).toBeNull(); expect(root.querySelectorAll('.circle-roster .danger-small')).toHaveLength(4);
  });
  it('kickConfirmsThenCallsTheRpc', async () => {
    const { root, client } = await mount(withCircle({ 'rpc:kick_circle_member': null }, { owner_id: 'me' }));
    click(root.querySelector('[data-user="u3"] .danger-small')); expect(rpcCalls(client, 'kick_circle_member')).toHaveLength(0);
    click(root.querySelector('.confirm-panel .primary-small')); await settle(); expect(rpcCalls(client, 'kick_circle_member')[0].args).toEqual({ p_user: 'u3' });
  });
  it('bonusesListIsReadOnly', async () => {
    const { root } = await mount(withCircle({ 'rpc:circle_bonuses': [{ week: '2026-10-19', days: 3, circle_name: 'Iron Tide' }] }));
    expect(root.querySelector('.circle-bonuses').textContent).toContain('2026-10-19'); expect(root.querySelector('.circle-bonuses').textContent).toContain('Collect it in the app'); expect(root.querySelector('.circle-bonuses button')).toBeNull();
  });
  it('aBonusReadFailureNeverBlanksTheCircle', async () => { const { root } = await mount(withCircle({ 'rpc:circle_bonuses': { code: 'PGRST202', message: 'x' } })); expect(root.querySelector('.circle-title h2').textContent).toBe('Iron Tide'); });
});

describe('without a circle', () => {
  const none = (extra = {}) => ({ 'rpc:my_circle': [], ...extra });
  const join = async (root, code) => { root.querySelector('.circle-join input').value = code; submit(root.querySelector('.circle-join')); await settle(); };
  const create = async (root, name) => { root.querySelector('.circle-create input').value = name; submit(root.querySelector('.circle-create')); await settle(); };
  it('offersJoinAndCreate', async () => { const { root } = await mount(none()); expect(root.querySelector('.circle-join')).not.toBeNull(); expect(root.querySelector('.circle-create')).not.toBeNull(); expect(root.textContent).toContain(t('circle_pitch')); });
  it('joinStatusesReadPlain', async () => {
    for (const [answer, key] of [['no_such_code', 'circles_join_no_such_code'], ['closed', 'circles_join_closed'], ['full', 'circles_join_full'], ['throttled', 'circles_join_throttled'], ['mystery', 'circles_join_failed']]) {
      const { root } = await mount(none({ 'rpc:join_circle': answer })); await join(root, 'K7M4Q9XA'); expect(status(root)).toBe(t(key));
    }
    expect(t('circles_join_throttled')).toContain('Too many code attempts today'); expect(joinRefusal('joined')).toBeNull();
  });
  it('joinRecordsRpcArgs', async () => {
    const { root, client } = await mount(none({ 'rpc:join_circle': 'joined' })); await join(root, '  k7m4q9xa '); expect(rpcCalls(client, 'join_circle')[0].args).toEqual({ p_code: 'K7M4Q9XA' });
  });
  it('joiningReloadsTheCircle', async () => {
    let joined = false;
    const { root, client } = await mount({ 'rpc:my_circle': () => (joined ? [row()] : []), 'rpc:join_circle': () => { joined = true; return 'joined'; }, 'rpc:circle_bonuses': [] });
    await join(root, 'K7M4Q9XA'); expect(rpcCalls(client, 'my_circle')).toHaveLength(2); expect(root.querySelector('.circle-title h2').textContent).toBe('Iron Tide');
  });
  it('aBadCodeShapeIsRefusedLocally', async () => {
    const { root, client } = await mount(none()); for (const code of ['SHORT', 'K7M4Q9X0', 'K7M4Q9XI', '']) { await join(root, code); expect(status(root)).toBe(t('cloud_circle_code_shape')); }
    expect(rpcCalls(client, 'join_circle')).toHaveLength(0);
  });
  it('createRefusesBadNamesLocally', async () => {
    const { root, client } = await mount(none()); await create(root, '   '); expect(status(root)).toBe(t('cloud_circle_name_missing'));
    await create(root, 'x'.repeat(25)); expect(status(root)).toBe(t('cloud_circle_name_length')); expect(rpcCalls(client, 'create_circle')).toHaveLength(0);
  });
  it('createCallsTheRpcWithTheTrimmedName', async () => {
    const { root, client } = await mount(none({ 'rpc:create_circle': null })); await create(root, '  Iron Tide '); expect(rpcCalls(client, 'create_circle')[0].args).toEqual({ p_name: 'Iron Tide' });
  });
  it('alreadyInACircleReadsPlain', async () => {
    const { root } = await mount(none({ 'rpc:create_circle': { code: 'P0001', message: 'You are already in a circle — leave it first.' } })); await create(root, 'Iron Tide');
    expect(status(root)).not.toMatch(/P0001/); expect(status(root)).toContain('already in a');
  });
});

describe('keeper actions', () => {
  const keeper = (extra = {}) => withCircle({ 'rpc:set_circle_goal': null, 'rpc:set_circle_name': null, 'rpc:rotate_circle_code': 'NEWCODE9', 'rpc:leave_circle': null, ...extra }, { owner_id: 'me' });
  it('goalRangeIs1To7', async () => {
    const { root, client } = await mount(keeper()); const form = root.querySelector('.circle-goal');
    form.querySelector('input').value = '8'; submit(form); await settle(); expect(status(root)).toBe(t('cloud_circle_goal_range')); expect(rpcCalls(client, 'set_circle_goal')).toHaveLength(0);
    form.querySelector('input').value = '0'; submit(form); await settle(); expect(rpcCalls(client, 'set_circle_goal')).toHaveLength(0);
    form.querySelector('input').value = '5'; submit(form); await settle(); expect(rpcCalls(client, 'set_circle_goal')[0].args).toEqual({ p_per_member: 5 });
  });
  it('keeperOnlyRefusalReadsTheSentence', async () => {
    const { root } = await mount(keeper({ 'rpc:set_circle_goal': { code: '42501', message: 'Only the Keeper can set the goal' } }));
    const form = root.querySelector('.circle-goal'); form.querySelector('input').value = '4'; submit(form); await settle(); expect(status(root)).toBe('Only the owner can set the goal');
  });
  it('rotateAndRenameCallTheRpcs', async () => {
    const { root, client } = await mount(keeper()); const form = root.querySelector('.circle-rename'); form.querySelector('input').value = ' Steel Tide '; submit(form); await settle();
    expect(rpcCalls(client, 'set_circle_name')[0].args).toEqual({ p_name: 'Steel Tide' });
    click(button(root, t('circle_new_code_button'))); expect(rpcCalls(client, 'rotate_circle_code')).toHaveLength(0); click(root.querySelector('.confirm-panel .primary-small')); await settle();
    expect(rpcCalls(client, 'rotate_circle_code')).toHaveLength(1);
  });
  it('renameRefusesBadNamesLocally', async () => {
    const { root, client } = await mount(keeper()); const form = root.querySelector('.circle-rename'); form.querySelector('input').value = ''; submit(form); await settle();
    expect(status(root)).toBe(t('cloud_circle_name_length')); expect(rpcCalls(client, 'set_circle_name')).toHaveLength(0);
  });
  it('leaveConfirmsThenCallsLeave', async () => {
    const { root, client } = await mount(withCircle({ 'rpc:leave_circle': null }));
    click(button(root, t('circle_leave_circle_button'))); expect(root.querySelector('.confirm-panel h3').textContent).toBe(format(t('circle_leave_title'), 'Iron Tide')); expect(root.querySelector('.confirm-panel').textContent).toContain(t('circle_leave_body'));
    expect(rpcCalls(client, 'leave_circle')).toHaveLength(0); click(root.querySelector('.confirm-panel .primary-small')); await settle(); expect(rpcCalls(client, 'leave_circle')).toHaveLength(1);
  });
  it('theLastMemberIsToldLeavingDeletesTheCircle', async () => {
    const { root } = await mount(withCircle({}, { members: [members[0]], owner_id: 'me' })); click(button(root, t('circle_leave_circle_button'))); expect(root.querySelector('.confirm-panel').textContent).toContain(t('circle_leave_last_body'));
  });
  it('stayLeavesTheCircleAlone', async () => {
    const { root, client } = await mount(withCircle()); click(button(root, t('circle_leave_circle_button'))); click(button(root, t('circle_stay_button'))); expect(root.querySelector('.confirm-panel')).toBeNull(); expect(rpcCalls(client, 'leave_circle')).toHaveLength(0);
  });
  it('serverLimitsSurfaceAsPlainMessages', async () => {
    const { root } = await mount(keeper({ 'rpc:set_circle_goal': { code: 'PGRST301', message: 'JWT expired' } })); const form = root.querySelector('.circle-goal'); form.querySelector('input').value = '4'; submit(form); await settle();
    expect(status(root)).not.toMatch(/JWT|PGRST/); expect(status(root).length).toBeGreaterThan(5);
  });
  it('circleScreenOnlyCallsCircleRpcs', async () => {
    const { client } = await mount(withCircle()); expect([...client.rpcs()].sort()).toEqual(['circle_bonuses', 'my_circle']); expect(client.tables().size).toBe(0);
  });
});
