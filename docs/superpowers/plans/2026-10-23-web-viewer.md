# Web Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A website where a signed-in lifter sees their own workouts, lift progress, trends, fatigue, level, deeds and routines on a PC, showing exactly the numbers the phone computed, in the phone's own theme and wording; phase 2 adds the app's social side (feed, inbox, allies, boards, circle) with the same server rules. Editing training data stays in the app.

**Architecture:** The phone computes, the web displays. Workouts come from `sessions` and `session_sets` (plus an owner-only side table for set kind, RPE and superset group, and owner-only tables for imported workouts). Every derived figure of the owner's own data comes from one owner-only snapshot row the phone builds from its existing domain code and uploads after the workout push. Everything the web would otherwise retype from Kotlin (colour tokens, accent palettes, plain and themed words, crest, deed and lift-board catalogues, deed seals, exercise art index, body map) is generated from the app's code and checked byte-identical in the gate. Phase 2 reads and acts on social data through the same tables, views and RPCs the app uses, so every server rule applies unchanged. The site is a static Vite build under `web/` published to GitHub Pages by a local script.

**Tech Stack:** Postgres/RLS (baseline plus hosted patches, schema 30 then 31), Kotlin (CloudSync, pure snapshot builder, golden-file generators, JUnit4), plain JavaScript ES modules with JSDoc types, Vite, Vitest + jsdom, `@supabase/supabase-js`, Python (publish, body map and art copy), Node 22.

**Spec:** `D:\Monarch\docs\superpowers\specs\2026-10-23-web-viewer-design.md` ("Owner's answers", "Calls made after the builder review", "Second-pass mockups" and "Phase 2" are binding, plus the owner's 2026-10-23 theme requirement in Task 8A). Mockups, Design canvas row "Web viewer": phase 1 WebSignIn, WebOverview, WebWorkouts, WebWorkoutDetail, WebLift, WebTrends, plus WebOverviewVellum and WebOverviewCopper (theme variants); phase 2 WebFeed, WebInbox, WebAllies, WebBoards, WebCircle, WebRoutines. The spec wins where they differ (see the coverage table and its cut list).

Paths: `S = app/src/main/kotlin/com/ironvellum/app`, `T = app/src/test/kotlin/com/ironvellum/app`, `W = web`, `G = web/src/generated`. Phase 1 is Tasks 1-15, phase 2 is Tasks 16-26 and starts after Task 15's live acceptance. Tasks 5A and 8A sit between their neighbours (named, not renumbered, so cross-references hold).

**Prerequisite (fatigue work).** `FatigueReading.kt`, `MuscleFatigue.kt` and `FatigueInput.kt` are committed; `ui/program/FatigueMap.kt` and `FatiguePane.kt` (which hold `levelWord`, `freshByLine`, `causeLine`, `levelsOf`, `fatigueLook`) were another agent's uncommitted work when this plan was written. Task 5A and Task 6 may use them only once both files are committed on `main`; until then Task 5A writes its tests against the domain types alone. Work from a fresh worktree of `main`, and `git add` explicit paths only (never `-A`).

## Global Constraints

- **Training data is read-only on the web:** no logging, editing or deleting; no plan builder, live workout or settings; no account creation or password reset (the app does both). Phase 2 adds Friends and view-only routines; its writes are only the social ones the app already makes (reactions, comments, ally requests, mute, block, report, circle actions) through the app's own tables and RPCs, never a new write path.
- **The web computes nothing about the owner's own data.** Every derived figure (per-set PR marks, session volume and hard sets, e1RM, weekly sets, streak, level, fatigue, theme tokens, set-scheme lines) is read from the snapshot or a generated file. The web may only format (units, dates, rounding), filter, sort by a given key and group by a key. Display-rule ports (`Loads.snap/format`, RIR table) are pinned by parity cases generated on the phone. For other lifters' rows (phase 2) the only arithmetic allowed is the app's own lifter-summary and board-ordering functions, ported with parity cases generated from the phone's code.
- **Nothing is retyped from Kotlin.** Tokens, accents, words, catalogues, seals, art index and body map are generated; each generator has a golden or `--check` test run in the gate. A hand-copied hex colour or app sentence in `web/src` outside `generated/` fails `generatedOnly.test.js` (Task 8A).
- Every web query on `sessions`, `session_sets`, `imported_*`, `session_set_details` and `web_snapshots` that serves the owner's own screens filters on `user_id = auth.uid()` explicitly (the read policies also admit friends' and public sessions). Friend screens (phase 2) read other people's rows only through the existing read policies and never touch the owner-only tables (test `friendScreensReadNoOwnerOnlyTable`, Task 18).
- Nothing new becomes readable by an ally or a stranger, with one deliberate exception in phase 2: `sessions.feed_card` (Task 16), friend-safe by construction (PR lift names with load x reps as logged, hard sets, tonnes of loaded work, muscle levels 0-3; never an e1RM) and readable exactly as the session is (`can_view_session`). Body weight, measurements, height and Health Connect data never upload. Owner-approved 2026-10-23: the snapshot carries which body figure the muscle maps use (`figure`) and the phone's appearance (`appearance`: theme mode, accent palettes, plain or themed words); both owner-only. Body-weight-revealing figures (bodyweight e1RM) live only in the owner-only snapshot.
- The new uploads are automatic for signed-in users and owner-only (feed_card as above); no opt-in switch. `PRIVACY.md` is updated in the same release.
- Everything stays at no cost: one snapshot row per user, size-capped; no paid service; no Actions minutes. No workflow file is added or changed (AGENTS.md); the only automatic trigger is GitHub's own Pages deploy, which the owner allowed.
- Schema ships as baseline edit + idempotent hosted patch + `schema_version()` literal and `NEEDED_SCHEMA_VERSION` bumped together (RELEASE_CHECKLIST section 4): **30** in Task 1, **31** in Task 16. Older schema: the phone skips web uploads and says so; the web says which version it needs.
- Phone: strings in `res/values/strings.xml` (plain `key`, themed `key__t` only for a sentence naming a glossary concept); `StringsXmlTest`, `GlossaryTest`, `WordsFormsTest`, `HardcodedUiLiteralTest` after any strings change. There is no new phone screen, so no emulator screenshot is owed; the only visible text is sync problem lines. If a task finds it must change a phone screen after all, it needs an emulator screenshot (through `tools/device.py lock`) before commit. Web screens are checked with browser screenshots (Browser pane, `resize_window` to 1440x900 and 390x844, dark and light), saved to `D:\Monarch\.tmp\audit\web\`.
- Gradle: one build at a time (no parallel agents); emulator only through `python3 tools/device.py lock -- ...` on `emulator-5554`, never the phone `R5GL14GXV3J`. `python3 tools/gate.py` green and under 5 minutes before any push. Web tests, Python generators' `--check` and Supabase assertions never need Gradle, so they run while another agent builds.
- Web: no analytics, no third-party hosts (fonts, body map, exercise art and generated assets ship with the site); email and password first; "use my own backend" accepts any URL and key, remembered in the browser only; browser storage (`localStorage`) is wrapped in try/catch and the page renders correctly without it.
- **Appearance follows the phone** (owner, 2026-10-23): theme mode System / Dark / Light (Vellum), every accent preset of `AccentPresets` (dark `entries`, Vellum `light`) with all derived tokens, and plain or themed names. A per-browser Appearance menu may override (stored in `localStorage` only, never uploaded); "Match my phone" resets it.
- Commit style: imperative capitalised subject, no prefixes, no attribution, flat bullet body only when several changes are grouped; push to `main` when verified; no force-push, history rewrite or tags.
- File encoding: UTF-8 without BOM, CRLF; check the first three bytes after writing. Generators write CRLF like the repo's existing generated sources (`CrestMarks.kt`, `ExerciseArtIndex.kt`), byte-exact in their golden tests (SVG art files are copied byte for byte), and never a BOM.

## Mockup to snapshot coverage

Every number a mockup shows is backed or cut. `Snap.x` is a snapshot field (Tasks 3-5A, 24); `Gen.x` a generated file (Task 8A); `Srv.x` a server read (phase 2).

| Mockup figure | Source on the phone / server | Field | Note |
|---|---|---|---|
| Sign-in page, own backend, "Stats as of" | `Cloud.probe`, snapshot timestamp | `builtAtMs` | the sign-in preview (last-workout card, chart) is a bundled fixture labelled "Example", never a real account's data |
| Greeting "Rowan", plate initials, sidebar level | account display name, `Xp.progress` | `profile.name`, `profile.level` | "Good morning" is cut: it needs the browser clock; the page says "Rowan" only |
| Plate rim metal, "wears Ember Crest", "Plate bearer" | worn crest id, worn title | `profile.crestId`, `profile.titleId`, `Gen.catalogue` | names, tones and art come from the generated catalogue |
| This week: workouts, hard sets, volume, vs last week | `PeriodStats.weeks`, `HardSets.isHard`, `WorkoutShare.totals().movedKg` | `week.*` | "+6% on last" is cut: the web would divide; show both volumes |
| "+340 this week" XP | sum of `xp_awarded` over this week's workouts | `week.xp` | |
| "4 planned" | presets' `scheduledDay` count | `week.plannedDays` | hidden when null |
| Streak "23 days, best 41 days" | `Streak.current/longest` (days) | `profile.streakDays`, `longestStreakDays` | days, as the phone counts |
| Records "7 PRs, last 30 days"; recent records with art | `LiftRecords.gains(now-30d, now)` | `trends.records30d`, `trends.recent[]` (with `art`) | e1RM gains only; the lift page shows rep records |
| Level 23, 6,420 / 8,000 XP | `Xp.progress` | `profile.*` | |
| Deeds "41 of 121", rows with seal and rarity | `Titles.ALL`, earned list | `deeds[]`, `profile.deedsEarned/Total`, `Gen.catalogue.seals` | `earned_titles` holds ids only, so names ship in the snapshot; seal polygons and category glyphs are generated |
| Last workout card: PR tags, exercise chips, muscle map, hard sets, STR | `SessionPeaks`, `HardSets`, `WorkoutShare` | `sessions[id].{prs,muscleLevels,strength,hardSets,volumeKg}` | tag text is `label` + load x reps as the phone formats it |
| Last workout "x3 x1 x1 from allies", "2 comments" | `session_likes`, `session_comments` on the owner's own session | `Srv` (Task 19) | phase 1 hides the line; Task 19 fills it from `public_feed` |
| Inbox button badge "3" | `my_inbox`, `inbox_seen` | `Srv` (Task 20) | hidden until phase 2 |
| Muscles right now: map, levels, top cause, "light by Sun" (the phone says "Fresh by Monday"), "estimate" | `FatigueModel.estimate`, `mostWorked`, `FatigueLevel`, `causeLine`, `freshByLine` | `fatigue.*` (Task 5A) | as of `builtAtMs`, never "now"; the snapshot is rebuilt only at sync. Cause text is the phone's `causeLine` ("about 60% from Tuesday's Romanian Deadlift"), which replaces the mockup's looser "Friday's pull-ups and rows" |
| Workouts header "412 workouts since Mar 2024" | count and earliest start | `profile.workouts`, `profile.firstMs` | |
| Month calendar, "9 workouts this month, 3 per week", "October so far" bars, top lifts | per calendar month, last 24 | `months["YYYY-MM"]` = `{workouts, perWeek, weeks:[{start,hardSets}], topLifts:[{name,art,sessions}]}` | the web picks the month; it adds nothing |
| Workout row: minutes, hard sets, tonnes, XP, PR tag, muscle strip | `WorkoutShare.totals`, `HardSets`, `SessionPeaks` | `sessions[id].{min,hardSets,volumeKg,xp,prs,groupLevels}` | key = Room `local_id`. The strip has six cells (the six groups of `WebSnapshotTuning.GROUPS`, new, phone-chosen); the mockup drew seven |
| "from Strong" | not stored (`WorkoutSession.imported` is a flag) | none | cut: tag reads "Imported" |
| Detail: set kind, load, reps, reps-left | `session_set_details` / imported columns | rows | reps-left = `Snap.rir` lookup |
| Detail: per-set e1RM incl. bodyweight variations ("68 kg" on a BW archer set), PR tag | `BodyShare.e1rmAddedKg`, `SessionPeaks` | `sessions[id].e1rm`, `peakSets` | key `"<exercise_name>#<wire set_index>"`, owner-only |
| Detail: exercise header (art, variation chip, PR tag) | `ExerciseArtIndex`, `Variation.label` | `sessions[id].prs`, `lifts[].art` | the mockup's one-line captions ("Top set, then back-offs", "Kept apart from plain pull-ups") have no per-workout phone source and are cut |
| Detail: Records, Strength, XP, Hard sets, Volume | `peaks`, `strength_score`, `xp_awarded` | `sessions[id].*` | |
| Detail: friends line "x Mara, Jonah, Ines . 2 comments" | reactions and comments | `Srv` (Task 19) | hidden in phase 1 |
| Detail: muscles worked | `HardSets.logged` credit per muscle | `sessions[id].muscleLevels` | levels 0-3 chosen on the phone (`WebSnapshotTuning.LEVEL_*`) |
| Detail: "Your note" | public `sessions.note` | row | never the private note |
| Lift header: art, "Lats and upper back most, ..." sentence, PR tags, map | `ExerciseArtIndex`, `exerciseMuscleSummary`, `ShareLevel` | `lifts[].{art,muscleSummary,muscleLevels}`, `variations[].peak` | "vertical pull" is cut; "6 variations logged" is `variations.length`; custom exercises get a placeholder |
| Lift: variations side by side (best e1RM, best set, last done) | `ExerciseHistory.peak`, `lastMs` | `variations[].{peak,lastMs}` | "Last done" prints the weekday or date from `lastMs` in the snapshot zone; "Assisted -20 kg x 10" and holds ("5 x 1 at 5 s") come from `peak` (`weightKg` negative, `durationSec`) |
| Lift: chart, chips, diamonds, 6 months | `ExerciseHistoryCalculator` series per variation | `variations[].points` | diamond = `pr` flag set on the phone |
| Lift: Best e1RM, Heaviest, Best N reps | `ExerciseHistory.peak/heaviest`, `SetRecords` | `variations[]` | "Best 6 reps / 8 reps" are `setRecords` (best per set position), as the phone shows them |
| Trends: hard sets per week by muscle group (stacked) | `HardSets.inRange` per week + `WebSnapshotTuning.GROUPS` | `trends.weeks[].groups` | six groups chosen on the phone (Back, Chest, Legs, Shoulders, Arms, Core); the phone has no such grouping today |
| Trends: hard sets per week line, training days grid, "3.1 a week", longest streak | `PeriodStats.weeks`, day list | `trends.weeks`, `days`, `daysPerWeek`, `profile.*` | range tabs 4 weeks / 12 weeks / 1 year only filter `weeks` by index |
| Trends: Fatigue / Coverage switch, legends, rows | `FatigueModel` / `HardSets.inRange` 7 days + floor | `fatigue.*`, `trends.windows["7"]` | Coverage uses the 4-set floor state per muscle; the older per-muscle bar list and the 28 and 84 day windows are cut (not in the mockup) |
| Units, effort scale, theme, accent, names | `Units.current`, `EffortSetting.scale`, `AppearanceStore`, `Words.themed` | `unit`, `effort`, `appearance`, `words` | Task 8A |
| Routines: list, weekly plan, entries, scheme line, map, "~58 min, 17 hard sets", "week covers" | `WorkoutPreset`, `SchemeText.summary/figure`, `ProgramRules.sessionSeconds`, `coverageHeadline` | `routines.*` (Task 24) | |
| Feed card, comments, reactions, allies, boards, circle | server views and RPCs | `Srv` (Tasks 16-23) | see each task |

**Cut or replaced figures (no phone source):** "+6% on last" week volume; "Good morning"; "from Strong"; the mockup's rep-only "Records" rows ("5 reps", "24 reps"; they live on the lift page); "Most reps" and "Best volume per day" cards on the lift page; the "vertical pull" pattern label; the detail captions under exercise names; the seventh strip cell; the per-muscle bar list and 28 and 84 day windows; Boards: "You're 3rd: 7,890 XP behind Mara. About two good weeks" (no phone function; the rank number comes from board order only); Allies: "Iron Tide" next to another lifter (`my_circle` answers only about my own circle); Routines: the "3 h 50 min" week total (no phone function; per-routine minutes use `sessionSeconds`).

## Review Focus

Inputs the spec implies and no obvious test covers; each has a test in its owning task.

1. **Account with no snapshot yet** (new web user, phone not updated or not synced) and a **backend behind schema 30** (or 31 for phase 2) or a **snapshot newer than the web knows**: a plain message, never a blank page or a thrown error; workouts still list without derived figures. Tests: Task 9 `noSnapshotShowsHint`, `oldSchemaNamesNeededVersion`, `newerSnapshotAsksForUpdate`; Task 2 `outdatedBackendSkipsWebUploads`.
2. **Other lifters' rows in a mixed response** (friends' and public sessions are RLS-readable): never listed, never counted on owner screens. Tests: Task 8 `everyQueryFiltersOnOwnUserId`, Task 9 `listIgnoresForeignRows`; Task 1 `allyReadsNothingFromNewTables`; Task 18 `friendScreensReadNoOwnerOnlyTable`.
3. **Re-pushing history must not resurface old workouts** in feeds, the inbox or circle weeks (twice: schema 30 and the feed_card re-push). Tests: Task 1 and Task 16 `repushLeavesFeedInboxAndCircleUntouched`.
4. **More than 1000 workouts** (PostgREST max-rows) and **three to five years of history** (snapshot size with fatigue, months and routines added, builder time). Tests: Task 9 `pagesPastTheThousandRowCap`, Task 3 `fiveYearSnapshotFitsUnderCap`, Task 4 `threeYearBuildIsFast`.
5. **Odd sets and units**: rows from an older client (null kind), holds (reps 0, seconds), distance activities, repeated exercise blocks, lb users (102.058 kg shown as 225), imported-only accounts, zero workouts, a custom exercise with no art. Tests: Task 10 `olderClientSetsReadAsWorking`, `holdAndActivityRows`, `customExerciseShowsPlaceholderArt`, Task 8 `loadFormatParity`, Task 9 `importedOnlyAccount`, Task 4 `repeatedMovementKeysMatchWireIndexes`.
6. **Time zone**: calendar, week labels and "fresh by Monday" use the snapshot's zone, not the browser's. Test: Task 9 `calendarUsesSnapshotZone`.
7. **Phone width**: every screen usable at 390 px (sidebar becomes a bottom bar). Check: screenshot in each web task.
8. **Stale fatigue**: the reading is as of the last sync. The page says "as of <time>" and never extrapolates; a reading over 24 h old shows a "sync the app for a fresh reading" line. Test: Task 9 `fatigueCardShowsAsOfAndStaleHint`.
9. **Appearance edge cases**: storage blocked or full, an unknown accent preset or a custom colour from the phone, light text on a light accent. Tests: Task 8A `appearanceWorksWithoutStorage`, `customPhoneAccentUsesSnapshotTokens`, `textOnAccentMeetsContrast`.
10. **Friend-safety leak check**: no e1RM and no figure derived from body weight in any friend-visible payload; a PR tag never carries an e1RM. Tests: Task 16 `feedCardIsBoundedAndShapeChecked` (SQL), Task 17 `feedCardHasNoBodyweightDerivedField`.
11. **Server limits read as plain sentences**, not codes: comment rate (10 a minute), daily (200) and per-workout (200) limits, 280 characters, 20 ally requests a day, 50 failed circle joins a day, 20 reports a day, keeper-only actions. Tests: Tasks 19, 21, 23 `serverLimitsSurfaceAsPlainMessages`.
12. **Blocked, muted, hidden and deleted people**: a hidden profile reads with the neutral "Ironbound" name, a muted lifter is absent from the feed, a block removes the friendship. Tests: Tasks 19, 21 `hiddenProfileUsesNeutralName`, `mutedLifterNotInFeed`.

---

### Task 1: Schema 30, hosted patch, assertions

**Files:**
- Modify: `supabase/migrations/0001_baseline.sql` (new tables, policies, `schema_version()` literal 29 to 30)
- Create: `supabase/hosted/2026-10-23-web-viewer.sql` (idempotent, cut verbatim from the baseline, header in the style of `2026-10-09-crests.sql`)
- Modify: `supabase/reset.sql` (drop the 4 new tables), `supabase/test/reset_seed.sql` (one row in each), `supabase/test/assert_all.sql`, `tools/gate.py:~308` (newest patch path), `S/data/cloud/Cloud.kt:34` (`NEEDED_SCHEMA_VERSION = 30`), `T/data/cloud/HostedPatchTest.kt`, `supabase/SELF_HOSTING.md`, `docs/RELEASE_CHECKLIST.md` and `docs/TODO.md` ("hosted project is at schema 29" lines)

**Interfaces:**
- Produces (tables, all `enable row level security`, `revoke all ... from anon`, owner-only policies `user_id = auth.uid()` for select/insert/update/delete except where noted):
  - `session_set_details(user_id uuid not null references profiles(id) on delete cascade, session_id uuid, exercise_name text, set_index int, kind text check (kind in ('working','warmup','top','backoff','drop','failure','rest_pause')), rpe int check (rpe between 5 and 10), superset_group int check (superset_group between 0 and 100), primary key (session_id, exercise_name, set_index), foreign key (session_id, exercise_name, set_index) references session_sets (session_id, exercise_name, set_index) on delete cascade)`. Insert/update `with check` also requires `exists (select 1 from sessions s where s.id = session_id and s.user_id = auth.uid())` (a foreign key check bypasses RLS, so ownership is asserted here).
  - `imported_sessions(id uuid pk default gen_random_uuid(), user_id uuid not null references profiles(id) on delete cascade, local_id bigint not null, label text not null check (char_length(label) <= 80), title text not null default '' check (<= 80), note text not null default '' check (<= 500), started_at timestamptz not null, completed_at timestamptz, edited_at timestamptz, unique (user_id, local_id))`.
  - `imported_session_sets(id uuid pk, user_id uuid not null references profiles(id) on delete cascade, session_id uuid not null references imported_sessions(id) on delete cascade, exercise_name text (<= 64), exercise_position int, set_index int, reps int >= 0, weight_kg numeric(6,2), modifiers text (<= 64), done boolean, duration_sec int, distance_m numeric(9,2), grade text (<= 12), kind text (same list), rpe int, superset_group int, unique (session_id, exercise_name, set_index))`; policy `with check` requires the parent `imported_sessions.user_id = auth.uid()`.
  - `web_snapshots(user_id uuid primary key references auth.users(id) on delete cascade, version int not null check (version >= 1), snapshot jsonb not null, content_hash text not null, size_bytes int not null, updated_at timestamptz not null default now(), constraint web_snapshots_size_cap check (size_bytes > 0 and size_bytes <= 2 * 1024 * 1024))`; select/insert/update own, no delete policy (as `cloud_archives`).
- `schema_version()` returns 30. No view or function is changed, and none may mention the new tables (so no feed, circle, board, lift-mark, XP or crest path can read them).
- Decision (deviation from the spec's "columns on `session_sets`"): `session_sets_read` admits allies row by row and Postgres cannot hide a column from them but not the owner, so new columns would put RPE and set kind where an ally can read them via the API. The side table keeps the spec's binding line "nothing new readable by an ally or a stranger", and old clients and schema-29 servers keep working because `SessionSetDto` is unchanged.

- [ ] **Step 1: Write the failing assertions.** Add a `do $web$` block to `assert_all.sql` (new fixture ids in the `a5500000-...-0000000000c?` range; ally via an accepted `friendships` row, stranger with none; owner profile visibility `public`, an ordinary synced session + set owned by the owner). Add the four tables to the RLS list near line 284. Tests (each `assert_true`/`refused_as` with a message):
  - `ownerReadsAndWritesOwnRows`: owner inserts a `session_set_details`, an `imported_sessions` + `imported_session_sets` row and a `web_snapshots` row, reads each back.
  - `allyReadsNothingFromNewTables`, `strangerReadsNothingFromNewTables`, `anonReadsAndWritesNothing` (anon insert refused `42501`): counts are 0 for ally and stranger on all four, even with the owner's profile `public`.
  - `cannotWriteAsSomeoneElse`: inserting a row with another `user_id`, or a `session_set_details` row for an ally's session id with own `user_id`, refused `42501`.
  - `badValuesRefused`: kind `'x'` and rpe 4 and 11 refused `23514`; snapshot `size_bytes = 2*1024*1024+1` refused `23514`.
  - `accountDeletionCascades`: `delete_my_account()` as a fixture lifter leaves no row in any new table (mirror the `cloud_archives` check near line 1271).
  - `nothingReadsTheImportedTables`: `not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.prosrc ilike '%imported_session%')` and the same over `pg_views.definition`.
  - `repushLeavesFeedInboxAndCircleUntouched`: record `public_feed` order for the owner's sessions, `my_inbox()` row count for the ally and `circle_member_days` for a fixture circle; re-run `insert into sessions ... on conflict (user_id, local_id) do update set label = excluded.label` (same values) and the same for `session_sets`; assert the three are identical and `circle_events` count is unchanged. (Read `my_inbox()` first: if it ever reads `sessions.updated_at`, fix the function in the same patch and say so.)
- [ ] **Step 2: Run it, expect FAIL.** `docker run -d --rm --name pg -e POSTGRES_PASSWORD=probe -p 5432:5432 postgres:16`, then the stub, the baseline and `assert_all.sql` with `psql -v ON_ERROR_STOP=1` as in RELEASE_CHECKLIST section 4. Expected: `ASSERTION FAILED` on the missing tables.
- [ ] **Step 3: Edit the baseline** in idempotent forms (`create table if not exists`, `drop policy if exists` then `create policy`), tables placed after `cloud_archives`; policies and revokes in the existing policy and grants sections; literal 29 to 30. Edit `reset.sql` (children before parents) and `reset_seed.sql` (one row per new table, and add them to its "fixtures landed" guard).
- [ ] **Step 4: Cut the hosted patch** verbatim from the baseline: tables, policies, revokes, beacon 30, `begin; ... commit;`, header saying it is idempotent and "apply AFTER 2026-10-09-crests.sql and BEFORE shipping the build that expects schema 30". Update `HostedPatchTest`: add the file to `applied`; change `the crests patch carries the schema the app needs` to assert the crests beacon is the literal 29 and add `the web viewer patch carries the beacon and every new table` (beacon equals `NEEDED_SCHEMA_VERSION`, `create table if not exists` count 4, the baseline has the same four). In `tools/gate.py backend()` replace the crests path with the new patch (the crests patch still applies first on a pre-29 project, but the gate only re-runs the newest). Bump `Cloud.kt`.
- [ ] **Step 5: Run the SQL steps again, expect PASS,** then the idempotency pass: apply the baseline twice, then the new patch twice, then `assert_all.sql`; then `reset_seed.sql`, `reset.sql`, `assert_reset.sql`, baseline, `assert_all.sql`. Unit: `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.data.cloud.HostedPatchTest" --tests "com.ironvellum.app.data.cloud.CloudConfigTest"` (when the build lock is free).
- [ ] **Step 6: Docs.** `SELF_HOSTING.md`: "migrations are applied in order" is untrue for later files here (the baseline is the whole schema), so say: self-hosters re-apply `0001_baseline.sql` (idempotent) to reach schema 30. RELEASE_CHECKLIST and TODO: hosted project is at 29 until the owner pastes the new patch; list it under section 4.1.
- [ ] **Step 7: Commit:** `git add supabase tools/gate.py app/src/main/kotlin/com/ironvellum/app/data/cloud/Cloud.kt app/src/test/kotlin/com/ironvellum/app/data/cloud/HostedPatchTest.kt docs/RELEASE_CHECKLIST.md docs/TODO.md` then `Add owner-only web viewer tables and bump the schema to 30`.

