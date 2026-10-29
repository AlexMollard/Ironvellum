# Play Data Safety form — answer sheet for Ironvellum

Filled-in answers for Play Console → Policy → App content → Data safety, with
the code that justifies each answer. Written 2026-09-15 against commit HEAD.

Code references:
- `AM` = `app/src/main/AndroidManifest.xml`
- `CS` = `app/src/main/kotlin/com/ironvellum/app/data/cloud/CloudSync.kt`
- `AR` = `app/src/main/kotlin/com/ironvellum/app/data/cloud/AccountRepository.kt`
- `HS` = `app/src/main/kotlin/com/ironvellum/app/data/HealthSync.kt`
- `RP` = `app/src/main/kotlin/com/ironvellum/app/data/Repository.kt`
- `M1` = `supabase/migrations/0001_baseline.sql` (the whole schema)

## Form-level answers

- **Does your app collect or share any of the required user data types?** Yes
  (for signed-in users only; the data types below).
- **Is all of the user data collected by your app encrypted in transit?** Yes —
  all cloud traffic is HTTPS/TLS via Supabase PostgREST over Ktor/OkHttp
  (`CS`; Supabase project URL is HTTPS). Health Connect reads are on-device
  IPC, no network involved.
- **Do you provide a way for users to request that their data is deleted?**
  **Yes — in-app deletion.** Allies → ALLIES → DELETE MY CLOUD ACCOUNT calls
  the `delete_my_account()` RPC (`M1`), which deletes the caller's
  auth identity; the profile, every social table and the cloud backup archive
  cascade from it (`AccountRepository.deleteCloudData()`).

## Per data type

### Profile info — display name (collected, shared)
- Collected: **Yes** (user types it at sign-in). Shared: **Yes** (feed,
  leaderboard, friendships). Optional: **Yes** (only if you sign in).
- Purpose: App functionality — account management and social features.
- Code: `CS` pushes `displayName` to `profiles`; `M1` defines the column and
  RLS (`can_view`).

### Fitness info — workouts (collected, shared)
- Collected: **Yes** — completed sessions with label, public title, public
  note (≤500 chars), timestamps, XP, strength score; set rows (exercise name,
  position, index, reps, weight kg, modifiers, duration, distance and grade).
  Set kind, effort (RPE) and superset group, plus completed imported workouts
  and their sets, are also synced on schema 30 or newer. These additional
  records are owner-only; imported workouts never enter feeds.
- Shared: **Yes** for ordinary completed sessions and their standard sets,
  subject to the profile and workout visibility settings enforced by RLS.
  Optional: **Yes** (sign-in only; signing out stops uploads).
- Purpose: App functionality — syncing training history and supporting social
  features.
- Code: `CS` `push()`; `M1` `sessions`, `session_sets`,
  `session_set_details`, `imported_sessions` and `imported_session_sets`.

### Fitness info — web stats snapshot (collected, not shared)
- On schema 31 or newer, signed-in sync uploads one owner-only `web_snapshots`
  row containing phone-computed lift and workout figures, weekly sets, fatigue,
  streak, level and deed names, the selected male or female muscle-map figure,
  and appearance settings. Bodyweight-lift strength estimates can reveal
  information about body weight, though raw body readings are not uploaded.
  The row is replaced when its contents change and deleted with the account.
- Purpose: App functionality — the read-only web viewer. Code: `CS` `push()`;
  `M1` `web_snapshots` and its owner-only policies.

### Fitness info — Health Connect data (NOT collected)
- The app **reads** steps, distance, active calories, sleep, resting heart
  rate, heart rate, weight, body fat from Health Connect (`AM`, 8 `READ_*`
  data permissions plus `READ_HEALTH_DATA_HISTORY`, which widens step reads to
  data recorded before the app was installed, and
  `READ_HEALTH_DATA_IN_BACKGROUND`, which lets the daily worker read; `HS`).
  Heart-rate samples (1.8) are only reduced to a resting heart rate for days
  with none (`RestingHr.kt`); the samples are not stored. Reads run from
  Settings, on return to the app (`HealthResumeSync.kt`, at most every five
  minutes) and from the daily worker. These reads stay **on the device**
  (stored in the local `health_days` Room table, `RP.syncHealthHistory`). They are **never uploaded** — the cloud
  schema has no table for them, and `push()` sends none of them.
- Data Safety: declare as **not collected, not shared**. But you MUST complete
  the **Health apps declaration** and the Health Connect permissions
  declaration in Play Console because the manifest declares the permissions
  (see `docs/RELEASE_CHECKLIST.md`).
- The app also **writes** one record type, an `ExerciseSessionRecord`
  (`WRITE_EXERCISE` in `AM`; `HealthWriteBack.kt`, `HS`): start, end, title and
  public note of a finished workout, strength training type, no segments, no
  sets. It is **opt-in** (Settings > Health Connect, off by default, requested
  at the switch), skips imported workouts, upserts by client record id so
  edits replace and deletes remove it, and goes straight from the device into
  Health Connect with no server of ours in the path. Same classification:
  not collected, not shared. It adds one write permission to the Health apps
  declaration and the Health Connect permissions declaration.
- Weight/body-fat read from Health Connect feeds local stat tracking. Raw
  readings and user-entered measurements stay on-device, but owner-only web
  snapshot strength estimates can be derived from body weight.

