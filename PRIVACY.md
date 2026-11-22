# Ironvellum Privacy Policy

Ironvellum (package `com.ironvellum.app`) is a training tracker. This policy describes
what the app stores, what leaves your device, and what never does. It is written
from the app's actual behaviour; nothing here is aspirational.

Documentation reviewed against the codebase on 2026-11-17. AI connections section added on 2026-11-22.

## The short version

- **Everything lives on your device first.** All training data is stored in a
  local Room database (`ironvellum.db`, see
  `app/src/main/kotlin/com/ironvellum/app/data/IronvellumDatabase.kt`).
- **Cloud sync is optional.** Training sync starts after sign-in. You can separately choose to share a training file or export an archive. Cloud features are disabled entirely unless Supabase credentials
  are configured (`Cloud.configured`, `data/cloud/Cloud.kt`).
- **AI connections are optional.** A compatible AI provider can access the synced data you allow and suggest workouts for your review. You can disconnect it in Account.
- **Raw body measurements stay on the device.** The owner-only web snapshot
  can contain strength estimates derived from body weight and the body figure
  selected for muscle maps.
- **The database is excluded from Android's backups.** Android would otherwise
  copy app data to your Google account and to a new phone during device
  transfer. `ironvellum.db`, its write-ahead sidecars, local database snapshots,
  exercise-conversion safety archives, and the crash journal are excluded
  from both Android cloud backup and phone-to-phone transfer
  (`app/src/main/res/xml/backup_rules.xml`,
  `app/src/main/res/xml/data_extraction_rules.xml`). Raw measurements remain
  on the device that recorded them.

- **Progress photos stay on your device and in your own backup folder.** The
  optional workout photo and weekly pose photos are JPEG files in the app's
  private storage (`files/photos`, `data/PhotoStore.kt`), with their dates and
  poses in `ironvellum.db`. They are never uploaded: not to the cloud backup,
  not to the web snapshot, not to any sync path (`NoCloudPhotosTest` fails the
  build if cloud code names a photo). Android's backups leave the folder out
  too. The only other copy is the one in the backup folder you pick yourself
  (see below). Photos are scaled to 2048 pixels and re-saved, which drops the
  location and other metadata the camera wrote.

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
- Progress photos: one optional photo a day with a workout, and front, side and
  back poses by week. The pictures are files in `files/photos`; the table
  `progress_photos` holds only the date, pose and file name. Deleting a workout
  deletes its photo; each photo can also be removed on its own.
- Private notes on workouts. The server schema has no private-notes column:
  "private" means the server cannot read it, not that the UI hides it
  (`supabase/migrations/0001_baseline.sql`, the comment above the `sessions` table).

The app also offers manual export/import of this data and portable settings as a JSON archive you
control (`ExportWriter.kt` / `ExportReader.kt`). Archives omit account credentials, device tokens,
and backup-folder permissions. An archive lists your photos (date, pose, file name) but never
contains the pictures; the weekly backup to a folder you picked copies the picture files into
a `ironvellum_photos` sub-folder of it, and restoring brings them back from there. The cloud
backup copy of the archive carries no photo rows at all.

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

## What signed-in users sync

Sign-in is either Google Sign-In via Android Credential Manager
(`data/cloud/AccountRepository.kt`, androidx.credentials + googleid) or email
plus password (`AccountRepository.signUp`/`signIn`, supabase-kt `Email`
provider). Either way your email address — and, for email sign-in, your
password — is sent to Supabase Auth over HTTPS/TLS; the app itself never
stores the password. If you sign in, the app syncs to the project's Supabase
backend (PostgreSQL over HTTPS/TLS) exactly these things
(`data/cloud/CloudSync.kt`):

