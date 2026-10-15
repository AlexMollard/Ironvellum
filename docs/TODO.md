# Ironvellum — outstanding work

What is genuinely left, why, and who can do it. Items are here because
something verifiable is missing, not because they sound like good ideas.

Every claim below was checked against the repo or a run; anything unproven says
so. Release mechanics live in `docs/RELEASE_CHECKLIST.md` — this file is the
open-work list. Last rewritten 2026-10-02; the history that used to fill it
lives in git (`git log`, `git log -S`), not here.

## Hosted schema: how a change reaches the live project

The hosted project is at `schema_version()` 29: the circles, release and crests
patches are applied (crests pasted by the owner 2026-10-15), `circle_draw_code()`
works, `sessions` and `public_feed` carry `edited_at`, warbands are gone, and the
worn crest is on profiles, the boards, the feed and `my_circle()`.

A schema change edits `supabase/migrations/0001_baseline.sql`, bumps the
`schema_version()` literal and `NEEDED_SCHEMA_VERSION` (`Cloud.kt`) together,
and ships an idempotent patch in `supabase/hosted/` that the owner pastes into
the SQL editor once, before the app build that needs it. `HostedPatchTest`
fails the unit gate if a patch drifts from the baseline. `python3 tools/gate.py
--backend` proves the baseline and the newest patch in Docker (applied twice,
assertions after); older patches migrate warbands and are not re-run there.

**Never paste `supabase/reset.sql` into the live project.** It deletes every
account. It exists for a throwaway project and for the gate's round trip.

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
   remains slow is compute, not queries: `SessionXp` costs about 1.2 s to build
   (its best-so-far fold and peak book over every set) and finishing a workout
   waits on it once. That is the next thing to profile.
2. **Scheduled file backup.** Settings > Data: pick a folder (Storage Access
   Framework), weekly archive export, keep the newest 5. Off until chosen.
3. **1.7 leftovers.** Plan builder `EffortStepper` in the Sets sheet style;
   "Rank" vs "Strength Rank"; Today header truncation beside "Add sex in
   Profile"; routine editor Load stepper showing "none"; persist Muscle Coverage
   dismissals per muscle; re-audit the 24-48dp touch targets.
4. **Measure.** Release APK size, and frame stats on the owner's phone.
5. **Home-screen widget.** Today's routine and streak; tap starts the workout.
6. **Honour the system font scale.** Drop `FIXED_FONT_SCALE`, fix every screen
   at large text, and add an instrumented font-scale sweep (closes the
   text-scale verification gap below).
7. **Health Connect write-back.** A finished workout is written as an exercise
   session. Needs a write permission, so the Play health-data form changes.
8. **String extraction.** Move UI text into string resources, plain and themed
   names included, with a lint guard against new literals. No translations yet;
   inviting translators is 1.9.
9. **Light theme.** Appearance: System, Dark, Light. Check the art on light.

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
| Play health-data declaration | All seven Health Connect permissions need the form; whether it was submitted is unknown from the repo | Mapping in `docs/PLAY_DATA_SAFETY.md` (Q5) |
| OAuth consent screen out of **Testing** | In Testing, only listed test users can sign in. The docs still say Testing; nothing in the repo proves it moved | Also needs the release keystore SHA-1 on the Android OAuth client |
| "Confirm email" in Supabase Auth | Unknown whether it was switched off; nothing proves either state | Optional: the sign-up trigger makes the profile either way |

## Open decisions (deliberately not taken here)

| Decision | Options | Recommendation |
|---|---|---|
| XP ceiling value | `100000000` (level 1414) in `push_aggregates` (`0001_baseline.sql`) | Tunable one-liner. Tighter (`10^7`, about level 450) is still decades of real training. Needs a hosted patch to change |
| Touch targets in the 24-48dp band | WCAG AA (24dp) is enforced on every tappable control and Material 48dp on the nav (`AccessibilityChecksTest`). Dense chips and list rows sit between; several controls were raised to 48dp since (back chip, segmented controls, tabs) | A layout call: forcing 48dp relayouts the information design. Which controls remain below 48dp was not re-audited |

## Verification gaps (honest, not deferred work)

- `find_lifter()` and `push_aggregates()` have no proven PostgREST round trip
  against the deployed project, and no local harness speaks it. Argument names
  are declared in `PushAggregatesArgs` / `FindLifterArgs` and checked against
  the SQL signatures; the SQL halves are proven in `supabase/test/assert_all.sql`.
  Only the transport is unproven. A signed-in sync, one friend request and one
  circle action against production are still the smoke test
  (`RELEASE_CHECKLIST.md` section 4).
- No real-device jank number. Emulator frame stats (software renderer) varied
  11 points between identical runs, so they attribute nothing.
- The cloud-backup exclusions (`db-snapshots`, `crash`, `ironvellum.db` in
  `backup_rules.xml` and `data_extraction_rules.xml`) are proven present and
  parsed, not proven at the payload level; that needs a device signed into a
  Google account.
- The signed-in sync path offline has never been exercised; it needs credentials.
- Emulator screenshots are not pixel-comparable with the phone (software
  rasterizer, anti-aliasing differs). Diff within one target, never
  across.
- Text-scale regressions have no automated guard: the scale is pinned
  (`FIXED_FONT_SCALE`), and the instrumented suite does not sweep display size.

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
