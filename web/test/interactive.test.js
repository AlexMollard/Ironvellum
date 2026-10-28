import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { renderLineChart } from '../src/components/lineChart.js';
import { renderStackedWeeks } from '../src/components/stackedWeeks.js';
import { renderTrainingDays } from '../src/components/trainingDays.js';
import { renderBodyMap } from '../src/components/bodyMap.js';
import { muscleStrip } from '../src/components/muscleStrip.js';
import { deedRow } from '../src/components/deedRow.js';
import { recordRow } from '../src/components/recordRow.js';
import { calendar } from '../src/components/calendar.js';
import { scrollBehavior, hideTip } from '../src/components/interact.js';
import { renderOverview } from '../src/screens/overview.js';
import { renderWorkouts } from '../src/screens/workouts.js';
import { formatLoad } from '../src/format.js';
import { bodyMapShapes } from '../src/generated/bodyMapShapes.js';

const tip = () => document.querySelector('#viewer-tooltip');
const key = (el, name) => el.dispatchEvent(new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true }));
const mount = () => { const root = document.createElement('div'); document.body.append(root); return root; };
// [at ms, unused, value, PR flag, date label, value text, axis label]
const point = (day, value, pr = 0, text) => [Date.UTC(2026, 9, day), null, value, pr, `${day} Oct 2026`, text];

beforeEach(() => { document.body.replaceChildren(); });
afterEach(() => { hideTip(); vi.unstubAllGlobals(); });

describe('line chart tooltip', () => {
  const series = [{ name: 'Bench', index: 0, points: [point(1, 100, 0, '100 kg'), point(8, 110, 1, '110 kg'), point(15, 105, 0, '105 kg')] }];
  const draw = (extra = {}) => { const root = mount(); renderLineChart(root, { series, label: 'Estimated 1RM', unit: 'kg', ...extra }); return root; };

  it('shows date, value with unit and the PR mark when a point is focused, in a polite live region', () => {
    const root = draw(); const marks = root.querySelectorAll('.chart-point');
    expect(marks).toHaveLength(3);
    marks[1].focus();
    expect(tip().getAttribute('aria-live')).toBe('polite');
    expect(tip().classList.contains('is-on')).toBe(true);
    expect(tip().textContent).toContain('8 Oct 2026'); expect(tip().textContent).toContain('110 kg'); expect(tip().textContent).toContain('PR');
    expect(tip().textContent).toContain('Bench');
    expect(marks[1].classList.contains('is-active')).toBe(true);
    expect(root.querySelector('.chart-cursor').getAttribute('visibility')).toBeNull();
  });
  it('has a single tab stop and walks the points with the arrow keys, Home and End', () => {
    const root = draw(); const marks = [...root.querySelectorAll('.chart-point')];
    expect(marks.map(m => m.getAttribute('tabindex'))).toEqual(['0', '-1', '-1']);
    marks[0].focus(); key(marks[0], 'ArrowRight');
    expect(document.activeElement).toBe(marks[1]); expect(tip().textContent).toContain('110 kg');
    key(marks[1], 'End'); expect(document.activeElement).toBe(marks[2]);
    key(marks[2], 'ArrowRight'); expect(document.activeElement).toBe(marks[2]);
    key(marks[2], 'Home'); expect(document.activeElement).toBe(marks[0]); expect(tip().textContent).toContain('100 kg');
    expect(marks.map(m => m.getAttribute('tabindex'))).toEqual(['0', '-1', '-1']);
  });
  it('hides on blur and on Escape', () => {
    const root = draw(); const mark = root.querySelector('.chart-point');
    mark.focus(); expect(tip().classList.contains('is-on')).toBe(true);
    mark.blur(); expect(tip().classList.contains('is-on')).toBe(false);
    mark.focus(); key(mark, 'Escape'); expect(tip().classList.contains('is-on')).toBe(false);
  });
  it('shows the nearest point on hover and on a touch tap', () => {
    const root = draw(); const svg = root.querySelector('svg'), marks = root.querySelectorAll('.chart-point');
    const at = i => ({ clientX: Number(marks[i].getAttribute('cx') ?? marks[i].points?.[0]?.x ?? 0), clientY: Number(marks[i].getAttribute('cy') ?? 0), bubbles: true });
    svg.dispatchEvent(Object.assign(new MouseEvent('pointermove', at(2)), { pointerType: 'mouse' }));
    expect(tip().textContent).toContain('105 kg');
    svg.dispatchEvent(new MouseEvent('pointerleave', { bubbles: false })); expect(tip().classList.contains('is-on')).toBe(false);
    svg.dispatchEvent(Object.assign(new MouseEvent('pointerdown', at(0)), { pointerType: 'touch' }));
    expect(tip().classList.contains('is-on')).toBe(true); expect(tip().textContent).toContain('100 kg');
    svg.dispatchEvent(Object.assign(new MouseEvent('pointerleave'), { pointerType: 'touch' })); expect(tip().classList.contains('is-on')).toBe(true);
    document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true })); expect(tip().classList.contains('is-on')).toBe(false);
  });
  it('respects the lb unit and the weekly title the caller supplies', () => {
    const lb = value => formatLoad(value, 'lb');
    const root = draw({ unit: 'lb', formatValue: lb, pointTitle: p => `Week of ${p[4]}`, series: [{ points: [[Date.UTC(2026, 9, 1), null, 100, 0, '1 Oct', undefined], [Date.UTC(2026, 9, 8), null, 110, 0, '8 Oct', undefined]] }] });
    root.querySelectorAll('.chart-point')[1].focus();
    expect(tip().textContent).toContain('Week of 8 Oct'); expect(tip().textContent).toContain(`${lb(110)} lb`);
  });
  it('hops between series with Up and Down at the nearest date', () => {
    const root = draw({ series: [{ name: 'A', index: 0, points: [point(1, 100), point(8, 110)] }, { name: 'B', index: 1, points: [point(1, 80), point(9, 90)] }] });
    const marks = [...root.querySelectorAll('.chart-point')]; marks[1].focus();
    key(marks[1], 'ArrowDown'); expect(document.activeElement).toBe(marks[3]); expect(tip().textContent).toContain('B');
    key(marks[3], 'ArrowUp'); expect(document.activeElement).toBe(marks[1]);
  });
});

