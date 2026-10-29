import { node } from './statCard.js';
import { hiddenName } from './hiddenName.js';
import { t, format } from '../i18n.js';

const KINDS = [['salute', 'feed_reaction_honour'], ['iron', 'feed_reaction_iron'], ['flame', 'feed_reaction_flame']];

/**
 * "Honour 3 · Iron 1 · 2 comments": what friends did on the owner's own workout, from that workout's own `public_feed` row.
 * Null when there is nothing to say (no row, no reactions, no comments), so the line is simply absent. `reactors` (the
 * detail page only) adds the names.
 */
export function alliesLine(row, { reactors } = {}) {
  if (!row) return null;
  const counts = KINDS.map(([kind, key]) => [kind, key, row.reactions?.[kind] ?? 0]).filter(([, , n]) => n > 0);
  const comments = row.commentCount ?? 0;
  if (!counts.length && !comments) return null;
  const parts = counts.map(([, key, n]) => format(t('feed_reaction_option_count'), t(key), n));
  if (comments) parts.push(`${comments} ${t(comments === 1 ? 'concept_remark_s' : 'concept_remark_pl')}`);
  const el = node('p', parts.join(' · '), 'allies-line muted');
  if (reactors?.length) el.append(' · ', node('span', reactors.map(r => hiddenName(r.name)).join(', '), 'allies-names'));
  return el;
}
