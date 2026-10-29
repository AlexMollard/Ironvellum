// The social data layer (phase 2): the same tables, views and RPCs the app calls, so the server's own rules (RLS, triggers,
// limits) are the only rules. Every method names the one table, view or RPC it reads in its comment; none touches an
// owner-only table (web_snapshots, imported_*, session_set_details, cloud_archives).
//
// A method throws the raw supabase-js error; screens turn it into a sentence with explain(). Nothing here is optimistic
// beyond what the app does.

/** @typedef {{
 *  feed(o:{beforeMs?:number, limit?:number}): Promise<any[]>,                          // public_feed
 *  workoutFeedRow(sessionId:string): Promise<any|null>,                                // public_feed filtered to one session (the owner's allies line)
 *  react(sessionId:string, kind:'salute'|'iron'|'flame'|null): Promise<void>,          // session_likes
 *  reactors(sessionId:string): Promise<any[]>, comments(sessionId:string): Promise<any[]>,
 *  addComment(sessionId:string, body:string): Promise<any>, deleteComment(id:string): Promise<void>,
 *  inbox(): Promise<{items:any[], seenAtMs:number|null}>, markInboxSeen(): Promise<void>,   // my_inbox, mark_inbox_seen, inbox_seen
 *  friends(): Promise<any[]>, findHunter(name:string): Promise<{id:string,displayName:string}|null>,
 *  requestFriend(userId:string): Promise<void>, acceptFriend(userId:string): Promise<void>, removeFriend(userId:string): Promise<void>,
 *  lifter(userId:string): Promise<any>, lifterWorkout(sessionId:string): Promise<any>,
 *  leaderboard(): Promise<any[]>, liftBoard(): Promise<any[]>, shadowBoard(): Promise<any[]>,
 *  mute(userId:string): Promise<void>, unmute(userId:string): Promise<void>, block(userId:string): Promise<void>, unblock(userId:string): Promise<void>,
 *  report(r:{targetUserId:string, sessionId?:string, commentId?:string, reason:'spam'|'abuse'|'cheating'|'other', note:string}): Promise<void>,
 *  circle(): Promise<any|null>, createCircle(name:string): Promise<void>, joinCircle(code:string): Promise<'joined'|'no_such_code'|'closed'|'full'|'throttled'>,
 *  leaveCircle(): Promise<void>, setCircleGoal(n:number): Promise<void>, setCircleName(name:string): Promise<void>,
 *  rotateCircleCode(): Promise<void>, kickCircleMember(userId:string): Promise<void>, circleBonuses(): Promise<any[]> }} SocialSource */

export const METHODS = ['feed', 'workoutFeedRow', 'react', 'reactors', 'comments', 'addComment', 'deleteComment', 'inbox', 'markInboxSeen', 'friends', 'findHunter',
  'requestFriend', 'acceptFriend', 'removeFriend', 'lifter', 'lifterWorkout', 'leaderboard', 'liftBoard', 'shadowBoard', 'mute', 'unmute', 'block',
  'unblock', 'report', 'circle', 'createCircle', 'joinCircle', 'leaveCircle', 'setCircleGoal', 'setCircleName', 'rotateCircleCode',
  'kickCircleMember', 'circleBonuses'];

/** A method whose screen has not landed yet: the error maps to a plain sentence, never a stack. */
export class NotYet extends Error {
  constructor(method) { super(`${method} is not available yet`); this.name = 'NotYet'; this.method = method; }
}
const notYet = method => async () => { throw new NotYet(method); };

export const FEED_PAGE = 50;
export const COMMENT_LIMIT = 200;

/** Awaits a PostgREST query and throws its error; returns `{ data, count }`. */
export async function checked(query) {
  const { data, error, count } = await query;
  if (error) throw error;
  return { data, count };
}
export const rowsOf = async query => (await checked(query)).data ?? [];

const iso = ms => new Date(ms).toISOString();
const COMMENT_COLUMNS = 'id,session_id,user_id,author_name,body,created_at';
export const REACTIONS = ['salute', 'iron', 'flame'];

