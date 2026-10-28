import { describe, it, expect, afterEach } from 'vitest';
import { formatWorkoutText, groupSets } from '../src/workoutText.js';
import { setWordsMode } from '../src/i18n.js';
import { formatDay, formatWeekday } from '../src/format.js';
afterEach(() => setWordsMode(false));
const workout = { title: 'Pull · Upper B', startedAt: '2026-10-17T07:12:00Z', completedAt: '2026-10-17T08:10:00Z', note: 'Felt strong.' };
const entry = { min: 58, hardSets: 17, volumeKg: 9800, peakSets: ['Pull-up#0'] };
const snapshot = { unit: 'kg', effort: 'rir', rir: { 8: 2, 7: 3 }, zone: 'UTC' };
const set = (name, position, index, extra) => ({ exerciseName: name, exercisePosition: position, setIndex: index, reps: 6, weightKg: 60, kind: null, ...extra });
const sets = [
  set('Pull-up', 0, 0, { weightKg: 12.5, modifiers: 'Weighted' }), set('Pull-up', 0, 1, { weightKg: 12.5, reps: 5 }), set('Pull-up', 0, 2, { weightKg: 10, rpe: 8 }),
  set('Barbell Row', 1, 0, { reps: 8 })
];
const lines = text => text.split('\n');
const when = `${formatWeekday('2026-10-17')} ${formatDay('2026-10-17')}`;
describe('workout text', () => {
  it('writes the header, blocks, per-block set numbers, PR, effort and note', () => {
    const out = lines(formatWorkoutText({ workout, entry, snapshot, sets }));
    expect(out[0]).toBe('Pull · Upper B');
    expect(out[1]).toBe(`${when} · 07:12 · 58 min · 17 hard sets · 9.8 t`);
    expect(out.slice(2)).toEqual(['', 'Pull-up · Weighted', '  1  12.5 kg × 6  (PR)', '  2  12.5 kg × 5', '  3  10 kg × 6  RIR 2', '', 'Barbell Row', '  1  60 kg × 8', '', 'Notes: Felt strong.']);
  });
  it('leaves out the note, the stats and the effort when they are not there', () => {
    const out = formatWorkoutText({ workout: { ...workout, note: '' }, entry: null, snapshot: { unit: 'kg', zone: 'UTC' }, sets: [set('Row', 0, 0, { rpe: 8 })] });
    expect(out).not.toContain('Notes'); expect(out).not.toContain('min'); expect(out).not.toContain('RIR');
    expect(lines(out)[1]).toBe(`${when} · 07:12`);
  });
  it('uses pounds, RPE, bodyweight and assisted loads the way the set table does', () => {
    const out = formatWorkoutText({ workout, entry, snapshot: { ...snapshot, unit: 'lb', effort: 'rpe' }, sets: [set('Squat', 0, 0, { weightKg: 102.058, rpe: 9 }), set('Dip', 1, 0, { weightKg: null }), set('Pull-up', 2, 0, { weightKg: -20, reps: 10 })] });
    expect(out).toContain('  1  225 lb × 6  RPE 9'); expect(out).toContain('  1  BW × 6'); expect(out).toContain('  1  -44 lb × 10');
  });
  it('names warm-up and back-off sets, and holds and runs without reps', () => {
    const out = formatWorkoutText({ workout, entry, snapshot, sets: [set('Lift', 0, 0, { kind: 'warmup', weightKg: 40 }), set('Lift', 0, 1, { kind: 'backoff' }), set('Plank', 1, 0, { reps: 0, weightKg: null, durationSec: 45 }), set('Run', 2, 0, { reps: 0, weightKg: null, distanceM: 5000 })] });
    expect(out).toContain('  1  40 kg × 6  Warm-up'); expect(out).toContain('  2  60 kg × 6  Back-off'); expect(out).toContain('  1  45 s'); expect(out).toContain('  1  5 km');
  });
  it('keeps a repeated exercise as two blocks, each numbered from 1', () => {
    const repeated = [set('Row', 0, 0), set('Row', 0, 1), set('Curl', 1, 0), set('Row', 2, 0)];
    expect(groupSets(repeated).map(g => g.sets.length)).toEqual([2, 1, 1]);
    const out = formatWorkoutText({ workout, entry: null, snapshot, sets: repeated });
    expect(out.match(/^Row$/gm)).toHaveLength(2); expect(out.match(/^ {2}1 {2}/gm)).toHaveLength(3);
  });
  it('pads set numbers in a block of ten or more', () => {
    const many = Array.from({ length: 10 }, (_, i) => set('Row', 0, i));
    const out = lines(formatWorkoutText({ workout, entry: null, snapshot, sets: many }));
    expect(out[4]).toBe('   1  60 kg × 6'); expect(out[13]).toBe('  10  60 kg × 6');
  });
  it('uses the themed words when Names is Themed', () => {
    setWordsMode(true);
    const out = formatWorkoutText({ workout: { startedAt: workout.startedAt }, entry, snapshot, sets });
    expect(lines(out)[0]).toBe('Trial'); expect(out).toContain('(Peak)'); expect(out).not.toContain('(PR)');
  });
});