### Photos — progress photos (NOT collected)
- 1.9 adds optional progress photos: one photo a day with a workout, and weekly
  front, side and back poses. They are taken with the system camera app
  (`ActivityResultContracts.TakePicture` into one empty cache file through the
  app's `FileProvider`) or chosen with the system photo picker
  (`PickVisualMedia`). The app declares **no camera permission and no storage
  permission**. The picture is scaled to 2048 px, re-saved as JPEG (location and
  other metadata dropped) and kept in app-private storage (`files/photos`,
  `PhotoStore`), with its date and pose in the local `progress_photos` table.
- They **never leave the device except into the folder the lifter picks for
  their own backup** (a `ironvellum_photos` sub-folder written through the
  Storage Access Framework). No server of ours receives them: the cloud archive
  carries no photo rows, the web snapshot and the Supabase schema have no photo
  field, and `NoCloudPhotosTest` fails the build if cloud code names one.
  Android's cloud backup and device transfer exclude `files/photos`
  (`backup_rules.xml`, `data_extraction_rules.xml`, `BackupRulesTest`).
- Data Safety: declare as **not collected, not shared**. No change to the
  "photos and videos" data type answer: nothing is collected or sent off the
  device by the app.

### Fitness info — idle-game aggregates (collected, shared)
- Garrison essence / figure count / figure rate are pushed as profile columns.
  Optional (sign-in only), shared per visibility.
- Also pushed as profile columns by the same call: **level, total XP, earned
  title count, lifetime strength and the training streak in days** — derived
  numbers rather than new measurements, but they do leave the device and they
  are what the leaderboard ranks on, so they are declared here rather than
  treated as internal. Code: `CS` `pushDerivedAggregates()` →
  `push_aggregates(p_level, p_total_xp, p_titles_count, p_strength,
  p_streak_days)`; `M11` grants that RPC and revokes direct column writes.
- Code: `CS` `ShadowPushDto`; `M1` `profiles` shadow columns; `M1` comment on them: "the cloud
  never holds the accrual clock".

### Identifiers — Google account user ID (collected, not shared beyond backend)
- Sign-in is Google via Credential Manager, or email/password via Supabase
  Auth (`AR`); either way Supabase auth issues an internal UUID used as the
  row key in every table. Purpose: account
  management. Optional (sign-in only). Not shared with third parties.

### Personal info — email address (collected, not shared)
- Collected: **Yes** — email/password sign-up sends the email (and password,
  over TLS, never stored by the app) to Supabase Auth
  (`AccountRepository.signUp`/`signIn`, `Email` provider); Google sign-in
  populates the auth user's email too (`AccountRepository.signInWithGoogle`).
  The app never reads the email back beyond restoring its own session, and no
  PostgREST table the app touches contains it.
- Shared: **No** (held by Supabase Auth only). Optional: **Yes** (sign-in
  only). Purpose: App functionality — account management.
- Deletion: in-app cloud account deletion removes the auth identity and its
  dependent rows through `delete_my_account()`.

### App interactions — likes, friend requests (collected, shared)
- `session_likes`, `friendships` (`M1`). Optional; purpose: social
  features.

### Inferred / advertising data
- **None.** No analytics, ads, or ad ID usage anywhere in
  `gradle/libs.versions.toml` or `app/build.gradle.kts`.

## Deletion

- **Local data:** uninstall (or Android "clear storage") removes `ironvellum.db`
  and everything else in the app's private storage.
- **Cloud data:** in-app deletion exists — Allies → ALLIES → DELETE MY CLOUD
  ACCOUNT calls `AccountRepository.deleteCloudData()`, which runs the
  signed-in-only `delete_my_account()` RPC (`M1`). It deletes the caller's
  auth identity, and the profile, every synced table and `cloud_archives`
  cascade from it. Asserted in `supabase/test/assert_all.sql`.

## OPEN QUESTIONS (owner must decide; do not submit the form until resolved)

- **Q1 — Deletion mechanism: RESOLVED, and now asserted.** In-app cloud deletion exists
  (`AccountRepository.deleteCloudData()`, surfaced in Allies → ALLIES). The
  server-side capability is in `M1` via the
  `profiles_delete` policy plus `on delete cascade`; only the UI was missing.
  The promise is executed rather than assumed: `supabase/test/assert_all.sql`
  deletes a lifter's profile as that lifter and asserts no rows survive in
  `sessions`, `session_sets`, `earned_titles`, `friendships`, `session_likes` or
  `level_ups`. Proven both ways against a real Postgres — a broken cascade fails
  on the foreign key, and a MISSING one (the silent case, which orphans rows
  while the app reports success) fails with
  `erasing a profile left rows behind in another table`.
  Remaining owner decision: whether to also offer removal of the auth identity,
  which requires service-role credentials and therefore a server-side action.
- **Q2 — Privacy policy URL:** must be a public, non-geofenced, non-editable
  URL identical in Play Console, in-app, and on the web. Owner must host
  `PRIVACY.md` somewhere permanent. Open.
- **Q3 — Google OAuth consent screen:** currently in Testing mode; only test
  users can sign in. Publishing it (and whether it needs verification for the
  requested scopes) is an owner decision. Open.
- **Q4 — Supabase project contact/retention policy:** how long the project
  owner retains cloud data for inactive accounts is not encoded anywhere; the
  privacy policy currently says "while the project exists". Confirm or amend.
- **Q5 — Health Connect permissions declaration:** the Google Health Connect
  API Request form must have been submitted for these eight data types (the
  history and background scopes need no separate data type), including heart
  rate (added in 1.8), plus the 1.8 write type `WRITE_EXERCISE` (exercise
  sessions), before shipping. The form wants a justification for each
  permission requested, so say why history and background reads are needed;
  whether it has been is unknown from the repo. Open.

## Policy sources (checked 2026-09-15)

- Data safety section: https://support.google.com/googleplay/android-developer/answer/10787469
- Health apps declaration: https://support.google.com/googleplay/android-developer/answer/14738291
- Health Content and Services policy: https://support.google.com/googleplay/android-developer/answer/16679511
- Health Connect access declaration: https://developer.android.com/health-and-fitness/health-connect/declare-access
