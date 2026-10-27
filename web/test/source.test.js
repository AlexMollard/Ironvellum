import { describe, it, expect } from 'vitest';
import { createSupabaseSource, createFixtureSource, SnapshotTooNew, snapshotForWorkout } from '../src/source.js';
import { NEEDED_SCHEMA_VERSION } from '../src/schema.js';

function fake(tables, errors = {}) {
  const calls = [];
  const matches = (r, filters) => filters.every(([key, value]) => key === 'sessions.user_id'
    ? (tables.sessions ?? []).some(s => s.id === r.session_id && s.user_id === value)
    : r[key] === value);
  return {
    calls,
    from(table) {
      const q = {
        filters: [], orders: [], start: 0, end: 999,
        select() { return this; },
        eq(key, value) { this.filters.push([key, value]); return this; },
        order(key) { this.orders.push(key); return this; },
        range(start, end) { this.start = start; this.end = end; return this; },
        result(single = false) {
          calls.push({ table, filters: [...this.filters], orders: [...this.orders] });
          if (errors[table]) return { data: null, error: errors[table] };
          const rows = (tables[table] ?? []).filter(r => matches(r, this.filters));
          rows.sort((a, b) => {
            for (const key of this.orders) {
              const x = a[key], y = b[key];
              if (x == null && y != null) return 1;
              if (x != null && y == null) return -1;
              if (x < y) return -1;
              if (x > y) return 1;
            }
            return 0;
          });
          return { data: single ? rows[0] ?? null : rows.slice(this.start, this.end + 1), error: null };
        },
        maybeSingle() { return Promise.resolve(this.result(true)); },
        then(resolve, reject) { return Promise.resolve(this.result()).then(resolve, reject); },
      };
      return q;
    },
    rpc: async () => ({ data: 30, error: null }),
  };
}

