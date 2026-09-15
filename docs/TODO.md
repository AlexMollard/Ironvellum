# Monarch — outstanding work

What is genuinely left, why, and who can do it. Items are here because
something verifiable is missing, not because they sound like good ideas.

Every claim below was checked against the repo or a run; anything unproven says
so. Release mechanics live in `docs/RELEASE_CHECKLIST.md` — this file is the
open-work list.

## Blocked on the owner (credentials or console access)

| # | Item | Why it is blocking | Notes |
|---|---|---|---|
| 1 | Apply `supabase/migrations/0009_backend_hardening.sql` | **Live exposure.** Until it lands, anyone holding the shipped publishable key can enumerate the accepted-friendship graph — including for hunters who set `private` — via the `is_friend` RPC | Idempotent; chain-tested against `postgres:16` |
| 2 | Apply `0010_hunter_discovery.sql` | Friend requests cannot bootstrap without it: a by-name lookup returns nothing and the app reports that a real hunter does not exist | Idempotent |
| 3 | Apply `0011_server_side_aggregates.sql` **with the matching app build** | Revokes direct writes to the ranked columns; an older client's profile upsert starts failing the moment it lands | Ship together, never before |
| 4 | Signing keystore | Release builds are unsigned (`app-release-unsigned.apk`); the build warns and names the four `local.properties` keys | A credential the owner must own — never generated here |
| 5 | Hosted privacy-policy URL | Play requires a URL, not an in-app document | Content exists in `PRIVACY.md` |
| 6 | Play health-data declaration | All seven Health Connect permissions need the form; none are in the heightened-scrutiny family | Mapping in `docs/PLAY_DATA_SAFETY.md` |
| 7 | OAuth consent screen out of **Testing** | In Testing, only listed test users can sign in | Also needs the release keystore SHA-1 |
| 8 | `versionCode` bump per upload | Currently `1`; a reused value is rejected | Sequence in the release checklist |

## Open decisions (deliberately not taken here)

| Decision | Options | Recommendation |
|---|---|---|
| Server-authoritative XP economy | Today: ranked columns are unwritable, level and title count derived, XP/strength bounded monotonic claims. Alternative: move activity curves, quest detection and reconciliation server-side | **Don't.** It ends offline-first XP, makes the device's number provisional, and creates a permanent dual implementation. Revisit only if the boards become competitive |
| XP ceiling value | `100000000` (level 1414) | Tunable one-liner in `0011`. Tighter (`10^7` ≈ level 450) is still decades of real training |
| Touch targets in the 24–48dp band | WCAG AA (24dp) enforced everywhere and Material 48dp on the nav. Dense chips and list rows sit between | A layout/design call: forcing 48dp relayouts the information design |
| Court at 2.0x font: manifest or single screen | The quest card fits the day, the program name and the button, but not the movement rows (4 at 1.0x, 0 at 2.0x). (a) keep today's degradation; (b) let the dashboard scroll above 1.5x; (c) drop the step ring at large scales to buy ~294px | **(a)**, currently shipped: a lost list is one tap away in Train, a lost button is not. Full measurements under the font-scale entry below |
| Court on a short window (landscape, or the largest display size with 2.0x text) | The quest panel is weighted, and a weighted child measured after the unweighted content above it gets nothing — the button went with it. Below 520 "text lines" of height the panel wraps its content and the page scrolls; the step gauge also yields below 400. Measured: stock portrait 891, landscape 411, largest display at 2.0x 347. (a) keep this; (b) design a landscape Court; (c) lock to portrait | **(a)**, shipped. The CTA is reachable with zero swipes in all four combinations of orientation and scale. (c) was tried and reverted: lint flags `LockedOrientationActivity` and `DiscouragedApi`, and Android 16 ignores the lock on large screens anyway |

## Verification gaps (honest, not deferred work)

- `find_hunter()` and `push_aggregates()` are **compile-verified only** from
  here. No local harness speaks PostgREST, so the first real round trip against
  the deployed project is their first proof. The SQL halves are proven in
  `supabase/test/assert_all.sql`.
- The `backend` and `instrumented` CI jobs have never run on GitHub's runners —
  a CI change is only truly tested by CI. Both were replayed locally command for
  command.
