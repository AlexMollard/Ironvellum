# Play Data Safety form — answer sheet for Monarch

Filled-in answers for Play Console → Policy → App content → Data safety, with
the code that justifies each answer. Written 2026-09-15 against commit HEAD.

Code references:
- `AM` = `app/src/main/AndroidManifest.xml`
- `CS` = `app/src/main/kotlin/com/monarch/app/data/cloud/CloudSync.kt`
- `AR` = `app/src/main/kotlin/com/monarch/app/data/cloud/AccountRepository.kt`
- `HS` = `app/src/main/kotlin/com/monarch/app/data/HealthSync.kt`
- `RP` = `app/src/main/kotlin/com/monarch/app/data/Repository.kt`
- `M1`/`M2`/`M8` = `supabase/migrations/0001_init.sql` / `0002…` / `0008…`

## Form-level answers

- **Does your app collect or share any of the required user data types?** Yes
  (for signed-in users only; the data types below).
- **Is all of the user data collected by your app encrypted in transit?** Yes —
  all cloud traffic is HTTPS/TLS via Supabase PostgREST over Ktor/OkHttp
  (`CS`; Supabase project URL is HTTPS). Health Connect reads are on-device
  IPC, no network involved.
- **Do you provide a way for users to request that their data is deleted?**
  **Yes — in-app deletion.** Guild → ALLIES → ERASE MY CLOUD DATA deletes the
  caller's `profiles` row under the owner-only `profiles_delete` RLS policy;
  every other table cascades from it (`AccountRepository.deleteCloudData()`).
  The auth identity is retained by design — note this on the form if asked.

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
  reps, weight kg, modifiers, duration, distance, grade). Shared: **Yes**,
  subject to the profile visibility setting (default `friends`) enforced by
  RLS. Optional: **Yes** (sign-in only).
- Purpose: App functionality — syncing training history across the social
  features.
- Code: `CS` `push()`; `M1` `sessions`/`session_sets`; `M2` adds `title`/`note`
  to `sessions`; `M3` adds duration/distance/grade.

### Fitness info — Health Connect data (NOT collected)
- The app **reads** steps, distance, active calories, sleep, resting heart
  rate, weight, body fat from Health Connect (`AM`, 7 `READ_*` permissions;
  `HS`). These reads stay **on the device** (stored in the local `health_days`
  Room table, `RP.syncHealthHistory`). They are **never uploaded** — the cloud
  schema has no table for them, and `push()` sends none of them.
- Data Safety: declare as **not collected, not shared**. But you MUST complete
  the **Health apps declaration** and the Health Connect permissions
  declaration in Play Console because the manifest declares the permissions
  (see `docs/RELEASE_CHECKLIST.md`).
- Weight/body-fat read from Health Connect feeds local stat tracking only;
  user-entered body measurements (`measurements` table, `RP` "device-only by
  design") also never leave the device (`M1` header comment).

### Fitness info — idle-game aggregates (collected, shared)
- Shadow essence / shadow count / shadow rate are pushed as profile columns.
  Optional (sign-in only), shared per visibility.
- Code: `CS` `ShadowPushDto`; `M8` `profiles` columns; `M8` header: "the cloud
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
- Deletion: tied to the auth identity, which in-app deletion deliberately
  retains (see Q1) — removal requires a server-side service-role action.

### App interactions — likes, friend requests (collected, shared)
- `session_likes`, `friendships` (`M1`, `M4`). Optional; purpose: social
  features.

### Inferred / advertising data
- **None.** No analytics, ads, or ad ID usage anywhere in
  `gradle/libs.versions.toml` or `app/build.gradle.kts`.

## Deletion

- **Local data:** uninstall (or Android "clear storage") removes `monarch.db`
  and everything else in the app's private storage.
- **Cloud data:** in-app deletion exists — Guild → ALLIES → ERASE MY CLOUD
  DATA calls `AccountRepository.deleteCloudData()`, deleting the caller's
  `profiles` row under the owner-only `profiles_delete` RLS policy; every
  other table cascades from it (`0001_init.sql`). The auth identity (email) is
  retained by design; removing it needs service-role credentials, so it is a
  server-side action by the project owner.

## OPEN QUESTIONS (owner must decide; do not submit the form until resolved)

- **Q1 — Deletion mechanism: RESOLVED.** In-app cloud deletion now exists
  (`AccountRepository.deleteCloudData()`, surfaced in Guild → ALLIES). The
  server-side capability was present from migration 0001 via the
  `profiles_delete` policy plus `on delete cascade`; only the UI was missing.
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
  API Request form must have been submitted for these seven data types before
  shipping; whether it has been is unknown from the repo. Open.

## Policy sources (checked 2026-09-15)

- Data safety section: https://support.google.com/googleplay/android-developer/answer/10787469
- Health apps declaration: https://support.google.com/googleplay/android-developer/answer/14738291
- Health Content and Services policy: https://support.google.com/googleplay/android-developer/answer/16679511
- Health Connect access declaration: https://developer.android.com/health-and-fitness/health-connect/declare-access
