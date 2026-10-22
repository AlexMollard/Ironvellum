# Web Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A read-only website where a signed-in lifter sees their own workouts, lift progress, trends, level and deeds on a PC, showing exactly the numbers the phone computed.

**Architecture:** The phone computes, the web displays. Workouts come from `sessions` and `session_sets` (plus an owner-only side table for set kind, RPE and superset group, and owner-only tables for imported workouts). Every derived figure comes from one owner-only snapshot row the phone builds from its existing domain code and uploads after the workout push. The site is a static Vite build under `web/` that talks to Supabase directly with the publishable key and is published to GitHub Pages by a local script.

**Tech Stack:** Postgres/RLS (baseline plus hosted patch, schema 30), Kotlin (CloudSync, pure snapshot builder, JUnit4), plain JavaScript ES modules with JSDoc types, Vite, Vitest + jsdom, `@supabase/supabase-js`, Python publish script, Node 22.

**Spec:** `D:\Monarch\docs\superpowers\specs\2026-10-23-web-viewer-design.md` ("Owner's answers" and "Calls made after the builder review" are binding). Mockups: Design canvas row "Web viewer" (WebSignIn, WebOverview, WebWorkouts, WebWorkoutDetail, WebLift, WebTrends); the spec wins where they differ (see the coverage table).

Paths: `S = app/src/main/kotlin/com/ironvellum/app`, `T = app/src/test/kotlin/com/ironvellum/app`, `W = web`. The working tree may hold another agent's uncommitted fatigue work: work from a fresh worktree of `main`, and `git add` explicit paths only (never `-A`).

## Global Constraints

