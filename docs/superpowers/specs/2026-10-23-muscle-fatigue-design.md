# Muscle fatigue: design

Agreed with the owner on 2026-10-23. Mockups: the "Muscle fatigue" row and the "App flow" board on the
Ironvellum Clean screens Design canvas (https://claude.ai/artifact/7CdzhVfKioJ2XGxWz9GXXz): TrainTabsPlan,
MusclesRightNow, MusclesBreakdown, MusclesThisWeek, ForgeFatigueLine, FatigueFootprint, FatigueExplainer,
AppFlow.

## Why

The owner wants to know why a muscle feels worked or sore, so they don't blame the wrong thing, and to
understand what each movement does to them. Helping plan comes second. The feature explains; it never
changes a plan on its own.

## Goals

- Estimate each muscle's current fatigue from logged training, and show it on a full-body heatmap.
- Explain each muscle's fatigue in plain words with rough shares ("about 60% from Tuesday's Romanian
  Deadlift").
- Flag when soreness (DOMS) is likely, and why.
- Show each exercise's typical fatigue footprint.
- Mention current fatigue in the plan builder without altering the plan.
- Give coverage (weekly hard sets per muscle) and fatigue one shared home and one shared map component.

## Non-goals

- Measuring the lifter. Everything is an estimate from logged sets and is labelled as one.
- Auto-adjusting plans, loads or days from fatigue.
- Soreness check-ins or per-user calibration (a possible later step, not now).
- Readiness scores, HRV, sleep or other Health Connect inputs.
- Any new cloud sync: fatigue is computed on the device from local history and never uploaded.

## Anti-goals (failure even if it works)

- Implying soreness means a good workout. The explainer says plainly that it doesn't.
- False precision: no decimals on fatigue levels; shares are rounded ("about 60%").
- A model that disagrees with coverage about what a hard set is.
- Making the Train screen harder to use.

## Where it lives

- **Train** gets two tabs: **Plan** (the current Train content minus the coverage block) and **Muscles**.
- **Muscles** has one switch, **Fatigue | Coverage**, over two separate maps of the same body figure.
  Fatigue is the default.
  - **Fatigue**: the heatmap, a legend, "Estimated from your last 7 days", and a "Most worked" list (top
    3–4 muscles with level, one-line cause, and "fresh by <day>").
  - **Coverage**: the existing weekly coverage view (hard sets vs the 4/week floor; covered, below, not
    trained, heavy). The `muscle_coverage` route stops being its own screen and opens Muscles on Coverage;
    every link that pointed at it (Train row, Optimise, plan builder coverage chip) lands there instead.
- **Tap a muscle** (Fatigue map) for the breakdown: level, contribution bar with each cause and its rough
  share, the soreness note if any, "back to light by <day>", and "How this is estimated ›". Each cause row
  opens that exercise's lift page.
- **Exercise info sheet** gains a fourth page, **Fatigue** (after About, Form, History): the exercise's
  fatigue footprint (where it lands, strong/medium/light), its tags (Stretch-heavy, Heavy whole-body), what
  3 hard sets at 1 rep left typically do and for how long, and one line on why it's more or less sore-making
  than a comparable lift.
- **Plan builder** (Proposed weekly plan): a compact "Muscles right now" row under the coverage chip naming
  what's still recovering and from which session, linking to Muscles · Fatigue; a quiet per-day note when a
  planned day hits a muscle still at Moderate or above ("Quads: Moderate by Monday · estimate"). Footer:
  nothing in the plan changed.
- **One coverage map everywhere** (owner-approved): setup's coverage preview, the plan builder's coverage
  preview and Muscles · Coverage all use the same Coverage map component, looking and behaving the same.

## The model

Reuse, don't duplicate: `HardSets.isHard` and `HardSets.logged` decide which sets count (warm-ups never;
RPE ≥ 7 when rated; unrated sets by the existing rule), and `MuscleMap.profile(name, modifiers)` gives each
muscle's share, with modifiers applied (incline/decline/elevated move chest credit; deficit sets stretch
bias). A set may carry several modifiers; all apply. Custom exercises use their own shares.

### Dose per set, per muscle

`dose = share × effort × stretch × systemic × novelty`

| Factor | Values |
|---|---|
| share | from `MuscleMap.profile` (1.0 prime mover … 0.25 synergist); only shares > 0 |
| effort (reps left) | failure or rest-pause 1.15; 0 → 1.0; 1 → 0.9; 2 → 0.8; 3 → 0.65; unrated hard set → 0.8 |
| stretch | 1.3 if the movement loads the muscle long (fatigue traits table, or a deficit modifier), else 1.0 |
| systemic | 1.2 for heavy whole-body lifts (axially loaded barbell squats and hinges), plus a 0.25 dose to lower back if it isn't already credited; else 1.0 |
| novelty | 1.4 if the lifter hasn't done this lift (variation-aware: `Variation.liftKey`) in the previous 21 days, falling to 1.2 on the 2nd exposure and 1.0 from the 3rd within 21 days |

Drop sets count as hard sets as `HardSets` already decides. Holds use the existing hold-to-reps rule.

### Recovery

Each muscle belongs to a recovery class with a half-life (time for residual fatigue to halve):

| Class | Half-life | Muscles |
|---|---|---|
| Fast | ~18 h (light in ~1.5 days) | calves, forearms, abs, obliques, tibialis, side delts, rear delts, rotator cuff, neck |
| Medium | ~24 h (light in ~2 days) | biceps, brachialis, triceps, lats, rhomboids, traps, front delts, serratus, adductors, abductors, hip flexors |
| Slow | ~36 h (light in ~3 days) | quads, hamstrings, glutes, upper/mid/lower chest, lower back |

