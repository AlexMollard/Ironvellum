import { describe, it, expect, vi } from 'vitest';
import { renderWorkouts } from '../src/screens/workouts.js';
import { setWordsMode } from '../src/i18n.js';
const router = { go: vi.fn() };
const snapshot = { builtAtMs: Date.parse('2025-10-05T00:00:00Z'), zone: 'Australia/Sydney', unit: 'kg', profile: { workouts: 2, firstMs: null }, sessions: { 1: { origin: 'imported', min: 42, hardSets: 7, volumeKg: 500, xp: 23, peaks: 1, groupLevels: { BACK: 3 }, prs: [{ label: 'Pull-up', reps: 5, weightKg: 10 }] } }, months: { '2025-10': { workouts: 2, perWeek: 2.3333333333333335, weeks: [{ start: '2025-10-01', hardSets: 7 }], topLifts: [] } } };
const rows = [{ id: 'imported-1', localId: 1, origin: 'imported', title: 'Pull', startedAt: '2025-10-05T23:30:00Z' }, { id: 'synced-2', localId: 2, origin: 'synced', label: 'Legs', startedAt: '2025-10-03T00:00:00Z' }, { localId: 3, userId: 'other', title: 'Private stranger', startedAt: '2025-10-04T00:00:00Z' }];
describe('workouts', () => {
  it('shows snapshot figures, imported tags, zone days and dashes for missing entries', async () => {
    const root = document.createElement('main'); await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => rows }, snapshot, router });
    for (const text of ['Pull', 'Imported', '42 min', '7 hard sets', '23 XP', 'PR', 'Legs', '— min', '2 workouts']) expect(root.textContent).toContain(text);
    expect(root.textContent).toContain('2.3 per week');
    expect(root.querySelector('[role="meter"]').getAttribute('aria-valuenow')).toBe('7');
    expect(root.textContent).not.toContain('Private stranger');
    expect(root.querySelector('[data-day="2025-10-06"]').classList.contains('has-workout')).toBe(true);
    expect(root.querySelector('.muscle-cell').dataset.level).toBe('3');
    root.querySelector('[aria-label="Previous month"]').click(); expect(root.textContent).toContain('— workouts');
  });
  it('makes each row one tab stop, with the muscle cells and copy button a Left/Right walk away', async () => {
    const root = document.createElement('main'); document.body.append(root);
    await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => rows, getSets: async () => [] }, snapshot, router });
    const stops = row => [...row.querySelectorAll('a[href], button, [tabindex]')].filter(el => el.tabIndex >= 0);
    const cards = [...root.querySelectorAll('.workout-row')]; expect(cards).toHaveLength(2);
    for (const card of cards) { const tabbable = stops(card); expect(tabbable).toHaveLength(1); expect(tabbable[0]).toBe(card.querySelector('.workout-title')); }
    const card = cards[0], title = card.querySelector('.workout-title'), press = (el, key) => el.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    title.focus(); press(title, 'ArrowRight'); expect(document.activeElement).toBe(card.querySelector('.muscle-cell'));
    for (let i = 0; i < 6; i++) press(document.activeElement, 'ArrowRight');
    expect(document.activeElement).toBe(card.querySelector('.copy-button'));
    expect(stops(card)).toEqual([card.querySelector('.copy-button')]);
    const down = new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }); document.activeElement.dispatchEvent(down);
    expect(down.defaultPrevented).toBe(false);
    press(document.activeElement, 'ArrowLeft'); expect(document.activeElement.classList.contains('muscle-cell')).toBe(true);
    root.remove();
  });
  it('filters titles and imported workouts', async () => {
    const root = document.createElement('main'); await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => rows }, snapshot, router });
    root.querySelector('[data-filter="imported"]').click(); expect(root.textContent).toContain('Pull'); expect(root.textContent).not.toContain('Legs');
    const search = root.querySelector('input'); search.value = 'missing'; search.dispatchEvent(new Event('input')); expect(root.textContent).toContain('No matching workouts');
  });
  it('uses origin to guard stats and PR filters when local IDs collide', async () => {
    const root = document.createElement('main');
    const collision = { id: 'sync/id', localId: 1, origin: 'synced', title: 'Other pull', startedAt: '2025-10-04T00:00:00Z' };
    await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => [...rows, collision] }, snapshot, router });
    const cards = [...root.querySelectorAll('.workout-row')];
    const other = cards.find(card => card.textContent.includes('Other pull'));
    expect(other.textContent).toContain('— min');
    expect(other.textContent).not.toContain('23 XP');
    expect(other.querySelector('a').getAttribute('href')).toBe('#/workouts/synced/sync%2Fid');
    root.querySelector('[data-filter="prs"]').click();
    expect(root.textContent).toContain('Pull');
    expect(root.textContent).not.toContain('Other pull');
  });
  it('marks the completion day across midnight and a month boundary', async () => {
    const root = document.createElement('main');
    const crossing = { id: 'crossing', origin: 'synced', localId: 4, startedAt: '2025-09-30T23:45:00Z', completedAt: '2025-10-01T00:15:00Z' };
    await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => [crossing] }, snapshot: { ...snapshot, zone: 'UTC' }, router });
    expect(root.querySelector('[data-day="2025-10-01"]').classList.contains('has-workout')).toBe(true);
    root.querySelector('[aria-label="Previous month"]').click();
    expect(root.querySelector('[data-day="2025-09-30"]').classList.contains('has-workout')).toBe(false);
  });
  it('uses themed names while retaining filters, calendar and snapshot values', async () => {
    const root = document.createElement('main');
    try {
      setWordsMode(true); await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => rows }, snapshot, router });
      expect(root.querySelector('h1').textContent).toBe('Chronicle');
      expect(root.textContent).toContain('2 trials'); expect(root.textContent).toContain('2.3 per week');
      expect(root.querySelector('[data-day="2025-10-06"]').getAttribute('aria-label')).toContain('trial');
      root.querySelector('[data-filter="imported"]').click(); expect(root.textContent).toContain('Pull'); expect(root.textContent).not.toContain('Legs');
    } finally { setWordsMode(false); }
  });
  it('counts current owner rows when the snapshot predates a new workout', async () => {
    const root = document.createElement('main');
    await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => rows }, snapshot: { ...snapshot, profile: { workouts: 1, firstMs: Date.parse('2020-01-01T00:00:00Z') } }, router });
    const heading = root.querySelector('.screen-header p').textContent;
    expect(Number.parseInt(heading, 10)).toBe(2);
    expect(heading).toContain('2025');
    expect(heading).not.toContain('2020');
    expect(root.querySelectorAll('.workout-row')).toHaveLength(2);
  });
  it('renders an empty account', async () => {
    const root = document.createElement('main'); await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => [] }, snapshot: null, router }); expect(root.textContent).toContain('No workouts yet');
  });
});
