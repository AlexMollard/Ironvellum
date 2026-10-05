# Strength Rank overhaul — design

Date: 2026-10-05. Status: approved in chat, awaiting spec review.

## Problem

Strength Rank ([Rank.kt](../../../app/src/main/kotlin/com/ironvellum/app/domain/Rank.kt))
only reads the six TIERED lift boards (pull-up, dip, squat, bench, deadlift,
overhead press) by estimated 1RM over bodyweight. A calisthenics-heavy lifter is
ranked on whichever of those six they happen to log, and none of their skill
work counts.

The owner's own log (pulled from the phone 2026-10-05) shows it: one weighted
pull-up set (5 × +15.2 kg at 78.9 kg, ratio 1.391 against a 1.40 Intermediate
floor) is the only input, so the rank reads Novice while the same log clears
freestanding Handstand Push-up (tier 4) and weighted Pistol Squat (tier 3).

The mean-over-lifts rule also punishes breadth: logging one extra weak lift
lowers the rank.

## Goals

1. Calisthenics skills count toward the rank on equal terms with loaded lifts.
2. Averaging is fair: a best per movement pattern, so adding a weak lift or an
   untrained pattern never lowers the rank.
3. The lifter can see why they hold their rank and what moves it next.
4. Reaching a new band for the first time is a moment.

## Non-goals

- No XP for ranking up. XP drives Level and Ascension; a bonus would couple the
  systems and force a rescore of past sessions.
- No change to `LiftBoards`, the ally boards, or anything that leaves the
  phone. Cloud sync is untouched.
- No new strength standards. Lift floors stay Strength Level's; skill
  difficulty stays the skill tree's own `tier`.
- Core, Mobility and distance skills do not count.

## Scoring

All inputs are limited to the last `Rank.WINDOW_DAYS` (90) days, as today.

### Steps

The scale stays steps 0..10, two per band: 0-2 Untrained, 3-4 Novice,
5-6 Intermediate, 7-8 Advanced, 9-10 Elite (`Rank.forStep`, unchanged).

**Lift route.** Unchanged maths: `LiftBoards.marks(...)` filtered to
`LiftKind.TIERED`, step from `LiftBoards.stepFor` (Epley e1RM / bodyweight
against the sex-specific floors). Needs a bodyweight reading.

**Skill route.** A skill-tree skill (`Skills.ALL`) is *cleared* in the window
when either:
- a done, non-assisted set whose normalised exercise name equals the skill's
  name meets the skill's `target` in its `metric` (reps, or `durationSec` for
  SECONDS), or
- a non-claimed `SkillPractice` of that skill has `value >= target`.

Claims never count (an honours mark, not a measured figure — same rule as
`LiftBoards.marks`). Clearing a skill of tier N gives **step 2N − 1**: the lower
half of band N (tier 1 Untrained … tier 5 Elite). Needs no bodyweight.

Modifiers other than "assisted" (weighted, deficit, elevated) do not disqualify
a set: they only make it harder.

### Patterns

Each counted skill and each TIERED lift belongs to exactly one pattern.

| Pattern | Lifts | Skill lines | Per-skill (Rings, Movement) |
|---|---|---|---|
| Pull | pull-up | Pull, Lever | Ring Row, Ring Muscle-up, Banded Iron Cross, Iron Cross, Muscle-up, Strict Muscle-up, Inverted Muscle-up, Tuck Human Flag, Human Flag |
| Push | dip, bench, overhead press | Push, Handstand, Planche | Ring Support Hold, RTO Support Hold, Ring Dip |
| Legs | squat, deadlift | Legs | — |

Not counted: lines Core, Mobility, Squat, Bench, Press, Deadlift (the gym lines
are bodyweight-multiple standards the lift route already measures); skills
Weighted Pull-up and Weighted Dip (loaded — the lift route measures them);
Kip-up and Handstand-to-Bridge; any skill whose `metric` is METRES.

The pattern map lives in code as one explicit table. A test asserts every
`Skills.ALL` entry is either mapped or deliberately excluded, so a new skill
cannot silently fall out.

### Rank

1. Per pattern: the best step from either route.
2. Rank step = mean over the patterns that have a step, rounded down.
3. Band = `Rank.forStep(rankStep)`.
4. Unranked (null) when no pattern has a step.