const feedRow = r => ({
  sessionId: r.session_id, userId: r.user_id, name: r.display_name, titleId: r.current_title_id, crestId: r.current_crest_id, level: r.level,
  label: r.label, title: r.title, note: r.note, completedAtMs: Date.parse(r.completed_at), durationSec: r.duration_sec, xp: r.xp_awarded,
  strength: r.strength_score, setsDone: r.sets_done, likeCount: r.like_count ?? 0, likedByMe: !!r.liked_by_me, topMovements: r.top_movements,
  bestSet: r.best_set, commentCount: r.comment_count ?? 0, reactions: r.reactions ?? {}, myReaction: REACTIONS.includes(r.my_reaction) ? r.my_reaction : null,
  editedAt: r.edited_at ?? null, card: r.feed_card ?? null,
});
const commentRow = r => ({ id: r.id, sessionId: r.session_id, userId: r.user_id, authorName: r.author_name, body: r.body, createdAtMs: Date.parse(r.created_at) });

function feedApi(client, userId) {
  return {
    // public_feed
    async feed({ beforeMs, limit = FEED_PAGE } = {}) {
      let query = client.from('public_feed').select('*').order('completed_at', { ascending: false }).limit(limit);
      if (beforeMs != null) query = query.lt('completed_at', iso(beforeMs));
      return (await rowsOf(query)).map(feedRow);
    },
    // public_feed filtered to one of the owner's own sessions: the reaction and comment counts of the allies line
    async workoutFeedRow(sessionId) {
      const { data } = await checked(client.from('public_feed').select('*').eq('session_id', sessionId).maybeSingle());
      return data ? feedRow(data) : null;
    },
    // session_likes: a merge on (session_id, user_id), so switching kind is the conflict path; null deletes my row
    async react(sessionId, kind) {
      if (kind == null) { await checked(client.from('session_likes').delete().eq('session_id', sessionId).eq('user_id', userId)); return; }
      await checked(client.from('session_likes').upsert({ session_id: sessionId, user_id: userId, kind }, { onConflict: 'session_id,user_id' }));
    },
    // session_likes embedding profiles(display_name): names without a second request; capped at 50
    async reactors(sessionId) {
      const rows = await rowsOf(client.from('session_likes').select('user_id,kind,created_at,profiles(display_name)').eq('session_id', sessionId).order('created_at', { ascending: false }).limit(50));
      return rows.map(r => ({ userId: r.user_id, name: r.profiles?.display_name ?? null, kind: REACTIONS.includes(r.kind) ? r.kind : null, atMs: Date.parse(r.created_at) }));
    },
    // session_comments, oldest first, at the server's per-workout ceiling so the thread is whole
    async comments(sessionId) {
      return (await rowsOf(client.from('session_comments').select(COMMENT_COLUMNS).eq('session_id', sessionId).order('created_at', { ascending: true }).limit(COMMENT_LIMIT))).map(commentRow);
    },
    // session_comments insert of (session_id, user_id, body) only, read back for the server's author_name and created_at
    async addComment(sessionId, body) {
      const { data } = await checked(client.from('session_comments').insert({ session_id: sessionId, user_id: userId, body }).select(COMMENT_COLUMNS).single());
      return commentRow(data);
    },
    // session_comments delete asking the rows back: RLS answers a refused delete with zero rows, not an error
    async deleteComment(id) {
      const { data } = await checked(client.from('session_comments').delete().eq('id', id).select('id'));
      if (!data?.length) { const gone = new Error('comment gone'); gone.name = 'CommentGone'; throw gone; }
    },
  };
}

function safetyApi(client, userId) {
  const plain = async query => { try { await checked(query); } catch (error) { if (error?.code !== '23505') throw error; } };
  return {
    // mutes / blocks: already muted or blocked (23505) is success
    mute: id => plain(client.from('mutes').insert({ muter_id: userId, muted_id: id })),
    unmute: async id => { await checked(client.from('mutes').delete().eq('muter_id', userId).eq('muted_id', id)); },
    block: id => plain(client.from('blocks').insert({ blocker_id: userId, blocked_id: id })),
    unblock: async id => { await checked(client.from('blocks').delete().eq('blocker_id', userId).eq('blocked_id', id)); },
    // reports: write-only, nothing is selected back (no client can read reports)
    async report({ targetUserId, sessionId, commentId, reason, note }) {
      const row = { reporter_id: userId, target_user_id: targetUserId, reason, note: String(note ?? '').trim() };
      if (sessionId) row.session_id = sessionId;
      if (commentId) row.comment_id = commentId;
      await checked(client.from('reports').insert(row));
    },
  };
}

