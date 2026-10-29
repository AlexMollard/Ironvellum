import { describe, it, expect, vi, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import snapshot from './fixtures/snapshot-v1.json';
import tokens from '../src/generated/tokens.json';
import accents from '../src/generated/accents.json';
import { renderRoutines } from '../src/screens/routines.js';
import { renderOverview } from '../src/screens/overview.js';
import { renderLifts } from '../src/screens/lifts.js';
import { workoutRow } from '../src/components/workoutRow.js';
import { weekPlan } from '../src/components/weekPlan.js';
import { personRow } from '../src/components/personRow.js';
import { podium } from '../src/components/podium.js';
import { plural } from '../src/format.js';
import { liftPath } from '../src/components/statCard.js';

afterEach(() => { document.body.innerHTML = ''; });
const router = () => ({ go: vi.fn() });
const only = (el, selector = '.stretch') => { const found = el.querySelectorAll(selector); expect(found).toHaveLength(1); return found[0]; };
// A path, not new URL(..., import.meta.url): Vite rewrites that form into an asset import.
const css = name => readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../src', name), 'utf8');

describe('a box that looks clickable is clickable all over', () => {
  const MONDAY = Date.UTC(2026, 9, 26, 12);
  const routines = (over = {}) => { const root = document.createElement('main'); document.body.append(root); renderRoutines(root, { snapshot: { ...snapshot, ...over }, router: router(), nowMs: MONDAY }); return root; };
  it('routineRowsAreOneLinkOverTheWholeRow', () => {
    const root = routines();
    for (const item of root.querySelectorAll('.routine-item')) { expect(item.classList.contains('is-link')).toBe(true); expect(only(item).classList.contains('routine-link')).toBe(true); }
  });
  it('aDayWithOneRoutineIsOneLinkAndARestDayIsInert', () => {
    const root = routines();
    for (const day of root.querySelectorAll('.week-day')) {
      if (day.classList.contains('is-rest')) { expect(day.classList.contains('is-link')).toBe(false); expect(day.querySelector('a, button')).toBeNull(); }
      else { expect(day.classList.contains('is-link')).toBe(true); expect(only(day).getAttribute('aria-label')).toMatch(/^\w+day, /); }
    }
    expect(root.querySelector('.week-day.is-open a').getAttribute('aria-current')).toBe('true');
  });
  it('aDayWithTwoRoutinesStacksOneFullLinkEach', () => {
    const [a, b] = snapshot.routines.presets, presets = [{ ...a, day: 2 }, { ...b, day: 2 }];
    const el = weekPlan(presets, { days: 1, hardSets: 1 }, a.id, router(), 2); const cell = el.querySelector('.week-day[data-day="2"]');
    expect(cell.classList.contains('is-multi')).toBe(true); expect(cell.classList.contains('is-link')).toBe(false);
    const slots = cell.querySelectorAll('.week-slot.is-link'); expect(slots).toHaveLength(2); for (const slot of slots) only(slot);
    expect(cell.classList.contains('is-today')).toBe(true);
  });
  it('anExerciseOfARoutineLinksToItsLiftPageOnlyWhereOneExists', () => {
    const root = routines(); const names = new Set(snapshot.lifts.map(l => l.name.toLowerCase()));
    const entries = [...root.querySelectorAll('.routine-entry')]; expect(entries.length).toBeGreaterThan(0);
    for (const entry of entries) {
      const name = entry.querySelector('.routine-name, strong').textContent, linked = entry.classList.contains('is-link');
      expect(linked).toBe(names.has(name.toLowerCase()));
      if (linked) expect(only(entry).getAttribute('href')).toBe(`#${liftPath(snapshot, name)}`);
    }
  });
  it('aWorkoutRowIsOneLinkAndItsCopyButtonStaysSeparate', () => {
    const workout = { origin: 'synced', id: 'w1', title: 'Pull', startedAt: '2026-10-25T10:00:00Z' };
    const entry = { min: 40, hardSets: 1, volumeKg: 100, xp: 10, prs: [], groupLevels: {} };
    const row = workoutRow(workout, entry, { unit: 'kg', zone: 'UTC' }, router(), { getSets: async () => [] });
    expect(row.classList.contains('is-link')).toBe(true); expect(only(row).classList.contains('workout-title')).toBe(true);
    expect(row.querySelector('.copy-button').classList.contains('stretch')).toBe(false);
    expect(row.querySelector('.row-figures').textContent).toBe('40 min · 1 hard set · 0.1 t · +10 XP');
  });
  it('aPersonRowAndAPodiumPlaceAreButtonsOnlyWhenTheyOpenAFolio', () => {
    const open = vi.fn(), person = { id: 'u1', name: 'Mara', level: 4 };
    const row = personRow(person, { onOpen: open }); expect(row.classList.contains('is-link')).toBe(true); only(row).click(); expect(open).toHaveBeenCalledWith(person);
    const flat = personRow(person); expect(flat.classList.contains('is-link')).toBe(false); expect(flat.querySelector('button')).toBeNull();
    const board = podium([{ rank: 1, row: person, figure: '9 XP' }, { rank: 2, row: { id: 'u2', name: 'Jonah' }, figure: '8 XP' }], 'me', id => (id === 'u1' ? open : undefined));
    expect(board.querySelectorAll('.podium-place.is-link')).toHaveLength(1); expect(only(board.querySelector('.place-1')).textContent).toBe('Mara');
    expect(board.querySelector('.place-2').querySelector('button')).toBeNull(); expect(board.querySelector('.place-2 .podium-name').textContent).toBe('Jonah');
  });
  it('theOverviewCardsAreOneLinkEachAndTheExerciseChipsLinkToTheirLifts', async () => {
    const workout = { origin: 'synced', id: 'w9', localId: 9, title: 'Pull day', startedAt: '2026-10-25T10:00:00Z', completedAt: '2026-10-25T11:00:00Z' };
    const lift = snapshot.lifts[0], root = document.createElement('main'); document.body.append(root);
    await renderOverview(root, { source: { userId: 'me', listWorkouts: async () => [{ ...workout, userId: 'me' }], getSets: async () => [{ exerciseName: lift.name, setIndex: 0 }, { exerciseName: 'Made-up lift', setIndex: 0 }] }, snapshot, router: router() });
    for (const tile of root.querySelectorAll('.kpi.is-link')) only(tile);
    only(root.querySelector('.last-workout.is-link'), '.lw-name.stretch');
    const chips = [...root.querySelectorAll('.last-workout .chips .chip:not(.pr-tag)')];
    expect(chips.map(c => [c.textContent, c.tagName])).toEqual([[lift.name, 'A'], ['Made-up lift', 'SPAN']]);
    expect(chips[0].getAttribute('href')).toBe(`#/lifts/${encodeURIComponent(lift.name)}`);
  });
  it('inertThingsAreDrawnFlat', () => {
    const theme = css('theme.css'), friends = css('friends.css');
    const rule = (text, selector) => text.split('\n').find(line => line.startsWith(`${selector} {`)) ?? '';
    for (const [text, selector] of [[theme, '.chip'], [theme, '.tile'], [friends, '.podium-place']]) expect(rule(text, selector), selector).not.toMatch(/border: 1px/);
    expect(rule(theme, '.calendar-day')).toContain('border: 1px solid transparent'); expect(rule(theme, '.calendar-day.has-workout')).toContain('border-color');
    expect(theme).toContain('.is-link .stretch::after { content: ""; position: absolute; inset: 0; }');
    expect(css('routines.css')).toContain('.week-day { display: grid; gap: 4px; align-content: start; min-height: 64px; padding: 8px 6px; border: 1px solid transparent;');
  });
});

describe('lifts', () => {
  const lifts = (ctx, name) => { const root = document.createElement('main'); document.body.append(root); renderLifts(root, { snapshot, router: router(), ...ctx }, name); return root; };
  it('listEntriesAreRealLinksWithOneTabStop', () => {
    const root = lifts({}); const entries = [...root.querySelectorAll('.lift-item')];
    expect(entries.length).toBeGreaterThan(1); for (const entry of entries) expect(entry.tagName).toBe('A');
    expect(entries[1].getAttribute('href')).toBe(`#/lifts/${encodeURIComponent(snapshot.lifts[1].name)}`);
    expect(entries.filter(e => e.getAttribute('tabindex') === '0')).toHaveLength(1);
  });
  it('theSearchTextSurvivesChoosingALiftButNotLeavingTheScreen', () => {
    const first = lifts({ sameScreen: false }); const input = first.querySelector('input'); input.value = 'pull'; input.dispatchEvent(new Event('input'));
    const picked = lifts({ sameScreen: true }, snapshot.lifts.find(l => /pull/i.test(l.name)).name);
    expect(picked.querySelector('input').value).toBe('pull'); for (const e of picked.querySelectorAll('.lift-item')) expect(e.textContent.toLowerCase()).toContain('pull');
    expect(lifts({ sameScreen: false }).querySelector('input').value).toBe('');
  });
  it('aVariationRowIsTheToggleAndOneAlwaysStaysOnTheChart', () => {
    const lift = snapshot.lifts.find(l => l.variations.length > 1), root = lifts({}, lift.name);
    const rows = [...root.querySelectorAll('.variation-table tbody tr')]; expect(rows.length).toBe(lift.variations.length);
    for (const row of rows) { expect(row.classList.contains('is-link')).toBe(true); only(row); }
    for (const toggle of root.querySelectorAll('.variation-toggle')) toggle.click();
    expect(root.querySelectorAll('.variation-toggle[aria-pressed="true"]').length).toBeGreaterThanOrEqual(1);
  });
});

describe('numbers meet nouns', () => {
  it('pluralAgreesWithTheFigure', () => {
    expect([0, 1, 1.0, 2, 14.5].map(n => plural(n, 'hard set'))).toEqual(['0 hard sets', '1 hard set', '1 hard set', '2 hard sets', '14.5 hard sets']);
    expect(plural(1, 'day')).toBe('1 day'); expect(plural(3, 'exercise')).toBe('3 exercises'); expect(plural(2, 'echo', 'echoes')).toBe('2 echoes');
  });
  it('theWeekStripAndRoutineListAgree', () => {
    const el = weekPlan(snapshot.routines.presets, { days: 1, hardSets: 1 }, 1, router());
    expect(el.querySelector('.week-plan-head').textContent).toBe('1 day · 1 hard set');
    const root = document.createElement('main'); document.body.append(root);
    const one = { ...snapshot.routines.presets[0], hardSets: 1 }; renderRoutines(root, { snapshot: { ...snapshot, routines: { ...snapshot.routines, presets: [one] } }, router: router(), nowMs: Date.UTC(2026, 9, 26) });
    expect(root.querySelector('.routine-figures').textContent).toContain('· 1 hard set');
  });
  it('theOverviewTilesReadInTheSingular', async () => {
    const one = { ...snapshot, profile: { ...snapshot.profile, streakDays: 1, longestStreakDays: 1 }, week: { ...snapshot.week, workouts: 1, hardSets: 1 }, trends: { ...snapshot.trends, records30d: 1 } };
    const root = document.createElement('main'); document.body.append(root);
    await renderOverview(root, { source: { userId: 'me', listWorkouts: async () => [], getSets: async () => [] }, snapshot: one, router: router() });
    const tiles = [...root.querySelectorAll('.kpi')].map(k => k.textContent);
    expect(tiles[0]).toContain('1 workout'); expect(tiles[0]).toContain('1 hard set'); expect(tiles[0]).not.toContain('hard sets');
    expect(tiles[1]).toContain('1 day'); expect(tiles[1]).toContain('Best: 1 day'); expect(tiles[2]).toContain('1 PR');
  });
});

describe('light and dark text stays readable', () => {
  const rgb = hex => [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16));
  const lin = v => { v /= 255; return v <= .03928 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4; };
  const lum = c => .2126 * lin(c[0]) + .7152 * lin(c[1]) + .0722 * lin(c[2]);
  const ratio = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + .05) / (Math.min(x, y) + .05); };
  const mix = (a, b, share) => a.map((v, i) => v * share + b[i] * (1 - share));
  for (const mode of ['dark', 'light']) {
    it(`${mode}TextTokensMeet4_5To1OnCardAndPage`, () => {
      const paper = tokens[mode].paper;
      for (const ground of [paper.Vault, paper.Abyss].map(rgb)) {
        expect(ratio(rgb(paper.InkMuted), ground), 'muted').toBeGreaterThanOrEqual(4.5);
        expect(ratio(mix(rgb(paper.InkMuted), rgb(paper.Abyss), .9), ground), 'faint (90% muted, as theme.css mixes it)').toBeGreaterThanOrEqual(4.5);
      }
      for (const [name, value] of Object.entries(tokens[mode].rarity)) expect(ratio(rgb(value), rgb(paper.Vault)), `rarity ${name}`).toBeGreaterThanOrEqual(4.5);
      for (const accent of accents[mode]) {
        expect(ratio(rgb(accent.tokens.text), rgb(paper.Vault)), `${accent.id} text`).toBeGreaterThanOrEqual(4.5);
        if (accent.tokens.reward) expect(ratio(rgb(accent.tokens.reward), rgb(paper.Vault)), `${accent.id} reward`).toBeGreaterThanOrEqual(4.5);
      }
    });
  }
});
