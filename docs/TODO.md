# Ironvellum — outstanding work

What is genuinely left, why, and who can do it. Items are here because
something verifiable is missing, not because they sound like good ideas.

Every claim below was checked against the repo or a run; anything unproven says
so. Release mechanics live in `docs/RELEASE_CHECKLIST.md` — this file is the
open-work list. Last rewritten 2026-10-02; the history that used to fill it
lives in git (`git log`, `git log -S`), not here.

## Hosted schema: how a change reaches the live project

The hosted project reports `schema_version()` 31 (HTTP 200). The circles,
release, crests, web-viewer and snapshot-cap patches are applied; `circle_draw_code()` works,
`sessions` and `public_feed` carry `edited_at`, warbands are gone, and the worn
crest is on profiles, the boards, the feed and `my_circle()`. The version probe
confirms the reported schema version, not live mutation or phone installation.
The 4 MiB snapshot uploader's schema prerequisite is satisfied; the updated
phone build and a real-account sync still need verification.

The read-only viewer is implemented under `web/`: Overview, Workouts, workout
details, Lifts and Trends, with generated phone appearance, art and both figures.
Local browser verification covers desktop and phone widths in dark and light.
The site is published at https://alexmollard.github.io/Ironvellum/ by the
manual-only `pages.yml` workflow. The first deployment succeeded and its
sign-in surface was verified in a browser. Live email/password reads across
all five pages were verified using disposable accounts, including private-row
isolation and colliding workout IDs; the test accounts were deleted afterward.
The phone-data and Google OAuth roundtrip acceptance are still outstanding;
phase two remains gated on that acceptance. Design data contracts and offline
fixture instructions are in `web/README.md`.

A schema change edits `supabase/migrations/0001_baseline.sql`, bumps the
`schema_version()` literal and `NEEDED_SCHEMA_VERSION` (`Cloud.kt`) together,
and ships an idempotent patch in `supabase/hosted/` that the owner pastes into
the SQL editor once, before the app build that needs it. `HostedPatchTest`
fails the unit gate if a patch drifts from the baseline. `python3 tools/gate.py
--backend` proves the baseline and the newest patch in Docker (applied twice,
assertions after); older patches migrate warbands and are not re-run there.

**Never paste `supabase/reset.sql` into the live project.** It deletes every
account. It exists for a throwaway project and for the gate's round trip.

## Roadmap checklist

The short view, by version; the detail of each item is in the sections below.

### 1.7 (released)
- [x] Release prepared and tag `v1.7` pushed; F-Droid builds from the tag

