# Monarch release checklist

Ordered steps to ship `com.monarch.app` to Google Play. Execute top to bottom.
Written 2026-09-15. Verification commands assume repo root `D:\Monarch` on
Windows: `.\gradlew.bat <task>`.

## 1. Signing key

1. Create a keystore (once, keep it safe — losing it means losing the app
   identity):
   ```
   keytool -genkeypair -v -keystore monarch-release.jks -alias monarch ^
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Add the signing keys to `local.properties` (gitignored; the release build
   reads env vars first, then these keys — absent keys yield an unsigned APK,
   never a configuration failure):
   ```
   monarch.keystore.path=D:/path/to/monarch-release.jks
   monarch.keystore.password=...
   monarch.key.alias=monarch
   monarch.key.password=...
   ```
4. Add the three backend keys to `local.properties` (gitignored). They become
   BuildConfig fields `SUPABASE_URL`, `SUPABASE_KEY`, `GOOGLE_WEB_CLIENT_ID`:
   ```
   supabase.url=https://<project>.supabase.co
   supabase.key=<anon key>
   google.webClientId=<web client id>.apps.googleusercontent.com
   ```
   Debug builds work with these blank (cloud degrades gracefully,
   `Cloud.configured == false`), but a RELEASE build without them ships an app
   whose social features can never sign in — treat them as mandatory for the
   release build and confirm the values are non-blank before step 3.
5. Verify configuration succeeds even without keys:
   `.\gradlew.bat :app:assembleRelease` (with keys: check
   `app/build/outputs/apk/release/` is signed; without: unsigned APK is fine
   for CI).

## 2. Version bump

- In `app/build.gradle.kts` → `defaultConfig`: increment `versionCode` (currently
  `1`) and set `versionName`. Play rejects a `versionCode` already uploaded.

## 3. Local validation

Run all three; all must pass before anything is uploaded:

```
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintRelease        # baseline: 0 errors / 31 warnings
.\gradlew.bat :app:assembleRelease
```

## 4. Supabase migrations

Four files are STAGED AND NOT APPLIED. Order matters, and one of them must go
out with its matching app build.

| file | what it does | urgency |
|---|---|---|
| `0008_shadow_board.sql` | adds the shadow columns and the board view | optional; the client degrades gracefully without it (the shadow push is swallowed separately from training sync) |
| `0009_backend_hardening.sql` | closes the friendship oracle, bounds feed text, blocks future-dated sessions, drops a dead view, pins a search path | **apply first — until it lands, anyone holding the shipped publishable key can enumerate the accepted-friendship graph, including for hunters who chose `private`** |
| `0010_hunter_discovery.sql` | adds `find_hunter()` | apply before relying on friend requests: without it a by-name lookup returns nothing and the app reports that a real hunter does not exist |
| `0011_server_side_aggregates.sql` | revokes direct writes to the ranked columns, derives level and title count, bounds the rest | **apply WITH the matching app build, never before** — the revoke makes an older client's profile upsert fail |

1. Confirm what is already applied, then apply the pending files in numeric
   order (supabase CLI or the SQL editor). Everything from `0005` onward is
   idempotent and may be re-run; `0001`–`0004` are immutable.
2. Re-run the assertion suite against a throwaway database first if the schema
   changed at all — CI does this on every push, but it is the one check that
   proves the guarantees still hold:

   ```bash
   docker run -d --rm --name pg -e POSTGRES_PASSWORD=probe -p 5432:5432 postgres:16
   psql -h localhost -U postgres -f supabase/test/supabase_stub.sql
   for f in supabase/migrations/*.sql; do psql -h localhost -U postgres -v ON_ERROR_STOP=1 -f "$f"; done
   psql -h localhost -U postgres -v ON_ERROR_STOP=1 -f supabase/test/assert_all.sql
   ```
3. Smoke-test sign-in, a sync, and one friend request against production before
   the release. The `find_hunter` and `push_aggregates` call paths are
   compile-verified only — no local harness speaks PostgREST, so the first real
   round trip is their first proof.

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
   cloud data from Guild → ALLIES, so the form's deletion question is "yes".
3. **Health apps declaration**: required (App content → Health apps) because
   the app reads health data; a fitness/tracking app falls under the Health
   Content and Services policy.
4. Store listing, content rating, target audience (health app: mind the
   families/ads policies), and upload the signed `app-release.apk` /
   `.aab` from step 3.

## 7. Post-upload

- Internal testing track first; verify sign-in (release SHA-1!), Health Connect
  sync, and a full workout push on a real device before promoting.

Policy references (checked 2026-09-15):
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Health apps declaration: https://support.google.com/googleplay/android-developer/answer/14738291
- Health Content and Services: https://support.google.com/googleplay/android-developer/answer/16679511
- Health Connect declaration: https://developer.android.com/health-and-fitness/health-connect/declare-access
