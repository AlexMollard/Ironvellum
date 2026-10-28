// Development only, imported solely by the DEV-gated fixture branch of src/main.js; a production build never includes it.
// The generated "Fictional lifter" snapshot has no records or deeds, so every card would render empty in screenshots.
// This adds synthetic values on top of it, in the same shape the phone writes. Nothing here is real account data.
const DAY = 86400000;
export function withScreenshotData(snapshot) {
  const latest = Math.max(...Object.keys(snapshot.sessions).map(Number)), at = snapshot.builtAtMs;
  Object.assign(snapshot.profile, { streakDays: 23, longestStreakDays: 41, deedsEarned: 3 });
  Object.assign(snapshot.week, { workouts: 3, hardSets: 48, volumeKg: 41200, prevVolumeKg: 38900, xp: 340, plannedDays: 4 });
  Object.assign(snapshot.sessions[latest], {
    xp: 340, min: 58, hardSets: 17, volumeKg: 9800, peaks: 2, strength: 598,
    prs: [{ label: 'Pull-up . Weighted', reps: 6, weightKg: 12.5, durationSec: null }, { label: 'Pull-up . Archer', reps: 5, weightKg: null, durationSec: null }],
    groupLevels: { BACK: 3, SHOULDERS: 2, ARMS: 3 },
    muscleLevels: { LATS: 3, RHOMBOIDS: 2, TRAPS: 2, BICEPS: 3, REAR_DELTS: 2, BRACHIALIS: 2, FOREARMS: 2 }
  });
  if (snapshot.sessions[1]) Object.assign(snapshot.sessions[1], { xp: 340, min: 58, strength: 598, peaks: 1, peakSets: ['Pull-up#1'], prs: [{ label: 'Pull-up . Weighted', reps: 6, weightKg: 12.5, durationSec: null }] });
  snapshot.deeds = [
    { name: 'Below the Floor', description: 'Synthetic example deed.', rarity: 'Common', category: 'Volume', atMs: at - 5 * DAY },
    { name: 'Bowstring', description: 'Synthetic example deed.', rarity: 'Rare', category: 'Strength', atMs: at - 3 * DAY },
    { name: 'Iron Habit', description: 'Synthetic example deed.', rarity: 'Epic', category: 'Workouts', atMs: at - 22 * DAY }
  ];
  Object.assign(snapshot.trends, {
    records30d: 7,
    recent: [
      { atMs: at - 3 * DAY, localId: latest, name: 'Pull-up', label: 'Pull-up . Weighted', art: 'pull-up', e1rmKg: 101, gainKg: 3 },
      { atMs: at - 4 * DAY, localId: latest, name: 'Barbell Squat', label: 'Barbell Squat', art: null, e1rmKg: 142, gainKg: 5 },
      { atMs: at - 16 * DAY, localId: latest, name: 'Weighted Dip', label: 'Weighted Dip . Deficit', art: 'weighted-dip', e1rmKg: 61, gainKg: 2 }
    ]
  });
  Object.assign(snapshot.fatigue, {
    levels: { ...snapshot.fatigue.levels, LATS: 3, RHOMBOIDS: 2, BICEPS: 2, REAR_DELTS: 2, TRAPS: 1 },
    worked: [
      { muscle: 'LATS', level: 3, cause: 'Friday’s pull-ups and rows', freshBy: 'light by Sun' },
      { muscle: 'BICEPS', level: 2, cause: 'Hammer curls and chins', freshBy: 'light by Sat' }
    ]
  });
  return snapshot;
}
