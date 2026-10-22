# Web viewer: design

Agreed with the owner on 2026-10-23. Mockups: the "Web viewer" row on the Ironvellum Clean screens Design canvas
(https://claude.ai/artifact/7CdzhVfKioJ2XGxWz9GXXz): WebSignIn, WebOverview, WebWorkouts, WebWorkoutDetail, WebLift, WebTrends.
A read-only website where anyone with an Ironvellum account signs in
and sees their own training from a PC: every workout, progress and trends. Mockups go on the Ironvellum Clean
screens Design canvas before any screen is built (owner rule).

## Why

The owner wants to look at their training on a big screen: browse workouts, read progress and trends,
without the phone in hand. It is for every user with an account, not a personal tool.

## Goals

- Sign in with the same account as the app and see only your own data.
- **Workouts**: a list and a calendar; each workout with its exercises in order, every set with its kind
  (warm-up, top, back-off, drop, working), load, reps, RPE or reps left, and the variation name.
- **Progress**: per lift and per variation, the estimated-1RM chart, PR history and best sets, with the
  same numbers the phone shows.
- **Trends**: weekly volume and hard sets per muscle, training frequency, streak, records over time.
- **Rewards**: level, XP and deeds.

## Non-goals

- Logging, editing or deleting anything on the web. Read-only.
- The plan builder, routines, the live workout, Friends, settings.
- Creating an account or recovering a password on the web: the app does both.
- Body weight, body measurements and Health Connect data. They stay on the phone, as the privacy policy
  promises (owner's default, 2026-10-23; an opt-in upload can be a later step).
- Translation. English and plain gym words to start; it follows the app's 1.9 translation work later.

## Anti-goals (failure even if it works)

- A number on the web that differs from the phone's for the same workout.
- Anything a friend can see that they cannot see in the app today. In particular, nothing from which body
  weight can be worked out may become readable by anyone but the owner.
- Imported history flooding friends' feeds.
- An automatically triggered GitHub workflow added without the owner's say (AGENTS.md).

## The decided approach: the phone computes, the web displays

Recon (2026-10-23) found the cloud copy is not enough to rebuild the phone's numbers: `session_sets` has no
warm-up flag, set kind or RPE; CSV-imported workouts are never pushed; body weight never leaves the phone,
and bodyweight PRs need it; exercise flags (`isWeighted`, metric, muscles) exist only in the manual backup.
Porting the Kotlin rules (`ProgramRules.epley`, `LiftRecords`, `BodyShare`, `PeakSets`, `HardSets`) to
JavaScript would also drift. So:

1. **Workouts** come from `sessions` and `session_sets`, which already sync after every workout and backfill
   history. Set kind (with warm-up as a kind), RPE and superset group go in an owner-only side table keyed to
   `session_sets` (the plan's `session_set_details`): new columns on `session_sets` would be readable by allies
   through its read policy. Older clients send none; the viewer shows those sets as plain working sets.
2. **Imported workouts** are pushed too, with audience `private` (owner-only under the existing
   `can_view_session`) and an `imported` flag, so they never appear in friends' feeds or count toward
   anything social they are excluded from today.
3. **Stats** come from one new owner-only table, a per-user snapshot of what the phone has already computed
   (per-lift and per-variation e1RM series and PRs, best sets, weekly hard sets and volume per muscle,
   frequency, streak, the exercise metadata the viewer needs to label things). The phone writes it on the
   same schedule as the workout push. Read and write are owner-only: the e1RM of a bodyweight lift lets body
   weight be worked out, so no friend or public policy may ever read it. Level, XP and deeds come from what
   already syncs (`profiles`, `level_ups`, `earned_titles`) or from the snapshot, builder's choice.
4. Schema changes ship as a migration with the next `NEEDED_SCHEMA_VERSION`, so self-hosters are told to
   apply it the way they are for every other change.

## The website

- A static site: no server of our own; it talks to Supabase directly with the publishable key, as the app
  does. Every query filters on `user_id = auth.uid()` explicitly, because the read policies also admit
  friends' and public sessions.
- Sign-in: email and password; Google on the shared backend. A "use my own backend" field takes a URL and
  key for self-hosters, remembered in the browser.
- Plain gym words, the app's look (dark and light, accent), readable at desktop widths and usable on a phone
  browser.
- Hosting: GitHub Pages from this public repository. Publishing a Pages site from a branch runs GitHub's own
  deploy job automatically; it is free on a public repository, but it is the owner's call under AGENTS.md
  (see Open questions).

## Constraints

- Phone side: strings in resources, the gate green, unit tests for the snapshot builder and the new columns,
  CloudSync tests for imported-workout push.
- Database: `supabase/test` assertions cover the new owner-only policy (an ally and a stranger read nothing).
- Web side: no data leaves for any host other than the user's Supabase backend; no analytics.
- Privacy policy (`docs/PRIVACY.md`) updated for the new uploads (set kind, RPE, imported workouts, the stats
  snapshot) before release.

## Owner's answers (2026-10-23)

- **The new uploads are automatic** for signed-in users (set kind, RPE, imported workouts, the stats
  snapshot), all owner-only, with `docs/PRIVACY.md` updated in the same release. No opt-in switch.
- **Hosting: GitHub Pages** on this public repository, free. The owner allows GitHub's automatic Pages deploy
  for it; no other workflow trigger changes. Build with email and password first; Google sign-in on the web
  follows once the owner has created the web OAuth client and added the site's address to Supabase's redirect
  list (both free).
- **Everything stays at no cost.** No paid service, no paid Supabase tier: keep the snapshot small (one row per
  user, capped like `cloud_archives`) so the shared free project's storage and egress are not strained.

## Calls made after the builder review (designer's calls, not the owner's)

1. **Imported workouts go to their own owner-only tables**, not `sessions`. A mirror of `sessions` and
   `session_sets` with owner-only read and write, which no feed, circle, leaderboard, lift-mark, XP or crest
   view or function reads. Filtering an `imported` flag in every aggregate would leave one forgotten function
   able to leak years of backdated history into other people's numbers; separate tables cannot. The viewer
   merges both into one workout list. This replaces "audience private plus an imported flag" above.
2. **The web computes nothing.** Every derived figure the viewer shows, per workout too (PR marks per set,
   session volume and hard-set totals, e1RM), comes from the phone. Per-session derived figures live in the
   owner-only snapshot keyed by session; the viewer only formats and sums nothing.
3. **Bodyweight sets show what friends already see.** `session_sets` keeps the load as logged (added load for
   bodyweight lifts), as today. Body-weight-revealing figures (bodyweight e1RM) come only from the owner-only
   snapshot. Nothing new becomes readable by an ally or a stranger.
4. **History is re-pushed once.** After the update the phone re-pushes every workout with the new columns
   filled (the push watermark's fingerprint gains the new fields, so the existing backfill does it). The
   builder must check that re-pushed rows do not resurface in feeds or trigger notifications (feed ordering
   and any trigger on update), and prevent it if they do.
5. **Deleting covers the new data.** Account deletion removes the snapshot and the imported tables
   (`on delete cascade` from the user, like every other table), and signing out of the cloud stops their
   upload like every other push.
6. **Snapshot freshness.** One row per user, overwritten, no history. It is written in the same push, after the
   workouts. The viewer shows "Stats as of <time>" from its timestamp, so a lag is visible, never hidden.
7. **Schema.** One migration, following however existing changes ship in `supabase/migrations` and
   `supabase/hosted`, with the next `NEEDED_SCHEMA_VERSION`. The viewer reads the schema beacon and, on a
   backend that is behind, says which version it needs instead of failing.
8. **Order of work.** Email and password sign-in and a local build first; Google sign-in and the public
   address once the owner has done the console setup and chosen hosting. "Use my own backend" accepts any
   address, as the app does; it is the user's own server.

## Left to the builder

- Web framework and chart library (keep it small; Node is installed), folder (`web/`), and how it is built
  and tested locally.
- The snapshot's exact shape and versioning, and how large it may grow.
- Column names and the set-kind wire values (reuse the export format's `kind` wire values).
- Which existing synced tables cover level, XP and deeds.
