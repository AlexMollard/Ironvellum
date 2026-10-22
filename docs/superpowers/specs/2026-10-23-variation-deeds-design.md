# Variation deeds: design

Approved by the owner on 2026-10-23. Sixteen new deeds for variations, now that every variation keeps its own
records. Names stay themed in both vocabulary modes, like every existing deed. 105 deeds become 121.

"Easier" means a variation whose modifiers `Variation.eases()` (assisted, incline). "Harder" means archer,
one-arm, decline, elevated or deficit. "Plain" means no splitting modifier (`Variation.key` is empty). Only
done working sets count (no warm-ups), as for every other deed.

## Unbound: moving off the easier version

| Name | Rarity | Earned when |
|---|---|---|
| Cast Off the Crutch | Common | A plain pull-up, chin-up or dip set, after an assisted set of that lift was logged earlier |
| Level Ground | Common | A flat push-up set, after an incline push-up set was logged earlier |
| Off the Leash | Rare | Moved off the easier version (as above, any lift) of 3 different lifts |
| No Hand Beneath | Fabled | A plain set whose reps beat the best assisted set's reps of the same lift |

## The Harder Road

| Name | Rarity | Earned when |
|---|---|---|
| Below the Floor | Common | First deficit set |
| Headlong | Common | First decline or feet-elevated push-up set |
| Bowstring | Rare | 5 reps of an archer pull-up or push-up in one set |
| Half the Hands | Fabled | First one-arm set |
| The Long Way Down | Rare | A deficit variation's best estimated 1RM beats the plain lift's best |

## Many Roads

| Name | Rarity | Earned when |
|---|---|---|
| Curious Iron | Common | 5 distinct variations logged (distinct `Variation.liftKey` with a non-empty key) |
| Many Roads | Rare | 15 distinct variations |
| Cartographer | Fabled | 40 distinct variations |
| Every Face of the Stone | Masterwork | Every single-modifier variation that `applicableModifiers` offers one lift (splitting tokens only), each logged at least once |

## Burdens

| Name | Rarity | Earned when |
|---|---|---|
| Twofold Burden | Rare | One set carrying two harder modifiers |
| Threefold Burden | Fabled | One set carrying three harder modifiers |
| The Unkind Angle | Masterwork | 5 reps in one set of a push-up that is archer, deficit and feet-elevated |

## Also in this work

- Hundred Risings and Two Hundred Suns (SessionReps) stop counting easier-variation reps, matching the
  strength deeds.
- Older clients show a blank name for a deed id they do not know (on a friend's profile or the leaderboard).
  Show a fallback name instead wherever a deed id from the cloud is rendered, so the next release copes with
  the release after it.
- No schema or cloud change: unlocks are stored by id.
- Each deed gets a description line in the existing voice, unit tests for its rule, and the ladder and rarity
  counts the deed tests check are updated.