---

### Task 2: Push set details and imported workouts

**Files:**
- Modify: `S/data/cloud/Dtos.kt`, `S/data/cloud/WireLimits.kt`, `S/data/cloud/CloudSync.kt` (push; `serverLocalIds` area; tombstones; companion), `S/data/cloud/Cloud.kt` (`schemaVersion`), `S/data/Repository.kt` (small reads only if needed)
- Modify tests: `T/data/cloud/AmendedPushTest.kt`, `T/data/cloud/WireNamesMatchSchemaTest.kt`
- Create tests: `T/data/cloud/WebUploadTest.kt`

**Interfaces:**
- Consumes: Task 1 tables; `SetKind.wire`, `SessionSet.kind/rpe/supersetGroup`, `partitionPushable`, `wireSetIndexes`, `WorkoutSession.imported`.
- Produces:
```kotlin
@Serializable data class SessionSetDetailDto(@SerialName("user_id") val userId: String, @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_name") val exerciseName: String, @SerialName("set_index") val setIndex: Int,
    @SerialName("kind") val kind: String?, @SerialName("rpe") val rpe: Int?, @SerialName("superset_group") val supersetGroup: Int?)
@Serializable data class ImportedSessionDto(/* id?, user_id, local_id, label, title, note, started_at, completed_at, edited_at */)
@Serializable data class ImportedSetDto(/* session_id, user_id, exercise_name, exercise_position, set_index, reps, weight_kg, modifiers, done, duration_sec, distance_m, grade, kind, rpe, superset_group */)
// CloudSync.Companion (internal):
fun detailRows(userId: String, cloudId: String, sets: List<SessionSet>): List<SessionSetDetailDto>   // only sets with kind != WORKING or rpe != null or supersetGroup != null; wire index via wireSetIndexes
fun pendingImportedForPush(completed: List<Pair<WorkoutSession, List<SessionSet>>>, watermark: Map<Long, Int>): List<Pair<WorkoutSession, List<SessionSet>>>  // imported only
fun <T> chunks(rows: List<T>): List<List<T>>   // 500 per request
// Cloud: suspend fun schemaVersion(client: SupabaseClient): Int?  // the rpc Cloud.probe makes, null when unreachable
// WireLimits: const val SNAPSHOT_MAX_BYTES = 2_097_152
```
`pendingForPush` keeps excluding imported (ImportMergeTest relies on it).

- [ ] **Step 1: Write failing tests** `WebUploadTest`: `plainWorkingSetsNeedNoDetail` (a WORKING, no-RPE, no-superset set yields no row); `detailRowsCarryKindRpeSuperset` (TOP + rpe 9 + group 1 gives `kind = "top"`, `rpe = 9`, `supersetGroup = 1`; WARMUP gives `"warmup"`); `detailIndexesMatchTheShiftedWireIndexes` (an exercise named twice in one workout: row `set_index` equals `wireSetIndexes` output); `importedPendingOnlyImported` and `importedSkipsWatermarkedSession`; `chunksOf500` (1201 rows give 500/500/201); `outdatedBackendSkipsWebUploads` (a pure `webReady(version: Int?) = version != null && version >= 30` is false for 29 and null). `AmendedPushTest`: keep `a never-amended trial keeps the fingerprint it was pushed at` green; add `a set with kind rpe or group changes the fingerprint` (TOP set differs from the same set WORKING; rpe 8 differs from null) and `plain sets keep the legacy fingerprint`. `WireNamesMatchSchemaTest`: add the three DTOs against `session_set_details`, `imported_sessions`, `imported_session_sets` (follow its `tableColumns` pattern) and `SNAPSHOT_MAX_BYTES` against `web_snapshots_size_cap`.
- [ ] **Step 2: Run, expect FAIL (compile):** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.data.cloud.WebUploadTest" --tests "com.ironvellum.app.data.cloud.AmendedPushTest"`.
- [ ] **Step 3: Implement.** Fingerprint: per set, append `kind.wire` only when not WORKING, `rpe` only when non-null, `supersetGroup` only when non-null, inside the existing per-set list, so only workouts with something new re-push once (spec call 4) and every other fingerprint is unchanged. In `push()`, after the `session_sets` upsert and before the watermark: if `webReady`, for each pushed session `delete from session_set_details where session_id in (...)` (chunked) then insert `detailRows` (chunked, `defaultToNull = false`); a failure adds a problem line (string resource) and removes that session from `pushedNow` so it retries; a 42P01/PGRST205 answer is treated as not ready (problem line once, no retry storm). Imported: sweep `imported_sessions` ids (paged like `serverLocalIds`), compute `pendingImportedForPush` against `watermark.filterKeys { it in importedOnServer }`, upsert sessions on `user_id,local_id`, resolve cloud ids, replace sets (delete by `session_id`, insert chunked), advance the watermark with the same `pushFingerprint`. Tombstone delete also runs against `imported_sessions` (ids share one space). Chunk the existing `session_sets` upsert with `chunks` (a one-off re-push of years of history must not be a single multi-megabyte request). Catch-up: store `web_caught_up:<backendUrl>:<userId>` in the `cloud_config` prefs; when `webReady` and the flag is absent, call `repo.clearPushWatermark()` once before computing `pending` and set the flag after a push with no problems (a self-hoster upgrading the backend later gets the same one-time re-push).
- [ ] **Step 4: Run, expect PASS** (same command), then `StringsXmlTest`, `GlossaryTest`, `WordsFormsTest`, `HardcodedUiLiteralTest` for the new problem strings (`cloud_sync_web_*`).
- [ ] **Step 5: Commit** `git add` the files above, `Upload set kind, effort and imported workouts to owner-only tables`.

---

### Task 3: Snapshot wire model, size cap and golden harness

**Files:**
- Create: `S/domain/WebSnapshot.kt` (types, `WebSnapshotTuning`, `WebSnapshotJson`), `T/domain/WebSnapshotJsonTest.kt`, `T/domain/WebSnapshotGoldenTest.kt`, `web/test/fixtures/` (directory; the golden file lands in Task 4/5)
- Modify: `S/data/cloud/Dtos.kt` (`WebSnapshotRowDto`)

