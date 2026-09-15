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
