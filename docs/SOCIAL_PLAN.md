# Social plan

Owner decisions (2026-09-29):

- **Friends first.** Built for people the owner knows. If they like it, it opens up
  to a wider community later, so nothing may block that: the existing `public`
  profile visibility stays the switch.
- **Text and numbers only.** No images or video until people ask for them. Icons,
  fonts and layout carry the look. File storage stays at 0.
- **The owner moderates.** Reports land in a table only the service role can read;
  the owner reviews them in the Supabase dashboard (`supabase/moderation.sql`).
- **Free tier.** Measured 2026-09-29: database 27 / 500 MB, egress 0 / 5 GB,
  1 MAU, storage 0 / 1 GB. Text rows are a few hundred bytes; nothing here may
  grow with time rather than with people (the inbox is derived, not stored).

## C7 proposal: shared wire metadata

Status (2026-11-12): proposal only, requested during deduplication review. No
generator, generated contract or cloud behavior has changed.

### Recommendation

Generate a narrow browser contract from the existing Kotlin serialization
descriptors and wire enums, rather than adding a second hand-maintained schema.
Keep SQL authoritative for server columns, constraints and privacy. Keep DTOs
authoritative for the Android client's accepted wire shape.

Existing foundations:
- `data/cloud/Dtos.kt` defines serial names, nullability and compatibility
  defaults, plus `Reaction.wire`.
- `WireNamesMatchSchemaTest` already compares serializer descriptor names with
  SQL table/view columns.
- `WebAssetsGoldenTest` already exports app-derived browser assets into
  `web/src/generated/`, checks freshness and supports `-Dgolden.update=true`.
- `web/src/data/socialSource.js` separately repeats reaction values and wire
  row shapes, alongside browser-specific normalization and request behavior.

### Smallest useful adoption

1. Start with `Reaction.wire` values exported to
   `web/src/generated/social-wire.json`. Replace the browser's reaction-value
   literal with those generated values, retaining unknown-reaction handling.
2. Only when browser tooling actually checks the declarations, extend the
   existing exporter with an explicit allowlist of `FeedEntryDto`, `CommentDto`,
   `InboxRowDto`, `LeaderboardDto` and `WorkoutXpLeaderboardDto`. Export wire
   names, primitive kinds, nullability and optional-field metadata. Do not scan
   or expose every DTO.
3. Generate raw-row JSDoc declarations beside that asset for browser authoring.
   These describe snake_case transport rows, not the camelCase screen models
   or the complete server schema. The current Vite build and Vitest suite do
   not type-check JSDoc; declarations alone do not enforce wire-name correctness.
4. Add other genuinely shared row families only after their current decoding
   differences are documented. Do not generate Android DTOs from SQL or replace
   request implementations.

### Integration and acceptance

Use the existing exporter update/check workflow documented in `web/README.md`;
check committed outputs in the local gate. No automatic workflow, dependency,
Python generator, Gradle configuration hook or runtime reflection is needed.
Committed outputs keep ordinary web and F-Droid builds independent of generation.

Before adoption, compare old/new browser behavior for every reaction (including
unknown values), missing/null fields, invalid inbox dates and private/hidden
rows. Existing cloud DTO/schema guards and web feed/inbox/board suites must still
pass. Demonstrate that changing a source enum changes its generated browser
values, without changing request parameters or domain calculations.

Descriptors do not capture arbitrary normalization, all default values or
conditional field omission. Keep `EncodeDefault.NEVER`, schema-version gates,
timestamp parsing, numeric handling and nested JSON conversion handwritten.
Keep lifetime and rolling rankings separate. Never generate cache, optimistic
update, RLS, visibility or ranking policies.

The tradeoff is coupling web metadata updates to the existing JVM exporter,
instead of introducing a new manifest and generator to maintain. A standalone
schema generator is justified only if another independent consumer needs it.
C10 Room schema snapshots remain independent historical migration evidence;
they are not inputs to this proposal and must not be deduplicated.

## Phases

| Release | Theme | Scope |
|---|---|---|
| 1.4 | Talk | Comments, themed reactions, inbox, per-workout audience, block / mute / report, remove ally, server rate limits |
| 1.4 | Fair fight | Ally boards per lift — calisthenics skill ladders first, bodyweight-relative tiers behind them (score computed on device, only the step leaves it); routine sharing as a pasteable text code; the ally workout view; inbox notifications |
| post-1.4 | Circles (first built as Warbands) | Invite-code groups of 2-8, pooled Veil banner line, weekly group challenge, trained-this-week marks. Skill witnessing deferred: claims are device-local and need their own design |
| Later | Community | Public audience and discovery, public Warbands, auto-hide after repeated reports, push (UnifiedPush, not FCM), media if asked for |