**Interfaces:**
- Produces: the v1 wire shape below, serialised by `WebSnapshotJson.write(snapshot: WebSnapshot): String` (hand-built with `StringBuilder` like `ExportWriter`, keys in fixed order, doubles rounded to the precision noted, so the same input gives byte-identical output and the content hash is stable) and `WebSnapshotJson.contentHash(json: String): String` (SHA-256 hex of the JSON with `builtAtMs` removed). Additive changes (a new key a v1 web ignores) do not bump `v`; only a removed or reinterpreted key does.
```
v:1  builtAtMs  zone ("Australia/Sydney")  unit "kg"|"lb"  effort "rpe"|"rir"  words "plain"|"themed"
rir: {"5":5,"6":4,"7":3,"8":2,"9":1,"10":0}                  // Effort.rir table; web looks up
appearance: {mode:"system"|"dark"|"light", themed:bool, dark:AccentSet, light:AccentSet}   // Task 5A
            // AccentSet = {preset:string|null (the stable AccentPreset.id), primary, secondary, bright, deep, muted, panelTint, panelTintBorder,
            //              fill, text, tint, border, reward, onPrimary, onSecondary}   "#RRGGBB"; preset null = a custom colour
loadCases: not in the snapshot (golden file only)
profile: {name, level, totalXp, intoLevel, needed, streakDays, longestStreakDays, deedsEarned, deedsTotal, figure,
          titleId|null, crestId|null, workouts, firstMs|null}  // figure: "male" | "female" as LocalBodySex resolves it
deeds: [{id, name, rarity, category, description, atMs}]      // earned, newest first
muscles: {"LATS":"Lats", ...}                                  // Muscle.name -> label, every Muscle
week: {startDay, endDay, workouts, hardSets, volumeKg, prevVolumeKg, xp, plannedDays|null, days:["YYYY-MM-DD"]}
months: {"YYYY-MM": {workouts, perWeek, weeks:[{start, hardSets}], topLifts:[{name, art|null, sessions}]}}   // last 24
sessions: {"<localId>": {min, exercises, hardSets, volumeKg, peaks, xp, strength, muscleLevels:{LATS:3},
          groupLevels:{BACK:3,...}, prs:[{label, reps, weightKg|null, durationSec|null}],
          peakSets:["<name>#<idx>"], e1rm:{"<name>#<idx>":kg(1dp)}}}
lifts: [{name, art|null, metric, addsToBody, byE1rm, muscleSummary, muscleLevels:{LATS:3,BICEPS:2},
         variations:[{key, label, sessions, sets, lastMs, perWeek|null, volumeKg|null,
          peak:{reps, weightKg|null, durationSec|null, atMs, e1rmKg|null}|null, heaviest:{reps, weightKg, atMs}|null,
          points:[[atMs, localId, value(2dp), pr(0|1)]], setRecords:[{position, reps, weightKg|null, atMs}]}]}]
fatigue: {atMs, newLifter, hasHistory, levels:{LATS:0-4,...}, worked:[{muscle, level, cause|null, freshBy|null}]}   // Task 5A
trends: {floor, heavy, windows:{"7":{from,to,perWeek:{LATS:x}, state:{LATS:"covered"|"below"|"heavy"|"none"}}},
         weeks:[{start, hardSets, volumeKg, workouts, groups:{BACK:x,...}}], days:["YYYY-MM-DD"], daysPerWeek,
         recordsPerMonth:[{month:"YYYY-MM", count}], records30d, recent:[{atMs, localId, name, label, art|null, e1rmKg, gainKg}]}
routines: absent in v1 phase 1; added by Task 24 (additive)
```
`WebSnapshotTuning` holds every new constant (`LEVEL_MOST = 3.0`, `LEVEL_SOME = 1.5`, `GROUPS: Map<Muscle, Group>` for the six groups, week and day window lengths 52 weeks / 182 days / 24 months); no magic numbers elsewhere. `WireLimits.SNAPSHOT_MAX_BYTES` (Task 2) is the cap.

- [ ] **Step 1: Write failing tests** `WebSnapshotJsonTest`: `writingTwiceIsByteIdentical`; `contentHashIgnoresBuiltAt` (same snapshot, different `builtAtMs`, equal hash; one changed number, different hash); `emptySnapshotIsValidAndTiny` (empty history gives parseable JSON under 8 KB with `sessions` and `lifts` empty); `everyGroupCoversEveryMuscle` (`WebSnapshotTuning.GROUPS` has a value for each `Muscle.entries`); `fiveYearSnapshotFitsUnderCap` (synthetic 5 years, 4 workouts a week, 8 exercises x 4 sets, 60 distinct lifts, 24 months, full fatigue: `size < WireLimits.SNAPSHOT_MAX_BYTES / 2`; if the measured size is higher, raise the cap in Task 1 and `WireLimits` together, never above 4 MiB, and re-run Task 1 step 5); `overCapIsRefusedBeforeUpload` is Task 6.
- [ ] **Step 2: Run, expect FAIL.** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.WebSnapshotJsonTest"`.
- [ ] **Step 3: Implement** the data classes mirroring the shape above (one nested class per object, `Map` ordered by insertion), `WebSnapshotJson`, `WebSnapshotTuning`, and `WebSnapshotRowDto(user_id, version, snapshot: JsonElement, content_hash, size_bytes)` in `Dtos.kt`.
- [ ] **Step 4: Golden harness.** `WebSnapshotGoldenTest.goldenFixtureIsCurrent` builds the snapshot from the fixed fictional history in Task 4's `WebSnapshotFixtures` and asserts `WebSnapshotJson.write(...)` equals `web/test/fixtures/snapshot-v1.json` (root found like `HostedPatchTest` does); with `-Dgolden.update=true` it rewrites the file instead. `web/test/fixtures/parity.json` carries `loadCases` (`{kg, unit, text}` from `Loads.format`, including 102.058 kg in lb = "225", 0, 0.45359237, 1000.5) and `formatSetsCases` generated the same way. The test is skipped, not failed, until Task 4 supplies the fixtures; remove the skip in Task 5A.
- [ ] **Step 5: Run, expect PASS** (the json tests), **Commit:** `Define the web snapshot wire shape and its size cap`.

---

### Task 4: Snapshot builder: sessions and lifts

**Files:**
- Create: `S/domain/WebSnapshotBuilder.kt` (`object WebSnapshotBuilder`, pure), `T/domain/WebSnapshotFixtures.kt` (fixed fictional history, 14 workouts over 10 weeks: a weighted pull-up with TOP/BACKOFF, an archer variation, an assisted and a negative pull-up, a deficit push-up, a hold, a distance activity, a repeated exercise block, two imported workouts, one warm-up per lift, RPE on some sets, one custom exercise), `T/domain/WebSnapshotBuilderTest.kt`
- Reads (do not edit): `LiftRecords`, `PeakSets`, `RepMax.Ledger`, `SessionPeaks`, `HardSets`, `WorkoutShare`, `BodyShare`, `ExerciseHistoryCalculator`, `SetRecords`, `ExerciseArtIndex`, `exerciseMuscleSummary` and `ShareLevel` (`ui/program/ExerciseMuscles.kt`), `Variation.label`, `CloudSync.wireSetIndexes`

**Interfaces:**
- Consumes: Task 3 types.
- Produces:
```kotlin
class SnapshotInput(val history: List<Pair<WorkoutSession, List<SessionSet>>>, val exercises: Map<Long, Exercise>,
    val stats: List<StatEntry>, val profile: PlayerProfile, val titles: List<UnlockedTitle>, val presets: List<WorkoutPreset>,
    val unit: LoadUnit, val effort: EffortScale, val themed: Boolean, val zone: ZoneId, val nowMs: Long,
    val appearance: WebSnapshot.Appearance, val fatigueText: FatigueText, val displayName: String)   // last three: Task 5A
object WebSnapshotBuilder {
    fun build(input: SnapshotInput): WebSnapshot
    internal fun sessionEntries(input: SnapshotInput): Map<Long, WebSnapshot.SessionEntry>
    internal fun liftEntries(input: SnapshotInput): List<WebSnapshot.Lift>
    /** One PR tag as the phone words it ("Pull-up . Weighted" + load x reps); phase 2 reuses this for feed cards. */
    internal fun prTags(session: WorkoutSession, sets: List<SessionSet>, book: RepMax.Ledger, exercises: Map<Long, Exercise>): List<WebSnapshot.PrTag>
}
```
Rules pinned: per-session `peakSets`/`peaks` come from `SessionPeaks.of` with the book `RepMax.Ledger.first(n)` over chronological history (as `WorkoutDetailScreen.sharePeaks` judges: only trials before this one), keys use the shifted wire set index; `hardSets` counts `HardSets.isHard` over `HardSets.logged`; `volumeKg` is `WorkoutShare.totals().movedKg`; `e1rm` per set is `BodyShare.e1rmAddedKg` with the weigh-in at the session (same call `ExerciseLogCalculator` makes), which includes bodyweight variations (archer, weighted, assisted); `prs` holds at most 6 tags, one per peak lift, each `label` = `Variation.label(name, modifiers)`, `reps`, `weightKg` as logged (added load, never an e1RM), `durationSec` for a hold; `groupLevels` aggregates `muscleLevels` per `WebSnapshotTuning.GROUPS` taking the maximum; lifts group by movement name then `Variation.key`, built with `ExerciseHistoryCalculator.build(exercise, rows, null, nowMs, variation, bodyweightAt)` (read how `Repository.kt:2291` builds `ExerciseSetRow`s from sets, and mirror it from the in-memory history); `art` = `ExerciseArtIndex.forName(name)?.id?.removePrefix("own_")`, the file name in `art/svg/` (null for a custom exercise); `muscleSummary` and `muscleLevels` from the exercise's muscle shares via `exerciseMuscleSummary` and `ShareLevel`; `pr` on a point = strictly greater than every earlier point; warm-ups never count; imported sessions appear in `sessions` and `lifts` like any other (the phone counts them too).