- Read-only web: no logging, editing or deleting; no plan builder, routines, live workout, Friends or settings; no account creation or password reset (the app does both).
- **The web computes nothing.** Every derived figure (per-set PR marks, session volume and hard sets, e1RM, weekly sets, streak, level) is read from the snapshot. The web may only format (units, dates, rounding), filter and group by a key. Display-rule ports (`Loads.snap/format`, RIR table) are pinned by parity cases generated on the phone.
- Every web query on `sessions`, `session_sets`, `imported_*`, `session_set_details` and `web_snapshots` filters on `user_id = auth.uid()` explicitly (the read policies also admit friends' and public sessions).
- Nothing new becomes readable by an ally or a stranger. Body weight, measurements, height, sex and Health Connect data never upload. Body-weight-revealing figures (bodyweight e1RM) live only in the owner-only snapshot.
- The new uploads are automatic for signed-in users and owner-only; no opt-in switch. `PRIVACY.md` is updated in the same release.
- Everything stays at no cost: one snapshot row per user, size-capped; no paid service; no Actions minutes. No workflow file is added or changed (AGENTS.md); the only automatic trigger is GitHub's own Pages deploy, which the owner allowed.
- Schema ships as baseline edit + idempotent hosted patch + `schema_version()` literal and `NEEDED_SCHEMA_VERSION` bumped together to **30** (RELEASE_CHECKLIST section 4). Older schema: the phone skips web uploads and says so; the web says which version it needs.
- Phone: strings in `res/values/strings.xml` (plain `key`, themed `key__t` only for a sentence naming a glossary concept); `StringsXmlTest`, `GlossaryTest`, `WordsFormsTest`, `HardcodedUiLiteralTest` after any strings change. There is no new phone screen, so no emulator screenshot is owed; the only visible text is sync problem lines.
- Gradle: one build at a time (no parallel agents); emulator only through `python3 tools/device.py lock -- ...` on `emulator-5554`, never the phone `R5GL14GXV3J`. `python3 tools/gate.py` green and under 5 minutes before any push.
- Web: no analytics, no third-party hosts (fonts and the body map ship in the bundle); email and password first; "use my own backend" accepts any URL and key, remembered in the browser only.
- Commit style: imperative capitalised subject, no prefixes, no attribution, flat bullet body only when several changes are grouped; push to `main` when verified; no force-push, history rewrite or tags.
- File encoding: UTF-8 without BOM, CRLF; check the first three bytes after writing.

## Mockup to snapshot coverage

Every number a mockup shows is backed or cut. `Snap.x` is a snapshot field (Task 3).

| Mockup figure | Source on the phone | Field | Note |
|---|---|---|---|
| Sign-in, own-backend, "Stats as of" | `Cloud.probe`, snapshot timestamp | `builtAtMs` | |
| This week: workouts, hard sets, volume, vs last week | `PeriodStats.weeks`, `HardSets.isHard`, `WorkoutShare.totals().movedKg` | `week.*` | "+6% on last" is cut: the web would divide; show both volumes |
| "4 planned" | presets' `scheduledDay` count | `week.plannedDays` | hidden when null |
| Streak "11 weeks, best 14 weeks" | `Streak.current/longest` (days) | `profile.streakDays`, `longestStreakDays` | **Days, not weeks**: the phone counts days; spec says same numbers |
| Records "7 PRs in 30 days", recent list | `LiftRecords.gains(now-30d, now)` | `trends.records30d`, `trends.recent` | e1RM gains only; the mockup's rep-only rows ("5 reps", "24 reps") are cut, those live on the lift page |
| Level, XP to next, deeds counts | `Xp.progress`, `Titles.ALL` | `profile.*`, `deeds` | `earned_titles` holds ids only, so names and rarity ship in the snapshot |
| Workout row: minutes, exercises, hard sets, tonnes, PR x N | `WorkoutShare.totals`, `HardSets`, `SessionPeaks` | `sessions[id].*` | key = Room `local_id`, shared by both tables |
| "from Strong" | not stored (`WorkoutSession.imported` is a flag) | none | cut: tag reads "Imported" |
| Detail: set kind, load, reps, reps-left | `session_set_details` / imported columns | rows | reps-left = `Snap.rir` table lookup |
| Detail: per-set e1RM, PR tag | `BodyShare.e1rmAddedKg`, `SessionPeaks` (RepMax) | `sessions[id].e1rm`, `peakSets` | key `"<exercise_name>#<wire set_index>"`, same `wireSetIndexes` shift as the push |
| Detail: Records, XP | `peaks`, `xp_awarded` | `sessions[id].peaks`, `xp` | |
| Detail: muscles worked | `HardSets.logged` credit per muscle | `sessions[id].muscleLevels` | levels 0-3 chosen on the phone (`WebSnapshotTuning.LEVEL_*`, new constants: the phone has no per-workout map today) |
| Detail: "Your note" | public `sessions.note` | row | never the private note (not uploaded) |
| Lift: variation chips, e1RM chart, diamonds | `ExerciseHistoryCalculator` series per variation | `lifts[].variations[].points` | diamond = strictly beats every earlier point (`LiftRecords.isFresh` rule), flagged on the phone |
| Lift: Best e1RM, Heaviest, Volume, Frequency, trials | `ExerciseHistory.peak/heaviest*/totalVolumeKg`, `trialsPerWeek` | `variations[]` | mockup "Most reps" and "Best volume per day" are cut (not on the phone) |
| Lift: "Best sets by reps" | `SetRecords.recordsOf` (best per set position) | `variations[].setRecords` | replaces the mockup card, as the phone shows it |
| Trends: hard sets per muscle, 4-set floor, 20+ heavy | `HardSets.inRange`, `ProgramRules.HEAVY_WEEK` | `trends.windows`, `floor`, `heavy` | per-week average per window, state per muscle chosen on the phone |
| Trends: hard sets / volume per week, training days, records per month | `PeriodStats.weeks`, `LiftRecords.gains` per month | `trends.weeks`, `days`, `recordsPerMonth` | week labels from the snapshot zone, never the browser clock |
| Units, effort scale | `Units.current`, `EffortSetting.scale` | `unit`, `effort` | a lb lifter sees lb |

## Review Focus

Inputs the spec implies and no obvious test covers; each has a test in its owning task.

1. **Account with no snapshot yet** (new web user, phone not updated or not synced) and a **backend behind schema 30** or a **snapshot newer than the web knows**: a plain message, never a blank page or a thrown error; workouts still list without derived figures. Tests: Task 9 `noSnapshotShowsHint`, `oldSchemaNamesNeededVersion`, `newerSnapshotAsksForUpdate`; Task 2 `outdatedBackendSkipsWebUploads`.
2. **Other lifters' rows in a mixed response** (friends' and public sessions are RLS-readable): never listed, never counted. Tests: Task 8 `everyQueryFiltersOnOwnUserId`, Task 9 `listIgnoresForeignRows`; Task 1 `allyReadsNothingFromNewTables`.
3. **Re-pushing history must not resurface old workouts** in feeds, the inbox or circle weeks. Test: Task 1 `repushLeavesFeedInboxAndCircleUntouched`.
4. **More than 1000 workouts** (PostgREST max-rows) and **three to five years of history** (snapshot size, builder time). Tests: Task 9 `pagesPastTheThousandRowCap`, Task 3 `fiveYearSnapshotFitsUnderCap`, Task 4 `threeYearBuildIsFast`.
5. **Odd sets and units**: rows from an older client (null kind), holds (reps 0, seconds), distance activities, repeated exercise blocks, lb users (102.058 kg shown as 225), imported-only accounts, zero workouts. Tests: Task 10 `olderClientSetsReadAsWorking`, `holdAndActivityRows`, Task 8 `loadFormatParity`, Task 9 `importedOnlyAccount`, Task 4 `repeatedMovementKeysMatchWireIndexes`.
6. **Time zone**: calendar and week labels use the snapshot's zone, not the browser's. Test: Task 9 `calendarUsesSnapshotZone`.
7. **Phone width**: every screen usable at 390 px (sidebar becomes a bottom bar). Check: screenshot in each web task.

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
- Produces: the v1 wire shape below, serialised by `WebSnapshotJson.write(snapshot: WebSnapshot): String` (hand-built with `StringBuilder` like `ExportWriter`, keys in fixed order, doubles rounded to the precision noted, so the same input gives byte-identical output and the content hash is stable) and `WebSnapshotJson.contentHash(json: String): String` (SHA-256 hex of the JSON with `builtAtMs` removed).
```
v:1  builtAtMs  zone ("Australia/Sydney")  unit "kg"|"lb"  effort "rpe"|"rir"  words "plain"|"themed"
rir: {"5":5,"6":4,"7":3,"8":2,"9":1,"10":0}                  // Effort.rir table; web looks up
loadCases: not in the snapshot (golden file only)
profile: {level, totalXp, intoLevel, needed, streakDays, longestStreakDays, deedsEarned, deedsTotal}
deeds: [{id, name, rarity, description, atMs}]                // earned, newest first
muscles: {"LATS":"Lats", ...}                                  // Muscle.name -> label, every Muscle
week: {startDay, endDay, workouts, hardSets, volumeKg, prevVolumeKg, plannedDays|null, days:["YYYY-MM-DD"]}
sessions: {"<localId>": {min, exercises, hardSets, volumeKg, peaks, xp, muscleLevels:{LATS:3}, peakSets:["<name>#<idx>"], e1rm:{"<name>#<idx>":kg(1dp)}}}
lifts: [{name, metric, addsToBody, byE1rm, variations:[{key, label, sessions, sets, lastMs, perWeek|null, volumeKg|null,
          peak:{reps, weightKg|null, atMs, e1rmKg|null}|null, heaviest:{reps, weightKg, atMs}|null,
          points:[[atMs, localId, value(2dp), pr(0|1)]], setRecords:[{position, reps, weightKg|null, atMs}]}]}]
trends: {floor, heavy, windows:{"7":{from,to,perWeek:{LATS:x}, state:{LATS:"covered"|"below"|"heavy"|"none"}}, "28":..., "84":...},
         weeks:[{start, hardSets, volumeKg, workouts}], days:["YYYY-MM-DD"], recordsPerMonth:[{month:"YYYY-MM", count}],
         records30d, recent:[{atMs, localId, name, label, e1rmKg, gainKg}]}
```
`WebSnapshotTuning` holds every new constant (`LEVEL_MOST = 3.0`, `LEVEL_SOME = 1.5`, week and day window lengths 52 weeks / 182 days / 12 months); no magic numbers elsewhere. `WireLimits.SNAPSHOT_MAX_BYTES` (Task 2) is the cap.

