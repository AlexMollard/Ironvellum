# 02: Distribution and build flavours

**Goal:** one source tree produces two builds:

- `foss`: no proprietary libraries, donation links on. Ships on F-Droid and
  GitHub Releases.
- `play`: Google sign-in included, no donation links. Ships on Google Play.

**Done when:** both variants pass `tools/gate.py`; the `fossRelease` APK
contains no `com.google.android.libraries.identity` or
`com.google.android.gms` classes; and an F-Droid metadata directory exists.

## Facts this rests on

- There are no product flavours today (`app/build.gradle.kts`, whole file).
- The proprietary dependencies are exactly `androidx.credentials:credentials`,
  `androidx.credentials:credentials-play-services-auth` and
  `com.google.android.libraries.identity.googleid:googleid`
  (`app/build.gradle.kts:184-186`, `gradle/libs.versions.toml:48-50`).
- Only one file imports them: `ui/social/AccountScreen.kt:66-72`, used solely
  by the private composable `GoogleSignInButton` (`:616-680`). It is gated by
  `Cloud.googleConfigured` (`Cloud.kt:21`) through `googleEnabled`
  (`AccountScreen.kt:444`).
- `AccountRepository.signInWithGoogle` (`:151`) uses supabase-kt's `IDToken`
  provider, which is FOSS, so it can stay in `main`.
- Health Connect (`androidx.health.connect:connect-client`) is AndroidX and
  Apache-2.0, so it stays in both flavours.
- `validateReleaseBackend` (`app/build.gradle.kts:129-157`) fails every release
  build without `supabase.url`, `supabase.key` **and** `google.webClientId` in
  `local.properties`. F-Droid's build server has no `local.properties`, so
  **today's `foss` release would not build on F-Droid.**

## Steps (code)

1. **Flavours.** In `app/build.gradle.kts`, add
   `flavorDimensions += "distribution"` with `foss` and `play`. Same
   `applicationId`. The two are signed with different keys and so never update
   over each other, which is the norm for F-Droid versus Play.
2. **Dependencies.** Move the three Google dependencies to
   `"playImplementation"(...)`. `androidx.credentials:credentials` is
   Apache-2.0 but useless without the Play bridge, so it moves too.
3. **Source split.** Move `GoogleSignInButton` and its imports to
   `app/src/play/kotlin/com/ironvellum/app/ui/social/GoogleSignIn.kt`. Add
   `app/src/foss/kotlin/.../GoogleSignIn.kt` with the same signature and an
   empty body. `AccountScreen` calls it unchanged, so there is no reflection
   and no runtime flag.
4. **Backend defaults the F-Droid server can see.** The Supabase URL and
   publishable key are public by design: RLS protects the data, and the key is
   already inside every APK. Commit them as `cloud-defaults.properties` at the
   repo root, holding `supabase.url` and `supabase.key` only.
   `local.properties` still overrides them. `google.webClientId` stays in
   `local.properties`, because only `play` uses it.
   - Tradeoff: forks that don't change it would sync to the shared project.
     The mitigation is a one-line README note, plus the fact that the key can
     be rotated in the dashboard.
5. **Release gate per flavour.** Split `validateReleaseBackend` so that
   `fossRelease` requires only the URL and key, and `playRelease` also
   requires `google.webClientId`. Keep the unsigned-build warning
   (`:144-153`).
6. **Support-links flag.** Add `buildConfigField("boolean", "SUPPORT_LINKS", ...)`,
   `true` in `foss` and `false` in `play`. Plan 04 reads it.
7. **Tooling.** `tools/gate.py` builds and tests the variant it names. Default
   to `foss` (the primary distribution) and add `--flavour play`. Instrumented
   tests run against one flavour, because the shared code is identical, but the
   gate compiles both flavours' androidTest.
8. **ProGuard.** Keep rules that name credentials classes move into a flavoured
   `proguard-rules-play.pro`. Check whether any exist first; otherwise R8 warns
   in `foss`.

**Checks:**

- `./gradlew :app:assembleFossRelease :app:assemblePlayRelease` both succeed.
- Dex probe: `apkanalyzer dex packages app-foss-release.apk | grep -c identity.googleid`
  prints `0`. See skill `android-r8-release-verification`.
- On device, the `foss` Account screen shows email sign-in with no Google
  button, and `play` still shows the button.

## F-Droid (code, then OWNER)

1. Add `fastlane/metadata/android/en-US/` with `title.txt`,
   `short_description.txt` (at most 80 characters), `full_description.txt`
   (adapted from the README "Why this exists" section),
   `images/icon.png` (from `docs/images/icon.png`),
   `images/phoneScreenshots/*.png` and `changelogs/<versionCode>.txt`. No such
   directory exists today. F-Droid and Play can both consume it.
   Screenshots must come **from the phone**: emulator shots render
   differently (README lines 141-144).
2. Declare the anti-features honestly. The shared backend is FOSS (Supabase),
   so `NonFreeNet` does not apply. Health Connect needs Google's Health
   Connect app on the device, which F-Droid may treat as `NonFreeDep`. Let the
   F-Droid reviewer decide, and don't argue it away.
3. **OWNER:** open a Request For Packaging at
   https://gitlab.com/fdroid/rfp/-/issues, or send a merge request to
   `fdroiddata` with the build recipe (`gradle: [foss]`,
   `subdir: app`). F-Droid builds from a tag, so each release needs one:
   `git tag v1.1.0` (tags stay owner-run, per `AGENTS.md`).
4. **Signing.** F-Droid signs with its own key by default. To let GitHub
   Releases and F-Droid users update each other, opt into
   reproducible builds, where F-Droid publishes your signature. Only attempt
   it once plain builds are accepted.

## GitHub Releases (OWNER per release)

```bash
python3 tools/gate.py
./gradlew :app:assembleFossRelease     # signed via local.properties keystore keys
gh release create v1.1.0 app/build/outputs/apk/foss/release/app-foss-release.apk \
  --notes-file fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
```

`apk.yml` stays as it is: a manual debug build for handing to a tester.

## Google Play (OWNER, after F-Droid)

Follow `docs/RELEASE_CHECKLIST.md` using `bundlePlayRelease`. It needs the
keystore, the hosted privacy policy URL (the GitHub URL of `PRIVACY.md` is
public and not editable by third parties), the health-apps declaration, and
the OAuth consent screen out of Testing. Ship `0011` together with this build.