- [ ] **Step 1: Write failing tests** (values asserted from the fixture; compute each expected number by hand from the fixture history in the test, not by calling the builder):
  - `sessionVolumeIsMovedKg` (a 3x8 at 60 kg barbell workout gives 1440), `bodyweightLiftsAddNoVolume`.
  - `hardSetsExcludeWarmupsAndEasySets` (RPE 6 and warm-up out, unrated 8 reps in, failure in).
  - `peaksAreJudgedAgainstEarlierTrialsOnly`: the same Pull-up top set is a peak in its first heavier workout and not in a later lighter one; `peakSetsUseWireKeys`; `repeatedMovementKeysMatchWireIndexes` (two push-up blocks key to `Push-up#0..2` and `Push-up#3..5`, matching `wireSetIndexes`).
  - `prTagsNameLiftAndLoadAsLogged` (Weighted pull-up +12.5 kg x 6 gives `label = "Pull-up . Weighted"`, `weightKg = 12.5`, `reps = 6`; an archer BW set gives `weightKg = null`); `prTagsNeverCarryE1rm` (the serialised tag has exactly the keys `label, reps, weightKg, durationSec`); `prTagsCapAtSix`.
  - `e1rmCountsBodyWeightForBodyweightLifts` (weighted pull-up +12.5 kg x6 at 80 kg body gives the `BodyShare` figure, plain pull-up its own, archer its own), `noWeighInFallsBackToAddedLoad`.
  - `variationsKeepSeparateSeries`: weighted and archer pull-ups are two entries with their own `points`; `prFlagMarksOnlyNewBests` (first point never flagged, an equal repeat not flagged); `holdIsJudgedBySeconds`; `assistedAndNegativePeaksKeepTheirSign` (assisted peak `weightKg = -20.0`, negative has `durationSec = 5`); `activityHasNoLiftEntry`; `setRecordsAreBestPerPosition` equals `SetRecords.recordsOf`.
  - `liftCarriesArtAndMuscleSummary` (Pull-up `art` equals `ExerciseArtIndex.forName("Pull-up")!!.id.removePrefix("own_")`; summary equals `exerciseMuscleSummary(shares)`); `customExerciseHasNoArt`.
  - `groupLevelsTakeTheMaximumPerGroup`.
  - `importedWorkoutsAreCounted` (an imported session has a `sessions` entry with `xp = 0` and feeds its lift's series).
  - `olderClientSetsReadAsWorking` is Task 10. `threeYearBuildIsFast`: 3-year synthetic history builds in under 5 s (same generous style as `ThreeYearHistoryTimingTest`; read it first).
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.WebSnapshotBuilderTest"`.
- [ ] **Step 3: Implement** `sessionEntries`, `prTags` and `liftEntries`, one pass over chronological history for sessions (reuse one `RepMax.Ledger`; do not rebuild a book per session).
- [ ] **Step 4: Run, expect PASS.** **Commit:** `Build per-workout and per-lift web snapshot figures from the phone's own rules`.

---

### Task 5: Snapshot builder: overview, months, trends, level and deeds

**Files:**
- Modify: `S/domain/WebSnapshotBuilder.kt`, `T/domain/WebSnapshotFixtures.kt`, `T/domain/WebSnapshotBuilderTest.kt`
- Reads: `Xp.progress`, `Titles.ALL`, `Streak`, `PeriodStats.weeks`, `HardSets.inRange`, `LiftRecords.gains`, `ProgramRules.HEAVY_WEEK`, `Muscle.label`

**Interfaces:**
- Produces: `internal fun profileAndDeeds(input): Pair<Profile, List<Deed>>`, `internal fun weekAndTrends(input): Pair<Week, Trends>`, `internal fun monthEntries(input): Map<String, WebSnapshot.Month>`. Window `"7"` is the rolling 7 days to `nowMs` (`HardSets.fromLog`), stored per week; `state` per muscle is `none` (0), `below` (< `floor` 4.0), `covered`, `heavy` (>= `ProgramRules.HEAVY_WEEK`); `muscleLevels` per session use `WebSnapshotTuning.LEVEL_*`; `trends.weeks[].groups` is `HardSets.inRange` for that week summed per `WebSnapshotTuning.GROUPS`; `daysPerWeek` is training days in the last 12 weeks over 12, one decimal; calendar weeks are Monday-start in `input.zone`; deed `name/description` are resolved in the current words mode and `words` records which; `profile.name` is `input.displayName`; `profile.titleId` and `crestId` are the worn ones.

- [ ] **Step 1: Write failing tests:** `levelAndXpMatchXpProgress` (126420 XP gives level, `intoLevel`, `needed` exactly as `Xp.progress`); `deedsListEarnedOnlyNewestFirstWithTotals` (`deedsTotal = Titles.ALL.size`, each deed carries its category); `streakUsesStreakCurrentAndLongest` (rest days tolerated exactly as `Streak`); `weekCountsOnlyThisCalendarWeekInZone` (a workout at 23:30 UTC Sunday lands in Monday's week for `Australia/Sydney`, still Sunday's for UTC); `weekXpSumsThisWeeksWorkouts`; `weeklyBucketsHave52EntriesWithZeros`; `groupsSumToTheWeeksHardSets` (the six group values of a week add up to that week's `hardSets` within 0.05); `windowSevenMarksStateByFloorAndHeavy` (3.9 below, 4.0 covered, 20.0 heavy, 0 none); `monthEntryCountsWorkoutsAndTopLifts` (October fixture: `workouts = 9`, `topLifts[0] = {name:"Pull-up", sessions:6}`); `monthsKeepTheLast24` ; `recentRecordsAreLiftGainsInLast30Days` and `recordsPerMonthCountsGainsPerCalendarMonth` (a first-ever lift is not a gain); `unitAndEffortAreCarried` (LB and RIR in, `unit = "lb"`, `effort = "rir"` out); `mutedWhenEmpty` (no history: all zero, no exception); `figureFollowsTheBodySexSetting` (FEMALE gives "female", MALE gives "male", unset gives whatever `LocalBodySex` falls back to in `MainActivity`).
- [ ] **Step 2: Run, expect FAIL:** same command as Task 4.
- [ ] **Step 3: Implement** the remaining builder parts; `build()` composes Tasks 4, 5 and (Task 5A) the fatigue and appearance parts.
- [ ] **Step 4: Run, expect PASS.** **Commit:** `Add overview, months, trends, level and deeds to the web snapshot`.

---

### Task 5A: Snapshot builder: fatigue and appearance, golden fixtures

**Files:**
- Modify: `S/ui/theme/AccentPalette.kt` (`AccentPreset` gains `val id: String`, set on all eight presets: a small, behaviour-neutral phone change); create: `S/ui/theme/WebAppearance.kt` (`internal object WebAppearance`), `S/ui/program/WebFatigueText.kt`, `T/ui/theme/WebAppearanceTest.kt`
- Modify: `S/domain/WebSnapshotBuilder.kt` (`fatigueSection`, appearance pass-through), `T/domain/WebSnapshotFixtures.kt`, `T/domain/WebSnapshotBuilderTest.kt`, `T/domain/WebSnapshotGoldenTest.kt` (remove the skip), create `web/test/fixtures/snapshot-v1.json` and `parity.json` by running the golden update once
- Reads: `FatigueModel.estimate`, `FatigueInput.sessions/firstWorkoutMs`, `FatigueReport.mostWorked/reading/levelAt`, `FatigueLevel`, `LightBy`, `AccentPalette` and its derived properties, `lightAccent()`, `accentForeground`, `lightAccentForeground`, `AccentPresets`, `AppearanceStore`, `ThemeMode`, and (once committed) `levelWord`, `causeLine`, `freshByLine` in `ui/program/FatigueMap.kt` and `FatiguePane.kt`

**Interfaces:**
- Consumes: Task 4 `SnapshotInput`.
- Produces:
```kotlin
/** The phone's own words for a fatigue row, injected so the builder stays pure. */
interface FatigueText {
    fun cause(c: CauseShare, nowMs: Long, zone: ZoneId): String      // causeLine
    fun freshBy(by: LightBy): String?                                 // freshByLine
}
internal object WebAppearance {
    fun accentSet(palette: AccentPalette, light: Boolean, presets: List<AccentPreset>): WebSnapshot.AccentSet  // every derived token, onPrimary/onSecondary from the foreground functions
    fun of(mode: ThemeMode, dark: AccentPalette, vellum: AccentPalette, themed: Boolean): WebSnapshot.Appearance
}
internal fun WebSnapshotBuilder.fatigueSection(input: SnapshotInput): WebSnapshot.Fatigue
```
`fatigue.levels` holds `FatigueLevel.ordinal` (0 Fresh .. 4 Very high) for every `Muscle`, from `FatigueModel.estimate(FatigueInput.sessions(history, null, exercises, nowMs), nowMs, FatigueInput.firstWorkoutMs(history, null))` and `levelAt(muscle, nowMs)`; `worked` is `mostWorked(zone)` (limit 4) with `cause` = `FatigueText.cause(first cause)`; `hasHistory` false when the log is empty. A palette equal to a preset (by primary and secondary) sets `preset` to that preset's stable `id` (a string such as `"ice_copper"`, never a list position, so a phone and a web of different builds still agree), else null.

- [ ] **Step 1: Write failing tests:** `fatigueLevelsMatchTheModel` (fixture history of two heavy pull days: every muscle's ordinal equals `FatigueLevel.of(report.residual(m))`); `fatigueWorkedIsMostWorkedInOrder`; `fatigueCauseUsesInjectedText` (a stub `FatigueText` returning `"cause:<lift>"` shows in `worked[0].cause`); `fatigueMutedWhenNoHistory` (`hasHistory = false`, all Fresh, `worked` empty); `fatigueIgnoresSessionsOlderThan28Days`; `fatigueAtMsIsNow` (equals `input.nowMs`). `WebAppearanceTest`: `everyDarkPresetRoundTrips` and `everyVellumPresetRoundTrips` (for each entry of `AccentPresets.entries` and `AccentPresets.light`, `accentSet(...)` has `preset` equal to its `id` and every token equals the property or `lightAccent()` value formatted `#RRGGBB`); `customAccentHasNullPreset`; `presetIdsAreUniqueAndStable` (eight distinct ids, pinned in the test as literals); `onPrimaryIsTheAppsForeground` (equals `accentForeground(primary)` dark, `lightAccentForeground(fill)` light); `modeIsCarried` (SYSTEM, DARK, LIGHT give `"system"`, `"dark"`, `"light"`); `themedFlagIsCarried`.
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.WebSnapshotBuilderTest" --tests "com.ironvellum.app.ui.theme.WebAppearanceTest"`.
- [ ] **Step 3: Implement.** Run `WebSnapshotGoldenTest` with `-Dgolden.update=true` once, read the written JSON against the fixture history by eye (a handful of numbers including two fatigue levels and one accent token), then run it again without the flag; remove the golden skip.
- [ ] **Step 4: Run, expect PASS.** **Commit:** `Add fatigue and appearance to the web snapshot` (include the two fixture files).

---

### Task 6: Push the snapshot

**Files:**
- Modify: `S/data/cloud/CloudSync.kt` (new step after the workout push and tier steps, before `pushDerivedAggregates`), `app/src/main/res/values/strings.xml` (`cloud_sync_web_snapshot_failed`, `cloud_sync_web_snapshot_too_large`, `cloud_sync_web_needs_schema`), `S/data/Repository.kt` if a read is missing
- Create: `T/data/cloud/WebSnapshotPushTest.kt`

**Interfaces:**
- Consumes: `WebSnapshotBuilder.build`, `WebSnapshotJson.write/contentHash`, `WireLimits.SNAPSHOT_MAX_BYTES`, Task 2 `webReady`, Task 5A `WebAppearance.of`, `FatigueText`.
- Produces: `internal fun snapshotDecision(json: String, serverHash: String?): SnapshotDecision` (sealed: `TooLarge(bytes)`, `Unchanged`, `Upload`); `CloudSync.snapshotNow()` is not public API, the step runs inside `push()`.

- [ ] **Step 1: Write failing tests** `WebSnapshotPushTest`: `unchangedContentIsNotUploaded` (server hash equals new hash gives `Unchanged`); `changedContentUploads`; `missingServerRowUploads`; `overCapIsRefusedBeforeUpload` (json of `SNAPSHOT_MAX_BYTES + 1` bytes gives `TooLarge`, nothing sent); `outdatedBackendSkipsWebUploads` already covers the gate; `signedOutPushesNothing` (the existing `requireAccount` path, assert the step is not reached).
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.data.cloud.WebSnapshotPushTest"`.
- [ ] **Step 3: Implement.** In `push()`, when `webReady`: read `Units.current`, `EffortSetting.scale`, `Words.themed`, `ZoneId.systemDefault()`, the account display name, `AppearanceStore` mode and both palettes (built into `WebAppearance.of`), a `FatigueText` backed by the real `causeLine` and `freshByLine`; build on `Dispatchers.Default`; `select content_hash from web_snapshots where user_id = me` (one tiny row); on `Upload`, `upsert(WebSnapshotRowDto, onConflict = "user_id")`. Any failure is a problem line (like the tier step), never a failed push. Order: strictly after sessions, sets, details and imported rows have landed. Known and accepted: a theme change or the passing of time changes the hash, but the upload happens only at the next push; the web shows "as of".
- [ ] **Step 4: Run, expect PASS,** then the four strings tests, then the whole unit gate once the build lock is free: `python3 tools/gate.py --no-device` (compiles instrumented sources; run the instrumented suite later in Task 15's gate run). **Commit:** `Upload the web snapshot after each push, skipping unchanged content`.

---

### Task 7: Privacy policy and data docs

**Files:**
- Modify: `PRIVACY.md` (the "What leaves the device" table and the "Not uploaded" paragraph; date line "Checked against the codebase on ..."), `docs/PLAY_DATA_SAFETY.md` (the data types collected), `supabase/SELF_HOSTING.md` (a short "Web viewer" paragraph: it reads your own project, needs schema 30)

- [ ] **Step 1:** Add rows to the table, in the policy's own style: set kind, effort and superset group per set (`session_set_details`, readable only by you); imported workouts with their sets (`imported_sessions`, `imported_session_sets`, readable only by you, never in feeds); the stats snapshot (figures the phone computed: per-lift strength estimates, weekly set counts, streak, level and deed names; `web_snapshots`, readable only by you, one row, replaced on each sync, deleted with your account). State plainly that the snapshot's bodyweight-lift estimates are why it is owner-only, that the snapshot also carries which body figure (male or female) your muscle maps use, readable only by you, that body readings, measurements, height and Health Connect data still never upload, and that signing out stops these uploads like every other. Say too that the snapshot carries your muscle fatigue reading, monthly summaries, routines and your theme and wording choices, readable only by you.
- [ ] **Step 2:** Verify against Task 1 and Task 2 by grep: every new table and column named in the policy exists in the baseline, and no claim says "set rows: exercise name, set index, reps, weight, modifiers, done" without the new rows. `grep -n "session_set_details\|imported_session\|web_snapshots" PRIVACY.md` returns all four.
- [ ] **Step 3: Commit** `Update the privacy policy and data safety notes for the web viewer uploads`.

---

### Task 8: Web scaffold, data layer, sign-in, shell

**Files:**
- Create: `web/package.json` (scripts `dev`, `build`, `test`; dependency `@supabase/supabase-js` pinned; devDependencies `vite`, `vitest`, `jsdom` pinned; justification in the README: supabase-js owns token refresh and PostgREST encoding, the rest is dev-only), `web/vite.config.js` (`base: './'`, injects `__DEFAULTS__` = URL and publishable key read from `cloud-defaults.properties` at build time, so there is one source), `web/index.html`, `web/src/main.js`, `web/src/router.js` (hash routes `#/`, `#/workouts`, `#/workouts/<id>`, `#/lifts`, `#/lifts/<name>`, `#/trends`; phase 2 adds `#/routines` and `#/friends/<tab>`), `web/src/shell.js` (sidebar with the plate, level and "Stats as of" line, bottom bar under 720 px, an Inbox button and the Routines and Friends entries present but hidden until phase 2, the footer line "Log and edit training in the app."), `web/src/theme.css` (placeholder: the dark default tokens only, replaced by Task 8A's generated tokens), `web/src/schema.js` (`export const NEEDED_SCHEMA = 30; export const SNAPSHOT_V = 1`), `web/src/format.js` (`formatLoad`, `formatSets`, `formatDay` with an explicit zone), `web/src/data/source.js` (the interface), `web/src/data/supabaseSource.js`, `web/src/data/fixtureSource.js`, `web/src/auth.js`, `web/src/screens/signin.js` (form, own-backend field, the fixture-driven "Example" preview from the WebSignIn mockup, the art credit line "Exercise art: Open Exercise Art / Alex Mollard, CC BY-SA 4.0. Colours changed." linking `art/ATTRIBUTION.md` text copied to `web/public/licenses/`), `web/src/assets/` (the three Chakra Petch TTFs from `app/src/main/res/font`, `licenses/OFL-ChakraPetch.txt`, `licenses/MIT-react-native-body-highlighter.txt`), `web/README.md`
- Create tests: `web/test/auth.test.js`, `web/test/source.test.js`, `web/test/format.test.js`, `web/test/schema.test.js`, `web/test/signin.test.js`
- Create: `T/data/cloud/WebViewerContractTest.kt` (Kotlin guard)
- Modify: `.gitignore` (`web/node_modules/`, `web/dist/`)

**Interfaces:**
- Produces (JSDoc-typed; all later web tasks consume these):
```js
// source.js
/** @typedef {{ userId: string, listWorkouts(): Promise<WorkoutRow[]>, getSets(w: WorkoutRow): Promise<SetRow[]>, getSnapshot(): Promise<Snapshot|null>, schemaVersion(): Promise<number|null> }} DataSource */
// WorkoutRow: {localId, origin:'synced'|'imported', id, label, title, note, startedAt, completedAt, editedAt, xp}
// SetRow:     {exerciseName, exercisePosition, setIndex, reps, weightKg, modifiers, done, durationSec, distanceM, grade, kind, rpe, supersetGroup}  // kind null from an older client
export function createSupabaseSource(client, userId): DataSource
export function createFixtureSource(fixture): DataSource      // fixture = test/fixtures/snapshot-v1.json plus rows
// format.js
export function formatLoad(kg, unit): string      // port of Loads.snap/format, pinned by parity.json
export function formatSets(n): string             // one decimal, trailing .0 dropped
export function formatDay(isoDay, zoneNote): string
// auth.js
export function signIn(config, email, password): Promise<Session>; export function signOut(): void
export function loadBackend(): {url, key, own: boolean}; export function saveBackend(url, key): void   // localStorage only
```
Behaviour: a boot check calls `schemaVersion()` (rpc `schema_version`, anon) before sign-in and shows `needs schema 30, this backend reports N` (never a stack trace); `?fixture=1` boots `createFixtureSource` with no network (used by tests and screenshots, built into dev only, tree-shaken from `build`); `getSnapshot()` returns `null` for no row and throws a typed `SnapshotTooNew` when `snapshot.v > SNAPSHOT_V`. The sign-in preview reads the fixture only and carries the word "Example".

- [ ] **Step 1: Write failing tests:** `auth.test.js`: `signsInWithEmailAndPassword` (fake client records `signInWithPassword`), `badPasswordShowsPlainMessage`, `ownBackendIsRememberedLocallyOnly` (url+key in `localStorage`, never in the URL or a request header other than the Supabase client), `signOutClearsSession`, `blockedStorageStillSignsIn` (a throwing `localStorage` does not break sign-in). `source.test.js`: `everyQueryFiltersOnOwnUserId` (fake PostgREST builder records calls: each of `sessions`, `session_sets` (by `session_id` of own sessions), `imported_sessions`, `imported_session_sets`, `session_set_details`, `web_snapshots` carries `eq('user_id', me)`); `noSnapshotShowsHint`, `newerSnapshotAsksForUpdate`, `oldSchemaNamesNeededVersion` are asserted at screen level in Task 9's shell test using this layer. `format.test.js`: `loadFormatParity` iterates `test/fixtures/parity.json` `loadCases` and `formatSetsCases` and asserts equality; `rirTableComesFromSnapshot`. `schema.test.js`: `neededSchemaMatchesTheApp`. `signin.test.js`: `previewIsLabelledExampleAndUsesNoNetwork` (no request fired, text contains "Example"), `creditLinePresent` (contains "Open Exercise Art", "CC BY-SA 4.0" and "Colours changed"). Kotlin `WebViewerContractTest`: `webNeededSchemaEqualsNeededSchemaVersion` (regex over `web/src/schema.js`), `webSnapshotVersionEqualsWebSnapshotVersion`, `fixturesExist`.
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm ci && npm test` (JS) and `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.data.cloud.WebViewerContractTest"`.
- [ ] **Step 3: Implement** the files above. `supabaseSource.listWorkouts` pages `sessions` (columns `id, local_id, label, title, note, started_at, completed_at, edited_at, xp_awarded`) and `imported_sessions` 1000 rows at a time ordered by `local_id`; `getSets` for a synced workout selects `session_sets` by `session_id` and `session_set_details` by `session_id` and merges on `(exercise_name, set_index)`; imported sets come with their own kind columns. `fixtureSource` serves the golden snapshot plus a small fixture of rows (`web/test/fixtures/rows.json`: workouts and sets whose `local_id`s and `"<name>#<idx>"` keys match the snapshot; extend the Kotlin fixtures in Task 4 to also emit it if easier, via the same golden test).
- [ ] **Step 4: Run, expect PASS.** Then `npm run build`, serve `web/dist` and open `?fixture=1` at 1440x900 and 390x844 with the browser tools (`resize_window`): the sign-in screen and the shell render, dark and light (`resize_window colorScheme`), no console errors, `read_network_requests` shows no host other than the page. Compare the sign-in page with WebSignIn by eye. Save screenshots to `D:\Monarch\.tmp\audit\web\`.
- [ ] **Step 5: Commit** `git add web .gitignore app/src/test/kotlin/com/ironvellum/app/data/cloud/WebViewerContractTest.kt` then `Add the web viewer scaffold with sign-in, data layer and shell`.

---

### Task 8A: Generated app assets and appearance (themes, words, catalogue, art)

Owner requirement 2026-10-23: the web supports the app's themes exactly like the app, and retypes nothing.

**Files:**
- Create (Kotlin, one golden-file test writes four files): `T/ui/theme/WebAssetsGoldenTest.kt`; outputs `G/tokens.json` (paper, ink and danger tokens for dark and Vellum from `DarkPaper`, `LightPaper`, `RarityTint`, `Metal` ramps incl. both Vellum ramps, `ArmyClass.LADDER`), `G/accents.json` (`dark` from `AccentPresets.entries`, `light` from `AccentPresets.light`; per preset the plain and themed names and every token of Task 5A's `AccentSet`, plus `contrast` = `contrastRatio` of text on fill for primary, secondary and each foreground), `G/words.json` (see below), `G/catalogue.json` (crests: id, name, group, kind, perk, short, art and ring tone, shapes from `CREST_SHAPES`; deeds from `Titles.ALL`: id, name, rarity, category, description; the four seal geometries from `sealGeometry` and the category glyph paths; lifts and tier names from `LiftBoards`, used in phase 2)
- Create (web): `web/src/theme.js` (`applyAppearance(snapshotAppearance, override)`), `web/src/appearance.js` (the Appearance menu: Theme System / Dark / Light, Accent presets for the effective theme, Names Plain / Themed, "Match my phone"), `web/src/i18n.js` (`t(key, form)` over `G/words.json`), `web/src/components/{plate,seal,crestMark,artImg,bodyMap}.js`, `tools/web_art.py`, `tools/bodymap_web.py` (reads `S/ui/program/BodyMapShapes.kt` packed strings and the `FRONT_PARTS`/`BACK_PARTS` tables in `BodyFigures.kt`, writes `G/bodyMapShapes.js`; deterministic, byte-identical on re-run, both figures), `G/bodyMapShapes.js`, `web/public/art/` (generated), `G/art-index.json` (name -> svg id, from `ExerciseArtIndex` through the same golden test), `web/words-keys.txt` (the string keys the web uses besides every `concept_*`, `accent_*` and `cloud_error_*`)
- Create tests: `web/test/theme.test.js`, `web/test/appearance.test.js`, `web/test/i18n.test.js`, `web/test/generatedOnly.test.js`, `web/test/art.test.js`, `web/test/bodyMap.test.js`, `tools/test_web_art.py`
- Modify: `web/src/theme.css` (now only structure and `var(--...)`; every colour value is injected from `tokens.json` at runtime by `theme.js`), `tools/gate.py` (run `python3 tools/web_art.py --check`), `web/README.md` (how to regenerate: `./gradlew :app:testFossDebugUnitTest --tests "*WebAssetsGoldenTest" -Dgolden.update=true`)

**Interfaces:**
- `words.json`: `{ "concept_workout_s": {"plain":"workout","themed":"trial"}, ... }`, built from every `concept_*` pair (`key` and `key__t`; an `always_` form has one text used for both) and each key in `web/words-keys.txt`, read through the app's own `Words` with `Words.useForTest(false/true)` so plain and themed resolution is the phone's; also `serverTerms` (the table behind `Words.fromServer`) so server sentences ("Too many remarks - wait a minute.") are swapped to plain words exactly as the phone does.
- Web: `t(key: string, form?: 'plain'|'themed'): string` (form defaults to the effective names mode; an unknown key throws in dev and returns the key in production), `fromServer(text: string): string`, `effectiveAppearance(snapshotAppearance, override): {light: boolean, tokens: AccentSet, themed: boolean}` (pure; `override` = `{mode?, accentId?, themed?}`), `applyAppearance(...)` sets CSS custom properties on `:root` and `data-theme`, `plate(el, {name, level, crestId})` (initials on a rim in the worn crest's ring tone using the metal ramp), `seal(el, {rarity, category, earned})`, `renderBodyMap(el, {levels, view, figure, look})` (SVG; `look` is `'worked'` (levels 0-3: Most, Some, A little), `'fatigue'` (levels 0-4: Fresh to Very high, High hatched, Very high cross-hatched, as `fatigueLook`) or `'coverage'` (covered, below, heavy, none); the figure is the snapshot's `profile.figure`, no toggle), `artImg(el, {art: string|null, name})` (CSS `mask-image: url(art/<id>.svg)` with `background: currentColor`, so the line art takes theme colours; a plain placeholder tile when `art` is null).
- `tools/web_art.py [--check]`: reads `G/art-index.json` and `art/svg/`, copies exactly the files the index names to `web/public/art/<id>.svg` (byte copies, sorted, deterministic), writes `web/public/art/CREDIT.txt` from `art/ATTRIBUTION.md` plus the line "Colours changed to follow the page theme." (the site recolours the drawings, which the licence asks to be said); `--check` fails when the copy is stale or `art/` is not at the pinned commit.

Behaviour: the web follows the phone: with no override, mode, accent tokens and names come from `snapshot.appearance`; with no snapshot (or a snapshot without `appearance`), the app defaults (System, emerald and gold, plain). System mode follows `prefers-color-scheme`. The override lives in `localStorage` (one JSON key, try/catch around every read and write, never sent anywhere); "Match my phone" deletes it. A phone accent that is not a preset (`preset: null`) is used from the snapshot's own tokens and is not offered in the menu (the menu lists presets only).

- [ ] **Step 1: Write failing tests.** `theme.test.js`: `followsThePhoneByDefault` (snapshot dark+Copper-in-Vellum fixture sets `--accent` to the fixture's `light.fill` when the effective theme is light); `systemModeFollowsPrefersColorScheme` (stub `matchMedia`; flipping it flips `data-theme`); `appearanceWorksWithoutStorage` (throwing `localStorage`: page themed from the snapshot, menu changes apply for the session, no exception); `overrideBeatsPhoneAndMatchMyPhoneResets`; `customPhoneAccentUsesSnapshotTokens` (`preset: null` fixture applies its tokens); `unknownOverridePresetIdFallsBackToPhone`; `textOnAccentMeetsContrast` (for every preset in `accents.json` and both themes, `contrast.primary >= 4.5` for the foreground the page uses on that fill, reading the generated ratio, and the page picks `onPrimary` from the table, not its own maths); `noHexOutsideGenerated` (see `generatedOnly`). `appearance.test.js`: `menuListsEveryPresetOfTheEffectiveTheme` (4 dark, 4 Vellum names, plain and themed by names mode), `namesToggleSwitchesVocabularyEverywhere` (Overview heading reads "Workouts" plain, the themed word when themed, from `words.json`). `i18n.test.js`: `everyUsedKeyExistsInBothFormsWhereTheAppHasOne` (scan `web/src` for `t('...')`; each key is in `words.json`; when `strings.xml` has a `key__t` twin the entry has both texts), `wordsParityWithTheApp` (the golden), `fromServerSwapsThemedTermsInPlainMode` (`"Too many remarks - wait a minute."` reads with the plain term for remark), `unknownKeyThrowsInDev`. `generatedOnly.test.js`: scans `web/src` (excluding `generated/`) for `#[0-9a-fA-F]{6}` and for a list of app sentences (taken from `words.json` plain values longer than 12 characters) and fails on a match. `bodyMap.test.js`: `bodyMapColoursByLevel` (levels 3/2/1/0 give four distinct fill classes, every region of a muscle coloured, unknown muscle ignored); `bodyMapFatigueLookHasFiveLevelsAndHatching`; `bodyMapCoverageLookHasFourStates`; `bodyMapUsesSnapshotFigure` (a female fixture renders the female outline and regions, a male fixture the male); `generatorIsDeterministic` (Python, run twice, compare bytes); `generatorCoversEveryRegion` (every `FRONT_PARTS`/`BACK_PARTS` key present). `art.test.js`: `artImgMasksTheSvgAndTakesCurrentColor`, `customExerciseShowsPlaceholderArt` (null `art` renders the placeholder, not a broken image), `everyIndexedArtFileExists`, `creditCarriesCcBySa`. `test_web_art.py`: `copyIsDeterministic` (run twice, compare bytes), `checkFailsOnStaleCopy`, `onlyIndexedFilesAreCopied`. `WebAssetsGoldenTest`: `generatedFilesAreCurrent` (writes with `-Dgolden.update=true`, else compares each of the five files), `accentsCoverEveryPreset` (8 presets keyed by `id`), `wordsCoverEveryConceptForm`, `catalogueCoversEveryCrestAndDeed` (28 crests, `Titles.ALL.size` deeds, seals for all four rarities).
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.ui.theme.WebAssetsGoldenTest"` (when the build lock is free); `cd web && npm test -- theme appearance i18n generatedOnly art`; `python3 tools/test_web_art.py`, `python3 tools/bodymap_web.py --check`.
- [ ] **Step 3: Implement.** Generate once (`-Dgolden.update=true`), review the diff of `accents.json` against `AccentPresets` by eye (two presets), then run without the flag. Files are written with CRLF (as the repo's other generated sources) and compared byte for byte; check no BOM. `tools/web_art.py`: stdlib only. Art size: all 562 files are about 13 MB; they are static files fetched on demand through the mask URL, not part of the JS bundle; `Task 13`'s dry run reports the total.
- [ ] **Step 4: Run, expect PASS.** `npm run build`, open `?fixture=1` and screenshot the sign-in page and the shell at 1440x900 in: dark + emerald, Vellum + forest, and with the Appearance menu open; then the Copper accent in dark (use the menu) and themed names (`Trials`); compare with WebOverviewVellum and WebOverviewCopper by eye. Save to `D:\Monarch\.tmp\audit\web\`.
- [ ] **Step 5: Commit** `git add web tools/web_art.py tools/test_web_art.py tools/gate.py app/src/test/kotlin/com/ironvellum/app/ui/theme/WebAssetsGoldenTest.kt` then `Generate the web's tokens, accents, words, catalogue and exercise art from the app`.

---

### Task 9: Overview and Workouts (list and calendar)

**Files:**
- Create: `web/src/screens/overview.js`, `web/src/screens/workouts.js`, `web/src/components/{statCard,calendar,workoutRow,pager,muscleStrip,fatigueCard,deedRow,recordRow}.js`
- Create tests: `web/test/overview.test.js`, `web/test/workouts.test.js`

**Interfaces:**
- Consumes: Task 8 `DataSource`, `format.js`; Task 8A `plate`, `seal`, `artImg`, `t`; snapshot `profile`, `week`, `months`, `deeds`, `fatigue`, `muscles`, `trends.recent`, `trends.records30d`, `sessions[localId]`; Task 8A's `renderBodyMap`.
- Produces: `renderOverview(root, ctx)`, `renderWorkouts(root, ctx)` (`ctx = {source, snapshot, router}`), `renderFatigueCard(el, {fatigue, muscles, figure, builtAtMs})` (reused by Task 12).

Rules: the list is `listWorkouts()` newest first, merged across both origins, searched by title/label only (`sessions[id]` carries no names), filtered All / With PRs (`peaks > 0`) / Imported; a row with no snapshot entry shows its title and date and a dash for derived figures, never a computed value; each row has the muscle strip (six cells shaded by `groupLevels`) and the first PR tag from `prs`; the calendar groups days with `Intl.DateTimeFormat(..., {timeZone: snapshot.zone})`; the month panel (count, per week, the "so far" bars, top lifts with art) reads `months[YYYY-MM]` of the displayed month and prints a dash for a month the snapshot lacks; the last-workout card shows PR tags from `prs`, exercise chips from the rows, the muscle map from `muscleLevels` and no allies line (phase 2); the "Muscles right now" card is the Fatigue map with the five levels and the legend, up to four `worked` rows, the phone's `cause` and `freshBy` text, the "estimate" caption and "as of <time>"; "Stats as of" prints `builtAtMs`.

- [ ] **Step 1: Write failing tests** (jsdom, fixture source; each expected string is read out of the fixture, so the page is proven to show exactly the snapshot's numbers): `overviewShowsSnapshotNumbers` (streak, level, XP, hard sets, volume, records30d, week XP, deeds list text equal fixture values); `overviewHidesPlannedWhenNull`; `deedRowsUseGeneratedSeals` (rarity picks the polygon, earned in colour, text from the snapshot); `fatigueCardShowsPhoneText` (rows show `cause` and `freshBy` verbatim, a Very high muscle uses the cross-hatch class); `fatigueCardShowsAsOfAndStaleHint` (builtAt 30 h before the injected clock shows the sync hint; 2 h shows none); `fatigueCardHiddenWhenNoHistory`; `workoutRowShowsDerivedFiguresFromSnapshot` (minutes, hard sets, tonnes, XP, `PR` tag, strip shades equal `groupLevels`); `listIgnoresForeignRows` (the fake source also returns a row with another `user_id`; it is not shown or counted); `importedOnlyAccount` (no synced sessions: list, calendar, tag "Imported", no crash); `emptyAccount` (zero workouts: friendly empty state); `pagesPastTheThousandRowCap` (2500 fake sessions: all listed, the source asked for 3 pages); `calendarUsesSnapshotZone` (a workout at 23:30 UTC lands on the zone's next day); `monthPanelReadsMonthsEntry` and `monthPanelDashesAMissingMonth`; `sessionMissingFromSnapshotShowsDashes`; `noSnapshotShowsHint`, `oldSchemaNamesNeededVersion` and `newerSnapshotAsksForUpdate` at shell level; `socialSlotsHiddenInPhaseOne` (no Inbox badge, no allies line).
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- overview workouts`.
- [ ] **Step 3: Implement** both screens against the mockups (WebOverview, WebOverviewVellum, WebWorkouts); calendar month navigation is local state; rows link to `#/workouts/<localId>`.
- [ ] **Step 4: Run, expect PASS;** build and screenshot `?fixture=1#/` and `#/workouts` at 1440x900 and 390x844 in dark and Vellum, and the Overview also in dark + Copper accent (Ice and Copper preset), Vellum + forest with themed names, and with the Appearance menu open; compare to WebOverview, WebOverviewVellum and WebOverviewCopper (once on the canvas) by eye (layout, not pixels), note any deliberate difference from the coverage table.
- [ ] **Step 5: Commit** `Add the web overview and workouts screens`.

---

### Task 10: Workout detail

**Files:**
- Create: `web/src/screens/workoutDetail.js`, `web/src/components/{setTable,prTag}.js`
- Create tests: `web/test/workoutDetail.test.js`

**Interfaces:**
- Consumes: Task 8 `getSets`; Task 8A `artImg`, `t`; snapshot `sessions[id].{peakSets,e1rm,peaks,hardSets,volumeKg,min,xp,strength,muscleLevels,prs}`, `lifts[].art`, `muscles`, `rir`, `effort`, `unit`.
- Produces: `renderWorkoutDetail(root, ctx, localId)`; it consumes Task 8A's `renderBodyMap` (look `'worked'`) and `artImg`.

Set rows group by `(exercise_name, exercise_position)` in position order (null positions last, then name, as `CloudSync.workout` does), show Warm-up/Top/Back-off/Drop/Failure/Rest-pause/Working from `kind` (null reads Working), load via `formatLoad` (a bodyweight set with no added load reads "BW", an assisted set a minus sign), reps-left from `snapshot.rir[rpe]` when `effort = "rir"` else RPE, e1RM from `e1rm["<name>#<setIndex>"]` (shown for bodyweight variations too, from the owner-only snapshot), PR tag from `peakSets`. The exercise header shows line art, the variation chip and the `prs` tag for that lift. Holds show seconds, distance sets show distance.

- [ ] **Step 1: Write failing tests:** `setRowsShowKindLoadRepsAndEffort`; `prTagFollowsPeakSets`; `e1rmComesFromSnapshotKey`; `bodyweightVariationShowsE1rm` (an archer BW set shows the snapshot's 68 kg, the row's load reads "BW"); `assistedLoadReadsMinus`; `olderClientSetsReadAsWorking` (kind null); `holdAndActivityRows` (reps 0 with 45 s; a 5 km row); `repeatedMovementBlocksStaySeparate`; `rirScaleShowsRepsLeftFromTable`; `lbUserSeesPounds`; `summaryShowsSnapshotFigures`; `noteIsThePublicNoteOnly`; `previousAndNextLinks`; `exerciseHeaderShowsArt` and `customExerciseShowsPlaceholderArt`; `noFriendsLineInPhaseOne`; `workedMapUsesSnapshotLevels` (the map's classes follow `muscleLevels`).
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- workoutDetail`.
- [ ] **Step 3: Implement.** Credit the MIT source in the page footer link to `licenses/MIT-react-native-body-highlighter.txt`.
- [ ] **Step 4: Run, expect PASS;** screenshot `#/workouts/<id>` (a weighted pull-up workout with an archer block, a hold workout, an imported one) at both widths, dark and Vellum.
- [ ] **Step 5: Commit** `Add the web workout detail with the muscle map`.

---

### Task 11: Lifts

**Files:**
- Create: `web/src/screens/lifts.js`, `web/src/components/{lineChart,variationTable}.js` (small hand-rolled SVG chart, no chart dependency), tests `web/test/lifts.test.js`

**Interfaces:**
- Consumes: snapshot `lifts[]`; Task 8A `artImg`; Task 8A `renderBodyMap`; produces `renderLifts(root, ctx, name?)`. The page header shows the line art, `muscleSummary`, the PR tags (the lift's records from `peak`, one per variation that has one) and the muscle map from `muscleLevels`. The chart plots `points` as given (x by `atMs`, range tabs 3 months / 6 months / 1 year / All filter points by `atMs >= now - range` where `now = builtAtMs`, no recomputation), diamonds where `pr = 1`, one line per selected variation. The "Variations side by side" table has one row per variation: a colour dot (toggles its line), label, best e1RM (`peak.e1rmKg`, or a dash), best set (`peak`: "+12.5 kg x 6", "BW x 12", "-20 kg x 10", "5 x 1 at 5 s"), last done (`lastMs` as weekday for this week else date, in the snapshot zone) and "not yet" when a variation has no sets. Records cards come from `peak`, `heaviest`, `setRecords`, `volumeKg`, `perWeek`, `sessions`.

- [ ] **Step 1: Write failing tests:** `listShowsVariationCounts`; `variationChipsSwitchSeries`; `variationTableShowsPeakAndLastDone` (every cell equals the fixture's `peak`, `lastMs`); `assistedAndHoldCellsFormatLikeThePhone` (`-20 kg x 10`, `5 x 1 at 5 s`); `variationWithNoSetsReadsNotYet`; `chartPointsEqualSnapshotPoints` (number of circles + diamonds equals the filtered points, y of the last point maps to its value); `diamondsOnlyWherePrFlagged`; `rangeTabsFilterByBuiltAt`; `recordsCardShowsSnapshotValues`; `holdLiftShowsSecondsNotKg`; `unloadedLiftHasNoChart` (fewer than 2 points shows the phone's "one session" line); `headerShowsArtSummaryAndMap`; `customExerciseShowsPlaceholderArt`; `searchFiltersLifts`; `deepLinkOpensTheLift`.
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- lifts`.
- [ ] **Step 3: Implement** to WebLift.
- [ ] **Step 4: Run, expect PASS;** screenshots at both widths for a multi-variation lift (Pull-up), a one-point lift and a custom exercise, dark and Vellum.
- [ ] **Step 5: Commit** `Add the web lifts screen with estimated 1RM charts and variations side by side`.

---

### Task 12: Trends

**Files:**
- Create: `web/src/screens/trends.js`, `web/src/components/{stackedWeeks,trainingDays,musclePanel}.js`, tests `web/test/trends.test.js`

**Interfaces:**
- Consumes: `trends.{floor,heavy,windows,weeks,days,daysPerWeek}`, `fatigue`, `muscles`, `profile.{streakDays,longestStreakDays}`, Task 9 `renderFatigueCard` pieces, Task 8A `renderBodyMap`, Task 11 `lineChart`; produces `renderTrends(root, ctx)`. Range tabs 4 weeks / 12 weeks / 1 year take the last 4, 12 or 52 entries of `trends.weeks`; the stacked chart plots `weeks[].groups` (six colours); the line chart plots `weeks[].hardSets`; the training-days grid shows `days`; the Muscles panel has a Fatigue | Coverage switch: Fatigue draws `fatigue.levels` with the five-level legend and `worked` rows (as Overview); Coverage draws `windows["7"].state` with the covered / below / heavy / none legend, the floor and heavy numbers from the snapshot, and a caption "Estimated from your last 7 days. Coverage shows hard sets against the 4-set floor." taken from the phone's strings via `t`.

- [ ] **Step 1: Write failing tests:** `stackedBarsMatchWeekGroups` (each segment height ratio equals `groups[g] / sum`, six legend entries); `rangeTabsTakeLastNWeeks`; `lineChartUsesSnapshotWeeks` (52 points, labels from the snapshot's `start`, not `Date.now`); `trainingDaysGridMatchesDays`; `daysPerWeekIsPrinted` (text "3.1 a week" from `daysPerWeek`); `streakTextIsDaysNotWeeks`; `musclePanelSwitchesFatigueAndCoverage`; `coverageStateDrivesFillClass` (covered, below, heavy, none); `fatigueRowsUsePhoneText`; `emptyHistoryShowsNoTrendsYet`.
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- trends`.
- [ ] **Step 3: Implement** to WebTrends.
- [ ] **Step 4: Run, expect PASS;** screenshots at both widths, dark and Vellum, Fatigue and Coverage.
- [ ] **Step 5: Commit** `Add the web trends screen with the fatigue and coverage map`.

---

### Task 13: Local publish to GitHub Pages and the gate hook

**Files:**
- Create: `tools/web_publish.py`, `tools/test_web_publish.py`
- Modify: `tools/gate.py` (run `npm test` for `web/` after the Gradle phases when `web/package.json` exists, `npm ci` first if `web/node_modules` is missing; one summary line), `AGENTS.md` ("Tests run locally" paragraph gains one sentence: the web tests are in the gate; Pages deploys by `tools/web_publish.py`, no workflow), `web/README.md`

**Interfaces:**
- Produces: `python3 tools/web_publish.py [--dry-run]`: runs `npm ci`, `npm test`, `npm run build` in `web/`; refuses when the working tree has uncommitted changes under `web/` or the tests fail; adds a throwaway `git worktree` on branch `gh-pages` (creating it as an orphan on first use), replaces its contents with `web/dist` plus `.nojekyll`, commits (`Publish web viewer <short sha>`), and pushes **without force**; `--dry-run` stops before the push and prints the file list and total size. It never edits repository settings and adds no workflow.

- [ ] **Step 1: Write failing tests** `test_web_publish.py` (`python3 tools/test_web_publish.py`): `refusesDirtyWebTree`, `dryRunDoesNotPush` (a temporary bare remote), `secondPublishAddsACommitAndNeverForces` (the push command list contains no `--force`/`-f`/`+`), `includesNoJekyllAndNoSourceMaps`, `includesTheArtFolderAndItsCredit`, `dryRunReportsFileCountAndTotalSize` (warns above 100 MB; the art is about 13 MB).
- [ ] **Step 2: Run, expect FAIL.** `python3 tools/test_web_publish.py`.
- [ ] **Step 3: Implement** (`// shortcut:` each publish appends a commit of the built bundle; if `gh-pages` history grows large the owner can squash it, a force-push that is theirs to do).
- [ ] **Step 4: Run, expect PASS;** then `python3 tools/web_publish.py --dry-run` and read its output. Do not push `gh-pages` or enable Pages: both are Task 15 owner steps.
- [ ] **Step 5: Commit** `Add a local GitHub Pages publish script and run the web tests in the gate`.

---

### Task 14: Google sign-in on the web (BLOCKED on the owner's console steps)

**Files:**
- Modify: `web/src/auth.js` (`signInWithGoogle`), `web/src/screens/signin.js` (the button, shown only when the backend is the shared one), `web/test/auth.test.js`

Blocked until the owner has done, in order (re-read the current Supabase "Login with Google" page before relying on any label below; menus move):
1. Google Cloud Console, the project that owns the app's existing OAuth Web client (`GOOGLE_WEB_CLIENT_ID`, used today as the Credential Manager audience): open that same **Web application** client (reusing it is expected to work, it needs no new client, but confirm the type is "Web application"). Add **Authorised redirect URI** `https://nhrresfzfhpxagnqrbzm.supabase.co/auth/v1/callback`. JavaScript origins are not needed for the redirect flow.
2. OAuth consent screen: authorised domain `supabase.co` present; publishing status "In production" (RELEASE_CHECKLIST section 5.1: in Testing only listed test users can sign in).
3. Supabase dashboard, Authentication, Providers, Google: enabled with the same client id and the client **secret** (the app's ID-token flow needed only the id; the redirect flow needs the secret).
4. Supabase dashboard, Authentication, URL Configuration: add the published address `https://alexmollard.github.io/Ironvellum/` and `http://localhost:5173/` (dev) to **Redirect URLs**.
5. Tell the agent the published address once Pages is on (Task 15).

- [ ] **Step 1: Write failing tests:** `googleButtonOnlyOnSharedBackend`; `googleCallsOAuthWithRedirectToCurrentPage` (fake client records `signInWithOAuth({provider:'google', options:{redirectTo: location.origin + location.pathname}})`); `returningFromOAuthRestoresSession`.
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- auth`. **Step 3: Implement.** **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `Add Google sign-in to the web viewer`. The first real Google round trip is Task 15.

---

### Task 15: Live acceptance (owner-gated)

Nothing here can be proven by fixtures: it is the first run against the real backend and the real phone.

- [ ] **Step 1 (owner):** paste `supabase/hosted/2026-10-23-web-viewer.sql` into the Supabase SQL editor; `select public.schema_version();` returns 30.
- [ ] **Step 2 (owner):** in the repository settings, Pages, source "Deploy from a branch", branch `gh-pages`, folder `/ (root)`. This is a settings change, so it is theirs.
- [ ] **Step 3:** `python3 tools/gate.py --backend` green (builds, unit, lint, instrumented, SQL). Push `main`. Then `python3 tools/web_publish.py`; wait for GitHub's own Pages deploy; open `https://alexmollard.github.io/Ironvellum/`.
- [ ] **Step 4:** install the build on the owner's phone (`:app:installFossDebug`, safe per AGENTS.md), open the app signed in, let it push once. Check: workouts re-push once; Friends feed order and inbox unchanged; `web_snapshots` has one row under 2 MiB.
- [ ] **Step 5 (owner):** sign in on the web with email and password; for every screen compare five numbers against the phone (streak, level and XP, last workout's hard sets and volume, a lift's best e1RM, a muscle's weekly sets); then the Overview fatigue levels against the phone's Muscles > Fatigue screen, and the web's theme, accent and words against the phone's Appearance settings. Any difference is a bug in the builder, not a rounding choice. Check a friend's account (public profile) sees nothing from the new tables.
- [ ] **Step 6:** after Task 14's console steps, repeat sign-in with Google; confirm "Stats as of" and sign-out. Record the result in `docs/TODO.md` ("Hosted schema" section: project at 30; web viewer live).
- [ ] **Step 7: Commit** `Record the web viewer as live and the hosted schema at 30`.

---

## Phase 2: social and routines

Starts after Task 15's live acceptance. Every table, view, RPC and column below was read from `supabase/migrations/0001_baseline.sql` and `S/data/cloud/{CloudSync,Dtos}.kt` on 2026-10-23; the web calls exactly what the app calls, so the server's own rules (RLS, triggers, limits) are the only rules. Phase 2 needs schema 31 (Task 16); a backend at 30 still serves phase 1 and the Friends entry says which version it needs.

Decisions made here (designer's calls, recorded for the owner):
1. **Friend-safe card fields: columns on `sessions`, not a side table.** `sessions_read` is `can_view_session(user_id, audience)`, which is exactly the audience the card must obey ("under the session's own audience"). One nullable `feed_card jsonb` column inherits that policy, rides the existing `public_feed` scan of `sessions` (no extra join per feed row), needs no new policy to get wrong, and is ignored by older clients. A side table would need its own policy duplicating `can_view_session` plus a join per row. Cost: up to 2 KiB more per session row, bounded by a check constraint. One column instead of several keeps the shape versionable (`v`) and the check simple. The column is owner-writable through the existing `sessions_write`.
2. **Social actions use the app's own calls verbatim** (insert into `friendships`, upsert `session_likes`, insert `session_comments`, RPCs for inbox and circles); no new server function is added in phase 2.
3. **Boards "Allies | Everyone" filter** (owner, 2026-10-23: Boards only, not Feed; the Feed mockup's control is dropped). The phone has none. The web adds it as a client filter by the accepted-ally id set (a filter by key, no new read), defaulting to Everyone, which is what the phone shows. The figures cut elsewhere in this plan are accepted by the owner (2026-10-23).

---

### Task 16: Schema 31, friend-safe feed card column

**Files:**
- Modify: `supabase/migrations/0001_baseline.sql` (the `sessions` column, `public_feed` re-created with `s.feed_card` appended last, `schema_version()` 30 to 31)
- Create: `supabase/hosted/2026-10-24-feed-card.sql` (idempotent, cut verbatim from the baseline, header in the style of `2026-10-09-crests.sql`: apply AFTER `2026-10-23-web-viewer.sql`)
- Create: `T/data/cloud/FeedCardCompatTest.kt`
- Modify: `supabase/test/assert_all.sql`, `tools/gate.py` (newest patch path), `S/data/cloud/Cloud.kt` (`NEEDED_SCHEMA_VERSION = 31`), `T/data/cloud/HostedPatchTest.kt`, `web/src/schema.js` (`NEEDED_SCHEMA = 31`) and `web/test/schema.test.js`, `supabase/SELF_HOSTING.md`, `docs/RELEASE_CHECKLIST.md`, `docs/TODO.md`

**Interfaces:**
- Produces: `sessions.feed_card jsonb check (feed_card is null or (jsonb_typeof(feed_card) = 'object' and char_length(feed_card::text) <= 2048))`; `public_feed.feed_card` (last column). `feed_card` shape, written only by the phone: `{"v":1,"hardSets":int,"volumeKg":number,"prs":[{"label":text,"reps":int,"weightKg":number|null,"durationSec":int|null}],"levels":{"LATS":1-3,...}}` (at most 6 prs; the server checks only object and size, the shape is the phone's contract and the web renders unknown keys as nothing).
- No new policy, no new grant: reads follow `sessions_read`, writes `sessions_write`.

- [ ] **Step 0: Prove installed phones survive the new column (blocks everything else in phase 2).** `FeedCardCompatTest`: `oldDtosDecodeRowsWithFeedCard` decodes a `public_feed` JSON row carrying an extra `"feed_card":{"v":1}` key with today's `FeedEntryDto`, and a `sessions` row with the extra key with `FriendSessionDto` and `AllyWorkoutSessionDto`, using the Json configuration the app's Supabase client actually uses (read `Cloud.kt` and the supabase-kt 3.8.0 default and say whether it is `ignoreUnknownKeys = true`; the repo has no explicit setting and earlier view columns such as `edited_at` and `current_crest_id` were added the same way). Run: `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.data.cloud.FeedCardCompatTest"`. Expected PASS. If it fails, stop: every phone in the field would break on the patch; switch the decision above to a side table `session_cards` read under `can_view_session` through its own policy and a new view, and re-plan Tasks 16 to 19.
- [ ] **Step 1: Write the failing assertions** (a `do $feed$` block in `assert_all.sql`, fixtures in the `a5500000-...-0000000000d?` range: an owner with a public profile, an ally by an accepted `friendships` row, a stranger, a blocked stranger; sessions with audience `profile`, `friends` and `private`, each with a `feed_card`):
  - `feedCardFollowsTheSessionsAudience`: the ally reads the cards of the `profile` and `friends` sessions through `sessions` and through `public_feed`; the stranger reads only the `profile` one (profile public); nobody but the owner reads the `private` one; the blocked stranger reads none.
  - `ownerWritesOwnFeedCardOnly`: owner updates own session's `feed_card`; the ally updating the owner's session is refused or affects 0 rows (RLS), and a stranger inserting a session with someone else's `user_id` is refused `42501`.
  - `feedCardIsBoundedAndShapeChecked`: a JSON array, a string and a 2049-character object are refused `23514`; `null` and a small object are accepted.
  - `publicFeedKeepsEveryOldColumnInOrder`: the first N column names of `public_feed` equal the schema-30 list (feed clients read them by name), and `feed_card` is the last.
  - `repushLeavesFeedInboxAndCircleUntouched`: record `public_feed` order for the owner's sessions, the ally's `my_inbox()` row count and `circle_member_days` for a fixture circle; run `insert into sessions ... on conflict (user_id, local_id) do update set label = excluded.label, feed_card = excluded.feed_card` with a changed card; the three are identical and `circle_events` count is unchanged (read `touch_updated_at` and any trigger on `sessions`: if one resurfaces rows on update, fix it in the same patch and say so).
- [ ] **Step 2: Run, expect FAIL** (Task 1 step 2 commands): `ASSERTION FAILED` on the missing column.
- [ ] **Step 3: Edit the baseline** (`alter table sessions add column if not exists feed_card jsonb` with the check as a named constraint added idempotently; `create or replace view public_feed` with the same body plus `s.feed_card` appended last; beacon 31). Cut the hosted patch verbatim: `begin; ... commit;`. Update `HostedPatchTest` (`applied` list, beacon equals `NEEDED_SCHEMA_VERSION`, the crests and web viewer patches keep their own literals 29 and 30) and `tools/gate.py backend()`.
- [ ] **Step 4: Run, expect PASS,** then the idempotency pass of Task 1 step 5 (baseline twice, both new patches twice, assertions; reset, reseed, baseline, assertions). Unit: `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.data.cloud.HostedPatchTest" --tests "com.ironvellum.app.data.cloud.CloudConfigTest"` and `cd web && npm test -- schema` (when the build lock is free).
- [ ] **Step 5: Docs** as Task 1 step 6 (hosted project at 30 until the owner pastes the patch; list under RELEASE_CHECKLIST 4.1).
- [ ] **Step 6: Commit** `git add supabase tools/gate.py app/src/main/kotlin/com/ironvellum/app/data/cloud/Cloud.kt app/src/test/kotlin/com/ironvellum/app/data/cloud/HostedPatchTest.kt web/src/schema.js web/test/schema.test.js docs/RELEASE_CHECKLIST.md docs/TODO.md` then `Add a friend-safe feed card column to sessions and bump the schema to 31`.

---

### Task 17: Phone uploads the feed card

**Files:**
- Create: `S/domain/FeedCard.kt` (`data class FeedCard`, `object FeedCardJson`), `T/domain/FeedCardTest.kt`
- Modify: `S/data/cloud/Dtos.kt` (`SessionDto.feed_card: JsonElement? = null`), `S/data/cloud/CloudSync.kt` (session push, fingerprint, `feedReady`), `T/data/cloud/WireNamesMatchSchemaTest.kt`, `T/data/cloud/AmendedPushTest.kt`, `PRIVACY.md` (a row: per-workout card of hard sets, tonnes, PR lift names with load and reps, muscle levels, visible exactly as the workout is), `docs/PLAY_DATA_SAFETY.md`

**Interfaces:**
- Consumes: Task 4 `WebSnapshotBuilder.prTags`, `sessionEntries` figures (`hardSets`, `volumeKg`, `muscleLevels`); Task 2 chunking.
- Produces:
```kotlin
data class FeedCard(val hardSets: Int, val volumeKg: Double, val prs: List<WebSnapshot.PrTag>, val levels: Map<Muscle, Int>)
object FeedCardJson { fun write(card: FeedCard): String /* keys v,hardSets,volumeKg,prs,levels in that order */ ; const val MAX_CHARS = 2048 }
// CloudSync.Companion (internal): fun feedReady(version: Int?) = version != null && version >= 31
//                                  fun feedCardFor(session, sets, book, exercises): FeedCard?   // null for an imported session or one with no done set
```
The card is built from the same ledger pass as the snapshot (no second history walk). `prs` carry `label`, `reps`, `weightKg` (added load as logged) and `durationSec`; no e1RM, no body figure. On a schema-30 backend the `feed_card` key is omitted entirely (an unknown column would fail the whole sessions upsert with PGRST204).

- [ ] **Step 1: Write failing tests:** `FeedCardTest`: `cardHasOnlyFriendSafeKeys` (parsed keys of a card are exactly `v,hardSets,volumeKg,prs,levels`; each pr has exactly `label,reps,weightKg,durationSec`); `feedCardHasNoBodyweightDerivedField` (a weighted pull-up and an archer session at 80 kg body weight: serialised text contains no `e1rm`, no `body`, `volumeKg` excludes bodyweight reps, `weightKg` equals the added load); `cardUnderServerCap` (12 exercises, 6 PRs, 12 muscles: `write(...).length <= FeedCardJson.MAX_CHARS`); `prsCapAtSix`; `importedSessionHasNoCard`; `emptyWorkoutHasNoCard`; `levelsUseTheTuningThresholds`. `AmendedPushTest`: `a card changes the fingerprint once` (the fingerprint of a completed workout gains the card's hash only when `feedReady`; schema-30 fingerprints stay as before) and `an amended workout refreshes its card`. `WebUploadTest` (extend): `olderBackendSendsNoFeedCard` (a `SessionDto` encoded for version 30 has no `feed_card` key; for 31 it has one); `feedReadyNeeds31`. `WireNamesMatchSchemaTest`: `sessions` columns include `feed_card`.
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.FeedCardTest" --tests "com.ironvellum.app.data.cloud.AmendedPushTest" --tests "com.ironvellum.app.data.cloud.WebUploadTest"`.
- [ ] **Step 3: Implement.** In `push()`, build cards for the pending sessions only when `feedReady`; `webCaughtUp` (Task 2) flag is keyed to the schema (`web_caught_up:<backendUrl>:<userId>:31`) so a user who updated before the backend did gets the one-time re-push when it arrives. `// shortcut:` the card is rebuilt at each push of a changed workout; a rule change in the phone's PR logic reaches friends' cards only when the workout is next amended or the catch-up flag is bumped.
- [ ] **Step 4: Run, expect PASS,** then the four strings tests if a problem line was added. **Commit** `git add` the files above, `Upload a friend-safe workout card with each completed workout`.

---

### Task 18: Friends shell, social data layer, server messages

**Files:**
- Create: `web/src/screens/friends/index.js` (sub-tabs Feed, Inbox, Allies, Boards, Circle at `#/friends/<tab>`, an Allies | Everyone control on Boards only), `web/src/data/socialSource.js` (the interface below, `createSupabaseSocial(client, userId)` and `createFakeSocial(script)`), `web/src/explain.js`, `web/src/components/{personRow,safetyMenu,reasonDialog,hiddenName}.js`, `web/test/helpers/fakeClient.js` (a recording fake of the supabase-js PostgREST builder and `rpc`, scripted per call with data or `{code, message}` errors), `web/test/friends.test.js`, `web/test/explain.test.js`
- Modify: `web/src/shell.js` (un-hide Friends and the Inbox button; the badge reads `Inbox.unread`), `web/src/router.js`, `web/src/schema.js` consumers (Friends says `needs schema 31` on an older backend)

**Interfaces:**
- Consumes: Task 8 client and `userId`; Task 8A `t`, `fromServer`, `plate`, `seal`, `crestMark`.
- Produces (JSDoc-typed; the later social tasks implement the methods in this order, each behind its own tests):
```js
/** @typedef {{
 *  feed(o:{beforeMs?:number, limit?:number}): Promise<FeedRow[]>,                   // public_feed
 *  react(sessionId:string, kind:'salute'|'iron'|'flame'|null): Promise<void>,       // session_likes
 *  reactors(sessionId:string): Promise<Reactor[]>, comments(sessionId:string): Promise<Comment[]>,
 *  addComment(sessionId:string, body:string): Promise<Comment>, deleteComment(id:string): Promise<void>,
 *  inbox(): Promise<{items:InboxItem[], seenAtMs:number|null}>, markInboxSeen(): Promise<void>,   // my_inbox, mark_inbox_seen, inbox_seen
 *  friends(): Promise<FriendRow[]>, findHunter(name:string): Promise<{id:string,displayName:string}|null>,
 *  requestFriend(userId:string): Promise<void>, acceptFriend(userId:string): Promise<void>, removeFriend(userId:string): Promise<void>,
 *  lifter(userId:string): Promise<LifterFolio>, lifterWorkout(sessionId:string): Promise<AllyWorkout>,
 *  leaderboard(): Promise<LeaderRow[]>, liftBoard(): Promise<LiftBoardRow[]>, shadowBoard(): Promise<ShadowRow[]>,
 *  mute(userId:string): Promise<void>, unmute(userId:string): Promise<void>, block(userId:string): Promise<void>, unblock(userId:string): Promise<void>,
 *  report(r:{targetUserId:string, sessionId?:string, commentId?:string, reason:'spam'|'abuse'|'cheating'|'other', note:string}): Promise<void>,
 *  circle(): Promise<Circle|null>, createCircle(name:string): Promise<void>, joinCircle(code:string): Promise<'joined'|'no_such_code'|'closed'|'full'|'throttled'>,
 *  leaveCircle(): Promise<void>, setCircleGoal(n:number): Promise<void>, setCircleName(name:string): Promise<void>,
 *  rotateCircleCode(): Promise<void>, kickCircleMember(userId:string): Promise<void>, circleBonuses(): Promise<Bonus[]> }} SocialSource */
// explain.js
export function explain(error): string   // port of Cloud.explain: 23505 name taken, 42501 keeper-only sentence else "not allowed", 23514 out of range, P0001 server sentence via fromServer, PGRST202/204/205 "not set up", auth codes, anything else "refused"
```
Behaviour: every method calls only the table, view or RPC named in its comment; the Friends screens run only on schema >= 31 (`schemaVersion()`); every thrown error reaches the screen through `explain`, so a code or a stack trace never shows; a signed-out or schema-behind state is a plain message. No write is optimistic beyond what the app does.

- [ ] **Step 1: Write failing tests.** `explain.test.js`: `p0001ShowsTheServerSentenceInPlainWords` (`'Too many remarks — wait a minute.'`, `'Daily remark limit reached — try again tomorrow.'`, `'This trial has reached its remark limit.'`, `'Too many ally requests today — try again tomorrow.'`, `'You can’t send an ally request to this Ironbound.'`, `'Too many reports today — try again tomorrow.'` each shown through `fromServer` in the current names mode, never the code); `keeperOnlyIsShownAs42501Sentence` (`'Only the Keeper can set the goal'` kept) and `other42501IsNotAllowed`; `23514IsOutOfRange`; `23505IsNameTaken`; `missingObjectsAreNotSetUp` (`PGRST202`, `PGRST204`, `PGRST205`); `unknownIsRefused`; `neverShowsACodeOrStack`. `friends.test.js`: `friendScreensReadNoOwnerOnlyTable` (script a visit to every sub-tab with the fake client; the set of tables read is a subset of `public_feed, session_likes, session_comments, friendships, profiles, sessions, session_sets, lift_board, leaderboard, shadow_board, blocks, mutes, inbox_seen, earned_titles, level_ups` plus RPCs `my_inbox, mark_inbox_seen, find_hunter, my_circle, circle_bonuses, create_circle, join_circle, leave_circle, set_circle_goal, set_circle_name, rotate_circle_code, kick_circle_member`, and `reports` insert only; never `web_snapshots`, `imported_*`, `session_set_details`, `cloud_archives`); `needsSchema31OnAnOlderBackend`; `signedOutShowsAPlainMessage`; `subTabsAreDeepLinks`; `audienceFilterIsAClientFilterByAllyIds` (Everyone issues the same request as the app; Allies filters the rows by accepted-ally ids, no extra table); `hiddenProfileUsesNeutralName` (a person row without a profile reads the neutral name the phone uses, `hiddenLifterName`, from `words.json`).
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- explain friends`.
- [ ] **Step 3: Implement** the shell, the fake client and the supabase implementation of the interface's shared plumbing (error mapping, paging); leave each screen's methods to its task, throwing `NotYet` in the fake.
- [ ] **Step 4: Run, expect PASS;** screenshot `#/friends/feed` empty state and the schema-behind message at both widths.
- [ ] **Step 5: Commit** `Add the web friends shell, social data layer and server message mapping`.

---

### Task 19: Feed, reactions and comments

**Files:**
- Create: `web/src/screens/friends/feed.js`, `web/src/components/{feedCard,commentThread,reactionBar}.js`, `web/test/feed.test.js`
- Modify: `web/src/data/socialSource.js` (feed, react, reactors, comments, addComment, deleteComment, report), `web/src/screens/overview.js` and `web/src/screens/workoutDetail.js` (the allies and comments line, from the owner's own `public_feed` row of that session)

**Interfaces:**
- Consumes: `public_feed` columns `session_id, user_id, display_name, current_title_id, level, label, title, note, completed_at, xp_awarded, strength_score, sets_done, like_count, liked_by_me, top_movements, best_set, duration_sec, comment_count, reactions, my_reaction, edited_at, current_crest_id, feed_card`; `session_likes (session_id, user_id, kind)`; `session_comments (id, session_id, user_id, author_name, body, created_at)`; `reports`.
- Produces: `renderFeed(root, ctx)`; the card shows plate and worn crest and title name (generated catalogue), headline (`title` or `label`), time and minutes (`duration_sec`), `+xp_awarded`, hard sets and tonnes and PR tags and the muscle map from `feed_card` (absent card: only `best_set` and `top_movements` as the phone's card shows; never computed), the three reaction buttons (salute, iron, flame) with `reactions` counts and the viewer's `my_reaction` highlighted, the comment count, an overflow menu (report, mute lifter, block lifter). Comments panel: the thread (oldest first, limit 200), your own comments with Delete, others' with Report, a composer with a live `n / 280` counter.

Rules: reaction is an upsert on `session_id,user_id` with the merged `kind` (switching kind is the conflict path) or a delete to clear; idempotent, no error on repeating the current state. A comment is trimmed, refused locally when empty or over 280 with the phone's own sentences (`cloud_remark_empty`, `cloud_remark_too_long` plural), inserted with `(session_id, user_id, body)` only and read back with the server's `author_name` and `created_at`; the owner of a workout may also delete others' comments on it (policy), which the thread offers on the owner's own workouts. Delete asks the rows back and says `cloud_remark_gone` when none was deleted. The feed pages by `completed_at < beforeMs`, 50 a page. A muted lifter's workouts are absent (the view hides them); a muted or blocked author's comments are absent (policy).

- [ ] **Step 1: Write failing tests** (fake client scripted per call; assert the recorded call as well as the screen):
  - `feedCardShowsFeedCardFields` (hard sets, tonnes, two PR tags `Back Squat 120 kg x 5` and the muscle map shades equal the fixture card's `levels`); `feedCardNeverShowsAnE1rm` (no text matching `e1RM` in a card with PRs); `cardWithoutFeedCardFallsBackToBestSet` (older client row shows `best_set` and `top_movements`, nothing computed); `feedPagesBy50AndByCompletedAt`; `crestAndTitleComeFromTheCatalogue` (an unknown crest id draws nothing, as the phone does).
  - `reactingUpsertsTheKind` (recorded: `session_likes` upsert `{session_id, user_id: me, kind:'iron'}` on conflict `session_id,user_id`), `switchingKindUpdatesNotDuplicates`, `clearingDeletesMyRow`, `reactorsListShowsNamesOrHidden` (own workouts only; limit 50).
  - `postingACommentSendsOnlyThreeColumns` (insert body keys exactly `session_id, user_id, body`), `commentReadBackShowsServerNameAndTime`, `emptyCommentIsRefusedLocally` (no request), `commentOver280IsRefusedBeforeSending` (281 chars: no request, message equals the app's sentence with 280), `exactly280IsAccepted`, `deleteOwnCommentAsksRowsBack` and `deleteRefusedReadsGone` (0 rows returned shows `cloud_remark_gone`), `ownerCanDeleteOthersCommentsOnOwnWorkout`, `threadStopsAt200`.
  - `serverLimitsSurfaceAsPlainMessages`: the fake rejects the insert with `P0001` and each of the three remark sentences; the screen shows the sentence, no code, keeps the typed text in the composer and re-enables Post.
  - `reportSendsReasonAndNote` (insert `reports` `{reporter_id: me, target_user_id, comment_id, reason:'abuse', note}`; reasons offered: Spam, Abuse, Cheating, Other with the phone's labels; note max 280; no select-back requested), `reportLimitReadsPlain` (`'Too many reports today — try again tomorrow.'`).
  - `mutedLifterNotInFeed` (the view's rows are rendered as given; muting calls `mutes` insert `{muter_id, muted_id}` and removes that lifter's cards from the page).
  - `overviewShowsAlliesLineFromOwnFeedRow` (reaction counts and comment count of the owner's last session read from `public_feed` filtered `session_id`; absent when the row is absent) and `detailShowsAlliesLine` (names from `reactors`).
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- feed`.
- [ ] **Step 3: Implement** to WebFeed.
- [ ] **Step 4: Run, expect PASS;** screenshots `#/friends/feed` with a card selected, the comment limit message, at both widths, dark and Vellum.
- [ ] **Step 5: Commit** `Add the web feed with reactions, comments and reports`.

---

### Task 20: Inbox

**Files:**
- Create: `web/src/screens/friends/inbox.js`, `web/test/inbox.test.js`
- Modify: `web/src/data/socialSource.js` (inbox, markInboxSeen), `web/src/shell.js` (badge and slow refresh), `web/src/data/poll.js` (a 30-minute refresh like the app's, paused when the tab is hidden)

**Interfaces:**
- Consumes: `my_inbox()` rows `kind, occurred_at, actor_id, actor_name, session_id, session_headline, comment_id, body, reaction`, `inbox_seen.seen_at`, rpc `mark_inbox_seen`, `friendships` (accept) from Task 21's `acceptFriend`.
- Produces: `renderInbox(root, ctx)`, `unreadCount(items, seenAtMs): number` (the phone's `Inbox.unread` rule: an item is unread when `seenAtMs` is null or it occurred after it, ported with a parity case generated on the phone).

Rules: kinds `request`, `accepted`, `comment`, `reply`, `reaction`, `band_join`/`circle_join`, `band_goal`/`circle_goal`, `circle_left`, `circle_keeper`, `circle_removed` map to the phone's items and words (via `t`); an unknown kind or reaction, or a comment or reaction row missing its workout, is dropped and never fails the list; sections as the mockup (Ally requests with Accept and Decline, On your workouts, Circle, Earlier this month); opening the tab calls `mark_inbox_seen` once and then the badge reads 0, which also clears the phone (one inbox, two windows; the page says so, as the mockup's note does); no push or email; refresh on open and every 30 minutes.

- [ ] **Step 1: Write failing tests:** `unreadCountMatchesPhoneParity` (cases generated from `Inbox.unread`); `everyKnownKindMaps` (one fixture row per kind, text equals the phone's wording through `t`); `unknownKindIsDropped`; `reactionWithoutSessionIsDropped`; `openingCallsMarkSeenOnce` (recorded rpc `mark_inbox_seen` exactly once per open, none on a background refresh); `badgeShowsUnreadAndClearsAfterOpen`; `acceptRequestFlipsAcceptedAndReloads` (recorded `friendships` update `{accepted:true}` where `requester_id` = actor and `addressee_id` = me, rows asked back; empty result shows `cloud_ally_no_pending`); `declineDeletesTheRow`; `refreshesEvery30MinutesAndPausesWhenHidden` (fake timers); `inboxSayingSeenAtIsServerTime` (no client clock is sent); `serverMessageSurfacesPlain` (a failing rpc shows the mapped sentence).
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- inbox`.
- [ ] **Step 3: Implement** to WebInbox. The phone side needs one change: none (the parity case is generated by a unit test that writes into `web/test/fixtures/parity.json`; extend the golden harness of Task 3).
- [ ] **Step 4: Run, expect PASS;** screenshot at both widths, dark and Vellum.
- [ ] **Step 5: Commit** `Add the web inbox with the app's unread rule`.

---

### Task 21: Allies, lifter folio and safety flows

**Files:**
- Create: `web/src/screens/friends/allies.js`, `web/src/components/{folio,liftTier}.js`, `web/test/allies.test.js`
- Create/Modify (phone, behaviour-neutral): `S/domain/LifterSummary.kt` (extracted from `ui/social/LifterScreen.kt` if the XP sum, per-week figure, last-workout wording and strength series are inline there; read it first), `T/domain/LifterSummaryTest.kt`, extend the golden harness with `lifterSummaryCases` in `web/test/fixtures/parity.json`
- Modify: `web/src/data/socialSource.js` (friends, findHunter, requestFriend, acceptFriend, removeFriend, lifter, lifterWorkout, mute, unmute, block, unblock, report)

**Interfaces:**
- Consumes: `friendships (requester_id, addressee_id, accepted, accepted_at)`, `profiles` (`id, display_name, current_title_id, current_crest_id, level, total_xp, streak_days, titles_count, lifetime_strength`), `find_hunter(name)`, `sessions` (`user_id`, `completed_at`, `*, session_sets(count)`), `session_sets`, `lift_board` for one `user_id`, `blocks`, `mutes`, `reports`.
- Produces: `renderAllies(root, ctx)` (left: "Add an ally by name", requests line linking Inbox, allies list; right: the folio); `LifterSummary.of(sessions, nowMs, zone)` in Kotlin and its JS port `lifterSummary(sessions, nowMs, zone)` (XP sum, per-week figure, last-workout wording key, strength series and its peak) pinned by `lifterSummaryCases`.

Rules and mapping to the app: add by exact name via `find_hunter` (self: `cloud_ally_self_request`; no match: `cloud_ally_not_found`), then insert `friendships {requester_id: me, addressee_id, accepted:false}`; a duplicate (`23505`) reads `cloud_ally_already_requested`; the server's 20-a-day and block sentences surface through `explain`. The list shows accepted allies and pending rows as the phone's `friends()` does; names for hidden profiles fall back to the inbox actor name then the neutral name. The folio shows what the phone's lifter screen shows, through the same reads: plate and crest and title names (catalogue), level, "allies since" from `accepted_at`, "N workouts shared" from a counted `sessions` query (`count: exact`, the server's number), `total_xp`, the phone's per-week figure and last-workout wording, the strength line from the last 20 sessions' `strength_score` with its peak, the recent workouts (each opens `lifterWorkout`: sessions + `session_sets` ordered as `CloudSync.workout` does, read-only, in the viewer's unit), the lift boards from `lift_board` for that lifter (tier name from the generated catalogue, `tier n of 10`), and the manage panel: Mute (toggle, `mutes` insert or delete), Remove ally (confirm; deletes the friendship row), Block (confirm with the phone's wording; `blocks` insert `{blocker_id, blocked_id}`, which the server uses to drop the friendship), Report (reason, optional note; `reports` insert `{reporter_id, target_user_id, reason, note}`). A blocked lifter disappears from every list after the action; the blocked list with Unblock is under the Allies tab's footer (the phone's account screen lists `blocks` with `blocked_name`).

- [ ] **Step 1: Write failing tests:** `LifterSummaryTest` (Kotlin): the existing behaviour of `LifterScreen` pinned (XP sum, `perWeek` over the loaded window with one decimal, last workout today / yesterday / n days, peak strength, empty list). `allies.test.js`: `findAllyByExactNameThenRequest` (recorded `find_hunter` args `{name}` then the `friendships` insert); `unknownNameReadsNotFound`; `selfRequestReadsSelf`; `duplicateRequestReadsAlreadyRequested`; `serverLimitsSurfaceAsPlainMessages` (the 20-a-day and block sentences, composer keeps the typed name); `pendingAndAcceptedAreSeparated`; `hiddenProfileUsesNeutralName`; `folioFiguresMatchPhoneParity` (loop `lifterSummaryCases`); `workoutsSharedUsesTheServerCount` (the fake returns count 214 with 20 rows: text "214"); `allySinceReadsAcceptedAt`; `folioReadsNoOwnerOnlyTable` (recorded tables); `liftBoardTiersUseCatalogueNames` (`tier 7 of 10`, ladder lifts show their rung label); `lifterWorkoutOrdersExercisesLikeTheApp`; `muteToggleInsertsAndDeletes`; `removeAllyConfirmsThenDeletes`; `blockConfirmsThenInserts` (recorded `blocks` insert has exactly `blocker_id, blocked_id`) and `blockedLifterLeavesTheList`; `reportSendsReasonNoteAndNeverReadsBack`; `reportDailyLimitReadsPlain`; `unblockDeletesTheBlock`.
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.LifterSummaryTest"` (compile first, when the lock is free) and `cd web && npm test -- allies`.
- [ ] **Step 3: Implement** the extraction (move, do not rewrite; `LifterScreen` calls the extracted functions) and the screen to WebAllies.
- [ ] **Step 4: Run, expect PASS;** the phone's `LifterScreen` is unchanged on screen, so no emulator screenshot is owed unless the extraction touched layout (if it did, take one through `tools/device.py lock`); web screenshots at both widths, dark and Vellum.
- [ ] **Step 5: Commit** `Add the web allies screen and lifter folio with mute, block and report`.

---

### Task 22: Boards

**Files:**
- Create: `web/src/screens/friends/boards.js`, `web/src/components/podium.js`, `web/test/boards.test.js`
- Modify (phone, behaviour-neutral): `S/ui/social/LeaderboardScreen.kt` (make `BoardMetric.value`, the sort and tie rank (1, 1, 3) and `LiftStanding` ranking `internal` in a small `S/domain/BoardRanking.kt`, moved not rewritten), `T/domain/BoardRankingTest.kt`, parity cases in `web/test/fixtures/parity.json`
- Modify: `web/src/data/socialSource.js` (leaderboard, liftBoard, shadowBoard)

**Interfaces:**
- Consumes: views `leaderboard (id, display_name, current_title_id, level, total_xp, streak_days, titles_count, lifetime_strength, sessions_last_7d, current_crest_id)`, `shadow_board (... shadow_essence, shadow_count, shadow_rate ...)`, `lift_board (user_id, display_name, current_title_id, level, lift, step, recent_step, current_crest_id)`, `LiftBoards` catalogue from `G/catalogue.json`.
- Produces: `renderBoards(root, ctx)`; `rankRows(metric, rows)` and `rankLift(rows, window)` ports of the phone's ranking, pinned by `boardCases`.

Rules: Training board with the phone's metrics (XP, Level, Streak, Deeds, Strength, Last 7 days; the XP default), podium for ranks 1-3 and rows from 4 with level, "n workouts this week" (from `sessions_last_7d`) and the value; Lifts board per lift with Week and All time windows, tier bars of 10 and "You: tier 6 . 1st" with ties sharing a rank; Shadow board (`shadow_essence` order) as the phone's Muster; boards are read-only; "Tiers, never weights" note kept from the mockup; no "behind" sentence (cut). Allies | Everyone is the client filter of Task 18.

- [ ] **Step 1: Write failing tests:** `BoardRankingTest` (Kotlin; ties 1,1,3; each metric's value; window nulling `recent_step` after 7 days); `boards.test.js`: `rankingMatchesPhoneParity` (loop `boardCases`); `podiumShowsTopThreeAndRowsFromFour`; `youAreHighlighted`; `metricSwitchReorders`; `liftBoardTiersAndWindows` (tier name from the catalogue, `recent` window uses `recent_step`); `tiesShareARank`; `shadowBoardOrdersByEssence`; `boardsReadOnlyViewsOnly` (recorded tables are `leaderboard, shadow_board, lift_board, friendships`); `emptyBoardReadsPlain`; `blockedLifterIsAbsentAsServed` (rows rendered as the view returns them).
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.BoardRankingTest"` and `cd web && npm test -- boards`.
- [ ] **Step 3: Implement** the extraction and the screen to WebBoards.
- [ ] **Step 4: Run, expect PASS;** web screenshots at both widths, dark and Vellum (phone unchanged on screen).
- [ ] **Step 5: Commit** `Add the web boards with the app's ranking rules`.

---

### Task 23: Circle

**Files:**
- Create: `web/src/screens/friends/circle.js`, `web/test/circle.test.js`
- Modify: `web/src/data/socialSource.js` (circle, createCircle, joinCircle, leaveCircle, setCircleGoal, setCircleName, rotateCircleCode, kickCircleMember, circleBonuses)

**Interfaces:**
- Consumes: rpc `my_circle()` (one row: `id, name, code, owner_id, week, per_member, pending_per_member, goal, circle_total, roster, weeks_met, members[user_id, display_name, level, current_title_id, current_crest_id, days_this_week, counts, last_workout_at]`), `create_circle(p_name)`, `join_circle(p_code)` (returns `joined`, `no_such_code`, `closed`, `full`, `throttled`), `leave_circle()`, `set_circle_goal(p_per_member)`, `set_circle_name(p_name)`, `rotate_circle_code()`, `kick_circle_member(p_user)`, `circle_bonuses()` (`week, days, circle_name`).
- Produces: `renderCircle(root, ctx)`.

Rules: no circle: a join-by-code field and a create field (name 1-24 characters, checked locally with the phone's sentence and again by the server); with a circle: header (name, "n of 8 members", `circle_total / goal days`), the week progress, one row per member with `per_member` check boxes filled by `days_this_week` (a member with `counts = false` reads "counts from next Monday"), the invite code with Copy, and a Keeper panel shown only when `owner_id = me`: set the goal (1-7, applies next week: `pending_per_member` shown), rename, rotate code (with a confirm), remove a member (never the Keeper), plus Leave the circle for everyone (a Keeper leaving hands over, as the server does). Weeks run Monday to Sunday UTC (the footer says so, as the mockup). "Weeks met" shows `weeks_met`; the bonus weeks list comes from `circle_bonuses()` with the line "collect it in the app" (bonus XP is paid by the phone). Cut: the 5-week "days trained per week" bar chart and "+480 XP" (the server returns only the current week to a client; the week tables are closed to clients) and "goal met 4 weeks running" (the server's `weeks_met` is lifetime, not consecutive).

- [ ] **Step 1: Write failing tests:** `circleHeaderAndRosterFromMyCircle` (fixture numbers: `11 / 15 days`, `5 of 8 members`, member 2 shows 2 of 3 boxes); `memberNotOnRosterReadsFromNextMonday`; `keeperPanelOnlyForTheKeeper` (a non-keeper sees no goal, rename, rotate or remove controls); `joinStatusesReadPlain` (`no_such_code`, `closed`, `full`, `throttled` each give the phone's sentence; `throttled` names the 50-a-day limit); `joinRecordsRpcArgs` (`join_circle` with `{p_code}` trimmed and upper-cased as the app does); `createRefusesBadNamesLocally` (0 and 25 characters: no request, `'A circle name is 1-24 characters'` plain); `alreadyInACircleReadsPlain` (server P0001 sentence); `keeperOnlyRefusalReadsTheSentence` (42501 `'Only the Keeper can set the goal'` shown, not "not allowed"); `goalRangeIs1To7` (8 refused locally, rpc arg is `p_per_member`); `kickNeverOffersTheKeeper`; `rotateAndRenameCallTheRpcs`; `leaveConfirmsThenCallsLeave`; `bonusesListIsReadOnly`; `serverLimitsSurfaceAsPlainMessages`.
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- circle`.
- [ ] **Step 3: Implement** to WebCircle.
- [ ] **Step 4: Run, expect PASS;** screenshots at both widths, dark and Vellum, keeper and member views.
- [ ] **Step 5: Commit** `Add the web circle with keeper actions`.

---

### Task 24: Snapshot routines section (phone)

**Files:**
- Modify: `S/domain/WebSnapshot.kt` (`Routines` types, JSON), `S/domain/WebSnapshotBuilder.kt` (`routineEntries`), `T/domain/WebSnapshotFixtures.kt` (presets: Upper A / Lower A / Upper B (weighted pull-up top set + 3 back-offs, archer 3x4-6, barbell row, a superset pair, a warm-up) / Lower B / an unscheduled Mobility; one custom exercise), `T/domain/WebSnapshotBuilderTest.kt`, `web/test/fixtures/snapshot-v1.json` (golden update)
- Reads: `WorkoutPreset`, `PresetEntry`, `SetScheme`, `SchemeText.summary/figure`, `ProgramRules.sessionSeconds`, `ProgramCoverage`/`coverageTally`/`coverageHeadline`, `HardSets`, `Exercise.custom/needs/shares`

**Interfaces:**
- Produces (additive to snapshot v1; a web that does not know it ignores it):
```
routines: {presets:[{id, name, note, day:1-7|null, minutes, hardSets, levels:{LATS:3,...},
            entries:[{position, name, art|null, label, sets, reps, loadKg|null, figure, scheme:text|null,
                      modifiers, supersetGroup:int|null, warmup:bool}]}],
           week: {days, hardSets, covers:{MUSCLE:"covered"|"below"|"heavy"|"none"}, headline:text},
           custom:[{name, muscles:{LATS:"main"|"assist"}, needs:[text]}]}
```
`figure` = `SchemeText.figure(scheme, sets, reps, kg, load = Loads.format in the snapshot unit, hold)`; `scheme` = `SchemeText.summary(scheme, sets)` (null for Straight); `minutes` = `ProgramRules.sessionSeconds(...) / 60` rounded as the phone shows it; `hardSets` and `levels` from the planned credit (`ProgramRules.weeklyVolume`/`ProgramCoverage.planned` for that preset alone, levels by `WebSnapshotTuning.LEVEL_*`); `week.covers` and `headline` from the phone's coverage over all scheduled presets (`coverageTally`, `coverageHeadline`); `week.days` is the count of scheduled presets and `week.hardSets` the phone's planned hard sets over them (`ProgramCoverage.planned`). `position` orders entries; the phone's own order and wording are kept.

- [ ] **Step 1: Write failing tests:** `routineEntriesKeepOrderAndWarmups`; `schemeLineIsThePhonesOwn` (top set + 3 back-offs gives `SchemeText.summary` text, Straight gives null); `figureUsesTheSnapshotUnit` (lb snapshot prints lb); `supersetGroupIsCarried`; `scheduledDayIsCarried` and `unscheduledIsNull`; `minutesAndHardSetsMatchTheRulesObject`; `weekCoversMatchesCoverageTally` (states and headline equal the phone's for the fixture); `customExerciseCarriesMusclesAndGear`; `emptyPresetsGiveEmptyRoutines`; `routinesFitTheCap` (extend `fiveYearSnapshotFitsUnderCap` with 12 routines of 10 entries and 20 custom exercises, still under half the cap); `weekPlannedDaysAgreesWithRoutines` (Task 5's `week.plannedDays` equals the count of presets with a day).
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.WebSnapshotBuilderTest"`.
- [ ] **Step 3: Implement,** run the golden update once, read the routines block against the fixture by eye, run again without the flag.
- [ ] **Step 4: Run, expect PASS** (builder, golden, `WebSnapshotJsonTest`). **Commit:** `Add routines, the week's coverage and custom exercises to the web snapshot`.

---

### Task 25: Routines screen (view only)

**Files:**
- Create: `web/src/screens/routines.js`, `web/src/components/{weekPlan,routineEntry}.js`, `web/test/routines.test.js`
- Modify: `web/src/shell.js` (un-hide Routines), `web/src/router.js` (`#/routines`, `#/routines/<id>`)

**Interfaces:**
- Consumes: snapshot `routines`, `muscles`, `profile.figure`; Task 8A `artImg`, `t`; Task 8A `renderBodyMap` (levels look and coverage look).
- Produces: `renderRoutines(root, ctx, id?)`.

Rules: left list (name, scheduled weekday short name or "no day", entry count) and the "The week covers" map with the phone's headline text; right: the weekly plan strip (Mon to Sun; a rest day reads "Rest"; the day of the open routine highlighted; a header line "N days . N hard sets" from `week.days` and `week.hardSets`; the mockup's "~3 h 50 min" is cut), the routine detail (title, weekday, "~N min . N hard sets" from `minutes` and `hardSets`, the note, numbered entries with art, name, variation chip, the scheme line and the phone's `figure`, Warm-up tag, superset marker, the load or "BW"), the "Works" map from `levels`, a "Custom exercises" section from `routines.custom` (name, muscles, gear; an addition the spec requires and the mockup omits), and a line "View only. Edit routines in the app."

- [ ] **Step 1: Write failing tests:** `listShowsRoutinesWithDays`; `weeklyPlanPlacesRoutinesOnTheirDays` (Mon Upper A, Wed Lower A, Fri Upper B, Sun Lower B, others Rest); `routineDetailShowsSnapshotFiguresVerbatim` (scheme line, figure, minutes, hard sets, note); `warmupAndSupersetMarkersShown`; `weekCoversUsesSnapshotStatesAndHeadline`; `customExercisesListed` and `customExerciseHasPlaceholderArt`; `noEditControlsAnywhere` (no input, no button except navigation and the appearance menu); `viewOnlyLineShown`; `noRoutinesShowsPlainEmptyState`; `olderSnapshotWithoutRoutinesShowsUpdateHint`.
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- routines`.
- [ ] **Step 3: Implement** to WebRoutines.
- [ ] **Step 4: Run, expect PASS;** screenshots at both widths, dark and Vellum.
- [ ] **Step 5: Commit** `Add the web routines screen`.

---

### Task 26: Phase 2 live acceptance (owner-gated)

- [ ] **Step 1 (owner):** only after Task 16 step 0 has passed, paste `supabase/hosted/2026-10-24-feed-card.sql` into the Supabase SQL editor; `select public.schema_version();` returns 31.
- [ ] **Step 2:** `python3 tools/gate.py --backend` green; push `main`; `python3 tools/web_publish.py`; install the build on the owner's phone (`:app:installFossDebug`), let it push once (every workout re-pushes once with its card).
- [ ] **Step 3:** with the owner's second account (the owner creates it in the app; no account is created by an agent): confirm the second account's feed shows a card with hard sets, tonnes, PR tags and a map, and no e1RM anywhere; confirm a `private` workout shows no card; confirm Friends feed order and the inbox did not change after the re-push.
- [ ] **Step 4 (owner):** on the web: react and comment on a friend's workout and see it on the phone; send, accept and decline an ally request; open the inbox and see the phone's badge clear; mute, unmute and report from a folio; open a circle and, as keeper, change the goal; compare a board's order with the phone's.
- [ ] **Step 5:** open Routines and compare three entries' scheme lines with the phone's Rite screen; change the phone's theme and accent, sync, and see the web follow after the next push.
- [ ] **Step 6:** record the result in `docs/TODO.md` (hosted project at 31; phase 2 live). **Commit** `Record the web viewer's social and routines phase as live`.

---

## Self-review (run on the finished plan)

- **Spec coverage:** Goals (workouts list and calendar, sets with kind/load/reps/RPE/variation, per-lift and per-variation e1RM, PRs, best sets, trends, rewards): Tasks 9-12 with the coverage table. Second-pass additions: exercise art (8A, 10, 11), metal plates and gold XP and deed seals (8A, 9), Muscles right now and the Fatigue | Coverage switch (5A, 9, 12), bodyweight-variation e1RM (4, 10), variations side by side (4, 11), workout-row strip and PR tags (4, 9), Workouts month summary (5, 9), sign-in preview (8). Theme requirement (mode, accents with derived tokens, plain or themed words, follows the phone, Appearance menu, tests): 5A, 8A. Phase 2: feed with friend-safe fields (16, 17, 19), inbox (20), allies and folio and safety (21), boards (22), circle (23), crest and title catalogue (8A, used in 19-22), routines (24, 25), tests per screen with a fake client (18-25). Non-goals respected (no training-data writes, no sign-up, no body weight). Anti-goals: numbers differ from phone (parity fixtures, Task 15 step 5, Task 26 steps 4-5), friend-visible data (Task 1 assertions, side table; Task 16 `feedCardFollowsTheSessionsAudience`, Task 17 `feedCardHasNoBodyweightDerivedField`), imported flooding feeds (own tables, Task 1 `nothingReadsTheImportedTables`, `repushLeavesFeedInboxAndCircleUntouched` twice), workflow trigger (Task 13 adds none).
- **Type consistency:** `webReady`, `feedReady`, `chunks`, `detailRows`, `SNAPSHOT_MAX_BYTES`, `WebSnapshotJson.write/contentHash`, `SnapshotInput` (with `appearance`, `fatigueText`, `displayName` added in 5A and consumed in 6), `PrTag {label, reps, weightKg, durationSec}` (Task 4, 17, 19), `AccentSet` (5A, 8A), `FeedCard` keys (16, 17, 19), `NEEDED_SCHEMA` 30 then 31, key format `"<exercise_name>#<wire set_index>"` and `art` = `ExerciseArtIndex` id without the `own_` prefix (the file name in `art/svg/`) used identically across tasks.
- **Proportion:** bodies are left to the implementer; the wire shape in Task 3 and the coverage table are the only large literals because both sides of the phone/web boundary depend on them. The plan is longer than the phase 1 plan because it now covers two phases, a theme system and ten social and routine screens, each with its own test list.
