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
    inbox: async () => ({
      seenAtMs: hours(5),
      items: [
        { kind: 'request', occurredAtMs: hours(1), actorId: 'u-ines', actorName: 'Ines Vale', sessionId: null, headline: '', commentId: null, body: '', reaction: null },
        { kind: 'comment', occurredAtMs: hours(2), actorId: 'u-jonah', actorName: 'Jonah Reed', sessionId: 's3', headline: 'Upper B', commentId: 'c1', body: 'Great bench numbers', reaction: null },
        { kind: 'reaction', occurredAtMs: hours(3), actorId: 'u-mara', actorName: 'Mara Quill', sessionId: 's3', headline: 'Upper B', commentId: null, body: '', reaction: 'flame' },
        { kind: 'circle_join', occurredAtMs: hours(9), actorId: 'u-ines', actorName: 'Ines Vale', sessionId: null, headline: '', commentId: null, body: 'Iron Tide', reaction: null },
        { kind: 'accepted', occurredAtMs: hours(40), actorId: 'u-mara', actorName: 'Mara Quill', sessionId: null, headline: '', commentId: null, body: '', reaction: null },
      ],
    }),
    markInboxSeen: async () => {}, acceptFriend: async () => {}, removeFriend: async () => {},
    friends: async () => [
      { userId: 'u-mara', name: 'Mara Quill', level: 31, titleId: null, crestId: 'iron', accepted: true, incoming: false, acceptedAtMs: hours(24 * 90) },
      { userId: 'u-jonah', name: 'Jonah Reed', level: 18, titleId: null, crestId: null, accepted: true, incoming: false, acceptedAtMs: hours(24 * 30) },
      { userId: 'u-ines', name: 'Ines Vale', level: 22, titleId: null, crestId: null, accepted: false, incoming: true, acceptedAtMs: null },
      { userId: 'u-odin', name: 'Odin Hart', level: 9, titleId: null, crestId: null, accepted: false, incoming: false, acceptedAtMs: null },
    ],
    blocked: async () => [{ userId: 'u-x', name: 'Rook Danner' }],
    leaderboard: async () => [
      ['fixture', 'Fictional lifter', 50, 61200, 12, 18, 98000, 4], ['u-mara', 'Mara Quill', 31, 48200, 6, 9, 14200, 5], ['u-jonah', 'Jonah Reed', 18, 22100, 3, 5, 9100, 3],
      ['u-ines', 'Ines Vale', 22, 19000, 9, 7, 8800, 6], ['u-odin', 'Odin Hart', 9, 6400, 1, 2, 2100, 1], ['u-eve', 'Eve Marsh', 14, 5200, 0, 3, 1900, 0],
    ].map(([id, name, level, totalXp, streakDays, titlesCount, lifetimeStrength, sessionsLast7d]) => ({ id, name, level, totalXp, streakDays, titlesCount, lifetimeStrength, sessionsLast7d, titleId: null, crestId: id === 'u-mara' ? 'iron' : null })),
    liftBoard: async () => [
      ['fixture', 'Fictional lifter', 'squat', 8, 7], ['u-mara', 'Mara Quill', 'squat', 7, 6], ['u-jonah', 'Jonah Reed', 'squat', 7, null], ['u-ines', 'Ines Vale', 'squat', 5, 5],
      ['fixture', 'Fictional lifter', 'bench', 6, 6], ['u-mara', 'Mara Quill', 'bench', 4, null], ['fixture', 'Fictional lifter', 'muscle_up', 3, null],
    ].map(([id, name, lift, step, recentStep]) => ({ id, name, level: 20, titleId: null, crestId: null, lift, step, recentStep })),
    shadowBoard: async () => [['u-mara', 'Mara Quill', 12480, 6, 218.2], ['fixture', 'Fictional lifter', 9100, 5, 160.4], ['u-jonah', 'Jonah Reed', 3300, 1, 40]].map(([id, name, essence, shadows, ratePerHour]) => ({ id, name, level: 20, titleId: null, crestId: null, essence, shadows, ratePerHour })),
    unmute: async () => {}, unblock: async () => {}, requestFriend: async () => {},
    findHunter: async name => ({ id: 'u-new', displayName: name }),
    lifter: async id => ({
      userId: id, profile: { name: id === 'u-jonah' ? 'Jonah Reed' : 'Mara Quill', level: 31, titleId: null, crestId: 'iron', totalXp: 48200, streakDays: 6, titlesCount: 9, lifetimeStrength: 14200 },
      workoutsShared: 214, accepted: true, allySinceMs: hours(24 * 90), muted: false,
      sessions: [900, 880, 905, 870, 860, 840].map((strength, i) => ({ id: `ls${i}`, label: i % 2 ? 'Legs' : 'Pull', title: i % 2 ? 'Leg day' : 'Heavy pull day', note: '', completedAtMs: hours(20 + i * 70), xp: 120 + i * 5, strength, sets: 16 - i, editedAtMs: i === 2 ? hours(10) : null })),
      lifts: [{ lift: 'squat', step: 7, recentStep: 7 }, { lift: 'bench', step: 5, recentStep: 4 }, { lift: 'pull_up', step: 8, recentStep: null }, { lift: 'muscle_up', step: 2, recentStep: null }],
    }),
    lifterWorkout: async () => ({ sessionId: 'ls0', userId: 'u-mara', headline: 'Heavy pull day', note: 'Felt strong.', completedAtMs: hours(20), xp: 120, strength: 900, exercises: [
      { name: 'Back Squat', position: 0, sets: [{ setIndex: 0, reps: 5, weightKg: 100, durationSec: null, distanceM: null, grade: null, modifiers: '', done: true }, { setIndex: 1, reps: 5, weightKg: 110, durationSec: null, distanceM: null, grade: null, modifiers: '', done: true }] },
      { name: 'Weighted Pull-up', position: 1, sets: [{ setIndex: 0, reps: 8, weightKg: 17.5, durationSec: null, distanceM: null, grade: null, modifiers: '', done: true }] },
    ] }),
  });
}
