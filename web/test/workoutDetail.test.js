import { describe, it, expect, vi } from 'vitest';
import { renderWorkoutDetail } from '../src/screens/workoutDetail.js';
const workouts = [{ id: 'cloud-1', origin: 'synced', localId: 1, title: 'Pull', startedAt: '2025-10-05T00:00:00Z', note: 'Public note', privateNote: 'Secret note' }, { id: 'cloud-2', origin: 'imported', localId: 2, title: 'Earlier', startedAt: '2025-10-04T00:00:00Z' }];
const sets = [{ exerciseName: 'Pull-up', exercisePosition: 0, setIndex: 0, weightKg: null, reps: 5, modifiers: 'Archer', kind: null, rpe: 8 }, { exerciseName: 'Pull-up', exercisePosition: 1, setIndex: 1, weightKg: -20, reps: 10, kind: 'top' }, { exerciseName: 'Custom hold', exercisePosition: 2, setIndex: 0, reps: 0, durationSec: 45 }, { exerciseName: 'Run', exercisePosition: 3, setIndex: 0, reps: 0, distanceM: 5000 }];
const snapshot = { unit: 'kg', effort: 'rir', rir: { 8: 2 }, zone: 'UTC', profile: { figure: 'male' }, lifts: [{ name: 'Pull-up', art: 'pull_up' }], sessions: { 1: { origin: 'synced', peaks: 1, strength: 42, xp: 15, hardSets: 3, volumeKg: 100, min: 20, muscleLevels: { LATS: 3 }, peakSets: ['Pull-up#0'], e1rm: { 'Pull-up#0': 68 }, prs: [{ label: 'Pull-up . Archer', reps: 5 }] } } };
describe('workout detail', () => {
  it('shows stored details, keeps repeated blocks separate and excludes private notes', async () => {
    const root = document.createElement('main'); const router = { go: vi.fn() };
    await renderWorkoutDetail(root, { source: { userId: 'me', listWorkouts: async () => workouts, getSets: async () => sets }, snapshot, router }, 'synced', 'cloud-1');
    for (const value of ['Working', 'Top', 'BW', '68 kg', 'PR', '45 s', '5 km', 'Public note', 'Previous', '42']) expect(root.textContent).toContain(value);
    expect(root.textContent).not.toContain('Secret note'); expect(root.textContent).not.toContain('comments');
    expect(root.querySelectorAll('h2').length).toBeGreaterThan(4); expect(root.querySelector('.exercise-art-placeholder')).not.toBeNull();
    expect(root.querySelector('[data-muscle="LATS"][data-level="3"]')).not.toBeNull();
    const cells = [...root.querySelectorAll('tbody tr')][0].textContent; expect(cells).toContain('2');
    expect([...root.querySelectorAll('tbody tr')][1].textContent).toContain('-20');
    root.querySelector('a[href="#/workouts/imported/cloud-2"]').click(); expect(router.go).toHaveBeenCalledWith('/workouts/imported/cloud-2');
  });
  it('uses the selected pound unit and the original RPE scale', async () => {
    const root = document.createElement('main');
    const poundSets = [{ exerciseName: 'Lift', exercisePosition: 0, setIndex: 0, weightKg: 102.058, reps: 5, kind: 'backoff', rpe: 9 }];
    await renderWorkoutDetail(root, { source: { userId: 'me', listWorkouts: async () => workouts, getSets: async () => poundSets }, snapshot: { ...snapshot, unit: 'lb', effort: 'rpe' }, router: { go: vi.fn() } }, 'synced', 'cloud-1');
    expect(root.textContent).toContain('225 lb'); expect(root.textContent).toContain('Back-off'); expect(root.textContent).toContain('RPE');
  });
  it('shows dashes without a snapshot', async () => {
    const root = document.createElement('main'); await renderWorkoutDetail(root, { source: { userId: 'me', listWorkouts: async () => workouts, getSets: async () => sets }, snapshot: null, router: { go: vi.fn() } }, 'synced', 'cloud-1');
    expect(root.textContent).toContain('Sync the app'); expect(root.textContent).toContain('—');
  });
  it('selects origin and cloud ID, suppressing derived stats from a colliding local ID', async () => {
    const root = document.createElement('main');
    const collision = { id: 'imported/id', origin: 'imported', localId: 1, title: 'Imported collision', startedAt: '2025-10-06T00:00:00Z' };
    const getSets = vi.fn(async () => []);
    await renderWorkoutDetail(root, { source: { userId: 'me', listWorkouts: async () => [...workouts, collision], getSets }, snapshot, router: { go: vi.fn() } }, 'imported', 'imported/id');
    expect(root.querySelector('h1').textContent).toBe('Imported collision');
    expect(getSets).toHaveBeenCalledWith(collision);
    expect(root.textContent).not.toContain('42');
    expect(root.querySelector('[data-muscle="LATS"]')).toBeNull();
    expect(root.querySelector('a[href="#/workouts/synced/cloud-1"]')).not.toBeNull();
  });
});
