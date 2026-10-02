# Ironvellum — outstanding work

What is genuinely left, why, and who can do it. Items are here because
something verifiable is missing, not because they sound like good ideas.

Every claim below was checked against the repo or a run; anything unproven says
so. Release mechanics live in `docs/RELEASE_CHECKLIST.md` — this file is the
open-work list. Last rewritten 2026-10-02; the history that used to fill it
lives in git (`git log`, `git log -S`), not here.

## Hosted schema: how a change reaches the live project

The hosted project is at `schema_version()` 28 (owner-verified in the SQL
editor 2026-10-02): the circles and release patches are applied,
`circle_draw_code()` works, `sessions` and `public_feed` carry `edited_at`, and
warbands are gone. Nothing is owed there.

A schema change edits `supabase/migrations/0001_baseline.sql`, bumps the
`schema_version()` literal and `NEEDED_SCHEMA_VERSION` (`Cloud.kt`) together,
and ships an idempotent patch in `supabase/hosted/` that the owner pastes into
the SQL editor once, before the app build that needs it. `HostedPatchTest`
fails the unit gate if a patch drifts from the baseline. `python3 tools/gate.py
--backend` proves the baseline and the newest patch in Docker (applied twice,
assertions after); older patches migrate warbands and are not re-run there.

**Never paste `supabase/reset.sql` into the live project.** It deletes every
account. It exists for a throwaway project and for the gate's round trip.

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
  rasterizer, ink seeds resolve per pixel size). Diff within one target, never
  across.
- Text-scale regressions have no automated guard: the scale is pinned
  (`FIXED_FONT_SCALE`), and the instrumented suite does not sweep display size.

## Not planned

- **Server-authoritative XP economy.** Today the ranked columns are unwritable,
  level and title count are derived, and XP and strength are bounded monotonic
  claims. Moving curves, quest detection and reconciliation server-side ends
  offline-first XP and leaves two implementations forever. Revisit only if the
  boards become competitive.
- **Honouring the system font scale.** Text is pinned to one size on purpose
  (`FIXED_FONT_SCALE`, dialogs re-pinned with `FixedTextScale`): a lifter who
  enlarges system text gets no larger text here, an accessibility cost taken for
  one correct layout. Display size still applies. Revisit if the app ever needs
  a store accessibility review.
- **Baseline profile / macrobenchmark.** Release cold start measured a median
  932 ms on a software-GPU emulator, an upper bound. No sign of a startup
  problem; revisit if a real-device number says otherwise.
- **String extraction for localisation.** `strings.xml` holds one string, the
  UI is Kotlin literals, and no second locale was asked for.
- **Automatic CI.** Every workflow is `workflow_dispatch` only; the gate runs
  locally (`python3 tools/gate.py`, see `AGENTS.md`). Do not add a trigger.