describe('week bars and training days', () => {
  const weeks = [{ start: '2026-10-05', groups: { BACK: 3, CHEST: 2 } }, { start: '2026-10-12', groups: { BACK: 5, LEGS: 4 } }];
  it('stacked weeks: focus shows the week and every group, arrows move between weeks', () => {
    const root = mount(); renderStackedWeeks(root, weeks);
    const hits = [...root.querySelectorAll('.chart-hit')]; expect(hits).toHaveLength(2);
    hits[0].focus(); expect(tip().textContent).toContain('Week of'); expect(tip().textContent).toContain('Back: 3'); expect(tip().textContent).toContain('Chest: 2');
    key(hits[0], 'ArrowRight'); expect(document.activeElement).toBe(hits[1]); expect(tip().textContent).toContain('Legs: 4');
    expect(hits[1].getAttribute('aria-label')).toContain('Back: 5');
  });
  it('training days: hover and focus say whether the day had a workout', () => {
    const root = mount(); renderTrainingDays(root, ['2026-10-27'], Date.UTC(2026, 9, 28), 'UTC');
    const cells = root.querySelectorAll('.training-day'); expect(cells).toHaveLength(182);
    const trained = root.querySelector('[data-day="2026-10-27"]'); trained.focus(); expect(tip().textContent).toContain('Workout');
    cells[0].focus(); expect(tip().textContent).toContain('Rest day');
    key(cells[0], 'ArrowRight'); expect(document.activeElement).toBe(cells[1]);
  });
  it('workouts "so far" bars: focus shows the week and its hard sets', async () => {
    const root = mount(); const workout = { id: 'a', origin: 'synced', localId: 1, title: 'Pull', startedAt: '2026-10-25T07:12:00Z' };
    const snapshot = { builtAtMs: Date.UTC(2026, 9, 27, 12), zone: 'UTC', unit: 'kg', sessions: {}, months: { '2026-10': { workouts: 1, perWeek: 1, weeks: [{ start: '2026-10-05', hardSets: 12 }, { start: '2026-10-12', hardSets: 7 }], topLifts: [] } } };
    await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => [workout], getSets: async () => [] }, snapshot, router: { go() {} }, nowMs: snapshot.builtAtMs });
    const bars = root.querySelectorAll('.week-bar'); expect(bars).toHaveLength(2);
    bars[0].focus(); expect(tip().textContent).toContain('12 hard sets'); expect(tip().textContent).toContain('Week of');
    key(bars[0], 'ArrowRight'); expect(document.activeElement).toBe(bars[1]); expect(tip().textContent).toContain('7 hard sets');
  });
});

