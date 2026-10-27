import { SNAPSHOT_V } from './schema.js';

export class SnapshotTooNew extends Error {
  constructor(version) { super(`This snapshot needs a newer web viewer (version ${version}).`); this.name = 'SnapshotTooNew'; this.version = version; }
}
const workout = (r, origin) => ({localId:r.local_id, origin, id:r.id, userId:r.user_id, label:r.label, title:r.title, note:r.note, startedAt:r.started_at, completedAt:r.completed_at, editedAt:r.edited_at, xp:r.xp_awarded ?? 0});
const set = r => ({exerciseName:r.exercise_name, exercisePosition:r.exercise_position, setIndex:r.set_index, reps:r.reps, weightKg:r.weight_kg, modifiers:r.modifiers, done:r.done, durationSec:r.duration_sec, distanceM:r.distance_m, grade:r.grade, kind:r.kind ?? null, rpe:r.rpe ?? null, supersetGroup:r.superset_group ?? null});
async function checked(query) { const {data,error} = await query; if (error) throw error; return data; }
async function pages(client, table, columns, userId, sessionId) {
  const all = [];
  for (let start = 0;; start += 1000) {
    let q = client.from(table).select(table === 'session_sets' ? `${columns},sessions!inner(user_id)` : columns).eq(table === 'session_sets' ? 'sessions.user_id' : 'user_id', userId);
    if (sessionId != null) q = q.eq('session_id', sessionId);
    q = q.order(sessionId != null ? 'set_index' : 'local_id', {ascending:true}).range(start, start + 999);
    const rows = await checked(q); all.push(...rows);
    if (rows.length < 1000) return all;
  }
}
export function createSupabaseSource(client, userId) {
  if (!userId) throw new Error('A signed-in user is required.');
  let ownedIds, version;
  return {
    userId,
    async schemaVersion() { const {data,error} = await client.rpc('schema_version'); version = error ? null : data; return version; },
    async listWorkouts() {
      const cols = 'id,user_id,local_id,label,title,note,started_at,completed_at,edited_at';
      const [synced, imported] = await Promise.all([pages(client,'sessions',`${cols},xp_awarded`,userId), version != null && version < 30 ? Promise.resolve([]) : pages(client,'imported_sessions',cols,userId)]);
      ownedIds = new Set([...synced.map(r => `synced:${r.id}`), ...imported.map(r => `imported:${r.id}`)]);
      return [...synced.map(r => workout(r,'synced')), ...imported.map(r => workout(r,'imported'))].sort((a,b) => (b.startedAt || '').localeCompare(a.startedAt || ''));
    },
    async getSets(w) {
      if (!ownedIds) await this.listWorkouts();
      if (!ownedIds.has(`${w.origin}:${w.id}`)) return [];
      const table = w.origin === 'imported' ? 'imported_session_sets' : 'session_sets';
      const rows = await pages(client,table,'*',userId,w.id);
      if (w.origin === 'imported') return rows.map(set);
      const details = version != null && version < 30 ? [] : await pages(client,'session_set_details','*',userId,w.id);
      const lookup = new Map(details.map(d => [JSON.stringify([d.exercise_name,d.set_index]),d]));
      return rows.map(r => set({...r,...lookup.get(JSON.stringify([r.exercise_name,r.set_index]))}));
    },
    async getSnapshot() {
      if (version != null && version < 30) return null;
      const row = await checked(client.from('web_snapshots').select('snapshot,version').eq('user_id',userId).maybeSingle());
      if (!row) return null;
      if (row.snapshot?.v > SNAPSHOT_V || row.version > SNAPSHOT_V) throw new SnapshotTooNew(row.snapshot?.v ?? row.version);
      return row.snapshot;
    }
  };
}
export function createFixtureSource(fixture) {
  const snapshot = fixture.snapshot ?? fixture;
  const rows = fixture.rows ?? {workouts:fixture.workouts ?? [],sets:fixture.sets ?? {}};
  return {userId:'fixture', schemaVersion:async () => 30, getSnapshot:async () => snapshot, listWorkouts:async () => [...rows.workouts], getSets:async w => [...(rows.sets[`${w.origin}:${w.id}`] ?? [])]};
}
