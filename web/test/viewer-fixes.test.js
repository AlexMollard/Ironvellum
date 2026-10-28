import { describe, it, expect, vi } from 'vitest';
import { renderOverview } from '../src/screens/overview.js';
import { renderTrends } from '../src/screens/trends.js';
import { renderWorkoutDetail } from '../src/screens/workoutDetail.js';
import { renderShell, statsAsOf } from '../src/shell.js';
import { workoutRow } from '../src/components/workoutRow.js';
import { calendar } from '../src/components/calendar.js';
import { setTable } from '../src/components/setTable.js';
import { renderFatigueCard } from '../src/components/fatigueCard.js';
import { sortedWorkouts, workoutDay, weekPassed } from '../src/components/statCard.js';
import { formatDay, formatVolume } from '../src/format.js';
const base = { builtAtMs: Date.UTC(2023, 10, 14, 12), zone: 'UTC', unit: 'kg', profile: { name: 'Lifter', level: 7, intoLevel: 20, needed: 100, totalXp: 1020, streakDays: 4, longestStreakDays: 8, deedsEarned: 1, deedsTotal: 20, figure: 'male', workouts: 3 }, week: { startDay: '2023-11-13', endDay: '2023-11-19', workouts: 2, hardSets: 13, volumeKg: 9800, prevVolumeKg: 700, xp: 99, plannedDays: null }, sessions: {}, muscles: {}, fatigue: { hasHistory: false }, trends: { records30d: 3, recent: [] }, deeds: [] };
const source = { userId: 'me', listWorkouts: async () => [], getSets: async () => [] };
const router = { go: vi.fn() };
describe('stale snapshot', () => {
  it('compares the week end with today in the snapshot zone', () => {
    expect(weekPassed(base, Date.UTC(2023, 10, 19, 12))).toBe(false);
    expect(weekPassed(base, Date.UTC(2023, 10, 20, 12))).toBe(true);
    expect(weekPassed({ ...base, zone: 'Pacific/Auckland' }, Date.UTC(2023, 10, 19, 12))).toBe(true);
    expect(weekPassed({ ...base, week: undefined }, Date.UTC(2030, 0, 1))).toBe(false);
  });
  it('relabels the week card once the week has passed', async () => {
    const fresh = document.createElement('main'); await renderOverview(fresh, { source, snapshot: base, router, nowMs: Date.UTC(2023, 10, 15) });
    expect(fresh.textContent).toContain('This week'); expect(fresh.textContent).not.toContain('Week of');
    const stale = document.createElement('main'); await renderOverview(stale, { source, snapshot: base, router, nowMs: Date.UTC(2023, 10, 25) });
    expect(stale.textContent).toContain(`Week of ${formatDay('2023-11-13')}`); expect(stale.textContent).not.toContain('This week');
  });
  it('shows the shell notice only when the week has passed', async () => {
    location.hash = '#/';
    for (const [nowMs, shown] of [[Date.UTC(2023, 10, 15), false], [Date.UTC(2023, 10, 25), true]]) {
      const root = document.createElement('div'); document.body.append(root);
      const shell = renderShell(root, { source, snapshot: base, onSignOut: () => {}, nowMs });
      expect(root.textContent.includes(`Stats from ${formatDay('2023-11-14')}. Open the app to sync.`)).toBe(shown);
      shell.dispose(); root.remove();
    }
  });
});
describe('workout day', () => {
  const crossing = { id: 'a', origin: 'synced', localId: 1, title: 'Late', startedAt: '2025-10-05T23:30:00Z', completedAt: '2025-10-06T00:40:00Z' };
  it('uses completion, else start, in the snapshot zone', () => {
    expect(workoutDay(crossing, 'UTC')).toBe('2025-10-06');
    expect(workoutDay({ startedAt: '2025-10-05T23:30:00Z' }, 'UTC')).toBe('2025-10-05');
    expect(workoutDay({ startedAt: '2025-10-05T23:30:00Z' }, 'Australia/Sydney')).toBe('2025-10-06');
  });
  it('agrees across the row, detail page, calendar and ordering', async () => {
    const snap = { ...base, zone: 'UTC' }; const expected = formatDay('2025-10-06');
    expect(workoutRow(crossing, undefined, snap, router).textContent).toContain(expected);
    const root = document.createElement('main');
    await renderWorkoutDetail(root, { source: { userId: 'me', listWorkouts: async () => [crossing], getSets: async () => [] }, snapshot: snap, router }, 'synced', 'a');
    expect(root.textContent).toContain(expected);
    expect(calendar('2025-10', [crossing], 'UTC', () => {}).querySelector('[data-day="2025-10-06"]').classList.contains('has-workout')).toBe(true);
    const earlier = { ...crossing, id: 'b', startedAt: '2025-10-05T10:00:00Z', completedAt: '2025-10-05T11:00:00Z' };
    expect(sortedWorkouts([earlier, crossing], source, 'UTC').map(w => w.id)).toEqual(['a', 'b']);
  });
  it('shows the same day on the overview last-workout card', async () => {
    const root = document.createElement('main');
    await renderOverview(root, { source: { ...source, listWorkouts: async () => [crossing] }, snapshot: base, router, nowMs: Date.UTC(2023, 10, 15) });
    expect(root.textContent).toContain(formatDay('2025-10-06'));
  });
});
describe('set numbers', () => {
  it('numbers rows by position and keeps setIndex for the lookups', () => {
    const sets = [{ exerciseName: 'Lift', setIndex: 4, reps: 5, weightKg: 50 }, { exerciseName: 'Lift', setIndex: 5, reps: 5, weightKg: 50 }];
    const table = setTable(sets, { e1rm: { 'Lift#5': 61 }, peakSets: ['Lift#5'] }, { unit: 'kg' });
    const rows = [...table.querySelectorAll('tbody tr')];
    expect(rows[0].children[0].textContent).toBe('1'); expect(rows[1].children[0].textContent).toBe('2');
    expect(rows[0].textContent).not.toContain('61 kg'); expect(rows[1].textContent).toContain('61 kg'); expect(rows[1].textContent).toContain('PR');
  });
});
describe('low fixes', () => {
  it('separates the muscle from its level', () => {
    const el = document.createElement('div');
    renderFatigueCard(el, { fatigue: { hasHistory: true, atMs: 1000, levels: {}, worked: [{ muscle: 'CHEST', level: 1 }] }, muscles: { CHEST: 'Lower chest' }, builtAtMs: 1000, nowMs: 1000 });
    expect(el.querySelector('.fatigue-row').textContent).toMatch(/Lower chest\W+Light/);
  });
  it('shows volume in tonnes, or thousands of pounds', () => {
    expect(formatVolume(9800)).toBe('9.8 t'); expect(formatVolume(640, 'kg')).toBe('0.6 t');
    expect(formatVolume(9800, 'lb')).toBe('21.6 k lb');
  });
  it('shows the volume with its unit on the overview', async () => {
    const root = document.createElement('main'); await renderOverview(root, { source, snapshot: base, router, nowMs: Date.UTC(2023, 10, 15) });
    expect(root.textContent).toContain('9.8 t');
    const lb = document.createElement('main'); await renderOverview(lb, { source, snapshot: { ...base, unit: 'lb' }, router, nowMs: Date.UTC(2023, 10, 15) });
    expect(lb.textContent).toContain('21.6 k lb');
  });
  it('formats "Stats as of" in the snapshot zone', async () => {
    const snapshot = { ...base, zone: 'Pacific/Kiritimati' };
    expect(statsAsOf(snapshot, Date.UTC(2023, 10, 15))).toBe('Stats as of today 02:00');
    expect(statsAsOf(snapshot, Date.UTC(2023, 10, 25))).toBe(`Stats as of ${formatDay('2023-11-15')} 02:00`);
    const root = document.createElement('div'); document.body.append(root);
    const shell = renderShell(root, { source, snapshot, onSignOut: () => {}, nowMs: Date.UTC(2023, 10, 15) });
    expect(root.querySelector('.sidebar').textContent).toContain('Stats as of today 02:00'); shell.dispose(); root.remove();
  });
  it('shows a friendly empty state when the snapshot lacks profile or trends', async () => {
    for (const snapshot of [{ ...base, profile: undefined }, { ...base, trends: undefined }]) {
      const root = document.createElement('main'); await renderOverview(root, { source, snapshot, router, nowMs: Date.UTC(2023, 10, 15) });
      expect(root.textContent).toContain('Open the app to sync');
    }
    for (const snapshot of [{ ...base, trends: {} }, { ...base, trends: undefined }, { ...base, profile: undefined, trends: { weeks: [] } }]) {
      const root = document.createElement('div'); expect(() => renderTrends(root, { snapshot })).not.toThrow();
      expect(root.textContent).toContain('No trends yet');
    }
  });
});
