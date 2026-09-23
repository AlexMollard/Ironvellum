# Ironvellum Privacy Policy

Ironvellum (package `com.ironvellum.app`) is a training tracker. This policy describes
what the app stores, what leaves your device, and what never does. It is written
from the app's actual behaviour; nothing here is aspirational.

Checked against the codebase on 2026-09-15.

## The short version

- **Everything lives on your device first.** All training data is stored in a
  local Room database (`ironvellum.db`, see
  `app/src/main/kotlin/com/ironvellum/app/data/IronvellumDatabase.kt`).
- **Cloud sync is optional.** Without signing in, no data ever leaves the
  device. The cloud features are disabled entirely unless Supabase credentials
  are configured (`Cloud.configured`, `data/cloud/Cloud.kt`).
- **Body measurements never leave the device.** The cloud schema deliberately
  has no table for them (`supabase/migrations/0001_init.sql`, header comment;
  `Repository.kt` "measurements — device-only by design").
- **The database is excluded from Android's backups.** Android would otherwise
  copy app data to your Google account and to a new phone during device
  transfer. `ironvellum.db`, its write-ahead sidecars, the local snapshot copies
  of it, and the crash journal are all excluded in every domain
  (`app/src/main/res/xml/backup_rules.xml`,
  `app/src/main/res/xml/data_extraction_rules.xml`), so your training data and
  measurements stay on the device that recorded them.

## What the app stores on your device

All of the following live only in `ironvellum.db` (Room) in the app's private
storage:

- Workout sessions and individual sets (exercise, reps, weight, modifiers,
  duration, distance, grade) — `data/db/Entities.kt`.
- Body measurements (weight, height, body fat, per-site readings). These are
  never uploaded; the cloud schema has no table for them on purpose.
- Skill practice logs, earned titles, presets, idle-game state (`idle_state`).
- Per-day Health Connect summaries (`health_days` table) written by the daily
  sync worker (`HealthSyncWorker.kt`).
- Private notes on workouts. The server schema has no private-notes column:
  "private" means the server cannot read it, not that the UI hides it
  (`supabase/migrations/0002_feed_and_notes.sql`, header comment).

The app also offers manual export/import of this data as a JSON archive you
control (`ExportWriter.kt` / `ExportReader.kt`).

## What the app reads from Health Connect

The app requests seven read-only Health Connect permissions
(`AndroidManifest.xml`): steps, distance, active calories burned, sleep,
resting heart rate, weight, and body fat. Reads happen:

- on demand from the Settings screen ("Connect & Sync"), and
- once per day via a WorkManager job (`HealthSyncWorker.kt`, 14-day window).

The results are aggregated per day and stored locally in `health_days`. Nothing
read from Health Connect is written back to Health Connect, and none of it is
uploaded to the cloud.

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
| Display name, profile visibility setting, level, total XP, streak days, title count, lifetime strength, worn title | `profiles` |
| Idle-game aggregates (muster essence, figure count, figure rate) — the idle accrual clock itself stays on-device | `profiles` columns (migration `0008`) |
| Completed sessions: label, public title (≤80 chars), public note (≤500 chars), timestamps, XP, strength score | `sessions` |
| Set rows: exercise name, set index, reps, weight, modifiers, done | `session_sets` |
| Unlocked titles with timestamps | `earned_titles` |
| Likes you give/receive | `session_likes` |
| Friend requests / friendships | `friendships` |

Access to all of it is governed by row-level security on the server; every
table is RLS-enabled (`0001_init.sql`). Your profile `visibility` setting
(`public` / `friends` / `private`) controls who else can see your shared data.

**Using your own backend:** Settings → CLOUD lets you point the app at your
own Supabase project instead of the shared one. When a custom backend is
configured, everything listed above goes to YOUR project — nothing is sent to
the maintainer's shared instance — and Google sign-in is unavailable there
(email sign-in only). See `supabase/SELF_HOSTING.md`.

**Not uploaded:** body measurements, private notes, local-only row ids beyond
the sync watermark, in-progress (abandoned) sessions, device identifiers, or
your raw Health Connect records.

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
- **Cloud data:** Allies → ALLIES → **ERASE MY CLOUD DATA** deletes your lifter
  row, which cascades to every synced session, set, earned title, level-up,
  like and ally link (`on delete cascade` on all of them), and signs you out.
  The delete is performed by you, under the `profiles_delete` row-level
  security policy, which permits the owner and nobody else.
- Your sign-in identity itself is left intact so you can start again from
  scratch; removing the account record needs service-role credentials that are
  deliberately never shipped in the app. Ask the project owner if you want the
  identity removed as well.
- Exported JSON archives are under your control; delete the file and it is
  gone.

## Contact

Open questions about this policy or data deletion: contact the project owner
via the repository's issue tracker.