| Data | Cloud table(s) |
|---|---|
| Email address — your sign-in identity, held by Supabase Auth (from email/password sign-up, or your Google account on Google sign-in) | Supabase `auth.users`; the app never reads it back beyond restoring its own session (`AccountRepository.kt`) |
| Display name, profile visibility setting, level, total XP, streak days, title count, lifetime strength, worn title, worn crest | `profiles` |
| Veil aggregates (lifetime essence earned, echo count and current essence-per-hour rate); the balance and accrual clock are not part of this profile aggregate upload | `profiles` shadow columns, through `push_aggregates()` |
| Completed sessions: label, public title (≤80 chars), public note (≤500 chars), timestamps, XP, strength score | `sessions` |
| Set rows: exercise name, position, set index, reps, weight, modifiers, done, duration, distance and grade | `session_sets` |
| A card of each completed workout, on schema 32 or newer: hard sets, tonnes of loaded work, the lifts that set a record with the load and reps as logged, and muscle levels from 0 to 3. It never holds a strength estimate or any figure derived from body weight, and it is visible exactly as the workout is (your profile and workout visibility settings decide who sees it) | `sessions.feed_card` |
| Set kind, effort (RPE) and superset group where recorded | `session_set_details`, readable only by you |
| Completed imported workouts and their sets, including set kind, effort and superset group | `imported_sessions`, `imported_session_sets`, readable only by you and never in feeds |
| Web stats snapshot: phone-computed workout and lift figures, including bodyweight-lift strength estimates, weekly sets, fatigue, streak, level and deed names; muscle-map figure (male or female) and appearance settings | `web_snapshots`, one owner-only row replaced when its contents change |
| Unlocked titles with timestamps | `earned_titles` |
| Lift leaderboard marks: lifetime/recent tier steps and the recent mark's timestamp, computed on the phone without uploading raw bodyweight | `lift_marks` |
| Reactions you give/receive and comments you post, including comment text and author name; visible to the workout's permitted audience | `session_likes`, `session_comments` |
| Friend requests / friendships | `friendships` |
| Group membership, name, invitation code, weekly goal and participation/bonus history; exposed through the group's permission-checked functions | `circles`, `circle_members`, `circle_weeks`, `circle_week_members`, `circle_events` |
| Block/mute choices and the last-seen inbox timestamp; personal lists are not published to other users | `blocks`, `mutes`, `inbox_seen` |
| Reports you submit: target, reason, optional note and referenced workout/comment. The server retains a content excerpt for moderation; reports are readable by the backend operator, not app clients | `reports` |
| Cloud backup, only when you tap BACK UP NOW: a copy of your training record (presets, completed sessions and sets with public notes, titles, skill practice, idle-game state, display name, XP) and portable settings such as appearance, training equipment and notification preferences. It leaves out private notes, body readings, measurements, height, sex and Health Connect data; restoring it keeps whatever of those is already on the phone | `cloud_archives`, readable only by you |

Access to all of it is governed by row-level security on the server; every
table is RLS-enabled (`0001_baseline.sql`). Your profile `visibility` setting
(`public` / `friends` / `private`) controls who else can see shared data; the
set details, imported workouts and web snapshot above remain owner-only. The
snapshot's bodyweight-lift estimates can reveal information about body weight;
they are not shared with allies or feeds. These uploads occur as part of sync
while signed in and stop when you sign out. Backends below schema version 30
do not receive set details or imported workouts; snapshots require schema 31. The snapshot
is not removed when you sign out: the app has no switch for it yet. Your account can delete it
(the backend grants the owner, and only the owner, a delete on it), and deleting your account removes it.

**Using your own backend:** Settings → Advanced → Use my own backend lets you point the app at your
own Supabase project instead of the shared one. When a custom backend is
configured, everything listed above goes to YOUR project — nothing is sent to
the maintainer's shared instance — and Google sign-in is unavailable there
(email sign-in only). See [self-hosting](supabase/SELF_HOSTING.md).

**Not uploaded:** progress photos (the pictures and their list), raw body readings and measurements, height, private notes,
in-progress (abandoned) sessions, device identifiers, or raw Health Connect
data. The owner-only snapshot includes the selected body figure and estimates
derived from body weight, but not the underlying readings. Session local IDs
are uploaded to match synced workouts; other local-only row IDs are not. The
cloud backup leaves out device-only readings, sex and private notes.

## Training files you choose to share

Train → AI tools lets you choose to share a dated file containing all completed workouts and their recorded sets and preparation snapshots, all saved workouts, referenced preparation routines and movements, and progress calculated from the completed workouts. Saved-workout, movement (including custom movement) and preparation names are included and may contain personal information. Workout, exercise and preparation notes, session labels, unfinished sessions, profile and body or health measurements, photos, social activity, rewards, account identities and raw archives are excluded. The file contains random local identifiers that associate a proposed draft with this dataset and export.

The personal connector on your computer reads the selected file. Your AI client can send tool results to the model provider under that provider's policies. The connector keeps proposed files and a local retry journal in the chosen outbox. On the phone, opening a proposed file shows a review. Pressing Save adds one unscheduled saved workout and a local receipt. The receipt survives deletion of the workout so importing the same proposal again leaves it deleted. You control copies on the phone, computer and any service you use for file transfer. [Personal connector guide](https://github.com/AlexMollard/Ironvellum/tree/archive-personal-connector/tools/local_connector/README.md).

## Optional AI connections

If your cloud operator enables AI connections, you can add its MCP address to a compatible AI app. You sign in to Ironvellum and choose which requested permissions to grant. The AI receives a separate credential limited to those permissions. Your Ironvellum sign-in stays with Ironvellum.

Depending on your choices, the AI provider can receive synced completed workouts and sets, training progress, or saved workouts. These views can lag behind your phone. Private notes, photos, raw health and body measurements, archives and unfinished sessions are excluded. The provider handles information it receives under its own policies.

With draft permission, the AI can suggest a new workout and see the status of its own suggestion. Review it in Train. Saving adds a new workout on that device without scheduling it or logging a session. Ironvellum keeps a local receipt so a retry cannot add the same draft twice. The cloud stores the suggestion, source, dates and decision. Pending drafts expire after 30 days. The service keeps terminal records for at least 90 days and records limited access history for 30 days.

In Account, open AI connections to see permissions and recent access, copy your server address or disconnect an AI. Disconnecting stops future access. Information already sent to the provider stays subject to its retention settings.

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
- **Cloud data:** Settings → Account (also available from the Friends/Allies
  account-settings gear) → **Delete my cloud account** deletes your
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