describe('muscle tooltips', () => {
  it('strip segments show the group and level on focus and move with the arrow keys', () => {
    const strip = muscleStrip({ BACK: 3, CHEST: 1 }); document.body.append(strip);
    const cells = [...strip.querySelectorAll('.muscle-cell')];
    cells[0].focus(); expect(tip().textContent).toContain('Back'); expect(tip().textContent).toContain('Most');
    key(cells[0], 'ArrowRight'); expect(document.activeElement).toBe(cells[1]); expect(tip().textContent).toContain('A little');
    cells[2].focus(); expect(tip().textContent).toContain('Not worked');
    expect(cells.map(c => c.getAttribute('tabindex')).filter(v => v === '0')).toHaveLength(1);
  });
  it('an unknown level reads as a dash', () => { const strip = muscleStrip(undefined); document.body.append(strip); strip.querySelector('.muscle-cell').focus(); expect(tip().textContent).toContain('—'); });
  it('body map regions show the muscle name and level, one region per muscle is a tab stop', () => {
    const part = bodyMapShapes.male.front[0], el = mount();
    renderBodyMap(el, { levels: { [part.muscle]: 2 }, look: 'worked', names: { [part.muscle]: 'Test muscle' } });
    const stops = [...el.querySelectorAll('.body-region[tabindex]')]; expect(stops.length).toBeGreaterThan(1);
    expect(stops.filter(s => s.getAttribute('tabindex') === '0')).toHaveLength(1);
    const first = el.querySelector(`.body-region[data-muscle="${part.muscle}"]`); first.focus();
    expect(tip().textContent).toContain('Test muscle'); expect(tip().textContent).toContain('Some');
    expect(el.querySelectorAll(`.body-region.is-active[data-muscle="${part.muscle}"]`).length).toBe(el.querySelectorAll(`.body-region[data-muscle="${part.muscle}"]`).length);
    expect(first.getAttribute('aria-label')).toBe('Test muscle: Some');
    key(first, 'ArrowRight'); expect(document.activeElement).toBe(stops[1]);
  });
  it('coverage maps use the coverage words', () => {
    const part = bodyMapShapes.male.front[0], el = mount(); renderBodyMap(el, { levels: { [part.muscle]: 'heavy' }, look: 'coverage' });
    el.querySelector(`.body-region[data-muscle="${part.muscle}"]`).focus(); expect(tip().textContent).toContain('Heavy');
  });
});

describe('deed panel', () => {
  const deed = { name: 'Iron Habit', rarity: 'Epic', category: 'Workouts', atMs: Date.UTC(2026, 9, 5), description: 'Train five weeks in a row.' };
  it('opens in place with the description, rarity and earned date, and closes again', () => {
    const el = deedRow(deed, 'UTC'); document.body.append(el);
    const button = el.querySelector('button.deed-row'), panel = el.querySelector('.deed-panel');
    expect(button.getAttribute('aria-expanded')).toBe('false'); expect(panel.hidden).toBe(true); expect(button.getAttribute('aria-controls')).toBe(panel.id);
    button.click();
    expect(button.getAttribute('aria-expanded')).toBe('true'); expect(panel.hidden).toBe(false);
    expect(panel.textContent).toContain('Train five weeks in a row.'); expect(panel.textContent).toContain('Fabled'); expect(panel.textContent).toContain('Earned'); expect(panel.textContent).toContain('2026');
    key(button, 'Escape'); expect(panel.hidden).toBe(true); expect(document.activeElement).toBe(button);
    button.click(); button.click(); expect(panel.hidden).toBe(true);
  });
  it('still opens when the deed has no description, and never injects markup', () => {
    const el = deedRow({ ...deed, name: '<img src=x onerror=alert(1)>', description: undefined }, 'UTC'); document.body.append(el);
    el.querySelector('button').click(); expect(el.querySelector('.deed-panel').textContent).toContain('Fabled'); expect(el.querySelector('img')).toBeNull();
  });
});