- [ ] **Step 1: Write failing tests** `WebSnapshotJsonTest`: `writingTwiceIsByteIdentical`; `contentHashIgnoresBuiltAt` (same snapshot, different `builtAtMs`, equal hash; one changed number, different hash); `emptySnapshotIsValidAndTiny` (empty history gives parseable JSON under 4 KB with `sessions` and `lifts` empty); `fiveYearSnapshotFitsUnderCap` (synthetic 5 years, 4 workouts a week, 8 exercises x 4 sets, 60 distinct lifts: `size < WireLimits.SNAPSHOT_MAX_BYTES / 2`; if the measured size is higher, raise the cap in Task 1 and `WireLimits` together, never above 4 MiB, and re-run Task 1 step 5); `overCapIsRefusedBeforeUpload` is Task 6.
- [ ] **Step 2: Run, expect FAIL.** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.WebSnapshotJsonTest"`.
- [ ] **Step 3: Implement** the data classes mirroring the shape above (one nested class per object, `Map` ordered by insertion), `WebSnapshotJson`, `WebSnapshotTuning`, and `WebSnapshotRowDto(user_id, version, snapshot: JsonElement, content_hash, size_bytes)` in `Dtos.kt`.
- [ ] **Step 4: Golden harness.** `WebSnapshotGoldenTest.goldenFixtureIsCurrent` builds the snapshot from the fixed fictional history in Task 4's `WebSnapshotFixtures` and asserts `WebSnapshotJson.write(...)` equals `web/test/fixtures/snapshot-v1.json` (root found like `HostedPatchTest` does); with `-Dgolden.update=true` it rewrites the file instead. `web/test/fixtures/parity.json` carries `loadCases` (`{kg, unit, text}` from `Loads.format`, including 102.058 kg in lb = "225", 0, 0.45359237, 1000.5) and `formatSetsCases` generated the same way. The test is skipped, not failed, until Task 4 supplies the fixtures; remove the skip in Task 5.
- [ ] **Step 5: Run, expect PASS** (the three json tests), **Commit:** `Define the web snapshot wire shape and its size cap`.

---

### Task 4: Snapshot builder: sessions and lifts

**Files:**
- Create: `S/domain/WebSnapshotBuilder.kt` (`object WebSnapshotBuilder`, pure), `T/domain/WebSnapshotFixtures.kt` (fixed fictional history, 14 workouts over 10 weeks: a weighted pull-up with TOP/BACKOFF, an archer variation, a deficit push-up, a hold, a distance activity, a repeated exercise block, two imported workouts, one warm-up per lift, RPE on some sets), `T/domain/WebSnapshotBuilderTest.kt`
- Reads (do not edit): `LiftRecords`, `PeakSets`, `RepMax.Ledger`, `SessionPeaks`, `HardSets`, `WorkoutShare`, `BodyShare`, `ExerciseHistoryCalculator`, `SetRecords`, `CloudSync.wireSetIndexes`

