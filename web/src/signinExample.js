// Static, clearly labelled example for the sign-in page. It is not account data, not a test fixture and makes no request.
export const exampleWorkout = {
  profile: { name: 'Example lifter' },
  when: 'Fri 17 Oct', meta: '07:12 · 58 min', xp: 340, title: 'Pull · Upper B', stats: '17 hard sets · 9.8 t', unit: 'kg', figure: 'male',
  prs: [{ label: 'Pull-up . Weighted', weightKg: 12.5, reps: 6 }],
  chips: ['Pull-up · Weighted', 'Pull-up · Archer', 'Barbell Row'],
  levels: { LATS: 3, RHOMBOIDS: 2, TRAPS: 2, BICEPS: 2, REAR_DELTS: 2, BRACHIALIS: 2, FOREARMS: 2 }
};
const values = [80, 81, 81, 83, 85, 84, 87, 88, 90, 91, 92, 94, 95, 94, 97, 99, 101];
const records = new Set([6, 9, 12, 14, 16]);
const day = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', timeZone: 'UTC' });
export const exampleChart = {
  title: 'Pull-up · estimated 1RM',
  points: values.map((value, i) => { const at = Date.UTC(2026, 5, 1 + i * 7); return [at, i + 1, value, records.has(i) ? 1 : 0, day.format(at), `${value} kg`]; })
};