- Emulator screenshots are **not** pixel-comparable with the phone: the software
  rasterizer differs, and ink seeds resolve per pixel size. Diff within one
  target, never across.

## Not planned

- **Baseline profile / macrobenchmark.** Release cold start measured at a median
  **932 ms** on a software-GPU emulator, which is an upper bound. No evidence of
  a startup problem. Revisit only if a real-device measurement contradicts it.
- **Previously regressed product rules, re-verified in code.** Each was listed as
  a past regression and each currently holds: the weight stepper shows `BW`
  rather than `0kg` stepping back from 2.5 (SessionScreen returns null, SkillDetail
  renders `BW` at 0.0); sets are removable while at least one remains; modifiers
  stay editable after an exercise is added; the celebration is a `Dialog`, so it
  composes in its own window above everything; skill journal rows are clickable;
  exercises rearrange via the preset editor's bounded up/down controls.
- **Health Connect, audited against every known trap.** Grouped aggregation uses
  `LocalDateTime` (Instant ranges throw and zero every day), one request per
  metric so a single denial degrades only itself, manifest/request parity across
  all seven permissions, the provider `<queries>` block and the
  `VIEW_PERMISSION_USAGE` alias both present, and empty days are never written.
  The daily `PeriodicWorkRequest` is enqueued from `MonarchApp.onCreate`.
- **Crash-class sweeps, clean.** Every unguarded collection access and every
  `!!` in production code was traced to its guard: leaderboard index and rank
  derive from one list, `CREST_FRAMES[index]` is bounded by `items(size)`,
  chart tails sit behind size checks, and each `!!` is null-checked at the call
  site. `PresetEditorViewModel.moveEntry` was the only hole and is fixed.
- **Compose list keys.** Three of four `items()` calls carry keys; the fourth
  iterates a compile-time constant whose order cannot change.
- **Feature breadth verified against the product rules.** 43 activities across
  Cardio/Sport/Climbing/Water/Mobility (22 sports), 95 titles, per-activity
  title rules, the equipped crest driving the plate, and calorie estimates
  derived from user data. Novel activity categories are appended by
  `activityCategoryOrder`, so a new category cannot hide an activity.
- **Screens visually verified phone-free** (Court, Train, Stats, Codex,
  Shadow): palette, padding, no overflow, tappable metric tiles, line-chart
  captions. Two suspected defects turned out to be misreads of the screenshot —
  settle layout questions with the accessibility dump's bounds, not pixels.
