// The social data layer (phase 2): the same tables, views and RPCs the app calls, so the server's own rules (RLS, triggers,
// limits) are the only rules. Every method names the one table, view or RPC it reads in its comment; none touches an
// owner-only table (web_snapshots, imported_*, session_set_details, cloud_archives).
//
// A method throws the raw supabase-js error; screens turn it into a sentence with explain(). Nothing here is optimistic
// beyond what the app does.

/** @typedef {{
 *  feed(o:{beforeMs?:number, limit?:number}): Promise<any[]>,                          // public_feed
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

export const METHODS = ['feed', 'react', 'reactors', 'comments', 'addComment', 'deleteComment', 'inbox', 'markInboxSeen', 'friends', 'findHunter',
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

/** The methods implemented so far; each screen's task adds its own here. */
function implementations(client, userId) {
  return {};
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