const KNOWN_INBOX_KINDS = new Set(['request', 'accepted', 'comment', 'reply', 'reaction', 'band_join', 'circle_join', 'band_goal', 'circle_goal', 'circle_left', 'circle_keeper', 'circle_removed']);
// The wire kinds keep their original spelling ("band_*"); the circle_* spellings are accepted too, as the app does.
const INBOX_KIND = { band_join: 'circle_join', band_goal: 'circle_goal' };

/** One my_inbox() row as an item, or null for a row this viewer cannot show (unknown kind or reaction, or a comment or reaction without its workout). */
export function inboxItem(r) {
  if (!KNOWN_INBOX_KINDS.has(r.kind)) return null;
  const kind = INBOX_KIND[r.kind] ?? r.kind, at = Date.parse(r.occurred_at);
  if (!Number.isFinite(at)) return null;
  if (['comment', 'reply', 'reaction'].includes(kind) && !r.session_id) return null;
  if (['comment', 'reply'].includes(kind) && !r.comment_id) return null;
  if (kind === 'reaction' && !REACTIONS.includes(r.reaction)) return null;
  return { kind, occurredAtMs: at, actorId: r.actor_id, actorName: r.actor_name || null, sessionId: r.session_id ?? null, headline: r.session_headline ?? '', commentId: r.comment_id ?? null, body: r.body ?? '', reaction: r.reaction ?? null };
}

function inboxApi(client, userId) {
  return {
    // my_inbox() (derived on the server, never stored) and inbox_seen.seen_at: unread is the phone's own rule over these two
    async inbox() {
      const rows = (await checked(client.rpc('my_inbox'))).data ?? [];
      const seen = (await rowsOf(client.from('inbox_seen').select('seen_at').eq('user_id', userId)))[0];
      return { items: rows.map(inboxItem).filter(Boolean), seenAtMs: seen ? Date.parse(seen.seen_at) : null };
    },
    // mark_inbox_seen: no argument, so the server's clock stamps it (a phone or browser clock running behind would leave items unread)
    async markInboxSeen() { await checked(client.rpc('mark_inbox_seen')); },
    // friendships: accepting asks the updated rows back (RLS turns a refused update into zero rows, not an error)
    async acceptFriend(otherId) {
      const { data } = await checked(client.from('friendships').update({ accepted: true }).eq('requester_id', otherId).eq('addressee_id', userId).select('requester_id'));
      if (!data?.length) { const none = new Error('no pending request'); none.name = 'NoPending'; throw none; }
    },
    // friendships: ends an alliance or withdraws or declines a pending request, whichever side made the row
    async removeFriend(otherId) {
      await checked(client.from('friendships').delete().or(`and(requester_id.eq.${userId},addressee_id.eq.${otherId}),and(requester_id.eq.${otherId},addressee_id.eq.${userId})`));
    },
  };
}

/** The methods implemented so far; each screen's task adds its area here. */
function implementations(client, userId) {
  return { ...feedApi(client, userId), ...safetyApi(client, userId), ...inboxApi(client, userId) };
}

/** @returns {SocialSource} */
export function createSupabaseSocial(client, userId) {
  if (!userId) throw new Error('A signed-in user is required.');
  const impl = implementations(client, userId);
  return Object.fromEntries(METHODS.map(name => [name, impl[name] ?? notYet(name)]));
}

/** A scripted stand-in: `script[method]` answers that call, every other method throws NotYet. Calls are recorded in order. */
export function createFakeSocial(script = {}) {
  const calls = [];
  const social = Object.fromEntries(METHODS.map(name => [name, async (...args) => {
    calls.push({ method: name, args });
    if (!(name in script)) throw new NotYet(name);
    const answer = script[name];
    return typeof answer === 'function' ? answer(...args) : answer;
  }]));
  social.calls = calls;
  return social;
}
