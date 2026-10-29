import { t, word, format } from '../i18n.js';
import { node } from './statCard.js';
import { hiddenName } from './hiddenName.js';
import { reasonDialog, confirmPanel } from './reasonDialog.js';
import { COMMENT_LIMIT } from '../data/socialSource.js';

export const COMMENT_MAX = 280;

/** The phone's two local refusals: empty, and over the 280-character ceiling (its plural sentence, in the current names mode). */
export function commentProblem(text) {
  const body = text.trim();
  if (!body) return t('cloud_remark_empty');
  if (body.length > COMMENT_MAX) return `${word('Comments', 'concept_remark_cpl')} are at most ${COMMENT_MAX} characters`;
  return null;
}

const ago = ms => new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(ms));

/**
 * A workout's comment thread: oldest first, up to the server's 200, a composer with a live `n / 280` counter, Delete on
 * your own comments (and on every comment of your own workout), Report on other people's. `social` does the work and
 * `explain` turns anything it throws into a sentence; a refused post keeps the typed text and re-enables Post.
 */
export function commentThread(root, { social, sessionId, me, ownWorkout, explain, onCount }) {
  root.replaceChildren(); root.classList.add('comment-thread');
  const list = node('div', null, 'comment-list'), status = node('p', null, 'notice form-status'); status.setAttribute('role', 'status');
  const form = node('form', null, 'comment-form');
  const input = node('textarea'); input.rows = 2; input.setAttribute('aria-label', word('Comment', 'concept_remark_c')); input.placeholder = t('comments_empty');
  const counter = node('span', `0 / ${COMMENT_MAX}`, 'comment-counter muted');
  const post = node('button', t('comments_send_button'), 'primary-small'); post.type = 'submit';
  form.append(input, node('div', null, 'comment-form-row')); form.lastChild.append(counter, post);
  root.append(list, status, form);
  let comments = [];

  const say = text => { status.textContent = text; };
  const draw = () => {
    list.replaceChildren();
    if (!comments.length) list.append(node('p', t('comments_empty'), 'muted'));
    for (const c of comments) {
      const item = node('div', null, 'comment'); item.dataset.id = c.id;
      const head = node('div', null, 'comment-head'); head.append(node('strong', c.userId === me ? t('comments_you') : hiddenName(c.authorName)), node('span', ago(c.createdAtMs), 'muted'));
      const actions = node('span', null, 'comment-actions');
      if (c.userId === me || ownWorkout) actions.append(button(t('comments_delete_button'), () => askDelete(c, item), 'comment-delete'));
      if (c.userId !== me) actions.append(button(t('comments_report_button'), () => askReport(c, item), 'comment-report'));
      head.append(actions); item.append(head, node('p', c.body, 'comment-body')); list.append(item);
    }
    onCount?.(comments.length);
  };
  const button = (text, run, cls) => { const b = node('button', text, `link-button ${cls}`); b.type = 'button'; b.addEventListener('click', run); return b; };
  const askDelete = (c, item) => {
    item.querySelector('.panel')?.remove();
    item.append(confirmPanel({ title: t('comments_delete_title'), body: t('comments_delete_body'), confirmLabel: t('comments_delete_button'), cancelLabel: t('comments_keep_button'), explain: error => (error?.name === 'CommentGone' ? t('cloud_remark_gone') : explain(error)),
      onConfirm: async () => { await social.deleteComment(c.id); comments = comments.filter(x => x.id !== c.id); draw(); }, onCancel: () => item.querySelector('.panel')?.remove() }));
  };
  const askReport = (c, item) => {
    item.querySelector('.panel')?.remove();
    item.append(reasonDialog({ title: format(t('comments_report_title'), hiddenName(c.authorName)), explain,
      onSend: async ({ reason, note }) => { await social.report({ targetUserId: c.userId, sessionId, commentId: c.id, reason, note }); item.querySelector('.panel')?.remove(); say(t('comments_report_sent')); }, onCancel: () => item.querySelector('.panel')?.remove() }));
  };
  input.addEventListener('input', () => { const n = input.value.trim().length; counter.textContent = `${n} / ${COMMENT_MAX}`; counter.classList.toggle('is-over', n > COMMENT_MAX); });
  form.addEventListener('submit', async event => {
    event.preventDefault(); say('');
    const problem = commentProblem(input.value); if (problem) { say(problem); return; }
    post.disabled = true;
    try { const added = await social.addComment(sessionId, input.value.trim()); comments = [...comments, added]; input.value = ''; counter.textContent = `0 / ${COMMENT_MAX}`; draw(); }
    catch (error) { say(explain(error)); }
    finally { post.disabled = false; }
  });
  return social.comments(sessionId).then(rows => { comments = rows.slice(0, COMMENT_LIMIT); draw(); }, error => { say(explain(error)); draw(); });
}
