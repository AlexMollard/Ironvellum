# 06: Board fairness before strangers compete

**Goal:** before the shared cloud is advertised, no board rewards not
training, and the docs describe the scoring the code actually runs.

**Done when:**

- idle accrual is bounded by the owner's chosen rule, and the Muster board
  ranks on it;
- `docs/TODO.md`'s stale strength row is closed;
- the visibility default is a deliberate decision rather than a leftover.

## 1. Idle accrual pays for absence (OWNER decision, then code)

`domain/Idle.kt`:

- full rate for 24 h (`FULL_RATE_HOURS`, `:31`);
- a linear taper to 10% over the next 48 h (`TAPER_WINDOW_HOURS`, `:38`;
  `MIN_EFFICIENCY`, `:41`);
- then 10% **forever**, with no cap (`accruedExact`, `:125-153`).

Measured effective hours paid: 1 week 60.0, 1 month 115.2, **1 year 919.2,
which is 38 times a capped day** (`docs/TODO.md` row 33). That essence is
pushed as `shadow_essence` (`CloudSync.kt:122`) and the `shadow_board` view
orders by it (`0008_shadow_board.sql:35-45`), so absence climbs the board.

The stated product rule is "accumulation caps at 24 hours, the rate decays
every few hours away". Options, from TODO row 33:

| Option | Behaviour | Year away pays |
|---|---|---|
| (a) | Hard cap at 24 effective hours; the taper becomes dead code | 24 h |
| (b) | Decay within the first 24 h, then stop | < 24 h |
| **(c, recommended)** | Keep the taper, cap the **total** at 3 days' worth (72 effective hours) | 72 h |
| (d) | Today's behaviour | 919 h |

Why (c): it bounds the board, keeps the "figures keep working while you rest"
flavour for a normal rest week (a week still pays 60 h, below the cap), and is
the smallest change: one `min()` in `accruedExact`.

This reshapes an economy real users have already earned against. Follow skill
`score-economy-rework-safety`: commit before any mutation test, and do not
restate already-banked essence. The cap applies to future collections only.

**Code once decided:**

- Add `MAX_EFFECTIVE_HOURS` to `Idle.kt`.
- Rewrite the KDoc that says decay "punishes stopping" (`:17`) and "the roll
  NEVER stops" (`:101`).
- Unit-test the boundaries at 71 h, 72 h, 1 week and 1 year of absence.
- Mutation-prove the cap by removing it, which must fail the 1-year case.

## 2. Strength score and holds: the TODO row is stale (code)

`docs/TODO.md` row 28 says `StrengthIndex.repScore` counts a 45 s hollow hold
as 45 reps. That is no longer true. The current code
(`SCORING_VERSION = 2`, `StrengthIndex.kt:42`) routes holds through
`holdScore` (`:84-85`), which applies
`MovementDifficulty.holdRepEquivalents` at 5 s per rep equivalent
(`MovementDifficulty.kt:291, 330-331`). `sessionScore` (`:132-141`) and every
caller (`Repository.kt:1055-1066`, `SetRecords.kt:64-67`, `Models.kt:247-251`,
`SessionScreen.kt:375-382`) branch on holds first.

Also stale: the row claims "nothing recomputes history".
`rescoreStrengthScores` runs once per `SCORING_VERSION` bump from
`ensureSeeded` (`Repository.kt:136-155`).

**Code:**

- Add one unit test pinning the owner's real case: a 45 s hollow hold scores
  as 9 rep equivalents, not 45. It must fail if `sessionScore` stops
  converting.
- Move row 28 out of "Open decisions", noting the option taken (b) and the
  scoring version it shipped in.
- Correct the "nothing recomputes history" sentence.

## 3. Who can see the boards (OWNER decision)

- Reads go through `can_view()` (`0001_init.sql:117-121`): the lifter
  themselves, anyone set `public`, or friends. The default visibility is
  `'friends'` (`0001_init.sql:22`).
- So with no change, the boards are friends-only, and strangers appear only if
  they opted into `public`.
- **Recommended:** keep it. A tracker that publishes a newcomer's training to
  strangers by default contradicts the privacy pitch the whole FOSS plan rests
  on. Public boards stay opt-in.

## 4. Ship order

`0008`, `0009` and `0010` go live first (plan 01). `0011` ships with the
next app build, and that build also carries the idle cap from section 1, so
the first build that can write to a public board is already the fair one.