describe('owner source', () => {
  it('pages workouts and sets with deterministic tie breakers and owner filters', async () => {
    const sessions = Array.from({ length: 2501 }, (_, i) => ({ id: `s${i}`, user_id: 'me', local_id: i, started_at: '2026-01-01' }));
    sessions.push({ id: 'foreign', user_id: 'other', local_id: 9999 });
    const sets = Array.from({ length: 1200 }, (_, i) => ({ id: `set${i}`, session_id: 's0', exercise_name: `Lift ${String(i).padStart(4, '0')}`, set_index: 0, reps: i } )).reverse();
    const details = sets.map(s => ({ user_id: 'me', session_id: 's0', exercise_name: s.exercise_name, set_index: 0, kind: 'top' })).reverse();
    const client = fake({ sessions, session_sets: sets, session_set_details: details });
    const source = createSupabaseSource(client, 'me');
    const rows = await source.listWorkouts();
    expect(rows).toHaveLength(2501);
    const result = await source.getSets(rows.find(r => r.id === 's0'));
    expect(result).toHaveLength(1200);
    expect(new Set(result.map(s => s.exerciseName)).size).toBe(1200);
    expect(result[0]).toMatchObject({ exerciseName: 'Lift 0000', reps: 0, kind: 'top' });
    expect(result[1199]).toMatchObject({ exerciseName: 'Lift 1199', reps: 1199, kind: 'top' });
    expect(client.calls.every(c => c.filters.some(([k, v]) => (k === 'user_id' || k === 'sessions.user_id') && v === 'me'))).toBe(true);
    expect(client.calls.find(c => c.table === 'session_sets').orders).toEqual(['set_index', 'exercise_name', 'id']);
    expect(client.calls.find(c => c.table === 'session_set_details').orders).toEqual(['set_index', 'exercise_name']);
  });

  it('rechecks ownership after the list changes and accepts newly added owned workouts', async () => {
    const tables = { sessions: [{ id: 'old', user_id: 'me', local_id: 1 }], session_sets: [{ id: 'a', session_id: 'old', exercise_name: 'A', set_index: 0, reps: 1 }] };
    const source = createSupabaseSource(fake(tables), 'me');
    const [old] = await source.listWorkouts();
    tables.sessions[0].user_id = 'other';
    expect(await source.getSets(old)).toEqual([]);
    tables.sessions.push({ id: 'new', user_id: 'me', local_id: 2 });
    tables.session_sets.push({ id: 'b', session_id: 'new', exercise_name: 'B', set_index: 0, reps: 2 });
    expect(await source.getSets({ origin: 'synced', id: 'new' })).toMatchObject([{ exerciseName: 'B', reps: 2 }]);
    expect(await source.getSets({ origin: 'other', id: 'new' })).toEqual([]);
  });

  it('keeps imported and synced IDs distinct and normalizes imported sets', async () => {
    const client = fake({
      sessions: [{ id: 's', user_id: 'me', local_id: 7 }],
      imported_sessions: [{ id: 'i', user_id: 'me', local_id: 7 }],
      imported_session_sets: [{ id: 'is', user_id: 'me', session_id: 'i', exercise_name: 'Climb', exercise_position: 2, set_index: 0, grade: 'V4', kind: 'working', rpe: 8 }],
    });
    const source = createSupabaseSource(client, 'me');
    const rows = await source.listWorkouts();
    expect(rows.map(r => [r.origin, r.id, r.localId])).toEqual([['synced', 's', 7], ['imported', 'i', 7]]);
    expect(await source.getSets(rows[1])).toMatchObject([{ exerciseName: 'Climb', exercisePosition: 2, grade: 'V4', kind: 'working', rpe: 8 }]);
  });

  it('distinguishes missing and unsupported snapshots, including row version', async () => {
    expect(await createSupabaseSource(fake({ web_snapshots: [] }), 'me').getSnapshot()).toBeNull();
    const source = createSupabaseSource(fake({ web_snapshots: [{ user_id: 'me', version: 2, snapshot: { v: 1 } }] }), 'me');
    await expect(source.getSnapshot()).rejects.toMatchObject({ name: 'SnapshotTooNew', version: 2 });
    expect(SnapshotTooNew.prototype).toBeInstanceOf(Error);
  });

  it('propagates query errors rather than returning empty data', async () => {
    const failure = new Error('query failed');
    await expect(createSupabaseSource(fake({}, { sessions: failure }), 'me').listWorkouts()).rejects.toBe(failure);
    await expect(createSupabaseSource(fake({}, { web_snapshots: failure }), 'me').getSnapshot()).rejects.toBe(failure);
    const client = fake({ sessions: [{ id: 's', user_id: 'me', local_id: 1 }] }, { session_sets: failure });
    await expect(createSupabaseSource(client, 'me').getSets({ origin: 'synced', id: 's' })).rejects.toBe(failure);
  });

  it('reads schema 30 data even though full snapshot readiness requires schema 31', async () => {
    const source = createSupabaseSource(fake({
      imported_sessions: [{ id: 'i', user_id: 'me', local_id: 1 }],
      web_snapshots: [{ user_id: 'me', version: 1, snapshot: { v: 1 } }],
    }), 'me');
    expect(NEEDED_SCHEMA_VERSION).toBe(31);
    expect(await source.schemaVersion()).toBe(30);
    expect(await source.getSnapshot()).toEqual({ v: 1 });
    expect((await source.listWorkouts())[0]).toMatchObject({ origin: 'imported', id: 'i' });
  });

  it('does not attach an old or different-origin session entry to a workout', () => {
    const workout = { localId: 1, origin: 'synced' };
    expect(snapshotForWorkout({ sessions: { 1: { origin: 'synced', xp: 5 } } }, workout)).toEqual({ origin: 'synced', xp: 5 });
    expect(snapshotForWorkout({ sessions: { 1: { origin: 'imported', xp: 5 } } }, workout)).toBeUndefined();
    expect(snapshotForWorkout({ sessions: { 1: { xp: 5 } } }, workout)).toBeUndefined();
  });

  it('serves fixture sets by origin and id', async () => {
    const source = createFixtureSource({ snapshot: { v: 1 }, rows: { workouts: [{ id: 'x', origin: 'imported' }], sets: { 'imported:x': [{ reps: 4 }] } } });
    expect(await source.getSets((await source.listWorkouts())[0])).toEqual([{ reps: 4 }]);
  });

  it('reads only legacy owner workouts and sets on an older schema', async () => {
    const client = fake({ sessions: [{ id: 's', user_id: 'me', local_id: 1, started_at: '2026-01-01' }], session_sets: [{ id: 'set', session_id: 's', exercise_name: 'Pull-up', set_index: 0, reps: 3 }] });
    client.rpc = async () => ({ data: 29 });
    const source = createSupabaseSource(client, 'me');
    expect(await source.schemaVersion()).toBe(29);
    expect(await source.getSnapshot()).toBeNull();
    const rows = await source.listWorkouts();
    expect(rows).toHaveLength(1);
    expect((await source.getSets(rows[0]))[0]).toMatchObject({ reps: 3, kind: null });
    expect(await source.getSets({ origin: 'imported', id: 'i' })).toEqual([]);
    expect(client.calls.map(c => c.table)).toEqual(['sessions', 'sessions', 'session_sets']);
  });
});