describe('links', () => {
  const router = { go: vi.fn() };
  const snapshot = {
    builtAtMs: Date.UTC(2026, 9, 27, 12), zone: 'UTC', unit: 'kg',
    profile: { name: 'Pat', level: 3, intoLevel: 10, needed: 100, totalXp: 500, streakDays: 3, longestStreakDays: 9, deedsEarned: 1, deedsTotal: 5, figure: 'male', workouts: 2 },
    week: { startDay: '2026-10-26', endDay: '2026-11-01', workouts: 1, hardSets: 10, volumeKg: 5000, prevVolumeKg: 4000, xp: 50 },
    sessions: { 1: { origin: 'synced', min: 50, hardSets: 10, volumeKg: 5000, peaks: 0, xp: 50, strength: 400, muscleLevels: {}, groupLevels: {}, prs: [] } },
    muscles: {}, fatigue: { hasHistory: false }, deeds: [{ name: 'Iron Habit', rarity: 'Epic', category: 'Workouts', atMs: Date.UTC(2026, 9, 5), description: 'Five weeks.' }],
    trends: { records30d: 2, recent: [{ name: 'Bench Press', label: 'Bench Press', atMs: Date.UTC(2026, 9, 20), e1rmKg: 100, gainKg: 2.5 }] }, months: {}
  };
  const workout = { id: 'a/b', origin: 'synced', localId: 1, title: 'Pull', startedAt: '2026-10-25T07:12:00Z' };
  const source = { userId: 'me', listWorkouts: async () => [workout], getSets: async () => [] };

  it('recent-record rows link to the lift, and the link covers the row', () => {
    const row = recordRow(snapshot.trends.recent[0], snapshot, router);
    expect(row.classList.contains('is-link')).toBe(true);
    expect(row.querySelector('a').getAttribute('href')).toBe('#/lifts/Bench%20Press');
    row.querySelector('a').click(); expect(router.go).toHaveBeenCalledWith('/lifts/Bench%20Press');
  });
  it('overview: tiles link to their pages, the last workout card to its detail, and deeds are buttons', async () => {
    const root = mount(); await renderOverview(root, { source, snapshot, router, nowMs: snapshot.builtAtMs });
    const tiles = [...root.querySelectorAll('.kpi')].map(k => [k.querySelector('.eyebrow').textContent, k.querySelector('a.kpi-link')?.getAttribute('href')]);
    expect(tiles.map(t => t[1])).toEqual(['#/workouts', '#/workouts', '#/lifts', '#/workouts']);
    expect(tiles[2][0]).toBe('Records'); expect(tiles[3][0]).toBe('Volume'); expect(tiles[0][0]).toBe('This week');
    expect(root.querySelector('.last-workout a.lw-name').getAttribute('href')).toBe('#/workouts/synced/a%2Fb');
    expect(root.querySelector('.records-card a').getAttribute('href')).toBe('#/lifts/Bench%20Press');
    expect(root.querySelector('.deeds-card button.deed-row')).not.toBeNull();
    expect(root.querySelector('[onclick]')).toBeNull();
  });
  it('calendar: a single-workout day is a link, a busy day a button, an empty day plain', () => {
    const other = { ...workout, id: 'c', localId: 2, startedAt: '2026-10-25T18:00:00Z' }, solo = { ...workout, id: 'd', localId: 3, startedAt: '2026-10-10T08:00:00Z' };
    const onPick = vi.fn();
    const el = calendar('2026-10', [workout, other, solo], 'UTC', () => {}, Date.UTC(2026, 9, 27), { router, onPick });
    const day = d => el.querySelector(`[data-day="2026-10-${d}"]`);
    expect(day('10').tagName).toBe('A'); expect(day('10').getAttribute('href')).toBe('#/workouts/synced/d');
    expect(day('25').tagName).toBe('BUTTON'); expect(day('25').getAttribute('aria-label')).toContain('2');
    day('25').click(); expect(onPick).toHaveBeenCalledWith('2026-10-25', [workout, other]);
    expect(day('11').tagName).toBe('SPAN');
  });
  it('a busy calendar day highlights its workouts in the list and scrolls to them', async () => {
    const rows = [workout, { ...workout, id: 'c', localId: 2, title: 'Second', startedAt: '2026-10-25T18:00:00Z' }, { ...workout, id: 'e', localId: 3, title: 'Other', startedAt: '2026-10-20T08:00:00Z' }];
    const scroll = vi.fn(); Element.prototype.scrollIntoView = scroll;
    const root = mount(); await renderWorkouts(root, { source: { userId: 'me', listWorkouts: async () => rows, getSets: async () => [] }, snapshot: { ...snapshot, months: {} }, router, nowMs: snapshot.builtAtMs });
    root.querySelector('button.calendar-day[data-day="2026-10-25"]').click();
    const lit = [...root.querySelectorAll('.workout-row.is-highlight')];
    expect(lit).toHaveLength(2); expect(lit.every(r => r.dataset.day === '2026-10-25')).toBe(true);
    expect(root.querySelectorAll('.workout-row')).toHaveLength(3); expect(scroll).toHaveBeenCalled();
    delete Element.prototype.scrollIntoView;
  });
});

describe('motion', () => {
  it('scrolls smoothly unless the reader asked for reduced motion', () => {
    vi.stubGlobal('matchMedia', q => ({ matches: false, media: q })); expect(scrollBehavior()).toBe('smooth');
    vi.stubGlobal('matchMedia', q => ({ matches: q.includes('reduce'), media: q })); expect(scrollBehavior()).toBe('auto');
    vi.unstubAllGlobals(); expect(scrollBehavior()).toBe('smooth');
  });
  it('the stylesheet switches every animation and transition off under prefers-reduced-motion, and only uses short durations', () => {
    const css = readFileSync('src/theme.css', 'utf8');
    const reduce = css.slice(css.indexOf('@media (prefers-reduced-motion: reduce)'));
    expect(reduce).toMatch(/animation: none !important/); expect(reduce).toMatch(/transition: none !important/);
    for (const [, ms] of css.matchAll(/\b(\d+)ms\b/g)) expect(Number(ms)).toBeLessThanOrEqual(180);
  });
});
