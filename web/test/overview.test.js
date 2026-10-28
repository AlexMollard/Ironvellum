import { describe, it, expect, vi } from 'vitest';
import { renderOverview } from '../src/screens/overview.js';
import { renderFatigueCard } from '../src/components/fatigueCard.js';
import { setWordsMode } from '../src/i18n.js';
const snapshot = { builtAtMs: 1700000000000, zone: 'UTC', unit: 'kg', profile: { name: 'Lifter', level: 7, intoLevel: 20, needed: 100, totalXp: 1020, streakDays: 4, longestStreakDays: 8, deedsEarned: 1, deedsTotal: 20, figure: 'male' }, week: { workouts: 2, hardSets: 13, volumeKg: 1000, prevVolumeKg: 700, xp: 99, plannedDays: null }, sessions: {}, muscles: { LATS: 'Lats' }, fatigue: { hasHistory: false }, trends: { records30d: 3, recent: [] }, deeds: [] };
const source = { userId: 'me', listWorkouts: async () => [], getSets: async () => [] };
describe('overview', () => {
  it('shows phone figures and hides an absent planned count', async () => {
    const root = document.createElement('main'); await renderOverview(root, { source, snapshot, router: { go: vi.fn() } });
    for (const value of ['13', '1.0 t', '99', '1020', '4 days', '3 in the last 30 days']) expect(root.textContent).toContain(value);
    expect(root.textContent).not.toContain('Planned days');
  });
  it('honestly reports a missing snapshot', async () => {
    const root = document.createElement('main'); await renderOverview(root, { source, snapshot: null, router: { go: vi.fn() } });
    expect(root.textContent).toContain('Sync the app');
  });
  it('links by cloud identity and suppresses a different origin’s snapshot entry', async () => {
    const root = document.createElement('main');
    const workout = { id: 'cloud/id', origin: 'imported', localId: 5, title: 'Imported pull', startedAt: '2025-10-05T00:00:00Z' };
    const otherSnapshot = { ...snapshot, sessions: { 5: { origin: 'synced', hardSets: 77, strength: 999, volumeKg: 123 } } };
    await renderOverview(root, { source: { userId: 'me', listWorkouts: async () => [workout], getSets: async () => [] }, snapshot: otherSnapshot, router: { go: vi.fn() } });
    expect(root.querySelector('a[href="#/workouts/imported/cloud%2Fid"]')).not.toBeNull();
    expect(root.textContent).toContain('— hard sets · — STR');
    expect(root.textContent).not.toContain('999 STR');
  });
  it('switches glossary names without changing snapshot figures', async () => {
    const root = document.createElement('main');
    try {
      setWordsMode(true); await renderOverview(root, { source, snapshot, router: { go: vi.fn() } });
      expect(root.textContent).toContain('Trials'); expect(root.textContent).toContain('Oath'); expect(root.textContent).toContain('1020');
      setWordsMode(false); await renderOverview(root, { source, snapshot, router: { go: vi.fn() } });
      expect(root.textContent).toContain('Workouts'); expect(root.textContent).toContain('Streak'); expect(root.textContent).toContain('1020');
    } finally { setWordsMode(false); }
  });
  it('shows the phone fatigue wording and a stale hint, but hides empty history', () => {
    const root = document.createElement('div');
    const fatigue = { hasHistory: true, atMs: 1000, levels: { LATS: 4 }, worked: [{ muscle: 'LATS', level: 4, cause: 'Phone cause', freshBy: 'Fresh by Monday' }] };
    renderFatigueCard(root, { fatigue, muscles: snapshot.muscles, builtAtMs: 1000, nowMs: 1000 + 30 * 3600000 });
    expect(root.textContent).toContain('Phone cause'); expect(root.textContent).toContain('Fresh by Monday'); expect(root.textContent).toContain('Sync the app');
    expect(root.querySelector('.cross-hatch')).not.toBeNull();
    expect(root.querySelectorAll('.fatigue-legend-item')).toHaveLength(5);
    expect(root.querySelector('.fatigue-legend-item.level-4').textContent).toBe(root.querySelector('.fatigue-row.level-4 .fatigue-level').textContent);
    renderFatigueCard(root, { fatigue, builtAtMs: 1000, nowMs: 1000 + 2 * 3600000 }); expect(root.textContent).not.toContain('Sync the app');
    renderFatigueCard(root, { fatigue: { hasHistory: false } }); expect(root.hidden).toBe(true);
  });
});
