import { node } from './statCard.js';
import { lastWorkoutCard } from './lastWorkoutCard.js';
import { reactionBar } from './reactionBar.js';
import { safetyMenu } from './safetyMenu.js';
import { titleName } from './personRow.js';
import { hiddenName } from './hiddenName.js';
import { formatVolume, formatDay } from '../format.js';
import { t, format } from '../i18n.js';

/** "just now", "5m ago", "3h ago", "yesterday", else the date: the phone's own wording. */
export function timeAgo(ms, nowMs = Date.now(), zone) {
  const minutes = Math.floor((nowMs - ms) / 60000);
  if (minutes < 1) return t('feed_time_just_now');
  if (minutes < 60) return format(t('feed_time_minutes_ago'), minutes);
  if (minutes < 1440) return format(t('feed_time_hours_ago'), Math.floor(minutes / 60));
  if (minutes < 2880) return t('feed_time_yesterday');
  return formatDay(new Date(ms).toISOString(), zone);
}

/**
 * One workout of the feed. The figures come from the phone's friend-safe card (`row.card`: hard sets, tonnes, PR tags,
 * muscle levels); a row without one (an older phone) shows only the server's own `best_set` and `top_movements`, and
 * nothing is computed here. Never an e1RM: the card does not carry one.
 */
export function feedCard(row, { router, snapshot, nowMs, social, explain, onComments, menu = () => [] }) {
  const unit = snapshot?.unit ?? 'kg', card = row.card && typeof row.card === 'object' ? row.card : null;
  const minutes = row.durationSec != null ? Math.max(1, Math.round(row.durationSec / 60)) : null;
  const when = timeAgo(row.completedAtMs, nowMs, snapshot?.zone);
  const stats = card ? [Number.isFinite(card.hardSets) ? `${card.hardSets} hard sets` : null, Number.isFinite(card.volumeKg) ? formatVolume(card.volumeKg, unit) : null].filter(Boolean).join(' · ')
    : [row.bestSet ? format(t('feed_best_set'), row.bestSet) : null, row.setsDone != null ? `${row.setsDone} sets` : null].filter(Boolean).join(' · ');
  const el = lastWorkoutCard({
    heading: hiddenName(row.name), profile: { name: hiddenName(row.name), crestId: row.crestId }, when: row.editedAt ? format(t('feed_stamp_amended'), when) : when,
    meta: [titleName(row.titleId), row.level != null ? `Level ${row.level}` : '', minutes != null ? `${minutes} min` : ''].filter(Boolean).join(' · '),
    xp: row.xp, title: row.title || row.label, stats, router, unit,
    prs: card?.prs ?? [], chips: card ? [] : String(row.topMovements ?? '').split(' · ').filter(Boolean),
    levels: card?.levels, names: snapshot?.muscles, figure: snapshot?.profile?.figure,
  });
  el.classList.add('feed-card'); el.dataset.session = row.sessionId; el.dataset.user = row.userId;
  const foot = node('div', null, 'feed-foot');
  foot.append(reactionBar(row, { onReact: kind => social.react(row.sessionId, kind), explain }));
  const comments = node('button', `${row.commentCount} ${t(row.commentCount === 1 ? 'concept_remark_s' : 'concept_remark_pl')}`, 'link-button comment-toggle'); comments.type = 'button';
  comments.addEventListener('click', () => onComments(row, el, comments));
  foot.append(comments);
  const items = menu(row, el);
  if (items.length) foot.append(safetyMenu(items, 'More'));
  el.append(foot);
  return el;
}