**Interfaces:**
- Consumes: Task 3 types.
- Produces:
```kotlin
class SnapshotInput(val history: List<Pair<WorkoutSession, List<SessionSet>>>, val exercises: Map<Long, Exercise>,
    val stats: List<StatEntry>, val profile: PlayerProfile, val titles: List<UnlockedTitle>, val presets: List<WorkoutPreset>,
    val unit: LoadUnit, val effort: EffortScale, val themed: Boolean, val zone: ZoneId, val nowMs: Long)
object WebSnapshotBuilder {
    fun build(input: SnapshotInput): WebSnapshot
    internal fun sessionEntries(input: SnapshotInput): Map<Long, WebSnapshot.SessionEntry>
    internal fun liftEntries(input: SnapshotInput): List<WebSnapshot.Lift>
}
```
Rules pinned: per-session `peakSets`/`peaks` come from `SessionPeaks.of` with the book `RepMax.Ledger.first(n)` over chronological history (as `WorkoutDetailScreen.sharePeaks` judges: only trials before this one), keys use the shifted wire set index; `hardSets` counts `HardSets.isHard` over `HardSets.logged`; `volumeKg` is `WorkoutShare.totals().movedKg`; `e1rm` per set is `BodyShare.e1rmAddedKg` with the weigh-in at the session (same call `ExerciseLogCalculator` makes); lifts group by movement name then `Variation.key`, built with `ExerciseHistoryCalculator.build(exercise, rows, null, nowMs, variation, bodyweightAt)` (read how `Repository.kt:2291` builds `ExerciseSetRow`s from sets, and mirror it from the in-memory history); `pr` on a point = strictly greater than every earlier point; warm-ups never count; imported sessions appear in `sessions` and `lifts` like any other (the phone counts them too).

