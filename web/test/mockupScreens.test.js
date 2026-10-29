import { describe, it, expect, vi } from 'vitest';
import { renderShell } from '../src/shell.js';
import { renderOverview } from '../src/screens/overview.js';
import { renderWorkouts } from '../src/screens/workouts.js';
import { lastWorkoutCard } from '../src/components/lastWorkoutCard.js';
import { muscleStrip } from '../src/components/muscleStrip.js';
import { renderLineChart } from '../src/components/lineChart.js';
import { prTag } from '../src/components/prTag.js';
import { seal } from '../src/components/seal.js';
import { calendar } from '../src/components/calendar.js';
import { exampleWorkout, exampleChart } from '../src/signinExample.js';
import catalogue from '../src/generated/catalogue.json';

const snapshot = {
  builtAtMs: Date.UTC(2026, 9, 27, 12), zone: 'UTC', unit: 'kg', profile: { name: 'Pat Lifter', level: 23, intoLevel: 6420, needed: 8000, totalXp: 126420, streakDays: 23, longestStreakDays: 41, deedsEarned: 1, deedsTotal: 121, figure: 'female', workouts: 3 },
  week: { startDay: '2026-10-26', endDay: '2026-11-01', workouts: 3, hardSets: 48, volumeKg: 41200, prevVolumeKg: 38900, xp: 340, plannedDays: 4 },
  sessions: { 1: { origin: 'synced', min: 58, hardSets: 17, volumeKg: 9800, peaks: 2, xp: 340, strength: 598, muscleLevels: { LATS: 3 }, groupLevels: { BACK: 3 }, prs: [{ label: 'Pull-up . Weighted', weightKg: 12.5, reps: 6 }] } },
  muscles: { LATS: 'Lats' }, fatigue: { hasHistory: false }, trends: { records30d: 7, recent: [] }, deeds: [{ name: 'Iron Habit', rarity: 'Epic', category: 'Workouts', atMs: Date.UTC(2026, 9, 5) }], months: {}
};
const workout = { id: 'a', origin: 'synced', localId: 1, title: 'Pull · Upper B', startedAt: '2026-10-25T07:12:00Z', completedAt: '2026-10-25T08:10:00Z' };
const sets = [{ exerciseName: 'Pull-up', modifiers: 'Weighted' }, { exerciseName: 'Barbell Row', modifiers: '' }];
const source = { userId: 'me', listWorkouts: async () => [workout], getSets: async () => sets };
const router = { go: vi.fn() };

describe('shell', () => {
  it('shows the phase 1 navigation, the user block and the read-only footer line', () => {
    location.hash = '#/workouts'; const root = document.createElement('div'); document.body.append(root);
    const shell = renderShell(root, { source, snapshot, onSignOut: () => {}, nowMs: Date.UTC(2026, 9, 27, 18) });
    expect([...root.querySelectorAll('nav a')].map(a => a.textContent.replace(/[^A-Za-z]/g, ''))).toEqual(['Overview', 'Workouts', 'Lifts', 'Trends']);
    for (const absent of ['Routines', 'Friends', 'Inbox', 'Allies', 'comments']) expect(root.textContent).not.toContain(absent);
    expect(root.querySelector('nav a[aria-current="page"]').dataset.path).toBe('/workouts');
    const user = root.querySelector('.sidebar-user').textContent;
    for (const text of ['Pat Lifter', 'Level 23', 'Stats as of today 12:00', 'Sign out', 'Log and edit training in the app.']) expect(user).toContain(text);
    shell.dispose(); root.remove();
  });
  it('moves the page header into the top bar and follows the route', async () => {
    location.hash = '#/'; const root = document.createElement('div'); document.body.append(root);
    const shell = renderShell(root, { source, snapshot, onSignOut: () => {}, nowMs: Date.UTC(2026, 9, 27, 18) });
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(root.querySelector('.topbar .screen-header h1').textContent).toBe('Overview');
    expect(root.querySelector('nav a[aria-current="page"]').dataset.path).toBe('/');
    shell.dispose(); root.remove();
  });
});

describe('overview', () => {
  it('shows four tiles, the level card, the last workout with its record chips, and one chip per movement', async () => {
    const root = document.createElement('main'); await renderOverview(root, { source, snapshot, router, nowMs: Date.UTC(2026, 9, 27) });
    expect(root.querySelectorAll('.kpi')).toHaveLength(4);
    expect(root.querySelector('.xp-bar').getAttribute('aria-valuenow')).toBe('6420');
    expect(root.querySelector('.xp-fill').style.getPropertyValue('--pct')).toBe('80.25%');
    expect(root.querySelector('.level-card').textContent).toContain('+340 this week');
    const last = root.querySelector('.last-workout');
    expect(last.querySelector('.pr-tag').textContent).toBe('PRPull-up · Weighted 12.5 kg × 6');
    expect([...last.querySelectorAll('.chips .chip:not(.pr-tag)')].map(c => c.textContent)).toEqual(['Pull-up · Weighted', 'Barbell Row']);
    expect(last.textContent).toContain('17 hard sets · 9.8 t · STR 598');
    expect(last.textContent).toContain('07:12 · 58 min');
    expect(root.querySelector('.deeds-card').textContent).toContain('1 of 121');
  });
  it('draws the female figure for a female snapshot', async () => {
    const root = document.createElement('main'); await renderOverview(root, { source, snapshot, router, nowMs: Date.UTC(2026, 9, 27) });
    const outline = svgRoot => svgRoot.querySelector('.body-outline').getAttribute('points');
    const maleRoot = document.createElement('main'); await renderOverview(maleRoot, { source, snapshot: { ...snapshot, profile: { ...snapshot.profile, figure: 'male' } }, router, nowMs: Date.UTC(2026, 9, 27) });
    expect(outline(root)).not.toBe(outline(maleRoot));
  });
});

