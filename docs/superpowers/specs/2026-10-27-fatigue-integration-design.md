# Fatigue in more places

Date: 2026-10-27. Owner asked to "integrate the fatigue system more heavily into the existing systems", then chose all four integrations below. Builds on `2026-10-23-muscle-fatigue-design.md`. Approved by the owner 2026-10-27 with the mockups (Ironvellum Clean screens canvas, Fat* boards).

## Intent

The fatigue estimate today is something you look at (Muscles screen, Train card, exercise sheet, plan builder). It should also help at the moments a decision is made: before a workout, while adding an exercise, and when shaping the week.

## Goals

1. **Today: adjust or lighten.** On a day whose routine hits a muscle that is still High or Very high, the Today card says so and offers to adjust today's workout only.
2. **Live picker: marks and fresh first.** Adding an exercise mid-workout marks those that lean on a High or Very high muscle and lists fresher ones first.
3. **Temper: recurring tired starts.** Temper finds a routine that keeps starting on a tired muscle and offers to move a day, trim the routine that causes it, or keep it.
4. **Today: rest or another routine.** When most of a scheduled routine's muscles are High or above, Today suggests resting or doing a fresher routine instead.

## Non-goals and anti-goals

- **Nothing changes by itself.** Every integration is a suggestion the lifter accepts with a tap. This narrows the fatigue spec's non-goal ("auto-adjusting plans, loads or days"): suggestions are now allowed, automatic changes still are not.
- **No new fatigue maths.** Everything reads the existing `FatigueReport` (`levelAt`, `reading`, `PlanFatigue.nextEightAm`).
- **No nagging.** Each suggestion can be dismissed for the day, and one setting turns all four off.
- **No change to how fatigue is measured or shown elsewhere.**

## Shared rules

- **Main muscles.** An exercise's main muscles are those with a share of at least `FatigueTuning.TRAINS_SHARE` (0.5) in `MuscleMap.profile` with its modifiers. This is the same rule `PlanFatigue.dayNotes` uses.
- **Trigger.** The trigger level is High or above (`FatigueLevel.HIGH`). Moderate never triggers anything.
- **When it is measured.** For today, levels are taken now. For another day shown on Today, they are taken at `PlanFatigue.nextEightAm` for that day and labelled as projected.
- **Setting.** One switch, "Fatigue advice", under Training settings, on by default, controls all four. When it is off, nothing new appears; the Muscles screens are unaffected.
- **Dismissals.** "Not today" is keyed by date and routine, reusing the `SuggestionDismissals` SharedPreferences pattern. Temper's "Keep as is" is a standing dismissal per routine and muscle, like `DismissedHeavyStore`. It comes back if the pattern changes muscle.

## 1. Today: adjust today's workout

**When it shows.** On a BEGIN or PLANNED day, when at least one exercise has a main muscle at High or above.

**What it shows.**
- A line inside the day card, above the exercise lines: the worst muscle, its level and its fresh-by day, how many exercises lean on it, and an **Adjust** action.
- Each affected exercise line carries a small level swatch and the muscle's name.
- Mockup: `FatTodayAdjust`.

**The Adjust sheet** (mockup `FatAdjustSheet`) lists each affected exercise with three choices:
- **Keep** (the default).
- **Lighten:** one set fewer, never below 1, and the target is one more rep in reserve.
- **Swap:** the top alternative from the existing ranker (`ProgramGenerator.easing` / `Easing.entryFor`, `Ranked` options) whose main muscles are below High.
  - The swap is offered only when such an alternative exists. Ties prefer the lifter's recent exercises.
  - Swaps go to dedicated exercises where a twin exists (`ExerciseTwins`), so the art and form guide follow.

**Begin with N changes** starts the session with the changes applied.
- **Mechanism:** an override passed into `Repository.startSessionFromPreset`: a swap map and a set trim plus RIR shift per entry.
- **The routine is never edited.**
- **Swap offer:** the post-session "you swapped, update the routine?" offer (`RiteSwap`) is not shown for swaps made here.

## 2. Live picker: marks and fresh first

`ExercisePickerSheet` gains an optional fatigue map, `Map<Muscle, FatigueLevel>`. Only the live workout passes it, from `rememberFatigueReport()`, which already includes the live session. Mockup: `FatPickerMarks`.

- **Marks.** A row whose exercise has a main muscle at High or above carries a swatch and "<Muscle> High" (or "Very high").
- **Order.** With no search text, rows sort by their worst main-muscle level, freshest first, within the existing grouping. Favourites stay pinned. With search text, match ranking wins and marks still show.
- **Note.** One quiet line explains the order: "Fresh muscles first · from your fatigue right now".
- **Other callers.** They pass nothing and are unchanged: the routine editor, Forge, the amend editor and import review.

