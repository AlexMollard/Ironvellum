import { describe, it, expect, vi, afterEach } from 'vitest';
import snapshot from './fixtures/snapshot-v1.json';
import { renderRoutines } from '../src/screens/routines.js';
import { setWordsMode } from '../src/i18n.js';

afterEach(() => { setWordsMode(false); document.body.innerHTML = ''; });
// Monday 26 Oct 2026, noon UTC: the fixture's zone has no offset surprises, and the default routine is Monday's.
const MONDAY = Date.UTC(2026, 9, 26, 12), WEDNESDAY = Date.UTC(2026, 9, 28, 12), THURSDAY = Date.UTC(2026, 9, 29, 12);
const mount = (over = {}, id, extra = {}) => {
  const root = document.createElement('main'), router = { go: vi.fn() }; document.body.append(root);
  renderRoutines(root, { snapshot: { ...snapshot, ...over }, router, nowMs: MONDAY, ...extra }, id);
  return { root, router };
};
const byName = name => snapshot.routines.presets.find(p => p.name === name);
const withRoutines = patch => ({ routines: { ...snapshot.routines, ...patch } });

describe('routines screen', () => {
  it('listShowsRoutinesWithDays', () => {
    const { root } = mount();
    const items = [...root.querySelectorAll('.routine-item')].map(i => [i.querySelector('.routine-link').textContent, i.querySelector('.routine-meta').textContent]);
    expect(items).toEqual([['Upper A', 'Mon · 2 exercises'], ['Lower A', 'Wed · 2 exercises'], ['Upper B', 'Fri · 4 exercises'], ['Lower B', 'Sun · 1 exercise'], ['Mobility', 'Unscheduled · 1 exercise']]);
  });
  it('weeklyPlanPlacesRoutinesOnTheirDays', () => {
    const { root } = mount(); const days = [...root.querySelectorAll('.week-day')];
    expect(days.map(d => d.querySelector('.week-routine')?.textContent ?? d.querySelector('.week-rest').textContent)).toEqual(['Upper A', 'Rest', 'Lower A', 'Rest', 'Upper B', 'Rest', 'Lower B']);
    expect(root.querySelector('.week-plan-head').textContent).toBe(`${snapshot.routines.week.days} days · ${snapshot.routines.week.hardSets} hard sets`);
  });
  it('theOpenRoutinesDayIsHighlighted', () => {
    const { root } = mount({}, String(byName('Upper B').id)); expect(root.querySelector('.week-day.is-open').dataset.day).toBe('5'); expect(root.querySelector('.routine-link[aria-current="true"]').textContent).toBe('Upper B');
    expect(root.querySelector('.routine-detail h2').textContent).toBe('Upper B');
  });
  it('anUnknownIdSaysSoAndShowsTodaysRoutine', () => {
    const { root } = mount({}, '999'); expect(root.querySelector('.routine-detail h2').textContent).toBe('Upper A');
    expect(root.querySelector('.routines-missing').textContent).toContain('Routine not found'); expect(root.querySelector('.routines-missing a').getAttribute('href')).toBe('#/routines');
  });
  it('opensTodaysRoutineThenTheNextScheduledDay', () => {
    expect(mount({}, undefined, { nowMs: WEDNESDAY }).root.querySelector('.routine-detail h2').textContent).toBe('Lower A');
    const thursday = mount({}, undefined, { nowMs: THURSDAY }).root; expect(thursday.querySelector('.routine-detail h2').textContent).toBe('Upper B');
    expect(thursday.querySelector('.week-day.is-today').dataset.day).toBe('4'); expect(thursday.querySelector('.week-today').textContent).toBe('Today');
  });
  it('routineDetailShowsSnapshotFiguresVerbatim', () => {
    const upperB = byName('Upper B'), { root } = mount({}, String(upperB.id));
    expect(root.querySelector('.routine-figures').textContent).toBe(`Friday · ~${upperB.minutes} min · ${upperB.hardSets} hard sets`);
    expect(root.querySelector('.routine-note').textContent).toBe(upperB.note);
    const rows = [...root.querySelectorAll('.routine-entry')];
    expect(rows.map(r => r.querySelector('.routine-figure').textContent)).toEqual(upperB.entries.map(e => e.figure));
    expect(rows[1].querySelector('.routine-scheme').textContent).toBe(upperB.entries[1].scheme); expect(rows[2].querySelector('.routine-scheme')).toBeNull();
    expect(rows[1].querySelector('.chip').textContent).toBe('Weighted'); expect(rows.map(r => r.querySelector('.routine-number').textContent)).toEqual(['1', '2', '3', '4', '5']);
    expect(rows[1].querySelector('.routine-load').textContent).toBe('12.5 kg'); expect(rows[3].querySelector('.routine-load').textContent).toBe('BW');
  });
  it('loadsReadInTheSnapshotUnit', () => {
    const { root } = mount({ unit: 'lb' }, String(byName('Lower B').id)); expect(root.querySelector('.routine-load').textContent).toBe('198.5 lb');
  });
  it('warmupAndSupersetMarkersShown', () => {
    const { root } = mount({}, String(byName('Upper B').id)); const rows = [...root.querySelectorAll('.routine-entry')];
    expect(rows[0].querySelector('.routine-warmup').textContent).toBe('Warm-up'); expect(rows[1].querySelector('.routine-warmup')).toBeNull();
    expect(rows[3].querySelector('.routine-superset').textContent).toBe('Superset 1'); expect(rows[4].querySelector('.routine-superset').textContent).toBe('Superset 1'); expect(rows[2].querySelector('.routine-superset')).toBeNull();
  });
  it('weekCoversUsesSnapshotStatesAndHeadline', () => {
    const { root } = mount(); const cover = root.querySelector('.routines-covers');
    expect(cover.querySelector('.routines-headline').textContent).toBe(snapshot.routines.week.headline);
    const states = Object.fromEntries([...cover.querySelectorAll('.body-region')].map(r => [r.dataset.muscle, r.dataset.level]));
    for (const [muscle, state] of Object.entries(snapshot.routines.week.covers)) if (states[muscle] !== undefined) expect(states[muscle]).toBe(state);
    expect(cover.querySelectorAll('.body-region.coverage').length).toBeGreaterThan(10);
  });
  it('theWorksMapUsesTheRoutinesLevels', () => {
    const { root } = mount({}, String(byName('Lower A').id)); const regions = [...root.querySelectorAll('.routine-detail .body-region.worked')];
    expect(regions.find(r => r.dataset.muscle === 'QUADS').dataset.level).toBe('3');
  });
  it('customExercisesListed', () => {
    const { root } = mount(); const rows = [...root.querySelectorAll('.custom-row')]; expect(rows.map(r => r.querySelector('strong').textContent)).toEqual(['Fictional lift', 'Fictional press']);
    expect(rows[1].textContent).toContain('(main)'); expect(rows[1].textContent).toContain('Bench');
  });
  it('customExerciseHasPlaceholderArt', () => {
    const custom = { id: 77, name: 'Fictional press', note: '', day: 2, minutes: 10, hardSets: 3, levels: {}, entries: [{ position: 0, name: 'Fictional press', art: null, label: '', sets: 3, reps: 8, loadKg: 20, figure: '3 × 8 @20 kg', scheme: null, modifiers: '', supersetGroup: null, warmup: false }] };
    const { root } = mount(withRoutines({ presets: [custom] }), '77'); expect(root.querySelector('.routine-entry .exercise-art-placeholder').textContent).toBe('F');
  });
  it('noEditControlsAnywhere', () => {
    for (const id of snapshot.routines.presets.map(p => String(p.id))) { const { root } = mount({}, id); expect(root.querySelectorAll('input, textarea, select, button, [contenteditable]')).toHaveLength(0); root.remove(); }
  });
  it('viewOnlyLineShown', () => { const { root } = mount(); expect(root.querySelector('.routines-viewonly').textContent).toBe('View only. Edit routines in the app.'); });
  it('noRoutinesShowsPlainEmptyState', () => {
    const { root } = mount(withRoutines({ presets: [], custom: [] })); expect(root.querySelector('.empty').textContent).toContain('No routines yet'); expect(root.querySelector('.routines-viewonly')).not.toBeNull(); expect(root.querySelector('.routine-item')).toBeNull();
  });
  it('olderSnapshotWithoutRoutinesShowsUpdateHint', () => {
    const { root } = mount({ routines: undefined }); expect(root.querySelector('.routines-hint').textContent).toContain('Sync the app'); expect(root.querySelector('.routine-item')).toBeNull();
  });
  it('themedWordsNameTheScreen', () => { setWordsMode(true); const { root } = mount(); expect(root.querySelector('h1').textContent).toBe('Rites'); });
});