describe('workouts', () => {
  it('draws one dot per workout on a day, marks today, and keeps unknown strips apart from level 0', async () => {
    const second = { ...workout, id: 'b', localId: 2, startedAt: '2026-10-25T17:00:00Z', completedAt: '2026-10-25T17:40:00Z' };
    const root = document.createElement('main');
    await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => [workout, second] }, snapshot, router, nowMs: Date.UTC(2026, 9, 27, 9) });
    expect(root.querySelectorAll('[data-day="2026-10-25"] .day-dot')).toHaveLength(2);
    expect(root.querySelector('.calendar-day.today').dataset.day).toBe('2026-10-27');
    const rows = [...root.querySelectorAll('.workout-row')];
    expect(rows[0].querySelector('.muscle-cell').dataset.level).toBe('');
    expect(rows).toHaveLength(2); expect(root.querySelector('.workouts-layout > .workouts-side .calendar')).not.toBeNull(); expect(root.querySelector('.workouts-layout > .list-card .row-chevron')).not.toBeNull();
  });
  it('completes the calendar weeks with blank cells', () => {
    const cells = calendar('2026-10', [], 'UTC', () => {}).querySelectorAll('.calendar-day');
    expect(cells.length % 7).toBe(0); expect(calendar('2026-10', [], 'UTC', () => {}).querySelectorAll('.calendar-day:not(.blank)')).toHaveLength(31);
  });
});

describe('components', () => {
  it('a strip is unknown without a snapshot entry and level 0 for an untrained group', () => {
    expect([...muscleStrip(undefined).children].map(c => c.dataset.level)).toEqual(['', '', '', '', '', '']);
    expect([...muscleStrip({ BACK: 2 }).children].map(c => c.dataset.level)).toEqual(['2', '0', '0', '0', '0', '0']);
  });
  it('a record chip reads BW for an unloaded set and seconds for a hold', () => {
    expect(prTag({ label: 'Pull-up . Archer', reps: 5 }).textContent).toBe('PRPull-up · Archer BW × 5');
    expect(prTag({ label: 'Plank', durationSec: 45 }).textContent).toBe('PRPlank 45 s');
  });
  it('the chart axis rounds out to nice bounds, draws a grid line per step and gold diamonds for records', () => {
    const root = document.createElement('div');
    renderLineChart(root, { series: [{ points: [[1, 1, 80, 0], [2, 2, 105, 1], [3, 3, 92, 0]] }], unit: 'kg', details: false });
    expect(root.querySelectorAll('.chart-grid')).toHaveLength(4);
    expect([...root.querySelectorAll('text.chart-text')].map(t => t.textContent)).toEqual(['80 kg', '90 kg', '100 kg', '110 kg']);
    expect(root.querySelectorAll('polygon.chart-pr')).toHaveLength(1); expect(root.querySelectorAll('circle')).toHaveLength(2); expect(root.querySelector('details')).toBeNull();
  });
  it('a deed seal draws like the app: metal outline, rim and a glyph at the tier size', () => {
    const holder = document.createElement('span'); seal(holder, { rarity: 'Rare', category: 'Volume' });
    const glyph = holder.querySelector('.seal-glyph');
    expect(glyph.getAttribute('transform')).toBe('translate(18 18) scale(1.1666666666666667)');
    expect(glyph.querySelectorAll('path')).toHaveLength(catalogue.glyphs.Volume.length);
    expect(holder.querySelectorAll('linearGradient stop')).toHaveLength(3);
    expect(holder.querySelectorAll('polygon')).toHaveLength(4);
  });
  it('an unknown category falls back to the Activities glyph, as in the app', () => {
    const holder = document.createElement('span'); seal(holder, { rarity: 'Common', category: 'Nope' });
    expect(holder.querySelectorAll('.seal-glyph path')).toHaveLength(catalogue.glyphs.Activities.length);
  });
  it('a locked seal takes the muted ink and no metal gradient', () => {
    const holder = document.createElement('span'); seal(holder, { rarity: 'Epic', category: 'Level', earned: false });
    expect(holder.querySelector('linearGradient')).toBeNull();
    expect(holder.querySelector('.deed-seal').classList.contains('unearned')).toBe(true);
  });
});

describe('sign-in example', () => {
  it('renders from static data without a request and carries no account or test-fixture strings', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('no network'));
    const card = lastWorkoutCard({ ...exampleWorkout, heading: 'Your last workout' });
    const chart = document.createElement('div'); renderLineChart(chart, { series: [{ points: exampleChart.points }], unit: 'kg', details: false });
    expect(card.textContent).toContain('Pull · Upper B'); expect(chart.querySelectorAll('.chart-pr').length).toBeGreaterThan(0);
    expect(fetchSpy).not.toHaveBeenCalled(); fetchSpy.mockRestore();
    expect(JSON.stringify([exampleWorkout, exampleChart])).not.toMatch(/Fictional|fixture/i);
  });
});
