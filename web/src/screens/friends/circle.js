import { node, displayDate } from '../../components/statCard.js';
import { personRow } from '../../components/personRow.js';
import { confirmPanel } from '../../components/reasonDialog.js';
import { hiddenName } from '../../components/hiddenName.js';
import { explain } from '../../explain.js';
import { t, format, word } from '../../i18n.js';

export const MAX_MEMBERS = 8, NAME_MAX = 24, GOAL_RANGE = [1, 7];
// The phone's invite alphabet (no 0, 1, I, L or O) and length.
const CODE_SHAPE = /^[2-9A-HJKMNP-Z]{8}$/;
const JOIN_REFUSALS = { no_such_code: 'circles_join_no_such_code', full: 'circles_join_full', closed: 'circles_join_closed', throttled: 'circles_join_throttled' };

/** The sentence for a join status other than `joined`, through the phone's own strings. */
export const joinRefusal = status => (status === 'joined' ? null : t(JOIN_REFUSALS[status] ?? 'circles_join_failed'));

/** When the week turns over: the next Monday 00:00 UTC (weeks run Monday to Sunday, UTC, as the server counts them). */
export function resetLabel(nowMs) {
  const d = new Date(nowMs), toMonday = (8 - (d.getUTCDay() || 7)) % 7 || 7;
  const next = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + toMonday));
  return `Mon ${next.getUTCDate()} ${next.toLocaleString('en-GB', { month: 'short', timeZone: 'UTC' })} 00:00 UTC`;
}

const checkboxes = (done, of) => { const bar = node('span', null, 'day-boxes'); bar.setAttribute('role', 'img'); bar.setAttribute('aria-label', format(t('circle_member_days'), done, of)); for (let i = 0; i < of; i++) bar.append(node('span', null, i < done ? 'day-box is-on' : 'day-box')); return bar; };

/**
 * The circle: the caller's group from `my_circle()` (none: join by code or form one), the week's progress and roster, the invite code with
 * Copy, the Keeper's panel (goal from next week, rename, new code, remove a member) and Leave for everyone. The server is the only rule:
 * the local checks (code shape, name length, goal range) only spare a round trip, and the server's own sentences are shown as given.
 */
