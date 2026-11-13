# Ironvellum release checklist

Ordered steps to ship `com.ironvellum.app` to Google Play. Execute top to bottom.
Written 2026-09-15. Verification commands run from the repo root on Windows:
`.\gradlew.bat <task>`.

## 1. Signing key

1. Create a keystore (once, keep it safe — losing it means losing the app
   identity):
   ```
   keytool -genkeypair -v -keystore ironvellum-release.jks -alias ironvellum ^
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Add the signing keys to `local.properties` (gitignored; the release build
   reads env vars first, then these keys — absent keys yield an unsigned APK,
   never a configuration failure):
   ```
   ironvellum.keystore.path=D:/path/to/ironvellum-release.jks
   ironvellum.keystore.password=...
   ironvellum.key.alias=ironvellum
   ironvellum.key.password=...
   ```
3. Confirm the release backend configuration. The public URL and publishable key
   come from committed `cloud-defaults.properties`; `local.properties` may override
   `supabase.url` and `supabase.key`. The release validation task rejects blank
   values. Never embed a service-role or server secret key in an APK.
   The `play` flavour also requires `google.webClientId`; `foss` does not use it.
4. Build `:app:assembleFossRelease` and `:app:bundleFossRelease`. Without signing
   credentials these produce unsigned artifacts, which are suitable for local
   verification but not direct installation or a Play upload. Sign a separate
   copy with the existing debug key for emulator smoke checks only. F-Droid
   builds and signs its own package from the release tag.

## 2. Version bump

- In `app/build.gradle.kts` → `defaultConfig`: increment `versionCode` and set
  `versionName` to match the tag. Play rejects a `versionCode` already uploaded.
- Prepared for this release: `versionName = "1.11"`, `versionCode = 12`, and
  `fastlane/metadata/android/en-US/changelogs/12.txt` (372 characters).

## 3. Local validation

Run all three; all must pass before anything is uploaded:

```
.\gradlew.bat :app:testFossDebugUnitTest
.\gradlew.bat :app:lintFossRelease        # must report zero errors
.\gradlew.bat :app:assembleFossRelease :app:bundleFossRelease --no-configuration-cache
```

Use the non-cached release invocation above: the backend-validation task actions
capture Gradle script objects and are not configuration-cache compatible.
R8 minification and resource shrinking remain enabled.

Pre-hardening baseline (2026-11-12), `python3 tools/gate.py --backend`: all
checks passed without an instrumented retry, but the run missed its budget.
The old summary incorrectly labelled it green; budget overruns now fail.

- 2,865 unit tests, 832 instrumented tests and 374 web tests; zero failures.
- Release lint: zero errors, 1,137 warnings. Web production build passes.
- Local Postgres baseline, hosted-patch replay/idempotence and reset assertions pass.
- Wall clock: 11m04s, above the seven-minute budget.

The minified, resource-shrunk FOSS APK and AAB build successfully as 1.11/code 12.
A separately debug-signed copy ran on `emulator-5554`: fresh setup, exercise art,
per-exercise pounds display preserving recorded kilograms, a completed workout
and rewards, and retained XP after a cold restart. Rest controls work in both
wording modes; all seven translated rest-settings screens were inspected.
The app's read-only backend test returns schema 33. No backend switch was saved.
The crash buffer stayed empty. Packaged serializers, launcher resources and both
backup-exclusion rule files were checked in the release APK.

The original release artifacts remain unsigned. Play signing, Google sign-in
and Health Connect were not exercised by this FOSS smoke. Authenticated production
sync and social flows were subsequently verified below. No release tag or GitHub
release has been created.

Hardening update (2026-11-13): strict release lint reports zero findings. The
fresh minified FOSS build also passed real production email signup, rejected
wrong credentials, signed in again, synced a 40 kg × 5 workout worth 60 XP,
and uploaded a 65,054-byte archive. A fresh local install restored the workout,
XP and explicit pounds override; the recorded load stayed 40 kg. Two disposable
accounts sent and accepted a friend request. The recipient's workout-XP view
was unreadable before acceptance and returned 60 XP afterward. Recent and
lifetime boards rendered independently, and authentication survived a cold restart.
These checks ran on an isolated API 37 emulator; no owner account or phone was used.

The approved manual Pages deployment
[43344295930](https://github.com/AlexMollard/Ironvellum/actions/runs/43344295930)
succeeded with all 374 web tests. The deployed `index-DhFkxaTg.js` was checked in
an isolated signed-in browser: one workout, 60 XP, and a set displayed as
88 lb (40 kg), 5 reps and 2 RIR. Recent and lifetime boards switched independently;
the rolling board restored its 168-hour caption. No browser errors were observed.

Both disposable accounts were then deleted through their own authenticated
`delete_my_account` calls. Subsequent password sign-in returned
`invalid_credentials`; their profiles, sessions, cloud archives and web snapshots
contained zero owned rows. The isolated browser was signed out and closed.

Complete follow-up verification (2026-11-13) passed all 2,865 unit, 832 instrumented
and 374 web tests, with no instrumented skips, no lint findings and all backend
assertions passed. The most recent complete run took 8m55s and correctly reported
**GATE RED**. Its web/backend concurrency experiment did not improve the overall
runtime and was reverted. Clock-batching, reduced-resolution and CPU-affinity
experiments were also discarded; the gate retains the original full-resolution
viewport, assertions and timing checks. Only the account fixture's SDK retry waits
use virtual coroutine time; production retry behavior remains unchanged.

This does **not** clear the gate budget: the complete current gate must still
finish below seven minutes before release.

Target SDK remains 36 by Alex's explicit 1.11 decision. This meets the current
[Play target-API requirement](https://developer.android.com/google/play/requirements/target-sdk);
the narrow `OldTargetApi` lint exception records that choice rather than claiming
API 37 behavior has been migrated.

## 4. Supabase schema

The whole schema is ONE file, `supabase/migrations/0001_baseline.sql`, the
source of truth for a fresh project. The hosted project holds real accounts and
is upgraded in place: a schema change edits the baseline, bumps the
`schema_version()` literal, and ships an idempotent patch in `supabase/hosted/`
cut from the baseline. Raise `NEEDED_SCHEMA_VERSION` only when the whole app
requires it. `python3 tools/gate.py --backend` exercises the baseline, hosted
patch replay and reset round trip against real Postgres. The hosted
project was at schema 30 before the snapshot cap patch
(`supabase/hosted/2026-10-28-web-snapshot-cap.sql`, schema 31) and the hardening patch
(`2026-10-28-web-snapshot-hardening.sql`: 8 MiB text bound, owner-only snapshot delete,
cheaper RLS; no schema bump, four web tables only); both are applied.

The owner applied `supabase/hosted/2026-10-29-feed-card.sql` (after the hardening patch)
to the hosted project on 2026-10-30, establishing schema 32. The patch adds
`sessions.feed_card` and the last column of `public_feed`. It stays optional for the app:
`NEEDED_SCHEMA_VERSION` is 31, so a phone on a backend at 31 keeps syncing and simply sends
no card (`CloudSync.feedReady` needs 32), and the web viewer's Friends area needs 32.
Alex applied `supabase/hosted/2026-11-12-workout-xp.sql` for 1.11. The minified
release's read-only backend test independently returned schema 33 on 2026-11-12.
This confirms reachability, the public key and schema version, not end-to-end
hosted leaderboard or authenticated sync behavior.
The patch adds the rolling workout-XP view without raising the app-wide minimum of 31.

**Never paste `supabase/reset.sql` into the hosted project.** It deletes every
account. It exists for a throwaway project and for the gate's round trip.

1. If this release bumps the schema, paste the new `supabase/hosted/` patch into
   the Supabase SQL editor once, BEFORE the build that needs it ships. Each
   patch is one transaction and idempotent. Confirm with
   `select public.schema_version();`, which must return the new number. Apply
   only patches newer than the version the project reports: older ones can no
   longer run (the circles patch renamed `warbands`, so the inbox patch fails
   with `42P01` and rolls back, harmlessly).
2. Re-run the assertion suite against a throwaway database first if the schema
   changed at all — automated CI is disabled by repository policy, so the local
   assertions are the check that proves the guarantees still hold:

   ```bash
   python3 tools/gate.py --backend
   ```

   That stands up `postgres:16` in Docker, applies the stub and the baseline,
   re-applies it, runs `assert_all.sql`, applies the newest hosted patch twice
   and asserts again, then seeds data, runs `reset.sql`, checks it left nothing
   behind, re-applies the baseline and asserts again. It never touches the
   hosted project. By hand, if you want the steps separately:

   ```bash
   docker run -d --rm --name pg -e POSTGRES_PASSWORD=probe -p 5432:5432 postgres:16
   psql -h localhost -U postgres -f supabase/test/supabase_stub.sql
   psql -h localhost -U postgres -v ON_ERROR_STOP=1 -f supabase/migrations/0001_baseline.sql
   psql -h localhost -U postgres -v ON_ERROR_STOP=1 -f supabase/test/assert_all.sql
   ```
3. Smoke-test sign-in, a sync, and one friend request against production with a
   dedicated test account before release. The read-only schema probe above does
   not exercise these authenticated flows.

## 5. Backend / Google Cloud Console

1. **OAuth consent screen**: if it is in **Testing** mode, only added test
   users can sign in. Publish it (move to In Production) before non-test
   users get the release. Verify the Android OAuth client's SHA-1 matches the
   release keystore (add the release SHA-1 fingerprint; debug SHA-1 only works
   for debug builds).
2. **Health Connect**: the Google Health Connect API Request form must cover
   the seven declared data types (steps, distance, active calories, sleep,
   resting heart rate, weight, body fat). See
   `docs/PLAY_DATA_SAFETY.md` Q5.

## 6. Play Console

1. **Privacy policy**: host `PRIVACY.md` on a public, non-editable,
   non-geofenced HTTPS URL (no PDF); put the URL in Play Console App content
   AND link it inside the app (Settings). Identical URL in all three places.
2. **Data safety form**: fill from `docs/PLAY_DATA_SAFETY.md`. Resolve its
   remaining OPEN QUESTIONS first. Deletion (Q1) is answered: the app deletes
   cloud data from Allies → ALLIES, so the form's deletion question is "yes".
3. **Health apps declaration**: required (App content → Health apps) because
   the app reads health data; a fitness/tracking app falls under the Health
   Content and Services policy.
4. Store listing, content rating, target audience (health app: mind the
   families/ads policies), and upload the signed `app-release.apk` /
   `.aab` from step 3.


## 7. F-Droid

Ironvellum is live on F-Droid (https://f-droid.org/packages/com.ironvellum.app/).
The fdroiddata recipe uses `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`,
so a release needs no merge request:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` (step 2).
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500
   characters at most); it is the release note F-Droid shows.
3. Commit, then push the tag `vX.Y` on that commit. F-Droid's update checker
   finds the tag, adds the build to the recipe and builds the `foss` flavour;
   it usually appears within a few days.
4. Store text and screenshots ship from `fastlane/metadata/android/en-US/` at
   the tagged commit, so update them before tagging.
5. Never rename or delete a store screenshot file. F-Droid keeps a screenshot
   that was deleted from the repository (fdroidserver issue 490), so it stays on
   the listing beside the new ones. A new set reuses the existing file names,
   `01-today.png` to `08-veil.png`: overwrite each file in place. If the set
   shrinks, overwrite the surplus names with copies of screenshots you still
   want (a repeat reads better than a stale image) and say so in the commit
   message; removing the file would not remove it from the listing.

## 8. Post-upload

- Internal testing track first; verify sign-in (release SHA-1!), Health Connect
  sync, and a full workout push on a real device before promoting.

Policy references (checked 2026-09-15):
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Health apps declaration: https://support.google.com/googleplay/android-developer/answer/14738291
- Health Content and Services: https://support.google.com/googleplay/android-developer/answer/16679511
- Health Connect declaration: https://developer.android.com/health-and-fitness/health-connect/declare-access
