# Ironvellum Privacy Policy

Ironvellum (package `com.ironvellum.app`) is a training tracker. This policy describes
what the app stores, what leaves your device, and what never does. It is written
from the app's actual behaviour; nothing here is aspirational.

Cloud workout upload details checked against the codebase on 2026-10-28.

## The short version

- **Everything lives on your device first.** All training data is stored in a
  local Room database (`ironvellum.db`, see
  `app/src/main/kotlin/com/ironvellum/app/data/IronvellumDatabase.kt`).
- **Cloud sync is optional.** Without signing in, no data ever leaves the
  device. The cloud features are disabled entirely unless Supabase credentials
  are configured (`Cloud.configured`, `data/cloud/Cloud.kt`).
- **Raw body measurements stay on the device.** The owner-only web snapshot
  can contain strength estimates derived from body weight and the body figure
  selected for muscle maps.
- **The database is excluded from Android's backups.** Android would otherwise
  copy app data to your Google account and to a new phone during device
  transfer. `ironvellum.db`, its write-ahead sidecars, the local snapshot copies
  of it, and the crash journal are all excluded in every domain
  (`app/src/main/res/xml/backup_rules.xml`,
  `app/src/main/res/xml/data_extraction_rules.xml`). Raw measurements remain
  on the device that recorded them.

## What the app stores on your device

The following are stored in `ironvellum.db` (Room) in the app's private
storage; the sync section below describes what can also leave the device:

- Workout sessions and individual sets (exercise, reps, weight, modifiers,
  duration, distance, grade) — `data/db/Entities.kt`.
- Body measurements (weight, height, body fat, per-site readings). These are
  never uploaded; the cloud schema has no table for them on purpose.
- Skill practice logs, earned titles, presets, idle-game state (`idle_state`).
- Per-day Health Connect summaries (`health_days` table) written by the daily
  sync worker (`HealthSyncWorker.kt`).
- Private notes on workouts. The server schema has no private-notes column:
  "private" means the server cannot read it, not that the UI hides it
  (`supabase/migrations/0001_baseline.sql`, the comment above the `sessions` table).

The app also offers manual export/import of this data and portable settings as a JSON archive you
control (`ExportWriter.kt` / `ExportReader.kt`). Archives omit account credentials, device tokens,
and backup-folder permissions.

## What the app reads from Health Connect

The app requests eight read-only Health Connect permissions
(`AndroidManifest.xml`): steps, distance, active calories burned, sleep,
resting heart rate, heart rate, weight, and body fat. Heart-rate samples are
only used to work out a resting heart rate for days that have none; the
samples themselves are not stored. Reads happen:

- on demand from the Settings screen ("Connect & Sync"),
- when you return to the app, at most once every five minutes, fetching only
  what changed since the last read, and
- once per day via a WorkManager job (`HealthSyncWorker.kt`, 14-day window).

The results are aggregated per day and stored locally in `health_days`. Raw
Health Connect data is not uploaded; bodyweight-derived estimates can be
included in the owner-only web snapshot.

## What the app writes to Health Connect

One thing, and only if you turn it on: Settings > Health Connect > "Save
finished workouts to Health Connect" (off by default). Turning it on asks
Health Connect for one write permission, `WRITE_EXERCISE`
(`AndroidManifest.xml`). While it is on, each workout you finish is written
once as an exercise session of the strength training type: its start and end
time, its name and its public note if you wrote one. Sets, reps, loads,
private notes, XP and every other figure are not written. Workouts imported
from other apps are never written.

The entry belongs to this app and carries a stable id, so changing a finished
workout replaces its entry instead of adding another, and deleting or
reopening a workout removes it. The write happens on the device, straight into
Health Connect; nothing about it goes to the cloud. Turning the switch off
stops new writes and leaves the entries already in Health Connect; you can
remove them, or the permission, in Health Connect itself.

## What leaves the device (signed-in users only)

Sign-in is either Google Sign-In via Android Credential Manager
(`data/cloud/AccountRepository.kt`, androidx.credentials + googleid) or email
plus password (`AccountRepository.signUp`/`signIn`, supabase-kt `Email`
provider). Either way your email address — and, for email sign-in, your
password — is sent to Supabase Auth over HTTPS/TLS; the app itself never
stores the password. If you sign in, the app syncs to the project's Supabase
backend (PostgreSQL over HTTPS/TLS) exactly these things
(`data/cloud/CloudSync.kt`):

