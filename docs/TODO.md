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
| Court at the largest display size AND 2.0x text | Only ~320dp wide by ~347 "text lines" of room. The step gauge now gives way there so the quest button stays reachable (measured y=1329..1557 with the gauge dropped, absent with it shown). (a) keep dropping the gauge; (b) also drop the week rail to keep it; (c) accept a scrolling dashboard at that setting only | **(a)**, shipped. The step count is on Stats; the button has no second home. Scrolling was tried and is a dead end — a weighted panel inside a scrolling column gets an infinite height and collapses |

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
  recorded so nobody repeats them: scrolling the dashboard collapses the
  weighted quest panel (a weight inside `verticalScroll` gets an infinite
  height), and `screenHeightDp >= 560` never fires because the largest display
  size still reports 693dp — height alone is the wrong measure, height divided
  by font scale is the right one (891 stock, 347 at the extreme).

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
