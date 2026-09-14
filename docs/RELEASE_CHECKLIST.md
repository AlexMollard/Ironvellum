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
3. Verify configuration succeeds even without keys:
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

1. Check what is applied vs staged. **`supabase/migrations/0008_shadow_board.sql`
   is STAGED AND NOT APPLIED** — it `ALTER`s the live `profiles` table (adds
   `shadow_essence`, `shadow_count`, `shadow_rate`) and creates the
   `shadow_board` view. Apply it only on explicit go-ahead; the client degrades
   gracefully without it (the shadow push is swallowed separately from training
   sync, `CloudSync.kt`).
2. Apply pending migrations to the production project (supabase CLI or SQL
   editor) and smoke-test sign-in + sync against production before the release.

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
   OPEN QUESTIONS first — especially Q1 (no user-facing cloud deletion path
   exists yet; the honest "no" answer risks review rejection).
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