- [ ] **Step 1: Write failing tests** (values asserted from the fixture; compute each expected number by hand from the fixture history in the test, not by calling the builder):
  - `sessionVolumeIsMovedKg` (a 3x8 at 60 kg barbell workout gives 1440), `bodyweightLiftsAddNoVolume`.
  - `hardSetsExcludeWarmupsAndEasySets` (RPE 6 and warm-up out, unrated 8 reps in, failure in).
  - `peaksAreJudgedAgainstEarlierTrialsOnly`: the same Pull-up top set is a peak in its first heavier workout and not in a later lighter one; `peakSetsUseWireKeys`; `repeatedMovementKeysMatchWireIndexes` (two push-up blocks key to `Push-up#0..2` and `Push-up#3..5`, matching `wireSetIndexes`).
  - `e1rmCountsBodyWeightForBodyweightLifts` (weighted pull-up +12.5 kg x6 at 80 kg body gives the `BodyShare` figure, plain pull-up its own), `noWeighInFallsBackToAddedLoad`.
  - `variationsKeepSeparateSeries`: weighted and archer pull-ups are two entries with their own `points`; `prFlagMarksOnlyNewBests` (first point never flagged, an equal repeat not flagged); `holdIsJudgedBySeconds`; `activityHasNoLiftEntry`; `setRecordsAreBestPerPosition` equals `SetRecords.recordsOf`.
  - `importedWorkoutsAreCounted` (an imported session has a `sessions` entry with `xp = 0` and feeds its lift's series).
  - `olderClientSetsReadAsWorking` is Task 10. `threeYearBuildIsFast`: 3-year synthetic history builds in under 5 s (same generous style as `ThreeYearHistoryTimingTest`; read it first).
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.WebSnapshotBuilderTest"`.
- [ ] **Step 3: Implement** `sessionEntries` and `liftEntries`, one pass over chronological history for sessions (reuse one `RepMax.Ledger`; do not rebuild a book per session).
- [ ] **Step 4: Run, expect PASS.** **Commit:** `Build per-workout and per-lift web snapshot figures from the phone's own rules`.

---

### Task 5: Snapshot builder: overview, trends, level and deeds

**Files:**
- Modify: `S/domain/WebSnapshotBuilder.kt`, `T/domain/WebSnapshotFixtures.kt`, `T/domain/WebSnapshotBuilderTest.kt`, `T/domain/WebSnapshotGoldenTest.kt` (remove the skip), create `web/test/fixtures/snapshot-v1.json` and `parity.json` by running the golden update once
- Reads: `Xp.progress`, `Titles.ALL`, `Streak`, `PeriodStats.weeks`, `HardSets.inRange`, `LiftRecords.gains`, `ProgramRules.HEAVY_WEEK`, `Muscle.label`

**Interfaces:**
- Produces: `internal fun profileAndDeeds(input): Pair<Profile, List<Deed>>`, `internal fun weekAndTrends(input): Pair<Week, Trends>`; `build()` now returns the complete v1 snapshot. Windows: `"7"` rolling 7 days to `nowMs` (`HardSets.fromLog`), `"28"` and `"84"` via `inRange`, each stored per week (sets divided by weeks, on the phone); `state` per muscle is `none` (0), `below` (< `floor` 4.0), `covered`, `heavy` (>= `ProgramRules.HEAVY_WEEK`); `muscleLevels` per session use `WebSnapshotTuning.LEVEL_*`; calendar weeks are Monday-start in `input.zone`; deed `name/description` are resolved in the current words mode and `words` records which.

- [ ] **Step 1: Write failing tests:** `levelAndXpMatchXpProgress` (126420 XP gives level, `intoLevel`, `needed` exactly as `Xp.progress`); `deedsListEarnedOnlyNewestFirstWithTotals` (`deedsTotal = Titles.ALL.size`); `streakUsesStreakCurrentAndLongest` (rest days tolerated exactly as `Streak`); `weekCountsOnlyThisCalendarWeekInZone` (a workout at 23:30 UTC Sunday lands in Monday's week for `Australia/Sydney`, still Sunday's for UTC); `weeklyBucketsHave52EntriesWithZeros`; `windowsDivideByWeeks` (28-day window total 20.0 gives perWeek 5.0); `muscleStateUsesFloorAndHeavy` (3.9 below, 4.0 covered, 20.0 heavy, 0 none); `recentRecordsAreLiftGainsInLast30Days` and `recordsPerMonthCountsGainsPerCalendarMonth` (a first-ever lift is not a gain); `unitAndEffortAreCarried` (LB and RIR in, `unit = "lb"`, `effort = "rir"` out); `mutedWhenEmpty` (no history: all zero, no exception).
- [ ] **Step 2: Run, expect FAIL:** same command as Task 4.
- [ ] **Step 3: Implement** the remaining builder parts; `build()` composes Tasks 4 and 5. Run `WebSnapshotGoldenTest` with `-Dgolden.update=true` once, read the written JSON against the fixture history by eye (a handful of numbers), then run it again without the flag.
- [ ] **Step 4: Run, expect PASS.** **Commit:** `Add overview, trends, level and deeds to the web snapshot` (include the two fixture files).

---

### Task 6: Push the snapshot

**Files:**
- Modify: `S/data/cloud/CloudSync.kt` (new step after the workout push and tier steps, before `pushDerivedAggregates`), `app/src/main/res/values/strings.xml` (`cloud_sync_web_snapshot_failed`, `cloud_sync_web_snapshot_too_large`, `cloud_sync_web_needs_schema`), `S/data/Repository.kt` if a read is missing
- Create: `T/data/cloud/WebSnapshotPushTest.kt`

**Interfaces:**
- Consumes: `WebSnapshotBuilder.build`, `WebSnapshotJson.write/contentHash`, `WireLimits.SNAPSHOT_MAX_BYTES`, Task 2 `webReady`.
- Produces: `internal fun snapshotDecision(json: String, serverHash: String?): SnapshotDecision` (sealed: `TooLarge(bytes)`, `Unchanged`, `Upload`); `CloudSync.snapshotNow()` is not public API, the step runs inside `push()`.

- [ ] **Step 1: Write failing tests** `WebSnapshotPushTest`: `unchangedContentIsNotUploaded` (server hash equals new hash gives `Unchanged`); `changedContentUploads`; `missingServerRowUploads`; `overCapIsRefusedBeforeUpload` (json of `SNAPSHOT_MAX_BYTES + 1` bytes gives `TooLarge`, nothing sent); `outdatedBackendSkipsWebUploads` already covers the gate; `signedOutPushesNothing` (the existing `requireAccount` path, assert the step is not reached).
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.data.cloud.WebSnapshotPushTest"`.
- [ ] **Step 3: Implement.** In `push()`, when `webReady`: read `Units.current`, `EffortSetting.scale`, `Words.themed`, `ZoneId.systemDefault()`, build on `Dispatchers.Default`; `select content_hash from web_snapshots where user_id = me` (one tiny row); on `Upload`, `upsert(WebSnapshotRowDto, onConflict = "user_id")`. Any failure is a problem line (like the tier step), never a failed push. Order: strictly after sessions, sets, details and imported rows have landed.
- [ ] **Step 4: Run, expect PASS,** then the four strings tests, then the whole unit gate once the build lock is free: `python3 tools/gate.py --no-device` (compiles instrumented sources; run the instrumented suite later in Task 15's gate run). **Commit:** `Upload the web snapshot after each push, skipping unchanged content`.

---

### Task 7: Privacy policy and data docs

**Files:**
- Modify: `PRIVACY.md` (the "What leaves the device" table and the "Not uploaded" paragraph; date line "Checked against the codebase on ..."), `docs/PLAY_DATA_SAFETY.md` (the data types collected), `supabase/SELF_HOSTING.md` (a short "Web viewer" paragraph: it reads your own project, needs schema 30)

- [ ] **Step 1:** Add rows to the table, in the policy's own style: set kind, effort and superset group per set (`session_set_details`, readable only by you); imported workouts with their sets (`imported_sessions`, `imported_session_sets`, readable only by you, never in feeds); the stats snapshot (figures the phone computed: per-lift strength estimates, weekly set counts, streak, level and deed names; `web_snapshots`, readable only by you, one row, replaced on each sync, deleted with your account). State plainly that the snapshot's bodyweight-lift estimates are why it is owner-only, that body readings, measurements, height, sex and Health Connect data still never upload, and that signing out stops these uploads like every other.
- [ ] **Step 2:** Verify against Task 1 and Task 2 by grep: every new table and column named in the policy exists in the baseline, and no claim says "set rows: exercise name, set index, reps, weight, modifiers, done" without the new rows. `grep -n "session_set_details\|imported_session\|web_snapshots" PRIVACY.md` returns all four.
- [ ] **Step 3: Commit** `Update the privacy policy and data safety notes for the web viewer uploads`.

---

### Task 8: Web scaffold, data layer, sign-in, shell

**Files:**
- Create: `web/package.json` (scripts `dev`, `build`, `test`; dependency `@supabase/supabase-js` pinned; devDependencies `vite`, `vitest`, `jsdom` pinned; justification in the README: supabase-js owns token refresh and PostgREST encoding, the rest is dev-only), `web/vite.config.js` (`base: './'`, injects `__DEFAULTS__` = URL and publishable key read from `cloud-defaults.properties` at build time, so there is one source), `web/index.html`, `web/src/main.js`, `web/src/router.js` (hash routes `#/`, `#/workouts`, `#/workouts/<id>`, `#/lifts`, `#/lifts/<name>`, `#/trends`), `web/src/shell.js` (sidebar, bottom bar under 720 px), `web/src/theme.css` (tokens copied from `ui/theme/Theme.kt`, dark and light via `prefers-color-scheme` with a stored override), `web/src/schema.js` (`export const NEEDED_SCHEMA = 30; export const SNAPSHOT_V = 1`), `web/src/format.js` (`formatLoad`, `formatSets`, `formatDate` with an explicit zone), `web/src/data/source.js` (the interface), `web/src/data/supabaseSource.js`, `web/src/data/fixtureSource.js`, `web/src/auth.js`, `web/src/screens/signin.js`, `web/src/assets/` (the three Chakra Petch TTFs from `app/src/main/res/font`, `licenses/OFL-ChakraPetch.txt`, `licenses/MIT-react-native-body-highlighter.txt`), `web/README.md`
- Create tests: `web/test/auth.test.js`, `web/test/source.test.js`, `web/test/format.test.js`, `web/test/schema.test.js`
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
Behaviour: a boot check calls `schemaVersion()` (rpc `schema_version`, anon) before sign-in and shows `needs schema 30, this backend reports N` (never a stack trace); `?fixture=1` boots `createFixtureSource` with no network (used by tests and screenshots, built into dev only, tree-shaken from `build`); `getSnapshot()` returns `null` for no row and throws a typed `SnapshotTooNew` when `snapshot.v > SNAPSHOT_V`.

- [ ] **Step 1: Write failing tests:** `auth.test.js`: `signsInWithEmailAndPassword` (fake client records `signInWithPassword`), `badPasswordShowsPlainMessage`, `ownBackendIsRememberedLocallyOnly` (url+key in `localStorage`, never in the URL or a request header other than the Supabase client), `signOutClearsSession`. `source.test.js`: `everyQueryFiltersOnOwnUserId` (fake PostgREST builder records calls: each of `sessions`, `session_sets` (by `session_id` of own sessions), `imported_sessions`, `imported_session_sets`, `session_set_details`, `web_snapshots` carries `eq('user_id', me)`); `noSnapshotShowsHint`, `newerSnapshotAsksForUpdate`, `oldSchemaNamesNeededVersion` are asserted at screen level in Task 9's shell test using this layer. `format.test.js`: `loadFormatParity` iterates `test/fixtures/parity.json` `loadCases` and `formatSetsCases` and asserts equality; `rirTableComesFromSnapshot`. `schema.test.js`: `neededSchemaIsThirty`. Kotlin `WebViewerContractTest`: `webNeededSchemaEqualsNeededSchemaVersion` (regex over `web/src/schema.js`), `webSnapshotVersionEqualsWebSnapshotVersion`, `fixturesExist`.
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm ci && npm test` (JS) and `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.data.cloud.WebViewerContractTest"`.
- [ ] **Step 3: Implement** the files above. `supabaseSource.listWorkouts` pages `sessions` (columns `id, local_id, label, title, note, started_at, completed_at, edited_at, xp_awarded`) and `imported_sessions` 1000 rows at a time ordered by `local_id`; `getSets` for a synced workout selects `session_sets` by `session_id` and `session_set_details` by `session_id` and merges on `(exercise_name, set_index)`; imported sets come with their own kind columns. `fixtureSource` serves the golden snapshot plus a small fixture of rows (`web/test/fixtures/rows.json`: workouts and sets whose `local_id`s and `"<name>#<idx>"` keys match the snapshot; extend the Kotlin fixtures in Task 4 to also emit it if easier, via the same golden test).
- [ ] **Step 4: Run, expect PASS.** Then `npm run build`, serve `web/dist` and open `?fixture=1` at 1440x900 and 390x844 with the browser tools (`resize_window`): the sign-in screen and the shell render, dark and light (`resize_window colorScheme`), no console errors, `read_network_requests` shows no host other than the page. Save screenshots to `D:\Monarch\.tmp\audit\web\`.
- [ ] **Step 5: Commit** `git add web .gitignore app/src/test/kotlin/com/ironvellum/app/data/cloud/WebViewerContractTest.kt` then `Add the web viewer scaffold with sign-in, data layer and shell`.

---

### Task 9: Overview and Workouts (list and calendar)

**Files:**
- Create: `web/src/screens/overview.js`, `web/src/screens/workouts.js`, `web/src/components/{statCard,calendar,workoutRow,pager}.js`
- Create tests: `web/test/overview.test.js`, `web/test/workouts.test.js`

**Interfaces:**
- Consumes: Task 8 `DataSource`, `format.js`; snapshot `week`, `profile`, `deeds`, `trends.recent`, `trends.records30d`, `sessions[localId]`.
- Produces: `renderOverview(root, ctx)`, `renderWorkouts(root, ctx)` (`ctx = {source, snapshot, router}`).

Rules: the list is `listWorkouts()` newest first, merged across both origins, searched by title/label/exercise names that the snapshot knows (`sessions[id]` carries no names; search is by title and label only), filtered All / With PRs (`peaks > 0`) / Imported; a row with no snapshot entry shows its title and date and a dash for derived figures, never a computed value; the calendar groups days with `Intl.DateTimeFormat(..., {timeZone: snapshot.zone})`; "Stats as of" prints `builtAtMs`.

- [ ] **Step 1: Write failing tests** (jsdom, fixture source; each expected string is read out of the fixture, so the page is proven to show exactly the snapshot's numbers): `overviewShowsSnapshotNumbers` (streak, level, XP, hard sets, volume, records30d, deeds list text equal fixture values); `overviewHidesPlannedWhenNull`; `workoutRowShowsDerivedFiguresFromSnapshot` (minutes, exercises, hard sets, tonnes, `PR x N`); `listIgnoresForeignRows` (the fake source also returns a row with another `user_id`; it is not shown or counted); `importedOnlyAccount` (no synced sessions: list, calendar, tag "Imported", no crash); `emptyAccount` (zero workouts: friendly empty state); `pagesPastTheThousandRowCap` (2500 fake sessions: all listed, the source asked for 3 pages); `calendarUsesSnapshotZone` (a workout at 23:30 UTC lands on the zone's next day); `sessionMissingFromSnapshotShowsDashes`; `noSnapshotShowsHint` and `oldSchemaNamesNeededVersion` and `newerSnapshotAsksForUpdate` at shell level.
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- overview workouts`.
- [ ] **Step 3: Implement** both screens against the mockups (WebOverview, WebWorkouts); calendar month navigation is local state; rows link to `#/workouts/<localId>`.
- [ ] **Step 4: Run, expect PASS;** build and screenshot `?fixture=1#/` and `#/workouts` at 1440x900 and 390x844, compare to the mockups by eye (layout, not pixels), note any deliberate difference from the coverage table.
- [ ] **Step 5: Commit** `Add the web overview and workouts screens`.

---

### Task 10: Workout detail and body map

**Files:**
- Create: `web/src/screens/workoutDetail.js`, `web/src/components/{setTable,bodyMap}.js`, `tools/bodymap_web.py` (reads `S/ui/program/BodyMapShapes.kt` packed strings and the `FRONT_PARTS`/`BACK_PARTS` tables in `BodyFigures.kt`, writes `web/src/generated/bodyMapShapes.js`; deterministic, byte-identical on re-run), `web/src/generated/bodyMapShapes.js`
- Create tests: `web/test/workoutDetail.test.js`, `web/test/bodyMap.test.js`

**Interfaces:**
- Consumes: Task 8 `getSets`; snapshot `sessions[id].{peakSets,e1rm,peaks,hardSets,volumeKg,min,xp,muscleLevels}`, `muscles`, `rir`, `effort`, `unit`.
- Produces: `renderWorkoutDetail(root, ctx, localId)`; `renderBodyMap(el, {levels, view})` (SVG, levels 0-3 to the Most/Some/A little classes). The male figure is the default and a local toggle (browser storage) switches to the female one: sex never uploads, so the page cannot know it.

Set rows group by `(exercise_name, exercise_position)` in position order (null positions last, then name, as `CloudSync.workout` does), show Warm-up/Top/Back-off/Drop/Failure/Rest-pause/Working from `kind` (null reads Working), load via `formatLoad`, reps-left from `snapshot.rir[rpe]` when `effort = "rir"` else RPE, e1RM from `e1rm["<name>#<setIndex>"]`, PR tag from `peakSets`. Holds show seconds, distance sets show distance.

- [ ] **Step 1: Write failing tests:** `setRowsShowKindLoadRepsAndEffort`; `prTagFollowsPeakSets`; `e1rmComesFromSnapshotKey`; `olderClientSetsReadAsWorking` (kind null); `holdAndActivityRows` (reps 0 with 45 s; a 5 km row); `repeatedMovementBlocksStaySeparate`; `rirScaleShowsRepsLeftFromTable`; `lbUserSeesPounds`; `summaryShowsSnapshotFigures`; `noteIsThePublicNoteOnly`; `previousAndNextLinks`; `bodyMapColoursByLevel` (levels 3/2/1/0 give four distinct fill classes, every region of a muscle coloured, unknown muscle ignored); `generatorIsDeterministic` (Python, run twice, compare bytes) and `generatorCoversEveryRegion` (every `FRONT_PARTS`/`BACK_PARTS` key present).
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- workoutDetail bodyMap`, `python3 tools/bodymap_web.py --check`.
- [ ] **Step 3: Implement.** Credit the MIT source in the page footer link to `licenses/MIT-react-native-body-highlighter.txt`.
- [ ] **Step 4: Run, expect PASS;** screenshot `#/workouts/<id>` (a weighted pull-up workout, a hold workout, an imported one) at both widths.
- [ ] **Step 5: Commit** `Add the web workout detail with the muscle map`.

---

### Task 11: Lifts

**Files:**
- Create: `web/src/screens/lifts.js`, `web/src/components/lineChart.js` (small hand-rolled SVG chart, no chart dependency), tests `web/test/lifts.test.js`

**Interfaces:**
- Consumes: snapshot `lifts[]`; produces `renderLifts(root, ctx, name?)`. The chart plots `points` as given (x by `atMs`, range tabs 3 months / 6 months / 1 year / All filter points by `atMs >= now - range` where `now = builtAtMs`, no recomputation), diamonds where `pr = 1`, one line per selected variation chip; records card from `peak`, `heaviest`, `volumeKg`, `perWeek`, `sessions`; set-position card from `setRecords`.

- [ ] **Step 1: Write failing tests:** `listShowsVariationCounts`; `variationChipsSwitchSeries`; `chartPointsEqualSnapshotPoints` (number of circles + diamonds equals the filtered points, y of the last point maps to its value); `diamondsOnlyWherePrFlagged`; `rangeTabsFilterByBuiltAt`; `recordsCardShowsSnapshotValues`; `holdLiftShowsSecondsNotKg`; `unloadedLiftHasNoChart` (fewer than 2 points shows the phone's "one session" line); `searchFiltersLifts`; `deepLinkOpensTheLift`.
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- lifts`.
- [ ] **Step 3: Implement** to WebLift.
- [ ] **Step 4: Run, expect PASS;** screenshots at both widths for a two-variation lift and a one-point lift.
- [ ] **Step 5: Commit** `Add the web lifts screen with estimated 1RM charts`.

---

### Task 12: Trends

**Files:**
- Create: `web/src/screens/trends.js`, `web/src/components/{barList,trainingDays,monthBars}.js`, tests `web/test/trends.test.js`

**Interfaces:**
- Consumes: `trends.{floor,heavy,windows,weeks,days,recordsPerMonth}`, `muscles`, `profile.{streakDays,longestStreakDays}`; produces `renderTrends(root, ctx)`. Window tabs Last 7 days / 4 weeks / 12 weeks switch `windows["7"|"28"|"84"]`; bars list muscles by `perWeek` descending with a floor tick at `floor` and an amber tick at `heavy`, bar colour from `state`; the body map reuses `renderBodyMap` with `state` mapped to its classes; weekly charts use `lineChart` over `weeks`.

- [ ] **Step 1: Write failing tests:** `barsMatchWindowFigures` (every bar width ratio and label equals the fixture's `perWeek`); `stateDrivesColour` (covered, below, heavy, none classes); `windowTabsSwitchData`; `weeklyChartsUseSnapshotWeeks` (52 points, labels from the snapshot's `start`, not `Date.now`); `trainingDaysGridMatchesDays`; `streakTextIsDaysNotWeeks`; `recordsPerMonthBarsMatch`; `emptyHistoryShowsNoTrendsYet`.
- [ ] **Step 2: Run, expect FAIL:** `cd web && npm test -- trends`.
- [ ] **Step 3: Implement** to WebTrends.
- [ ] **Step 4: Run, expect PASS;** screenshots at both widths, dark and light.
- [ ] **Step 5: Commit** `Add the web trends screen`.

---

### Task 13: Local publish to GitHub Pages and the gate hook

**Files:**
- Create: `tools/web_publish.py`, `tools/test_web_publish.py`
- Modify: `tools/gate.py` (run `npm test` for `web/` after the Gradle phases when `web/package.json` exists, `npm ci` first if `web/node_modules` is missing; one summary line), `AGENTS.md` ("Tests run locally" paragraph gains one sentence: the web tests are in the gate; Pages deploys by `tools/web_publish.py`, no workflow), `web/README.md`

**Interfaces:**
- Produces: `python3 tools/web_publish.py [--dry-run]`: runs `npm ci`, `npm test`, `npm run build` in `web/`; refuses when the working tree has uncommitted changes under `web/` or the tests fail; adds a throwaway `git worktree` on branch `gh-pages` (creating it as an orphan on first use), replaces its contents with `web/dist` plus `.nojekyll`, commits (`Publish web viewer <short sha>`), and pushes **without force**; `--dry-run` stops before the push and prints the file list and total size. It never edits repository settings and adds no workflow.

- [ ] **Step 1: Write failing tests** `test_web_publish.py` (`python3 tools/test_web_publish.py`): `refusesDirtyWebTree`, `dryRunDoesNotPush` (a temporary bare remote), `secondPublishAddsACommitAndNeverForces` (the push command list contains no `--force`/`-f`/`+`), `includesNoJekyllAndNoSourceMaps`.
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
- [ ] **Step 5 (owner):** sign in on the web with email and password; for every screen compare five numbers against the phone (streak, level and XP, last workout's hard sets and volume, a lift's best e1RM, a muscle's weekly sets). Any difference is a bug in the builder, not a rounding choice. Check a friend's account (public profile) sees nothing from the new tables.
- [ ] **Step 6:** after Task 14's console steps, repeat sign-in with Google; confirm "Stats as of" and sign-out. Record the result in `docs/TODO.md` ("Hosted schema" section: project at 30; web viewer live).
- [ ] **Step 7: Commit** `Record the web viewer as live and the hosted schema at 30`.

---

## Self-review (run on the finished plan)

- **Spec coverage:** Goals (workouts list and calendar, sets with kind/load/reps/RPE/variation, per-lift and per-variation e1RM, PRs, best sets, trends, rewards): Tasks 9-12 with the coverage table; non-goals respected (no body weight, no writes, no sign-up); anti-goals: numbers differ from phone (parity fixture + Task 15 step 5), friend-visible data (Task 1 assertions, side table), imported flooding feeds (own tables, Task 1 `nothingReadsTheImportedTables`, `repushLeavesFeedInboxAndCircleUntouched`), workflow trigger (Task 13 adds none). Calls 1-8 map to Tasks 1, 4-5, 2, 2, 1, 6, 1, 14. Constraints: strings (Task 2/6), schema assertions (Task 1), no hosts or analytics (Tasks 8, 13), privacy (Task 7).
- **Type consistency:** `webReady`, `chunks`, `detailRows`, `SNAPSHOT_MAX_BYTES`, `WebSnapshotJson.write/contentHash`, `SnapshotInput`, `snapshot.v`, `NEEDED_SCHEMA = 30`, key format `"<exercise_name>#<wire set_index>"` are used identically across Tasks 1-12.
- **Proportion:** bodies are left to the implementer; the wire shape in Task 3 and the coverage table are the only large literals because both sides of the phone/web boundary depend on them.