Modifiers on recovery:
- A session dose to one muscle above 10 hard-set equivalents stretches that session's half-life by 25%.
- Lifters whose first logged workout is under 8 weeks ago get 20% longer half-lives.

Residual fatigue of a muscle at time t is the sum over every contributing set in the last 7 days of
`dose × 0.5^(hours since session / half-life)`. Session time is `completedAtMs ?: startedAtMs` (there is
no per-set timestamp). Each contribution is kept with its source (exercise, variation, session) so the
breakdown can rank causes and compute shares.

### Levels

The scale is relative to a reference: 6 hard-set equivalents of residual fatigue = 100%.

| Level | Residual |
|---|---|
| Fresh | under 15% |
| Light | 15–35% |
| Moderate | 35–60% |
| High | 60–85% |
| Very high | 85% and over |

"Back to light by <day>" is when residual falls below 35%, solved from the sum of decays. Shares in the
breakdown are each source's current residual over the muscle's total, rounded to the nearest 5%, top 3
shown, the rest as "other".

### Soreness likely

Shown for a muscle when, in the last 72 hours, it received at least 1.5 dose from sets with stretch > 1 or
novelty > 1, and it is now between 12 and 72 hours after that session. The note names the cause in plain
words ("Romanian deadlifts were new to you and load the stretched hamstring").

All constants above are starting estimates from the research the owner discussed, kept together in one
place so they can be tuned. They are not derived from the user's data.

## Visuals

- Fatigue map: the existing body figure (`BodyFigures`, `TappableFigure`, sex from `LocalBodySex`), five
  levels from bare to strong in the primary accent, with diagonal hatch on High and cross-hatch plus the
  caution tone on Very high, so level is never shown by colour alone. Legend under the figure.
- Coverage map: the existing coverage look (`CoverageFigure` states), unchanged.
- Light and Moderate must be distinguishable at legend-swatch size (the mockup's two opacities were too
  close).
- All copy in `strings.xml` (plain `key`, themed `key__t` where a concept word differs), per AGENTS.md.

## Constraints

- Computed on the device from local history; fast enough to render the Muscles tab without a visible delay
  on a year of history (the window is 7 days plus 21 days of history for novelty).
- Variation-aware throughout (combined modifiers are one variation).
- Every UI piece verified by an emulator screenshot (owner rule); the model has unit tests for each factor,
  decay, levels, shares, the soreness rule and the light-by day.

## Also in this work (owner-approved, from the app-flow review)

- Rename the Progress tab's **Practice log** to **Skill practice** in plain mode (the themed name stays), so
  it isn't confused with History.
- Check that Account opened from Friends and from Settings is the same screen; make it so if not.

## Calls made after the builder review (designer's calls, not the owner's)

1. **Only hard sets count.** Fatigue reads exactly the sets `HardSets.isHard` counts, so it never disagrees
   with coverage. Rated sets are therefore RPE 7–10 (0–3 reps left); the effort table's "4+ → 0.4" row is
   dropped.
2. **Fatigue traits are their own data.** "Stretch-heavy" and "heavy whole-body" live in a fatigue-only
   table keyed by exercise name (plus the deficit modifier for stretch). `ExerciseProfile.stretchBias` is
   not changed, so the plan generator and Optimise behave exactly as before.
3. **Days are local calendar days.** "Fresh/light by <day>" is the local day on which residual first drops
   below 35%: "today" or "tomorrow" when that close, the weekday name within 7 days, "in over a week"
   beyond, and no line at all when already below. The plan builder's per-day note gives the level expected
   at 08:00 local on that planned day's next date, and is omitted when that level is Light or Fresh.
4. **One Coverage map component is part of this feature.** Setup's preview, the plan builder's preview and
   Muscles · Coverage render the same component with the same states and legend. Previews keep their
   current interactivity (tap to select where they allow it today); only Muscles · Coverage is guaranteed
   interactive.
5. **Tabs and deep links.** Train opens on Plan. Muscles opens on Fatigue unless reached by a link that used
   to open `muscle_coverage` (Train's coverage row, Optimise, the plan builder's coverage chip), which opens
   it on Coverage. The chosen sub-tab and switch are remembered while the app is running, not across
   restarts, and a link always wins.
6. **A live workout counts as it happens.** Done hard sets of an in-progress workout are included, timed at
   "now", so the map reacts as sets are logged.
7. **"New lifter" means the earliest completed workout of any origin**, imported or restored included, is
   under 8 weeks old.
8. **The exercise Fatigue page is derived, never hand-written.** It applies the dose formula to that
   exercise's profile for 3 hard sets at 1 rep left with novelty 1.0. Per-muscle bands are Strong (dose ≥
   2.0), Medium (1.0–2.0), Light (under 1.0); muscles under 0.3 are omitted. "Typically" is the level and
   light-by time those 3 sets alone would produce on a fresh muscle. The "why" line is built from the traits
   ("loads the muscle long", "heavy whole-body lift", or neither) and compares with the highest-share
   alternative for the same lead muscle that lacks the trait, when one exists.
9. **Custom exercises** use their own MAIN/ASSIST shares with stretch 1.0 and systemic 1.0.
10. **Screenshots can be batched per stage**, as long as every piece of UI has an emulator screenshot before
    its stage is committed. Fatigue screens need fictional history seeded on the emulator (several
    workouts across the last 7 days, including a new and a stretch-heavy lift).

## Left to the builder

- Exact composable structure, state holders and where the model lives (a pure Kotlin `domain/` object is
  expected, like `HardSets`).
- Exact copy wording within the tone of the mockups.
- Which lifts count as "heavy whole-body" (start from barbell squat/front squat/deadlift/RDL/good morning
  patterns in `MuscleMap`), and which profiles need `stretchBias` added.
- How the tab state is remembered across visits.