| Data | Cloud table(s) |
| Email address — your sign-in identity, held by Supabase Auth (from email/password sign-up, or your Google account on Google sign-in) | Supabase `auth.users`; the app never reads it back beyond restoring its own session (`AccountRepository.kt`) |
| Display name, profile visibility setting, level, total XP, streak days, title count, lifetime strength, worn title, worn crest | `profiles` |
| Idle-game aggregates (muster essence, figure count, figure rate) — the idle accrual clock itself stays on-device | `profiles` columns (migration `0008`) |
| Completed sessions: label, public title (≤80 chars), public note (≤500 chars), timestamps, XP, strength score | `sessions` |
| Set rows: exercise name, position, set index, reps, weight, modifiers, done, duration, distance and grade | `session_sets` |
| Set kind, effort (RPE) and superset group where recorded | `session_set_details`, readable only by you |
| Completed imported workouts and their sets, including set kind, effort and superset group | `imported_sessions`, `imported_session_sets`, readable only by you and never in feeds |
| Web stats snapshot: phone-computed workout and lift figures, including bodyweight-lift strength estimates, weekly sets, fatigue, streak, level and deed names; muscle-map figure (male or female) and appearance settings | `web_snapshots`, one owner-only row replaced when its contents change |
| Unlocked titles with timestamps | `earned_titles` |
| Likes you give/receive | `session_likes` |
| Friend requests / friendships | `friendships` |
| Cloud backup, only when you tap BACK UP NOW: a copy of your training record (presets, completed sessions and sets with public notes, titles, skill practice, idle-game state, display name, XP) and portable settings such as appearance, training equipment and notification preferences. It leaves out private notes, body readings, measurements, height, sex and Health Connect data; restoring it keeps whatever of those is already on the phone | `cloud_archives` (migration `0013`), readable only by you |

Access to all of it is governed by row-level security on the server; every
table is RLS-enabled (`0001_baseline.sql`). Your profile `visibility` setting
(`public` / `friends` / `private`) controls who else can see shared data; the
set details, imported workouts and web snapshot above remain owner-only. The
snapshot's bodyweight-lift estimates can reveal information about body weight;
they are not shared with allies or feeds. These uploads occur as part of sync
while signed in and stop when you sign out. Backends below schema version 30
do not receive set details or imported workouts; snapshots require schema 31.

**Using your own backend:** Settings → CLOUD lets you point the app at your
own Supabase project instead of the shared one. When a custom backend is
configured, everything listed above goes to YOUR project — nothing is sent to
the maintainer's shared instance — and Google sign-in is unavailable there
(email sign-in only). See `supabase/SELF_HOSTING.md`.

**Not uploaded:** raw body readings and measurements, height, private notes,
in-progress (abandoned) sessions, device identifiers, or raw Health Connect
data. The owner-only snapshot includes the selected body figure and estimates
derived from body weight, but not the underlying readings. Session local IDs
are uploaded to match synced workouts; other local-only row IDs are not. The
cloud backup leaves out device-only readings, sex and private notes.

## What the app does NOT contain

- **No analytics SDK. No ads SDK. No trackers.** Verified against the
  dependency list `gradle/libs.versions.toml` and `app/build.gradle.kts`: the
  only dependencies are AndroidX (Compose, Room, WorkManager, Credentials),
  Health Connect client, Supabase/Ktor (cloud sync you opt into), and JUnit.
- The app does not sell data, share it with advertisers, or use it for
  advertising in any way.

## Retention and deletion

- **Local data** persists until you uninstall the app or clear its storage from
  Android system settings. Uninstalling removes `ironvellum.db` and everything in
  the app's private storage.
- **Cloud data:** Allies → ALLIES → **DELETE MY CLOUD ACCOUNT** deletes your
  sign-in identity itself (`delete_my_account()` in the baseline schema), which
  cascades to your lifter row and from there to every synced workout, set,
  earned title, level-up, like and ally link, and to your cloud backup archive
  (`on delete cascade` on all of them), then signs you out. The function only
  ever deletes the caller: it acts on `auth.uid()` and is not callable without
  a sign-in. Signing in again afterwards creates a new, empty account. The
  whole cascade is asserted in `supabase/test/assert_all.sql`.
- Exported JSON archives are under your control; delete the file and it is
  gone.

## Contact

Open questions about this policy or data deletion: contact the project owner
via the repository's issue tracker.