export async function renderCircle(root, ctx) {
  const { social, source, nowMs = Date.now() } = ctx, me = source.userId;
  const body = node('div', null, 'circle'), status = node('p', null, 'notice friends-notice'); status.setAttribute('role', 'status');
  root.append(body, status);
  const say = text => { status.textContent = text; };
  const guarded = async (run, done) => { say(''); try { await run(); if (done) say(done); await load(); return true; } catch (error) { say(explain(error)); return false; } };
  const field = (label, { max, value = '' } = {}) => { const wrap = node('label', label), input = node('input'); input.type = 'text'; input.autocomplete = 'off'; if (max) input.maxLength = max; input.value = value; wrap.append(input); return [wrap, input]; };

  const noCircle = () => {
    body.replaceChildren(node('p', t('circle_pitch'), 'muted'));
    const join = node('form', null, 'panel circle-join'), [codeLabel, code] = field(t('circle_code_field_label'), { max: 8 }), go = node('button', t('circle_join_confirm'), 'primary-small'); go.type = 'submit';
    join.append(node('h3', t('circle_join_button')), codeLabel, node('p', t('circle_join_hint'), 'muted'), go);
    join.addEventListener('submit', async event => {
      event.preventDefault(); const cleaned = code.value.trim().toUpperCase();
      if (!CODE_SHAPE.test(cleaned)) { say(t('cloud_circle_code_shape')); return; }
      say(''); go.disabled = true;
      try { const refusal = joinRefusal(await social.joinCircle(cleaned)); if (refusal) say(refusal); else await load(); } catch (error) { say(explain(error)); } finally { go.disabled = false; }
    });
    const create = node('form', null, 'panel circle-create'), [nameLabel, name] = field(t('circle_name_label'), { max: NAME_MAX }), make = node('button', t('circle_create_button'), 'primary-small'); make.type = 'submit';
    create.append(node('h3', t('circle_form_button')), nameLabel, make);
    create.addEventListener('submit', async event => {
      event.preventDefault(); const trimmed = name.value.trim();
      if (!trimmed) { say(t('cloud_circle_name_missing')); return; }
      if (trimmed.length > NAME_MAX) { say(t('cloud_circle_name_length')); return; }
      say(''); make.disabled = true;
      try { await social.createCircle(trimmed); await load(); } catch (error) { say(explain(error)); } finally { make.disabled = false; }
    });
    body.append(join, create);
  };

  const withCircle = (circle, bonuses) => {
    const isKeeper = circle.ownerId === me, hasGoal = circle.goal > 0, met = hasGoal && circle.total >= circle.goal;
    body.replaceChildren();
    const head = node('section', null, 'panel circle-head'), title = node('div', null, 'circle-title');
    title.append(node('h2', circle.name), node('span', met ? t('circle_goal_met') : hasGoal ? format(t('circle_progress_week'), circle.total, circle.goal) : t('circle_warmup_week'), met ? 'chip circle-progress is-met' : 'chip circle-progress'));
    head.append(title, node('div', `${circle.members.length} of ${MAX_MEMBERS} members`, 'muted circle-count'));
    if (hasGoal) { const rail = node('div', null, 'goal-rail'); rail.setAttribute('role', 'img'); rail.setAttribute('aria-label', `Group goal, ${circle.total} of ${circle.goal} ${circle.goal === 1 ? 'day' : 'days'}`); for (let i = 0; i < circle.goal; i++) rail.append(node('span', null, i < circle.total ? 'rail-cell is-on' : 'rail-cell')); head.append(rail); }
    const codeRow = node('div', null, 'circle-code'), copy = node('button', 'Copy'); copy.type = 'button';
    copy.addEventListener('click', async () => { try { await navigator.clipboard.writeText(circle.code); say('Code copied'); } catch { say(circle.code); } });
    codeRow.append(node('span', format(t('circle_code_label'), circle.code), 'muted'), copy); head.append(codeRow);
    const roster = node('div', null, 'circle-roster');
    for (const m of circle.members) {
      const trailing = node('span', null, 'circle-member-side');
      if (m.counts === false) trailing.append(node('span', 'counts from next Monday', 'muted'));
      else trailing.append(checkboxes(Math.min(m.daysThisWeek, circle.perMember), circle.perMember), node('span', format(t('circle_member_days'), m.daysThisWeek, circle.perMember), 'muted'));
      if (isKeeper && m.userId !== circle.ownerId) {
        const remove = node('button', t('circle_remove_button'), 'danger-small'); remove.type = 'button'; remove.setAttribute('aria-label', format(t('circle_remove_member_description'), hiddenName(m.name)));
        remove.addEventListener('click', () => panel(confirmPanel({ title: format(t('circle_remove_title'), hiddenName(m.name)), body: t('circle_remove_body'), confirmLabel: t('circle_remove_button'), cancelLabel: t('circle_keep_button'), explain,
          onConfirm: async () => { await social.kickCircleMember(m.userId); slot.replaceChildren(); await load(); }, onCancel: () => slot.replaceChildren() })));
        trailing.append(remove);
      }
      const row = personRow({ id: m.userId, name: m.name, level: m.level, titleId: m.titleId, crestId: m.crestId }, { trailing, onOpen: ctx.folioOf?.(m.userId) }); row.dataset.user = m.userId;
      row.querySelector('.person-sub').textContent = [m.userId === me ? t('circle_member_you') : '', m.userId === circle.ownerId ? word('Keeper', 'concept_keeper_c') : '', row.querySelector('.person-sub').textContent].filter(Boolean).join(' · ');
      roster.append(row);
    }
    const slot = node('div', null, 'circle-slot'), panel = el => slot.replaceChildren(el);
    head.append(roster); body.append(head);
    const manage = node('section', null, 'panel circle-manage'); manage.append(node('h3', format(t('circle_manage_title'), circle.name)));
    manage.append(node('p', [`${circle.perMember} ${circle.perMember === 1 ? 'day' : 'days'} each`, circle.pendingPerMember != null ? `${circle.pendingPerMember} from next week` : '', format(t('circle_manage_weeks_met'), circle.weeksMet), format(t('circle_manage_resets'), resetLabel(nowMs))].filter(Boolean).join(' · '), 'muted circle-facts'));
    const actions = node('div', null, 'panel-actions'), button = (label, run, cls) => { const b = node('button', label, cls); b.type = 'button'; b.addEventListener('click', run); return b; };
    if (isKeeper) {
      const goalForm = node('form', null, 'circle-goal'), [goalLabel, goal] = field(t('circle_goal_title'), { value: String(circle.pendingPerMember ?? circle.perMember) }), set = node('button', t('circle_weekly_goal_label'), 'primary-small');
      goal.inputMode = 'numeric'; set.type = 'submit'; goalForm.append(goalLabel, node('p', t('circle_goal_hint'), 'muted'), set);
      goalForm.addEventListener('submit', event => { event.preventDefault(); const n = Number(goal.value.trim()); if (!Number.isInteger(n) || n < GOAL_RANGE[0] || n > GOAL_RANGE[1]) { say(t('cloud_circle_goal_range')); return; } guarded(() => social.setCircleGoal(n)); });
      const renameForm = node('form', null, 'circle-rename'), [nameLabel, name] = field(t('circle_name_label'), { max: NAME_MAX, value: circle.name }), rename = node('button', t('circle_rename_title'), 'primary-small'); rename.type = 'submit'; renameForm.append(nameLabel, rename);
      renameForm.addEventListener('submit', event => { event.preventDefault(); const trimmed = name.value.trim(); if (!trimmed || trimmed.length > NAME_MAX) { say(t('cloud_circle_name_length')); return; } guarded(() => social.setCircleName(trimmed)); });
      manage.append(goalForm, renameForm);
      actions.append(button(t('circle_new_code_button'), () => panel(confirmPanel({ title: t('circle_rotate_title'), body: t('circle_rotate_body'), confirmLabel: t('circle_new_code_button'), cancelLabel: t('circle_keep_button'), explain,
        onConfirm: async () => { await social.rotateCircleCode(); slot.replaceChildren(); await load(); }, onCancel: () => slot.replaceChildren() }))));
    }
    const last = circle.members.length <= 1;
    actions.append(button(t('circle_leave_circle_button'), () => panel(confirmPanel({ title: format(t('circle_leave_title'), circle.name), body: last ? t('circle_leave_last_body') : t('circle_leave_body'), confirmLabel: t('circle_leave_button'), cancelLabel: t('circle_stay_button'), explain,
      onConfirm: async () => { await social.leaveCircle(); slot.replaceChildren(); await load(); }, onCancel: () => slot.replaceChildren() })), 'danger-small'));
    manage.append(actions, slot); body.append(manage);
    body.append(node('p', 'Weeks run Monday to Sunday, UTC.', 'muted circle-footer'));
    if (bonuses.length) {
      const list = node('section', null, 'circle-bonuses'); list.append(node('h3', 'Bonus weeks'));
      for (const b of bonuses) list.append(node('div', `${displayDate(`${b.week}T12:00:00Z`)} · ${b.days} ${b.days === 1 ? 'day' : 'days'}${b.circleName ? ` · ${b.circleName}` : ''}`, 'muted'));
      list.append(node('p', 'Collect it in the app.', 'muted')); body.append(list);
    }
  };

  async function load() {
    const circle = await social.circle();
    if (!circle) { noCircle(); return; }
    let bonuses = []; try { bonuses = await social.circleBonuses(); } catch { bonuses = []; }
    withCircle(circle, bonuses);
  }
  await load();
}
