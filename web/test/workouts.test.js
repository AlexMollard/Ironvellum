import { describe, it, expect, vi } from 'vitest';
import { renderWorkouts } from '../src/screens/workouts.js';
import { setWordsMode } from '../src/i18n.js';
const router = { go: vi.fn() };
const snapshot = { builtAtMs: Date.parse('2025-10-05T00:00:00Z'), zone: 'Australia/Sydney', unit: 'kg', profile: { workouts: 2, firstMs: null }, sessions: { 1: { min: 42, hardSets: 7, volumeKg: 500, xp: 23, peaks: 1, groupLevels: { BACK: 3 }, prs: [{ label: 'Pull-up', reps: 5, weightKg: 10 }] } }, months: { '2025-10': { workouts: 2, perWeek: 2.3333333333333335, weeks: [{ start: '2025-10-01', hardSets: 7 }], topLifts: [] } } };
const rows = [{ localId: 1, origin: 'imported', title: 'Pull', startedAt: '2025-10-05T23:30:00Z' }, { localId: 2, origin: 'synced', label: 'Legs', startedAt: '2025-10-03T00:00:00Z' }, { localId: 3, userId: 'other', title: 'Private stranger', startedAt: '2025-10-04T00:00:00Z' }];
describe('workouts', () => {
  it('shows snapshot figures, imported tags, zone days and dashes for missing entries', async () => {
    const root = document.createElement('main'); await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => rows }, snapshot, router });
    for (const text of ['Pull', 'Imported', '42 min', '7 hard sets', '23 XP', 'PR', 'Legs', '— min', '2 workouts']) expect(root.textContent).toContain(text);
    expect(root.textContent).toContain('2.3 per week');
    expect(root.querySelector('meter').value).toBe(7);
    expect(root.textContent).not.toContain('Private stranger');
    expect(root.querySelector('[data-day="2025-10-06"]').classList.contains('has-workout')).toBe(true);
    expect(root.querySelector('.muscle-cell').dataset.level).toBe('3');
    root.querySelector('[aria-label="Previous month"]').click(); expect(root.textContent).toContain('— workouts');
  });
  it('filters titles and imported workouts', async () => {
    const root = document.createElement('main'); await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => rows }, snapshot, router });
    root.querySelector('[data-filter="imported"]').click(); expect(root.textContent).toContain('Pull'); expect(root.textContent).not.toContain('Legs');
    const search = root.querySelector('input'); search.value = 'missing'; search.dispatchEvent(new Event('input')); expect(root.textContent).toContain('No matching workouts');
  });
  it('uses themed names while retaining filters, calendar and snapshot values', async () => {
    const root = document.createElement('main');
    try {
      setWordsMode(true); await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => rows }, snapshot, router });
      expect(root.querySelector('h1').textContent).toBe('Trials');
      expect(root.textContent).toContain('2 trials'); expect(root.textContent).toContain('2.3 per week');
      expect(root.querySelector('[data-day="2025-10-06"]').getAttribute('aria-label')).toContain('trial');
      root.querySelector('[data-filter="imported"]').click(); expect(root.textContent).toContain('Pull'); expect(root.textContent).not.toContain('Legs');
    } finally { setWordsMode(false); }
  });
  it('renders an empty account', async () => {
    const root = document.createElement('main'); await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => [] }, snapshot: null, router }); expect(root.textContent).toContain('No workouts yet');
  });
});
