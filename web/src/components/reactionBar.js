import { t, format } from '../i18n.js';
import { node } from './statCard.js';

const KINDS = [['salute', 'feed_reaction_honour'], ['iron', 'feed_reaction_iron'], ['flame', 'feed_reaction_flame']];

/**
 * The three reactions of a workout (the phone's Honour, Iron and Flame), each with the server's count and the viewer's own
 * highlighted. `row` is a feed row ({ reactions, myReaction }); pressing the current one clears it. `onReact(kind|null)` writes;
 * the bar only changes after the write succeeded, as the app's overlay does, and a refused write shows its sentence.
 */
export function reactionBar(row, { onReact, explain }) {
  const bar = node('div', null, 'reaction-bar'); bar.setAttribute('role', 'group'); bar.setAttribute('aria-label', 'Kudos');
  const status = node('span', null, 'reaction-status muted'); status.setAttribute('role', 'status');
  const buttons = new Map();
  const draw = () => {
    for (const [kind, button] of buttons) {
      const mine = row.myReaction === kind;
      button.setAttribute('aria-pressed', String(mine)); button.classList.toggle('is-mine', mine);
      const name = t(KINDS.find(k => k[0] === kind)[1]);
      button.textContent = format(t('feed_reaction_option_count'), name, row.reactions[kind] ?? 0);
      button.title = mine ? format(t('feed_reaction_mine_description'), name) : `${t('feed_reaction_pay_description')}: ${name}`;
    }
  };
  for (const [kind] of KINDS) {
    const button = node('button', null, 'reaction'); button.type = 'button'; button.dataset.kind = kind; buttons.set(kind, button);
    button.addEventListener('click', async () => {
      const next = row.myReaction === kind ? null : kind; button.disabled = true; status.textContent = '';
      try {
        await onReact(next);
        const counts = { ...row.reactions };
        if (row.myReaction) counts[row.myReaction] = Math.max(0, (counts[row.myReaction] ?? 0) - 1);
        if (next) counts[next] = (counts[next] ?? 0) + 1;
        row.reactions = counts; row.myReaction = next; draw();
      } catch (error) { status.textContent = explain(error); }
      finally { button.disabled = false; }
    });
    bar.append(button);
  }
  bar.append(status); draw();
  return bar;
}
