import { describe, it, expect } from 'vitest';
import { formatSetLoad, addsToBody } from '../src/format.js';
import { setTable } from '../src/components/setTable.js';
import { formatWorkoutText } from '../src/workoutText.js';
const snapshot = { unit: 'kg', zone: 'UTC', lifts: [{ name: 'Pull-up', addsToBody: true }, { name: 'Barbell Row', addsToBody: false }] };
const set = (exerciseName, extra) => ({ exerciseName, exercisePosition: 0, setIndex: 0, reps: 6, weightKg: 60, kind: null, ...extra });
const cases = [
  ['a weighted pull-up', set('Pull-up', { weightKg: 12.5 }), '+12.5 kg'],
  ['a barbell row', set('Barbell Row'), '60 kg'],
  ['a bodyweight set', set('Pull-up', { weightKg: null }), 'BW'],
  ['an assisted set', set('Pull-up', { weightKg: -20 }), '-20 kg'],
];
describe('weight added to the body', () => {
  it('reads the per-lift flag the snapshot carries, ignoring case and spacing', () => {
    expect(addsToBody(snapshot, ' pull-up ')).toBe(true); expect(addsToBody(snapshot, 'Barbell Row')).toBe(false);
    expect(addsToBody(snapshot, 'Unlisted')).toBe(false); expect(addsToBody(null, 'Pull-up')).toBe(false);
  });
  it('formats the load with a plus only when it is added to a bodyweight movement', () => {
    expect(formatSetLoad({ weightKg: 12.5 }, 'kg', true)).toBe('+12.5 kg'); expect(formatSetLoad({ weightKg: 12.5 }, 'kg')).toBe('12.5 kg');
    expect(formatSetLoad({ weightKg: 0 }, 'kg', true)).toBe('BW'); expect(formatSetLoad({ weightKg: -20 }, 'kg', true)).toBe('-20 kg');
    expect(formatSetLoad({ weightKg: 25 }, 'lb', true)).toBe('+55 lb');
  });
  for (const [name, s, load] of cases) {
    it(`shows ${name} as ${load} in the set table`, () => {
      expect(setTable([s], null, snapshot).querySelector('tbody tr').children[2].textContent).toBe(load);
    });
    it(`shows ${name} in the copied text`, () => {
      const out = formatWorkoutText({ workout: { startedAt: '2026-10-17T07:12:00Z' }, entry: null, snapshot, sets: [s] });
      expect(out).toContain(s.weightKg == null ? '  1  BW × 6' : `  1  ${load} × 6`);
    });
  }
});