- **Large system font scale, measured on device at 1.0x, 1.5x and 2.0x.** Three
  defects found and fixed: the six nav labels wrapped mid-word and then clipped,
  the rank and class line was cut mid-phrase ("E-Rank · the" instead of "the
  Awakened"), and the worn title cut without an ellipsis.

  Nav labels are pinned to their design size rather than merely capped — both
  `fontSize` **and** `letterSpacing` are declared in `sp`, so clamping only the
  size still let the label grow ~10% at 1.5x and ~20% at 2.0x. Caught by
  comparing dump widths across scales, not by looking: `Court` read
  102 -> 112 -> 123px. With the tracking clamped too, all three scales now
  measure identically (`Court` 102px, `Shadow` 139px ending at 1047px of 1080).

  Deliberate tradeoff: six slots cannot share one screen width at 2.0x however
  they wrap, and the label is supplementary — the icon carries the meaning, and
  the `contentDescription` a screen reader announces is unaffected by scaling.

  A 2.0x sweep then found the worst defect of the set: on the Court screen the
  **primary action disappeared**. The quest card's header was unweighted and the
  button declared last, so Compose measured the header first, it consumed the
  card, and `ACCEPT QUEST` was measured at zero height. The title and note now
  sit inside the one flexible child, making the button the only unweighted one
  and therefore measured first — it renders at both scales (611x89px at 2.0x,
  319x47px at 1.0x) and the movement rows degrade away instead. The quest
  header row also collided with the set tally; the tally now yields (21px gap
  at 2.0x, 357px at 1.0x).

  **The geometric sweep that reported "zero issues" was worthless and is not
  the evidence here.** Its squish predicate (`w < 90 && h > 2w`) was tested
  against a deliberately reverted nav bar — a build whose labels demonstrably
  wrapped mid-word — and it found nothing, because the wrapped labels measured
  154x162px, far wider than the 90px threshold. A detector that cannot fire on
  a known defect proves nothing. The bad state separates cleanly on the aspect
  ratio of a single-word label instead: 1.05 broken versus 0.29-0.40 fixed.
  What actually found the real defect was checking sentinel strings —
  `ACCEPT QUEST` was simply absent from the dump.

  Display size was swept too, at 320dp wide (the largest "Display size" step):
  clean on its own. Combined with 2.0x text it broke Court again — the quest
  button absent, the titles counter wrapped to 179x228px — because that
  configuration leaves only ~347 "text lines" of height. The step gauge now
  yields there, which restores the button (y=1329..1557). Two wrong turns are
  recorded: `screenHeightDp >= 560` never fires because the largest display
  size still reports 693dp — height alone is the wrong measure, height divided
  by font scale is the right one (891 stock, 411 landscape, 347 at the
  extreme). And scrolling the dashboard *alone* collapses the weighted quest
  panel, because a weight inside `verticalScroll` gets an infinite height —
  which is why the panel must drop its weight AND the page scroll together.
  Either change on its own loses the button; both together carry it.

  With Court fixed, every other destination was swept in the worst
  configuration available (landscape + largest display size + 2.0x text) and the
  primary content of all six is reachable with zero swipes, as are the live
  session screen and settings. Court was the only vulnerable screen because it
  is the only one that does not scroll.

  The preset editor took three attempts, and every failure was the harness
  rather than the app: tapping coordinates from a dump taken BEFORE scrolling
  taps whatever moved into that spot, so the editor never opened and the
  "unreachable" searches were scrolling the presets list behind it. Taking the
  bounds and the tap in the same breath opens it, and `Save Preset` is reachable
  (2 swipes at stock, 7 at the largest display size with 2.0x text, in both
  orientations). The first pattern was wrong too — that button is a
  Material `Button`, so its label stays `Save Preset` rather than being
  uppercased the way `MonarchButton` does it.

  Process death was checked too: with a live session open, the app was
  backgrounded and its process killed for real (`pidof` empty afterwards), then
  relaunched from the launcher. No crash — the crash buffer is empty — and the
  session survives, because it lives in Room rather than in memory: Court offers
  `RESUME · Quick Session` and reopening it lands back on `TRIAL IN PROGRESS`.
  Navigation position is not restored, which is a deliberate non-goal: the work
  is safe and one tap away. Two traps voided the first attempts — `am kill` is
  ignored for a foreground app (background it first), and the instrumented gate
  uninstalls the app, so a sweep straight after one tests an empty launcher.

  Offline was checked in airplane mode: the app launches, Court renders with its
  resume CTA, and Guild degrades to its sign-in prompt rather than hanging or
  crashing (crash buffer empty). Logging itself never touches the network — it
  is Room, covered by the instrumented suite — so the part that remains
  unverified is the SIGNED-IN sync path offline, which needs credentials and is
  listed under the gaps above rather than claimed here.

  Scale was measured for the first time: 1,000 completed sessions with 5,000
  sets — four sessions a week for five years — inserted into an isolated
  database, then the three read paths that load on a UI thread were timed.
  Journal 76ms, titles ledger 122ms, export 131ms, completed count 0ms on an
  emulator. The guard now fails above 1,500ms, which keeps roughly an order of
  magnitude of headroom for slower hardware while still catching the regression
  that matters (an accidental N+1 or a scan per row lands in seconds). The
  first draft used 4,000ms, which with 30x headroom would never have failed —
  the numbers came from deliberately setting the budget to 1ms and reading the
  assertion, which also proved the check fires.

  The UI side of that scale check found two eager lists over unbounded data.
  The workout log rendered EVERY completed session from a plain scrolling
  Column, so a row was composed per workout whether on screen or not — a
  thousand of them after a few years. It is now a `LazyColumn` with stable keys
  (month header as its own item, sessions keyed by id). Train's activity log had
  the same shape and is capped at six rows, since `FULL WORKOUT LOG` beside it
  leads to the complete month-grouped history. A new instrumented test seeds a
  completed session and asserts the log renders that session's own label, so the
  conversion is proven by content rather than by compiling; removing the row
  emission fails it with `the log did not render the session labelled "Heavy
  Pull"`.

  Three more lists over data that grows with use were capped the same way, each
  with the unbounded source named in a comment: the stats readings list (12 of
  them; the charts above already span the whole history), a movement's set log
  in the explorer (14 training days, newest first), and the skill practice
  journal (14 practice days). None of these is a lazy list, so every row handed
  to them is composed — the cap is what bounds the work, and the full record
  stays reachable through the charts and detail screens.

  Both halves are proven against real data rather than by reading the code: an
  instrumented test seeds 60 completed sessions, then asserts the Train log
  renders a bounded number while the full log can SCROLL to far more. Removing
  the Train cap fails it with `rendered 60 of 60 sessions`; silently capping the
  full log at three per month fails it with `reached 9 distinct sessions`. The
  first draft of that test was wrong in an instructive way — it counted the full
  log's rows once and found 5, which is correct lazy behaviour rather than a
  defect, so the assertion had to scroll and accumulate instead.

  Both of those tests seed the app's OWN database (a second handle on the file
  is the divergence trap on `DbSnapshot`), so they now clear the sessions table
  before AND after. Without the cleanup every run left 60 more sessions behind,
  which would eventually break the flow tests that reason about today's quest —
  for a reason invisible in their own code. Checked by running the whole
  instrumented suite twice in a row rather than once.



  The minified release build was re-smoked after all of this, since R8 problems
  only appear there: `assembleRelease` is clean at 7.2 MB, debug-signed with
  `apksigner` (verify returns 0, `CN=Android Debug`), installed, and all six
  destinations render with an empty crash buffer. Still unexercised in release:
  the Supabase DTO serializers' actual HTTP round trip, which needs credentials.
  The two failure modes behind that worry were checked directly in the APK
  though, and both are closed:

  - **Serializer stripping.** Concatenating the dex and probing for names that
    must survive: `$$serializer` x16, and `ProfileDto`, `SessionDto`,
    `SessionSetDto`, `FeedEntry`, `ProfileNameDto`, `MonarchDatabase_Impl` all
    present. Kept names mean decoding will not fail for want of a serializer.
  - **The ServiceLoader engine trap.** The APK's `META-INF/services` entries ARE
    renamed (`dn1`, `fd0`, `hd2`, …), which is exactly what breaks ktor engine
    discovery in release — but `Cloud.client()` names the engine at compile time
    (`httpEngine = OkHttp.create()`), so nothing depends on that discovery.
    `okhttp3` and `OkHttpEngine` are both in the dex.

  `bundleRelease` also builds clean at 9.4 MB, which packages by a different
  path from the APK.

  Resource shrinking was checked the same way, and two obvious methods are
  both worthless here: the APK's resource FILES are renamed (`res/tn.png`), so
  name matching finds nothing, and AGP re-encodes images in release, so byte
  matching against the sources finds nothing either. `aapt2 dump resources`
  reads the actual table: every art resource is present by name, and following
  each entry to its renamed file shows real bytes rather than the stub the
  shrinker leaves behind — `art_empty_quests` 188 KB, `art_empty_stats` 131 KB,
  `art_empty_skills` 105 KB, the vector line and rank icons ~1 KB each. There
  are no dynamic (`getIdentifier`) lookups in the app, which is what would make
  the shrinker strip art it cannot see referenced.

  The release credential gate was proven by its NEGATIVE case, reversibly:
  blanking `supabase.key` fails `validateReleaseBackend` with
  `Missing local.properties keys: supabase.key`, and restoring the file returns
  it byte-for-byte (sha256 verified, 273 bytes) after which the release builds
  again with only the expected unsigned warning. A gate that has never been
  seen to fail is not a gate — a release shipping blank keys would "work" and
  be dead at runtime.

  The Play data-safety sheet was audited against the code that uploads, and one
  real omission turned up: `push_aggregates` sends level, total XP, earned title
  count, lifetime strength and the **training streak**, but the sheet declared
  only the shadow figures under aggregates. Now declared, with the call named.
  `PRIVACY.md` already mentioned the streak, so the gap was in the form answers
  alone. Note the method — matching DTO field names against the document finds
  28 "missing" fields and means nothing, because the form asks for categories
  rather than columns; the check that works is listing what the client actually
  writes and asking which category declares it.

  The crash journal — which stands in for a crash-reporting SDK — was verified
  with a real crash for the first time: `am crash` routes through the app's
  `UncaughtExceptionHandler`, a record lands in `files/crash/` (893 bytes with
  app version, API level, device, thread and the full stack), and after
  relaunching, Settings reads `1 crash record · latest 2026-09-15T15:22:49Z`.
  Two traps: the section sits below the fold, so a dump without scrolling says
  "none" and looks like a failure, and `am crash` kills the process — the
  journal being readable afterwards is the point, not a side effect.

  The "syncs automatically on a daily schedule" rule was checked at the OS
  rather than at the call site: `dumpsys jobscheduler` shows a job owned by
  `com.monarch.app/androidx.work.impl.background.systemjob.SystemJobService`,
  waiting on `TIMING_DELAY`, with `batteryNotLow=true` and no other constraint —
  which is exactly what `HealthSyncWorker.schedule()` builds, so the job is ours
  and came from that request. The one-day interval itself is not printed in that
  dumpsys output, so it stays sourced from the code
  (`PeriodicWorkRequestBuilder(Duration.ofDays(1))`) rather than claimed as
  observed.

  StrictMode is now installed in debug builds (log, never crash), because
  nothing in this project would otherwise notice main-thread disk or network
  work — the way an app earns an ANR on a cold morning with a big database. It
  immediately reported 183 violations on launch, two of them ours:
  `CrashJournal.install` (directory checks) and `DbSnapshot.capture` (the byte
  copy), with the slowest single read at 72ms.

  Both were then measured rather than assumed: the snapshot copies a 0.3 MB
  database in **11 ms** at 1,000 sessions, and it is throttled to one copy a
  day, so the cost lands on a single cold start and is not an ANR. Moving it
  off the main thread would race Room's first open — the capture has to finish
  before anything touches the database — so the defence is a budget instead:
  the timing test fails above 150ms. The rest of the violations are framework
  work at startup (`ActivityThread`, preference and profile loading), not ours.

  Then StrictMode earned its place by finding a live one: opening Settings read
  the crash journal **during composition**, so every visit listed a directory on
  the main thread (`SettingsScreen.kt:317-318` → `CrashJournal.crashCount()` and
  `latestTimestamp()`), and the Clear button did the same on the tap's own
  frame. Both now run on `Dispatchers.IO`. Navigating all six destinations plus
  Settings afterwards reports **zero violations in our code**.

  That zero is only worth something because the same run proves the detector was
  live: launching with the log cleared reports 10 violations first. An earlier
  version of this check reported "no violations" while the app was not even
  installed — the instrumented gate uninstalls it — so the control comes first
  now. And the async read was proven to actually read: with a real crash
  recorded, Settings shows `1 crash record · latest …`, not the default zero.

  Hunting main-thread I/O turned up a worse defect in the opposite direction:
  `shareText` had **no** disk I/O because it put the whole export in
  `Intent.EXTRA_TEXT` — and the export measures **0.87 MB at 1,000 sessions**,
  against a binder transaction limit near 1 MB. The data-rescue action would
  have thrown `TransactionTooLargeException` precisely for the hunters with the
  most to lose, and works today only because a young database is small. The
  share sheet now stages the JSON in `cacheDir/exports` and passes a
  `content://` URI through a `FileProvider` whose path config exposes that one
  directory (the database, its snapshots and the crash journal stay
  unreachable). The crash-log share uses the same route rather than keeping a
  second path with its own failure mode. Driven on device: the chooser opens and
  `cache/exports/monarch_export.json` is written, crash buffer empty.

  A privacy hole of my own making, found by checking the backup rules against
  what is actually on disk: `monarch.db` is excluded from cloud backup and
  device transfer, but `DbSnapshot` writes **byte-for-byte copies of that same
  database** into `files/db-snapshots/`, and the `file` domain is backed up by
  default. The exclusion was being defeated by the safety net added beside it.
  Both rule files now exclude `db-snapshots` and `crash` in every domain
  (cloud-backup and device-transfer), confirmed present in the packaged APK.
  The export staging directory needs no rule — `cache` is never backed up.
  Verified as far as this emulator allows: the rules are byte-present in the
  packaged APK (`db-snapshots`, `crash`, `monarch.db` all appear in both files)
  and the platform parses them with no error. A payload-level proof was
  attempted and **failed for harness reasons, not app reasons**: the local
  transport stored nothing for this package, and a control build with the
  exclusions stripped out stored nothing either, so an absent payload was no
  evidence of exclusion. Recorded as a gap rather than a pass — proving what
  the backup actually contains needs a device signed into a Google account.

  The two headline calendar numbers had no test of their own. The title rules
  for streak and best-week were covered by setting the ledger field directly,
  which never runs the arithmetic that produces it — and `trainingStreakDays`
  read `LocalDate.now()` internally, so its boundaries were unreachable from a
  test at all. `today` is now a defaulted parameter (production callers
  unchanged, still device-local in the same zone the dates were bucketed in) and
  twelve cases cover the grace day, a two-day gap, gaps after old history,
  month/leap-day/year boundaries, dates ahead of a backwards clock, and the
  rolling seven-day window being `[start, start+7)` rather than a calendar week.
  Mutation-proven both ways: dropping the grace day fails one, widening the
  window to eight days fails another.

  The calorie estimator had a live defect that 16 existing tests did not reach:
  `stepsKcal` reported `"height"` as a missing input on the branch where height
  was **present and used**, so the detail sheet told a hunter to "Log height for
  a sharper estimate" about a height they had already logged. What is actually
  absent there is Health Connect's measured distance, which nobody can log by
  hand; the basis string now discloses "stride from height" instead. No existing
  test asserted the `missing` list at all — which is precisely why a
  user-visible wrong sentence survived a suite that covered the arithmetic well.
  Seven cases added for what the original 16 skipped: speed banding needing both
  distance and duration, exact band thresholds, the unknown-name fallback chain,
  a REPS set costing nothing on its own, elapsed time not being charged twice
  once timed sets have consumed part of it, the stride disclosure, and coarse
  confidence propagating to a day total. All four behavioural mutations caught
  by name.

  **Two process failures worth more than the fix.** First, I wrote the new tests
  with `write` over a path that already held `EnergyTest.kt` and destroyed all
  16 — caught only because the suite total came to 210 when 205 + 21 should be
  226, and the file showed as *modified* rather than added. The original was
  recovered from `HEAD` and merged; nothing was lost, but nothing would have
  noticed either. Read before writing a test file. Second, I reported the
  estimator as having "no tests at all", which was simply false. Two earlier
  mutation attempts were also **no-ops by construction** (one added a comment,
  one an unused val) and "passed" while proving nothing; a mutation that does
  not change behaviour is not evidence.

  Following the clobber, I audited whether any earlier commit had quietly
  shrunk a test file: three did, and all three were legitimate — six
  measurement-goal tests died with the goals feature the owner reversed, one
  typeface-weight test died with a reverted typeface, and three aggregate-DTO
  tests moved server-side into `supabase/test/aggregates_probe.sql` (the reason
  is written in the test file's own header). No silent losses but mine.

  That audit did surface a real gap. `Dtos.kt` opens by stating the rule the
  whole cloud read path rests on — every `@SerialName` must match its
  snake_case column exactly — and **nothing enforced it**. kotlinx does not
  fail on a mismatch, it decodes the default, so a typo in `shadow_essence`
  shows a leaderboard where every hunter has 0 essence: a wrong ranking
  presented as fact, no error anywhere, and the client-side names are invisible
  to the SQL probes. `WireNamesMatchSchemaTest` now reads the migrations that
  define those relations (base tables plus their later `add column`s, and the
  last definition of each view) and checks the serial names against them.
  Proven in both directions: three DTO typos each fail their own case, and
  dropping `shadow_rate` from the view in the migration fails too. The guard
  also asserts it parsed something, because a schema-parsing test that finds no
  columns would otherwise pass vacuously forever.

  Writing it found two bugs in my own parser first, both caught because the
  test failed loudly rather than silently: one `alter table` may carry several
  `add column` clauses (0002 adds `title` and `note` together), and a view's
  select list ends at its own `from` — not at the `from` inside the correlated
  sub-select that computes `sessions_last_7d`.

  The same stringly-typed hole existed on the RPC calls, which this file listed
  as "compile-verified only": `push_aggregates` took six hand-written
  `put("p_...")` keys and `find_hunter` one, none of them checkable. A renamed
  parameter is a 404 from PostgREST, and because the aggregates push is wrapped
  in `runCatching` (deliberately, so a pre-0011 database doesn't fail the whole
  sync) the symptom is a cloud row that silently stops updating — the
  leaderboard freezes and nothing reports why. The names now live in
  `PushAggregatesArgs` / `FindHunterArgs` and are encoded by `rpcArgs()`, since
  the pinned postgrest-kt `rpc` overload takes only a `JsonObject`. Two tests
  check them against the `create function` signatures and against the encoded
  body, because a correct descriptor with a broken encoder would still send the
  wrong payload. Caught by mutation from both sides: renaming the argument
  client-side and renaming the parameter in migration 0011 each fail both
  tests. Removing an argument entirely does not even compile now, which is
  better than a test — that is the point of the typed shape.

  Verified the library contract from the artifact rather than from memory: the
  typed `rpc(function, T)` overload I first assumed does not exist in the
  pinned version, and the compiler listed the two real candidates.

  Separately, the calendar broke a test rather than the app: `WorkoutFlowTest`
  assumed today has a seeded program, and the four-weekday seed meant it failed
  the morning the date rolled to a rest day. It now walks the week rail to a day
  that has one.

  A sentinel sweep then covered 18 primary controls across all six
  destinations at 1.0x and 2.0x. Two read as regressions — `BEGIN` on Train and
  `SHADOW DRAW` on Shadow — and both were **false positives**: each is reachable
  after one swipe (y=698 and y=909). That is the rule the method needs: on a
  scrolling screen, absence from the first viewport means nothing. Court is the
  only non-scrolling destination, which is exactly why a missing button there
  was real. No further defects found.

  Still no automated guard: the instrumented suite runs at the device's own
  font scale, so a regression needs a manual `settings put system font_scale`
  sweep, and the check must be sentinel strings plus measured geometry rather
  than a screenshot or an unvalidated heuristic.

  Mutation-proven rather than assumed: rebuilt at `HEAD~2` (the pre-fix
  layout), `ACCEPT QUEST` is **absent** from the 2.0x dump; with the fix it sits
  at y=1712..1801, clear of the nav bar at y=2189, and 1.0x is unchanged at
  y=1547..1594.

  Upstream growth, measured at 1.0x vs 2.0x (top edge of each section): the
  identity name does not move, the XP line drops +191px, the step ring +294px,
  the quest title +385px. Nothing is pushed under the nav bar — the card
  absorbs it — but the movement manifest is what pays: **4 rows render at 1.0x,
  0 at 2.0x.**

  **Owner decision, not taken here:** at 2.0x the quest card can show the day,
  the program name and the button, but not the movement list. Three options,
  none silently chosen: (a) keep today's behaviour, the list degrades away and
  the card stays one screen; (b) let the dashboard scroll above 1.5x, which
  breaks the single-screen rule deliberately rather than accidentally; (c) drop
  the step ring or stat panel at large scales to buy the list ~294px. Today's
  behaviour is (a) because losing the list is recoverable — the same movements
  are one tap away in Train — while losing the button is not.
- **Lint's 10 remaining warnings.** Audited individually, all deliberate: 8 are
  `ModifierParameter` ordering convention, and the 2 asking for a plain
  `Modifier` default are the two composables that must carry their own size
  (`DeedsBoard` is the scroll container that must never be handed infinite
  height; `TrendChart` carries the chart height). Changing either moves layout
  at every call site to satisfy a convention.
- **R8 keep rules.** Reviewed: scoped to `com.monarch.app.**`, no blanket
  `-keep class ** { *; }`, and each rule documents the consumer rule it mirrors.
- **String extraction for localisation.** Hundreds of strings, high breakage, no
  second locale asked for.