A pattern with no qualifying work in the window is absent, never zero. The
rank can still fall: a pattern left untrained past the window drops out, and
best-in-window falls with detraining.

## Progress breakdown

A pure function returns, alongside the band:

- per pattern: step, band, the source behind it (lift name + set, or skill
  name), and a **next target**:
  - lift route: the added load (bodyweight lifts) or bar load needed at the
    same reps as the best set to reach the next step boundary, e.g.
    "+0.6 kg on pull-up × 5". Needs the step boundaries `stepFor` already
    computes, exposed as `internal` from `LiftBoards`.
  - skill route: the next skill that would raise the step — lowest tier above
    the current one in that pattern, preferring one whose prerequisites include
    the skill behind the current step, else catalogue order. Shown with its
    standard, e.g. "Clear Archer Push-up (8 reps per side)".
  - a pattern shows every route that has a next target, lift first. A route
    with no next target (Elite on that route, or no skill above the current
    tier) is omitted; a pattern with neither shows "Top of the scale".
  - the load target is rounded up to 0.1 kg so meeting it always clears the
    boundary.
- headline: steps still needed for the next band,
  `nextBandFloor × patternCount − sum(steps)`, e.g. "2 steps to Advanced".
  Omitted at Elite.

## UI

- The rank chip on the dashboard ([DashboardScreen.kt:645](../../../app/src/main/kotlin/com/ironvellum/app/ui/dashboard/DashboardScreen.kt))
  becomes tappable and opens a new `RankSheet` (own file under
  `ui/components/`) with the breakdown above. The dashboard edit is limited to
  the click handler and sheet state.
- `TermInfo.RANKS` copy is rewritten for the new rule (patterns, skills count,
  core does not, untrained patterns are left out).
- `docs/GLOSSARY.md` Strength Rank row is updated to match.

## Rank-up moment

- Persist the highest band ever held as an index in SharedPreferences
  (`rank_state.xml`, key `highest_band`).
- First computation after this ships: if the key is absent, write the current
  band silently. The scoring change itself is not a rank-up.
- On sealing a session, compute the rank after the seal. If its band index is
  above `highest_band`, set `rankUp: String?` on `Repository.CompletionResult`
  and store the new highest.
- `VictoryOverlay` shows a "RANK UP · Intermediate" beat with a
  `HapticFeedbackType.Confirm`, after the peaks and before the XP bar.
- Fires once per band ever: dropping and regaining a band does not repeat it.
  Imported sessions never trigger it (they are not sealed through the
  completion path).

## Testing

Plain JVM tests (domain is pure Kotlin):

- `RankTest` rewritten:
  - the owner's real log → Pull 4, Push 7, Legs 5 → Intermediate.
  - bench-only lifter at 1.3 × BW → Intermediate (absent patterns excluded).
  - adding 60 s hollow holds and 10 hanging leg raises to the owner's log does
    not change the rank (Core excluded).
  - a weak extra lift in an existing pattern does not lower it (best-of).
  - assisted set and claimed practice do not clear a skill.
  - a set 91 days old does not count.
  - skill-only lifter with no weigh-in is ranked.
  - a weighted Pistol set clears Pistol Squat.
- pattern-table completeness test over `Skills.ALL`.
- progress: pull-up next target for the owner reads +0.6 kg at × 5; headline
  step count is correct; no headline at Elite.
- rank-up: absent key initialises silently; higher band sets `rankUp`; regain
  after drop does not.
- `GlossaryTest` stays green after the glossary edit.

Then `python3 tools/gate.py`, and a visual check of `RankSheet` and the Victory
beat on the owner's phone via `:app:installFossDebug`.

## Worked examples

1. Owner: Pull 4 (pull-up 5 × +15.2 kg), Push 7 (7 freestanding HSPU clears
   tier 4), Legs 5 (8 weighted pistols clear tier 3). Mean 5.33 → step 5 →
   **Intermediate**.
2. Bench-only lifter at 1.3 × BW: Push step 5, no other pattern →
   **Intermediate**.
3. Owner adds 60 s hollow holds and 10 hanging leg raises: Core does not count
   → still **Intermediate**.