## 3. Temper: a routine that keeps starting tired

A new finding kind in `OptimiseModel.buildReport`, `OptKind.RECOVERY`, listed with the existing heavy, overlap and spread findings. Mockups: `FatTemperFinding`, `FatTemperOptions`.

**Detection.** It uses logged history, not the plan alone. For each routine, take its last 3 completed workouts within 6 weeks. For each, rebuild the report at that workout's start (`FatigueModel.estimate` on the history before it) and read its main muscles. A finding appears when the same muscle was High or above at the start of at least 2 of those 3 workouts.

**The cause** is the most recent earlier workout that put the most dose into that muscle.

**Options** (radio, then Apply):
- **Move** the tired routine to the nearest day that gives the cause at least 1 more day of gap. This uses `Recovery.bestFitDays` with the current schedule, and is offered only if a free day exists.
- **Trim** one set from the cause routine's exercise that loads the muscle most, through the existing `TRIM` edit path. It shows the projected level afterwards.
- **Keep as is:** a standing dismissal for that routine and muscle.

The sheet shows when each workout began, with the muscle's level at each start.

## 4. Today: rest or another routine

**When it shows.** On a BEGIN day, when at least half of the routine's distinct main muscles are High or above now. It replaces the item 1 line on that card, so the two never show together. Mockup: `FatTodayRest`.

**What it shows.**
- A panel listing up to 3 of those muscles with their fresh-by days and the session that caused it.
- **Do <routine>:** offered only when another routine exists that was not completed this week and whose main muscles are all Moderate or below now. When several qualify, the freshest is chosen. It begins that routine.
- **Rest today:** dismisses the panel for today. Nothing else changes; the routine stays scheduled.
- **Begin <routine> anyway:** a quiet link.

## Temporary or permanent

| Integration | Changes | Lasts |
|---|---|---|
| 1. Adjust today | Today's workout only (swap, lighten) | That workout; the routine is untouched |
| 4. Rest or another routine | Nothing stored; starts another routine or hides the panel | Today |
| 2. Live picker | Nothing; marks and order only | While the picker is open |
| 3. Temper | The routine or weekly plan (move day, trim a set), only after Apply | Permanent, like every Temper change |

Mockups `FatRealTodayAdjust` and `FatRealTodayRest` draw items 1 and 4 on the real Today layout (header, level and oath, week rail, day card, peak and Veil rows). The card follows Today's fit rule: the fatigue line takes room from the exercise lines ("+N more") before the Veil compacts.

## Worked examples

1. **Monday Push Strength, lower chest still High.** The lower chest is still High from Wednesday's dips, at about 0.40 residual against the 0.35 to 0.60 Moderate band. Today shows "Lower chest is still High · Fresh by Tue · 2 exercises lean on it". Adjust offers Lighten for Weighted Dip and Swap Decline Push-up → Pike Push-up · deficit. Begin starts with those; Push Strength itself is unchanged.
2. **Mid-workout, adding a Push exercise.** The picker lists Pike Push-up and Overhead Press first. Weighted Dip and Decline Push-up sit lower, marked "Lower chest High".
3. **Back + Chest on Wednesday.** It found lats High at the start on 8 and 22 October, after Monday's Pull Strength. Temper offers to move it to Thursday or trim a Pull Strength row.
4. **Edge case: a routine with 4 main muscles, 2 of them High.** That is exactly half, so the rest panel shows. If no other routine has all its muscles at Moderate or below, the panel offers only Rest today and Begin anyway.
5. **Edge case: fatigue advice off.** Today, the picker and Temper look exactly as they do now.

## Constraints

- Every UI string goes in `strings.xml`, with `key` and `key__t` where a themed word appears ("trial", "rite"...).
- One tab row per screen; nothing here adds one.
- Every UI change is verified with emulator screenshots, dark and light.
- Tests:
  - the trigger and the half-rule, including the boundaries;
  - the swap choice is below High;
  - the override leaves the routine untouched;
  - picker ordering with and without a query;
  - Temper detection over a fictional history, including the 2-of-3 rule and the 6-week limit;
  - dismissals;
  - the setting.

## Left to the builder

- Exact wording beyond the mockups, kept in the app's plain voice with themed twins.
- Whether Lighten trims the last working set or the last back-off set when a scheme has back-offs. Prefer the last back-off.
- The order the four ship in. Suggested: 1 and 4 together (both on Today), then 2, then 3.
