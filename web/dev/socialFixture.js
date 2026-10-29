// Development only (imported from main.js behind `import.meta.env.DEV`): a scripted social source so the Friends screens open
// offline in `?fixture=1`. Every name is invented; nothing here is real account data.
import { createFakeSocial } from '../src/data/socialSource.js';

export function createFixtureSocial() {
  return createFakeSocial({
    feed: async () => [],
    friends: async () => [],
  });
}