Triggers for "Later": friends of friends asking to join, or people asking for
public boards.

## 1.4 contract

### Backend: `supabase/migrations/0001_baseline.sql` (schema_version 19)

The 1.4 objects below live in the single baseline file, which is idempotent. Every existing view column keeps its name and
meaning, because 1.3 clients still read them (`like_count`, `liked_by_me`, ...),
and 1.3 still inserts likes without a kind.

- `sessions.audience text not null default 'profile'`, check in
  (`profile`, `friends`, `private`). `profile` = follow the profile visibility
  (today's behaviour); `friends` = friends only even on a public profile;
  `private` = owner only. The effective audience is the stricter of profile and
  session.
- `blocks(blocker_id, blocked_id, blocked_name, created_at)`, PK pair, owner-only
  RLS. Blocking deletes the friendship in either direction. `blocked_name` is
  filled by trigger, because a blocked profile becomes unreadable.
- `mutes(muter_id, muted_id, muted_name, created_at)`, same shape. Muting hides
  the muted lifter's workouts from the muter's feed and their comments and
  reactions from the muter's inbox and threads. The muted lifter is not told.
- `blocked_between(a, b)`: SECURITY DEFINER, revoked from every client role (it
  would otherwise be an oracle for who blocked whom).
- `can_view(owner)` gains "not blocked in either direction".
- `can_view_session(owner, audience)`: SECURITY DEFINER, granted to authenticated.
  Replaces `can_view(user_id)` in the read policies of `sessions`,
  `session_sets` and `session_likes`, and in the likes insert policy.
- `session_likes.kind text not null default 'salute'`, check in
  (`salute`, `iron`, `flame`). Still one reaction per lifter per workout (the PK);
  a new update policy lets the owner of the row change `kind` only (identity
  columns guarded by trigger).
- `session_comments(id, session_id, user_id, author_name, body, created_at)`.
  `body` 1-280 chars after trim. `author_name` and `created_at` are set by
  trigger, never trusted from the client; a rename propagates. Read: the workout
  is visible to the reader and the author is not blocked either way or muted by
  the reader. Insert: own row, on a visible workout. Delete: the author, or the
  owner of the workout. No update.
- `friendships.accepted_at timestamptz`, set by trigger when `accepted` flips.
- `inbox_seen(user_id, seen_at)`, owner read; `mark_inbox_seen()` RPC writes
  server time.
- `my_inbox()` RPC, SECURITY DEFINER, granted to authenticated, returns only the
  caller's items from the last 30 days, newest first, at most 100:
  `kind` (`request` | `accepted` | `comment` | `reaction`), `occurred_at`,
  `actor_id`, `actor_name`, `session_id`, `session_headline`, `comment_id`,
  `body`, `reaction`. Excludes actors blocked either way or muted by the caller.
- `reports(id, reporter_id, target_user_id, session_id, comment_id, reason, note,
  excerpt, status, created_at)`. Insert own only, never about yourself; no client
  read, update or delete. `reason` in (`spam`, `abuse`, `cheating`, `other`),
  `note` at most 280. `excerpt` is copied by trigger from the comment body or the
  workout title and note, so the evidence survives a delete.
- Rate limits, by trigger, SQLSTATE P0001 with a user-ready message:
  10 comments per minute and 200 per day per lifter, 200 comments per workout,
  20 friend requests per day, 20 reports per day. Friend requests between
  blocked lifters are refused.
- `public_feed` gains `comment_count`, `reactions` (jsonb kind to count),
  `my_reaction`, and drops workouts by lifters the reader muted.
- Cascades: every new table is erased with the account.

### Client

| Owner | Adds |
|---|---|
| `data/cloud` | `Reaction`, `SessionAudience`, `ReportReason`, `Comment`, `InboxItem`, `Inbox`, `BlockedLifter`; `CloudSync` comment, reaction, inbox, block, mute, unfriend and report calls; `FeedEntry.commentCount / reactions / myReaction`; `NEEDED_SCHEMA_VERSION = 18`; wire-name guard coverage |
| Room | `sessions.audience` (schema 30, `MIGRATION_29_30`), pushed with the workout and part of its push fingerprint, carried in the export archive |
| UI | Reaction picker and comment count on feed cards, a comments screen, an INBOX tab with an unread count and a dot on the Allies nav tab, lifter actions (remove ally, mute, block, report), a BLOCKED list under Allies, the audience picker on a workout's record |

Out of scope for 1.4: replies and threads, friends' PRs or skill claims in the
inbox (the server does not hold them), comments on the local workout record,
push notifications, media.

## 1.5 contract

Owner decisions (2026-09-30): build ally strength boards, routine share codes,
an ally workout view and inbox notifications. Boards rank by
**tier, not ratio**: allies already see set loads on the feed, so an exact
bodyweight ratio would let them solve for bodyweight, which never leaves the
phone. (Warbands followed later the same day — see the roadmap above.)

- **Ally strength boards.** `domain/LiftBoards.kt` ranks 17 boards, calisthenics
  first and barbell last, grouped Pull, Push, Static, Legs, Barbell. Two kinds:
  - TIERED (weighted pull-up, weighted dip, squat, bench press, deadlift,
    overhead press): Epley e1RM over bodyweight at the time, with bodyweight +
    added load for pull-ups and dips. Five sex-aware tiers (Iron, Bronze, Silver,
    Gold, Mythic), each split into I and II, plus Initiate below Iron: steps 0-10.
  - LADDER (schema 21): an ordered list of skill rungs, easiest first, each a
    catalogue exercise plus the reps or hold seconds from its `Skills.kt`
    standard. Step = the highest rung with a done, non-assisted set meeting the
    standard (a higher rung implies the lower ones); no bodyweight needed.
    Skill-practice records count like sets, claims do not. Boards (wire):
    `one_arm_pull`, `muscle_up`, `push_up`, `hspu`, `front_lever`, `back_lever`,
    `planche`, `handstand`, `l_sit`, `human_flag`, `pistol`. Handstand Walk is
    not a rung: a workout set cannot carry its metres.
  Only the step leaves the phone. The board shows the tier name, or the rung's
  exercise with "rung 4 of 6".
  Server: `lift_marks(user_id, lift, step, recent_step, recent_at, updated_at)`,
  readable by the owner and accepted allies (`is_ally`), view `lift_board` with
  `recent_step` only while `recent_at` is within 7 days. The `lift_marks_lift`
  check is dropped and re-added by name in the baseline, so pasting it over a
  schema-20 database widens it without a reset. LIFTS board beside TRAINING and
  GARRISON, WEEK / ALL TIME. Schema version 21.
- **Routine share codes.** `IVR1:` + base64url of deflated JSON. Share from
  Train; import from New workout › Import code, adding workouts unscheduled or
  replacing the routine after a confirm. Per-exercise generator reasons are not
  stored in Room, so codes carry workout notes but not reasons.
- **Ally workout view.** The comments screen becomes the workout view: sets in
  exercise order (`session_sets.exercise_position`), reactions, comments. The
  whole feed card opens it.
- **Inbox notifications.** A 30-minute WorkManager job reads `my_inbox()`; delivery
  is best-effort and Android may defer it. Sign-in, foreground resume and a
  notification-permission grant also check immediately.
  - The first successful read for an account/backend silently establishes its
    history baseline, even before permission is granted.
  - Later reads post one private Android notification for events newer than
    both the delivery mark and the server seen-mark. Advance the delivery mark
    and reaction memory only after posting succeeds.
  - Serialize checks and reject responses from an account/backend that changed
    during the read. A failed startup restore can retry, but automatic recovery
    cannot undo an explicit sign-out.
  - Temporary network, HTTP 429 and server failures receive at most two retries
    with exponential backoff starting at one minute; permanent failures wait for
    a later check.
  - No Google push service. Toggle under Account › Notifications; disabling it
    cancels background polling and queued retries. Foreground reads can still
    establish the silent baseline and refresh the unread count.

## 1.11 rolling workout XP

- The Training board defaults to **Workout XP / Trial XP**, summed over the
  elapsed last 168 hours using workout completion time, not upload time.
- Only completed, non-future workouts visible to the reader contribute.
  Skill claims, circle bonuses and imported workouts are excluded. Private
  workouts count for their owner, not for other readers.
- Aggregate the recent population before taking its top 100. Rank by recent
  workout XP, then name and id; lifetime XP does not break recent-score ties.
- Keep **Lifetime XP** and the other existing lifetime-population metrics.
  A missing recent view is an explicit error, never a lifetime fallback.
- Schema 33 adds the caller-RLS-filtered `workout_xp_leaderboard` view. Apply
  `supabase/hosted/2026-11-12-workout-xp.sql` before releasing this feature.
  The app-wide minimum remains 31; this optional view does not block sync.



## Moderation

`supabase/moderation.sql` holds the owner's queries: open reports with names,
delete a comment, close a report, and delete an abusive account. Run them in the
Supabase SQL editor. Nothing in the app can read reports.
