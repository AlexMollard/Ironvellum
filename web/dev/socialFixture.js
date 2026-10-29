// Development only (imported from main.js behind `import.meta.env.DEV`): a scripted social source so the Friends screens open
// offline in `?fixture=1`. Every name is invented; nothing here is real account data.
import { createFakeSocial } from '../src/data/socialSource.js';

const hours = n => Date.now() - n * 3600000;
const feedRow = over => ({
  sessionId: 's1', userId: 'u-mara', name: 'Mara Quill', titleId: null, crestId: null, level: 31, label: 'Pull', title: 'Heavy pull day', note: '',
  completedAtMs: hours(3), durationSec: 4020, xp: 140, strength: 910, setsDone: 18, likeCount: 4, likedByMe: false, topMovements: 'Back Squat · Weighted Pull-up · Barbell Row',
  bestSet: '5 x 120.0 kg', commentCount: 2, reactions: { salute: 2, iron: 1, flame: 1 }, myReaction: 'iron', editedAt: null,
  card: { v: 1, hardSets: 14, volumeKg: 6120, prs: [{ label: 'Back Squat', reps: 5, weightKg: 120, durationSec: null }, { label: 'Weighted Pull-up', reps: 8, weightKg: 17.5, durationSec: null }], levels: { QUADS: 3, LATS: 3, BICEPS: 2, GLUTES: 2, HAMSTRINGS: 1 } }, ...over,
});

export function createFixtureSocial() {
  const comments = [
    { id: 'c1', sessionId: 's1', userId: 'u-jonah', authorName: 'Jonah Reed', body: 'That squat looked easy. Next week 125?', createdAtMs: hours(2) },
    { id: 'c2', sessionId: 's1', userId: 'fixture', authorName: 'Fictional lifter', body: 'Strong work.', createdAtMs: hours(1) },
  ];
  return createFakeSocial({
    feed: async ({ beforeMs } = {}) => (beforeMs != null ? [] : [
      feedRow({}),
      feedRow({ sessionId: 's2', userId: 'u-jonah', name: 'Jonah Reed', level: 18, title: '', label: 'Legs', completedAtMs: hours(20), xp: 90, commentCount: 0, reactions: {}, myReaction: null, card: null, durationSec: 3000 }),
      feedRow({ sessionId: 's3', userId: 'fixture', name: 'Fictional lifter', level: 50, title: 'Upper B', completedAtMs: hours(30), xp: 210, commentCount: 1, myReaction: null, reactions: { salute: 1 },
        card: { v: 1, hardSets: 17, volumeKg: 9800, prs: [{ label: 'Weighted Dip', reps: 8, weightKg: 30, durationSec: null }], levels: { CHEST: 3, TRICEPS: 2, DELTS: 1 } } }),
    ]),
    workoutFeedRow: async () => feedRow({}),
    react: async () => {}, mute: async () => {}, block: async () => {}, report: async () => {},
    reactors: async () => [{ userId: 'u-mara', name: 'Mara Quill', kind: 'salute' }, { userId: 'u-jonah', name: 'Jonah Reed', kind: 'iron' }],
    comments: async () => comments.map(c => ({ ...c })),
    addComment: async (_id, body) => ({ id: `c${Date.now()}`, sessionId: 's1', userId: 'fixture', authorName: 'Fictional lifter', body, createdAtMs: Date.now() }),
    deleteComment: async () => {},
    friends: async () => [],
  });
}