### 1.8: faster, sturdier, easier to reach
- [x] 1 History at scale: no re-pricing per set tick, indices, targeted queries (7d5c9ccf)
- [x] XP rescore in one pass over history: 4.1 s to 0.08 s on 450 trials (52351cdd)
- [x] 2 Scheduled file backup to a chosen folder, newest 5 kept (8680225f)
- [x] 3 1.7 leftovers and the 24-48dp touch-target audit (53815525, e144686c, bc739d7c)
- [x] 4 Measure: release APK size (foss 14.3 MB); phone frame stats 2026-10-19 (debug build, 8.9% janky, p95 34 ms); a release-build pass and per-screen attribution are open
- [x] 5 Home-screen widgets "Today" and "Streak & week", 2x1 and 4x2 (8bef739a, f046207e, 9a120550)
- [x] 6 Honour the system font scale, with a 200% sweep test (131a1e53 to 61106a9b)
- [x] 7 Health Connect write-back of finished workouts, opt-in (c21654c7)
- [x] 8 String extraction into resources, with a lint guard against new literals: strings.xml is translator-ready (positional placeholders, context comments, locale config, docs/TRANSLATING.md), en-XA and ar-XB swept, social, idle/Veil, Cloud and Account messages, data copy and counts-as-plurals moved (phase 8); ratchet 1768 literals in 113 files down to 495 in 54, every one a justified exemption (`justified` in HardcodedUiLiteralTest: exercise and template catalogue names, citations, formats, routes, keys). Rank titles, skill, title, crest, deed and relic catalogue names stay English data for a later step; ar-XB and en-XA layout leftovers are listed in the phase 7 report for 1.9
- [x] 9 Light theme: System, Dark, Light (47d97395, 384353ba, fbd34fa7)
- [x] Gate housekeeping: back under 5 minutes (3m36s to 3m58s after an edit), self-healing emulator lock, lint warnings 58 to 44 (be444d0e, d8bd7153)
- [x] Weekly recap: Sunday 19:00 notification (private on the lock screen), recap screen from Today Mon to Wed and Stats, 1080x1350 share card in the app theme; range aggregation and card renderer are shared with the Month/Year in review
- [x] Month / year in review: scrolling story with the best week, lifts gained or new, most trained muscles, volume, calendar (a year: twelve month bars), streak and skills claimed; 1080x1350 share card in the app theme and words; Today card in the first week of a month (a year, in January, replacing December's) with a dismiss; listed in Stats > Training > Recaps; a lifter who started part way through is told "since you started in March" and compared with nothing; no notification
- [x] Live workout on the lock screen: Android 16 Live Update (chip with set progress or rest countdown, ProgressStyle card), improved notification below 16, Start today / Resume shortcuts, Quick Settings tile, Settings > During a workout > Lock screen and shortcuts; the Live Update itself is not yet seen on an Android 16 device (no API 36 image installed)
- [x] Release: version 1.8 (code 9), CHANGELOG, store changelog 9.txt, README, store and privacy text, store screenshots 01/05 reshot and 09-11 added (6f38ac2d, fdb7ac4e, fddedb6e)
- [x] Owner: a week on the phone first: a locked-screen workout (Live Update), Sunday's recap, heart-rate permission, share card save
- [x] Owner: push tag `v1.8`

### 1.9: Play preparation, photos, translations (owner agreed 2026-10-22, built in this order)
- Large screens: request portrait on phones and center medium-width content at up to 600dp; expanded screens place Today, Train, workout history, live workout and muscle details in two panes.
- Archive backups include portable settings; older archives leave current settings unchanged. `python tools/phone_backup.py --serial SERIAL --output NEW_DIRECTORY` also captures private preferences with the database; keep that folder private.
- The live exercise picker starts its fatigue report with the workout and waits for it before opening when advice is enabled.
- Dedicated-exercise prompts in the workout and routine editor survive activity recreation; Switch and Keep retain their original target.
- Convert previews matching saved workout entries alongside history and backs up before moving both in one transaction.
- The rest panel gives the alternate workout action the remaining width, keeping “Do Push Strength” on one line at 320dp.
- Today’s visible exercise targets open the shared detail sheet with the selected exercise and modifiers; the fit budget retains 48dp targets.
- [ ] 1 Play-ready code: done: privacy policy row in Settings > Support (both flavours), PLAY_DATA_SAFETY.md updated for heart rate, background reads and WRITE_EXERCISE, `bundlePlayRelease` builds (unsigned, 19.1 MB). Open: sign it once the owner's keystore exists; release-build frame stats on the phone (a release build signed with the debug key installs over the debug one and keeps the data; the owner uses it about 3 minutes, then the debug build goes back)
- [ ] 2 Progress photos, on the phone and in the folder backup, never the cloud:
  - workout photo: optional "Add a photo?" at the end of a workout, of anything (a thumbs-up is fine), at most one a day, kept with that workout
  - weekly poses: front, side and back on a day you pick (Sunday by default), separate from the workout photo, compared week to week in Stats > Body
- [ ] 3 Translations by Haiku agents, back-translated and checked by Sonnet, plus the long-text layout sweep: Spanish, German, French, Portuguese (BR), Japanese, Korean, Chinese (Simplified). Arabic waits for the RTL leftovers
- [ ] 4 Upkeep: Gradle 9.8, WorkManager 2.12, Ktor 3.6; gate back under 5 minutes and the instrumented flake found
- [ ] Owner: Play developer account ($25, identity check)
- [ ] Owner: release signing keystore
- [ ] Owner: confirm the public privacy policy URL (default: PRIVACY.md on GitHub)
- [ ] Owner: Play health-data declaration, including the 1.8 write permission
- [ ] Owner: Google sign-in OAuth consent screen out of Testing
- [ ] Play store listing
- [ ] Closed test: 12+ testers opted in for 14 continuous days; recruit spares
- [ ] Read the test: time to first logged set, week 2 and week 4 retention, where testers get stuck; simplify where they do

### 2.0: Google Play launch
- [ ] Supabase: spend cap, usage alerts, sign-up rate limit, recheck the free-tier limits
- [ ] Recheck Google developer verification status (F-Droid build included)
- [ ] Production release on Play
- [ ] Launch posts (r/bodyweightfitness, r/fdroid, "free Strong alternative" threads)

### Not planned
- Wear OS
- iPhone (revisit if Play shows traction)

## 1.8 plan (owner agreed 2026-10-16, built in this order, one build at a time)

Theme: faster, sturdier, easier to reach. Measured against the F-Droid field on
2026-10-16 (GymMane, Flexify, wger, LibreFit, Bati, RepForge and others).

1. **History at scale.** Done 2026-10-16. Seeded a fictional three-year lifter
   (3 sessions a week, 468 sessions, 8,424 sets over real catalogue lifts, a
   weigh-in a week) with `ThreeYearHistoryTimingTest` (opt-in: `timings=true`,
   see its header) and timed the paths that read the whole history. Median of
   three on the emulator, ms; one run of the old code against one of the new, and
   the emulator drifts by up to 40% between runs (Today read 680 to 968 before
   and 621 to 660 after, with nothing about it changed), so read the large gaps:

   | Path | Before | After |
   |---|---|---|
   | Five set ticks in a live trial, history re-priced by the session screen | 9,850 (5 re-pricings, on the main thread) | 0 (none) |
   | One live-tally tick, 24 sets | 82 to 153 | 3 (54 for a cold open) |
   | Lift page (Bench Press) to first data | 594 to 784 | 130 |
   | Workout detail to first data | 64 | 10 |
   | First session date | 53 | 1 |
   | Strength profile / load for an extra set | 73 / 66 | 38 / 28 |
   | Today to first data | 680 to 968 | 621 to 660 (unchanged) |
   | Stats, Workout log to first data | 64, 58 | 49, 34 |
   | Titles ledger, Strength Rank | 143, 121 | 146, 102 (unchanged) |
   | Finishing a workout (18 sets) | 1,282 to 1,690 | 1,315 (unchanged) |

   What the measurement found, which was not what the item guessed. The indices
   are in (migration 43 to 44) but move nothing at this size: the per-lift
   queries were already 1 to 9 ms on 8,400 sets. What hurt was that Room re-runs
   `observeCompletedWithSets()` on any write to `set_logs`, so every set ticked in a
   live trial re-priced the whole history in the session screen's four consumers,
   about two seconds a tick on the main thread. `observeHistory()` is now
   `distinctUntilChanged()`, which removes it. The tally keeps what it priced
   (`SessionXp.TallyMemo`), so a tick prices the new set alone instead of every
   prefix, and the `shortcut:` comment is gone. The Lift page scores only its own
   movement (`SetRecords.recordsOf`), and the first-session date, lifetime
   strength, strength profile, workout detail and reminder read targeted queries.

   Left on the full history, because they price or rank every set: Today, Stats,
   the log, the Titles ledger and Strength Rank, `completeSession`, the cloud push
   fingerprints, the XP rescore, the export and `SessionViewModel`'s records. What
   remained slow was compute, not queries: `SessionXp` cost about 1.2 s to build on
   the emulator. Profiled on the JVM (52351cdd): the rescore built one per trial
   over the whole history, now a `SessionXp.Timeline` folds it once (450 trials:
   4.1 s to 0.08 s), and one build fell from 13 ms to 7 ms.
2. **Scheduled file backup.** Done 2026-10-16 (8680225f). Settings > Data:
   pick a folder (Storage Access Framework), weekly archive export through the
   same `exportJson` the manual export uses, keep the newest 5 files it wrote
   (never any other file). Off until chosen. One WorkManager periodic job, with
   a 7-day initial delay (without it the job runs at once and doubles the first
   backup). Last backup, a lost folder permission and a failed write show in
   red in Data. Run against a real folder on the emulator: 7 backups made, 5
   kept, a foreign file untouched.
3. **1.7 leftovers.** Done 2026-10-16 (53815525, e144686c, bc739d7c). Checked
   on the emulator first: the plan builder's `EffortStepper` already draws the
   Sets sheet's compact pads (09719818), the Today header already stacks "Add sex
   in Profile" under the name (8332604c), the rite editor's Load already reads
   "Add" or "BW" (562889b4), Settings > Appearance already puts "Choose a pair"
   under Colour pairs, and deed and skill standards already hold on "Add sex in
   Profile" (a7d7511c); nothing to change there. Changed: Muscle Coverage
   dismissals are kept per muscle (`DismissedHeavyStore`, appearance prefs); the
   rank-up row reads "Strength Rank up" so it is not taken for the level title;
   the tape pane is "Body measurements" in plain mode and the stale roster and
   standings lines and the empty measurement line follow the vocabulary switch;
   the 24-48dp audit is in Open decisions, with the controls raised.
4. **Measure.** Release APK size, and frame stats on the owner's phone.
   APK measured 2026-10-16 (unsigned, R8): foss 14.3 MB, play 15.0 MB; of foss,
   `classes.dex` 8.4 MB (the guide and catalogue data in Kotlin included) and
   `assets` 5.4 MB (exercise art paths). No action needed. Frame stats on the
   owner's S25 Ultra (SM-S938B, 120 Hz) 2026-10-19, foss debug at ac978ee1, about
   3 minutes of normal use (Today, Stats, a lift page, the log, a live workout):
   5847 frames, 8.9% janky (missed deadline), median 7 ms, p90 17 ms, p95 34 ms,
   p99 73 ms; GPU p99 8 ms; 486 slow UI-thread frames. The GPU is idle; the
   hitches are main-thread work. A debug build is the worst case (no R8, no
   baseline profile). Launch alone (204 frames) was 28% janky, p99 150 ms. Open:
   measure a release build, and attribute the slow frames to screens (JankStats or
   a Perfetto trace) before changing anything. Raw dumps: `.tmp/framestats/`.
5. **Home-screen widget.** Done 2026-10-16 (8bef739a). Two widgets from one state
   function (`widget/TodayWidgetState.kt`, built on Today's own day kind, week ticks
   and streak): "Today" (the day card) and "Streak & week", each in a 2x1 and a 4x2
   layout, designs approved by the owner. Classic RemoteViews, no new dependency. The
   button begins today's rite through the call Today's Begin makes, resumes a trial
   under way, begins an empty workout on a free day and opens the plan builder with no
   plan; anywhere else opens Today. Pushed, never polled: a Room invalidation observer
   (sessions, set_logs, presets) redraws on any workout, set or plan write, and a
   WorkManager run at 00:01 local turns the day over, re-aimed by `TimeChangeReceiver`.
   Checked on the emulator in begin, live, done and rest-day states at 2x1 and 4x2
   (`.tmp/widget-shots/`). Live elapsed minutes only move when something is written, so
   they can lag a minute or more between sets.
   Redesigned to the v4 mockups: four size buckets (2x1, 4x1, 4x2, 4x3+) chosen through the
   API 31 size map (options bundle on 29-30), exercise rows with set pips and rep boxes,
   records starred, rest-day and no-plan faces, Week with date columns and set bars, both
   themes. Pictures and side-by-side sheets in .tmp/widget-v4-shots/. A Recent row only
   opens the app: no intent repeats a past workout.
6. **Honour the system font scale.** Done 2026-10-16 (131a1e53, e57b90b5, a7e09c50).
   `FIXED_FONT_SCALE` is gone, so Compose follows the system scale. Every screen and
   sheet was audited at 1.0, 1.3 and 2.0 on the emulator with seeded data and fixed to
   wrap, grow, stack or scroll. Scale is capped only where an element cannot grow, via
   `CappedFontScale`, each with a comment: bottom-bar labels, the week rail, the
   body-map FRONT/BACK labels and the sealed-day stamp. `FontScaleSweepTest` sets the
   real system scale to 2.0 and asserts that key controls are shown and not clipped.
7. **Health Connect write-back.** Done 2026-10-16 (c21654c7). Settings > Health Connect
   has "Save finished workouts to Health Connect" (off by default; turning it on asks
   for `WRITE_EXERCISE` only). A sealed, non-imported workout is written as one
   strength-training `ExerciseSessionRecord` (start, end, title or routine name, public
   note), after the Room commit, on an app-lifetime scope, so a seal never waits on or
   fails because of it. Idempotent: client id `ironvellum-<id>-<startedAtMs>` (the start
   time guards against a reinstall reusing an id), version = the latest of seal, amend
   and write time, so amends and title or note edits replace the entry; delete and
   reopen remove it. Failures are logged and show as "Last save failed" on the page; a
   revoked permission shows plainly and the switch reads off. No exercise segments:
   Health Connect checks segment types against the session type and wants per-exercise
   timing, which means a catalogue-to-type table and invented timings. The Play declaration is in the owner list below. Checked on the emulator (API 35, real Health
   Connect): write, amend (still one entry) and delete (entry gone), screenshots in
   `.tmp/hc-shots/`. The write-back switch is included in portable settings, but the Health Connect
   grant is not; a restored switch still checks the device permission before writing. Archive or cloud
   restore and "delete all data" leave earlier entries in Health Connect.
8. **String extraction.** Move UI text into string resources, plain and themed
   names included, with a lint guard against new literals. No translations yet;
   inviting translators is 1.9. Owner decisions 2026-10-16: built AFTER item 9;
   scope is screen text only (every screen, dialog, notification, widget, setting,
   error and generated hint, about 3,300 literals); exercise names, the exercise
   guides and title/skill names stay English data for a later step. Plain and
   themed become two whole sentences per key (`key` and `key__t`), no concept
   interpolation, `<plurals>` and positional args; `Words` resolves the pair at
   runtime. A language ships on its plain strings; the Themed names switch only
   shows for languages whose themed strings are complete. Phases, each behind the
   gate: foundation (`UiText`, a disk-backed test resolver, a ratcheting guard
   test, GlossaryTest reading strings.xml), Words and WordLines, notifications and
   widgets, small screens, train and program screens, domain-generated copy, then
   Weblate prep with an en-XA pseudolocale sweep.
   Phase 1 done (a3cf4ea3): `UiText`, `DiskStrings`, `StringsXmlTest`, `HardcodedUiLiteralTest` (baseline 4,539 literals in 187 files, `docs/STRINGS.md`), Settings > Reminder migrated.
   Phase 3 done (cbd17a7f): notifications (channels, inbox, Summons, live workout and rest) and widget text moved to resources.
Phase 4 done: Settings pages, sheets and dialogs (all but the debug-only XP rescore dialog), first-run onboarding and the notification ask moved to resources (baseline 3,045 literals in 158 files).
Phase 5 done: the train screens (live workout, sets sheets, rest bar, finish and seal, workout detail, routines, editor, share, exercise explorer) and the program screens (plan builder, Forge cycle, preview, generator suggestions, effort editor, muscle coverage) moved to resources (baseline 1,971 literals in 128 files). Left for phase 6: strings built by domain and data helpers (`SchemeText`, `SessionClock`, `RestTimer`, `Effort`, `Units`, `WorkoutShare`, `RoutineCode`) and the `launchGuarded` log labels in SessionScreen.
Phase 6 done: domain-generated copy (clocks, units and loads, set kinds, effort, scheme text and suggestions, progression, warm-ups, workout share, routine codes, energy, muscles, day names, plan generator, templates and rules reasons, exercise picker, plan builder headers) moved to resources as whole-sentence pairs; English text sniffing replaced by `removalSamePattern` and `ProgramRules.isMaxNote`; `launchGuarded` added to DEVELOPER_CALLS (baseline 1,768 literals in 113 files). Left for phase 7: Weblate prep and the en-XA pseudolocale sweep, plus the remaining small literals (TermInfo, Gear/TrainingSplit/SessionKind labels, goal chips, Repository errors, citation labels, victory peak detail weights such as `0kg`, and ProgramTemplates seeded names stay data).
Phase 8 done: the screens no phase covered moved to resources: social (account, account settings, circles, feed, inbox, comments, lifter, leaderboard), idle / Veil (Veil, rate, collection, crests, houses, buy sheet, draw results), Cloud, Account and Circles messages, backup / Health Connect / import warnings, and the three template summaries that named a concept (baseline 1,016 literals in 72 files down to 496 in 55). `tools:ignore="PluralsCandidate"` came off the `<resources>` root: every count of things is a `<plurals>`; strings whose number is an amount, ordinal, range or progress carry a per-string ignore with a translator comment. The baseline now holds only justified exemptions: `HardcodedUiLiteralTest.justified` gives the reason for each file and `every baseline entry is justified` fails on one without. Item 8 stays ticked only while that holds.
9. **Light theme.** Appearance: System, Dark, Light. Check the art on light.
   Built before item 8 (owner, 2026-10-16); palette mocked up and approved first.
   Done: Vellum tokens, per-theme accents, themed widgets (47d97395, 384353ba, fbd34fa7).

Skipped deliberately: Wear OS.

## 1.9 and 2.0: Google Play (owner agreed 2026-10-16)

1.9 prepares, 2.0 launches. The `play` flavour, Google sign-in and
`docs/PLAY_DATA_SAFETY.md` already exist; the owner items below (keystore,
privacy-policy URL, health-data form, OAuth out of Testing) are the blockers.

- **1.9: translations and Play prep.** Invite translators onto the 1.8 string
  resources. Owner: developer account ($25, identity check), release keystore,
  privacy policy at a public URL with an in-app link, store listing, health-data
  declaration (now including the 1.8 write permission).
- **Closed test, started during 1.9.** A new personal account needs 12 testers
  opted in for 14 continuous days before production access (Play Console Help
  14151465). Recruit spares: guides report the clock resetting if the count
  drops, which Google's page does not state.
- **2.0: production launch on Play.** The owner's Supabase project stays the
  default so social works for Play users; before launch turn on its spend cap
  and usage alerts, rate-limit sign-ups, and recheck the free-tier limits.
- **Developer verification.** Google's verification for apps installed outside
  Play was scheduled for four countries from 2026-09-30 and worldwide in 2027;
  the Play account's identity check should cover the F-Droid build too. Recheck
  the status before 2.0.
- **iPhone: not planned.** Near rewrite (Compose UI, HealthKit, Live
  Activities, Sign in with Apple), $99/yr and a Mac. Revisit if Play shows
  traction.

## Blocked on the owner (credentials or console access)

| Item | Why it is blocking | Notes |
|---|---|---|
| Signing keystore | Release builds are unsigned: `app/build.gradle.kts` only wires a signing config when all four `ironvellum.keystore.*` keys (or `IRONVELLUM_KEYSTORE_*` env vars) resolve to a real file, and `local.properties` holds none of them. The build warns and names the keys | A credential the owner must own — never generated here. Env vars were not inspected |
| Hosted privacy-policy URL | Play requires a URL, not an in-app document. The content exists in `PRIVACY.md`; no URL to it is recorded anywhere, and the app has no in-app link to the policy (the checklist asks for one: `RELEASE_CHECKLIST.md` section 6) | The GitHub URL of `PRIVACY.md` is public and not editable by third parties (`docs/open-source/02-distribution-and-flavours.md`). Same URL in Play Console and in the app |
| Play health-data declaration | All seven Health Connect read permissions need the form, and so does the 1.8 write permission `WRITE_EXERCISE` (exercise sessions, opt-in); whether any was submitted is unknown from the repo | Mapping in `docs/PLAY_DATA_SAFETY.md` (Q5) |
| OAuth consent screen out of **Testing** | In Testing, only listed test users can sign in. The docs still say Testing; nothing in the repo proves it moved | Also needs the release keystore SHA-1 on the Android OAuth client |
| "Confirm email" in Supabase Auth | Unknown whether it was switched off; nothing proves either state | Optional: the sign-up trigger makes the profile either way |

## Open decisions (deliberately not taken here)

| Decision | Options | Recommendation |
|---|---|---|
| XP ceiling value | `100000000` (level 1414) in `push_aggregates` (`0001_baseline.sql`) | Tunable one-liner. Tighter (`10^7`, about level 450) is still decades of real training. Needs a hosted patch to change |
| Touch targets in the 24-48dp band | WCAG AA (24dp) is enforced on every tappable control and Material 48dp on the nav (`AccessibilityChecksTest`). Dense chips and list rows sit between; several controls were raised to 48dp since (back chip, segmented controls, tabs) | Re-audited 2026-10-16 with `AccessibilityChecksTest.reportControlsBelowTheMaterialTarget` (opt-in: `-Pandroid.testInstrumentationRunnerArguments.touchAudit=true`, read `adb logcat -d -s TouchAudit`), which walks the sweep's screens at 411dp and logs every tappable control from 24 to 47dp. It found 55; raised to 48dp where only a minimum height or a short width changed: every button (42 to 48), the text link ("Got it", 37 wide), the plate and bar steppers (44), the exercise search field (24 high, inside a 48dp panel), Today's header link and name, the picker's info and star buttons, the feed and comment links, the live trial's warm-up, drop, effort and rep-left rows, "Add a set" and "More" links, the reorder grip and the Veil's inscription link. Still under 48dp, and why: the body-map muscle zones in a technique's "worked muscles" diagram (24 to 46dp, zones of one drawing; forcing 48 overlaps neighbours); "needs L-sit (Core)" under a skill-tree node (76 x 25, the tree's row heights are measured around it); the technique sheet's LOG tab (46 x 48, tab widths are shared); the compact stepper pads (40 x 48, two steppers share a range row on a 360dp phone); the live trial's own stepper pads (44 x 48, two figures share one half of a row); the colour hue strip (44 high, a drag area, not a button). Screens the sweep cannot reach on an empty install (live trial, Sets sheet, rite editor) were audited from source only |

## Verification gaps (honest, not deferred work)

- `find_lifter()` and `push_aggregates()` have no proven PostgREST round trip
  against the deployed project, and no local harness speaks it. Argument names
  are declared in `PushAggregatesArgs` / `FindLifterArgs` and checked against
  the SQL signatures; the SQL halves are proven in `supabase/test/assert_all.sql`.
  Only the transport is unproven. A signed-in sync, one friend request and one
  circle action against production are still the smoke test
  (`RELEASE_CHECKLIST.md` section 4).
- Real-device jank is measured on a debug build only (item 4); no release-build
  number yet, and no per-screen attribution. Emulator frame stats (software
  renderer) varied 11 points between identical runs, so they attribute nothing.
- The cloud-backup exclusions (`db-snapshots`, `crash`, `ironvellum.db` in
  `backup_rules.xml` and `data_extraction_rules.xml`) are proven present and
  parsed, not proven at the payload level; that needs a device signed into a
  Google account.
- The signed-in sync path offline has never been exercised; it needs credentials.
- Emulator screenshots are not pixel-comparable with the phone (software
  rasterizer, anti-aliasing differs). Diff within one target, never
  across.
- The font-scale sweep runs the seeded app at 2.0 by default (`-e fontscale 1.3` for
  another size). The onboarding steps and the Automatic backup panel's on state (it
  needs the system folder picker) are checked by hand, not by the suite.

## Not planned

- **Server-authoritative XP economy.** Today the ranked columns are unwritable,
  level and title count are derived, and XP and strength are bounded monotonic
  claims. Moving curves, quest detection and reconciliation server-side ends
  offline-first XP and leaves two implementations forever. Revisit only if the
  boards become competitive.
- **Baseline profile / macrobenchmark.** Release cold start measured a median
  932 ms on a software-GPU emulator, an upper bound. No sign of a startup
  problem; revisit if a real-device number says otherwise.
- **Automatic CI.** Every workflow is `workflow_dispatch` only; the gate runs
  locally (`python3 tools/gate.py`, see `AGENTS.md`). Do not add a trigger.
