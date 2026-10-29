import { word } from '../../i18n.js';
import { explain } from '../../explain.js';
import { FRIENDS_SCHEMA_VERSION } from '../../schema.js';
import { node, link } from '../../components/statCard.js';
import { NotYet } from '../../data/socialSource.js';
import { renderFeed } from './feed.js';
import { renderInbox } from './inbox.js';

/** The sub-tabs, in the order the app shows them. Each screen is `(root, ctx, arg) => Promise<void>`, added by its task. */
export const TABS = [['feed', () => 'Feed'], ['inbox', () => word('Inbox', 'concept_inbox_c')], ['allies', () => word('Friends', 'concept_ally_cpl')], ['boards', () => 'Boards'], ['circle', () => word('Group', 'concept_circle_c')]];
export const SCREENS = {};
export const friendsTitle = () => word('Friends', 'concept_ally_cpl');

/** The accepted allies' user ids out of `social.friends()` rows. */
export const allyIdsOf = friends => new Set(friends.filter(f => f.accepted).map(f => f.userId));
/** The Boards filter is client side: the same request as the app, then a filter by the ally id set (a filter by key, no extra read). */
export const filterByAllies = (rows, allyIds, idOf = row => row.id) => rows.filter(row => allyIds.has(idOf(row)));

let audience = 'everyone';
export const resetAudience = () => { audience = 'everyone'; };

function tabsNav(router, active) {
  const nav = node('nav', null, 'friends-tabs'); nav.setAttribute('aria-label', 'Friends sections');
  for (const [id, label] of TABS) {
    const a = link(label(), `/friends/${id}`, router, 'friends-tab'); a.dataset.tab = id;
    if (id === active) a.setAttribute('aria-current', 'page'); nav.append(a);
  }
  return nav;
}

/** Whether the backend can serve this area: null when it is (or its version is unknown), else the sentence to show. */
export async function friendsBlocker(source) {
  const version = await source.schemaVersion?.();
  return version != null && version < FRIENDS_SCHEMA_VERSION ? `${friendsTitle()} needs schema ${FRIENDS_SCHEMA_VERSION}; this backend reports ${version}.` : null;
}

export async function renderFriends(root, ctx, tab = 'feed', arg) {
  const header = node('div', null, 'screen-header'); header.append(node('h1', friendsTitle())); root.append(header);
  const social = ctx.social;
  if (!social) { root.append(node('p', 'Sign in to see this.', 'notice friends-notice')); return; }
  const blocker = await friendsBlocker(ctx.source);
  if (blocker) { root.append(node('p', blocker, 'notice friends-notice')); return; }
  if (!SCREENS[tab]) { root.append(tabsNav(ctx.router, 'feed'), node('p', 'Page not found.')); return; }
  root.append(tabsNav(ctx.router, tab));
  const body = node('div', null, 'friends-body');
  if (tab === 'boards') {
    const control = node('div', null, 'segmented audience'); control.setAttribute('role', 'group'); control.setAttribute('aria-label', 'Show');
    for (const [value, label] of [['allies', word('Friends', 'concept_ally_cpl')], ['everyone', 'Everyone']]) {
      const b = node('button', label); b.type = 'button'; b.dataset.audience = value; b.setAttribute('aria-pressed', String(audience === value));
      b.addEventListener('click', () => { audience = value; ctx.router.go('/friends/boards'); }); control.append(b);
    }
    root.append(control);
  }
  root.append(body);
  try {
    let allyIds;
    if (tab === 'boards' && audience === 'allies') allyIds = allyIdsOf(await social.friends());
    await SCREENS[tab](body, { ...ctx, audience, allyIds }, arg);
  } catch (error) { body.replaceChildren(node('p', explain(error), 'notice friends-notice')); }
}

SCREENS.feed = renderFeed;
SCREENS.inbox = renderInbox;
for (const [id] of TABS) SCREENS[id] ??= async () => { throw new NotYet(id); };
