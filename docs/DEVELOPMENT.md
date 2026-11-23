# Development

Kotlin 2.4.21, Jetpack Compose with Material 3, Room 2.8.5, WorkManager,
Health Connect 1.1.0, and Supabase 3.9.0 over Ktor 3.6.1. AGP
9.5.2, minSdk 29, compileSdk 37.1, targetSdk 36. The domain layer is pure Kotlin, which is why
most of it is covered by fast unit tests. Room is the source of truth; the
cloud is a backup, never an authority.

**Tests run locally.** The workflows are manual (`workflow_dispatch`) only;
the gate runs on your machine:

```bash
python3 tools/gate.py              # Android + web build, tests, lint, instrumented
python3 tools/gate.py --backend    # also SQL assertions in disposable Postgres (Docker)
python3 tools/gate.py --no-device  # compile instrumented tests without running them
```

The gate must finish strictly under ten minutes; passing tests after the
deadline still produce a red verdict. `--no-device` is not instrumented-test
acceptance. The web phase also needs Node 22.12+ and npm.

Validation on 2026-11-21: the full FOSS gate passed in **8m41s** with warm
Gradle caches and a running API 35 emulator (`Ironvellum_Art_API35`, swangle,
8 cores / 8 GB). All 2,912 unit tests, 869 instrumented tests, 380 web tests
and four generated-art checks passed; lint reported no issues. The local
runner selected that installed AVD in place of `IronvellumEmu`; it used the
unchanged gate, emulator lock and ten-minute budget. This is a warm-run
measurement, not a cold-cache build guarantee.

> [!WARNING]
> The instrumented suite calls `pm clear`, deletes rows from the app's own
> database and uninstalls the app. Pointed at a phone with real training on it,
> it destroys that history. `tools/gate.py` pins the run to an emulator and
> refuses a physical device unless you pass `--serial`. For a manual run, use
> the shared emulator lock helper, which also pins `ANDROID_SERIAL`:
> ```bash
> python tools/device.py lock --label "instrumented tests" -- ./gradlew :app:connectedFossDebugAndroidTest
> ```
> On Windows, replace `./gradlew` with `.\gradlew.bat`. Every worktree shares
> the main checkout's emulator lock; do not install or clear data outside it.

`python tools/device.py up` boots the emulator. Run the `install`, `launch`
and `shot` commands under the same lock, for example
`python tools/device.py lock -- python tools/device.py install`.
Cloud defaults live in `cloud-defaults.properties`; gitignored
`local.properties` overrides them and holds Google sign-in and signing keys.

More in `docs/`:

- [docs/DESIGN.md](DESIGN.md) - the Clean look
- [docs/GLOSSARY.md](GLOSSARY.md) - every word the app shows
- [docs/RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) - what ships before a release
- [docs/TODO.md](TODO.md) - outstanding work
- [Wear OS companion plan](superpowers/plans/2026-11-13-fdroid-wear-os-companion.md) - F-Droid-compatible live workout controls, transport feasibility, timers and verification gates
- [docs/open-source/](open-source/README.md) - historical implementation plans
- [COSTS.md](../COSTS.md) - what the shared cloud costs
- [AGENTS.md](../AGENTS.md) - standing rules for agents working in the repo
- [docs/archive/GOAL.md](archive/GOAL.md) - the original v1 brief (historical)

## Build from source

Install JDK 17 and Android SDK platform 37.1; the Gradle 9.7.1 wrapper is included.
Set `ANDROID_HOME` or `sdk.dir` in your gitignored `local.properties`.

```bash
git clone https://github.com/AlexMollard/Ironvellum.git
cd Ironvellum
./gradlew :app:assembleFossDebug
```

On Windows use `.\gradlew.bat :app:assembleFossDebug`. Generated Android assets
are checked in, so building the phone app does not require the art submodule.
Web development and rebuilding exercise art do: run
`git submodule update --init art` first.

Two flavours share one application id. `foss` is the F-Droid and GitHub build and shows support links. `play` adds Google sign-in and shows none; build it with `assemblePlayDebug`. The app needs Android 10 (API 29) or newer.

## Portable Android tooling

Run commands from the repository root. Scripts derive checkout paths from their
own location; no particular checkout directory, username or drive is required.

The device helper and gate share SDK discovery: `ANDROID_HOME`, the legacy
`ANDROID_SDK_ROOT`, the checkout's ignored `local.properties` (`sdk.dir`), an ADB
installation on `PATH`, then the current user's standard Android Studio SDK
location on Windows, macOS or Linux. `IRONVELLUM_ADB` and
`IRONVELLUM_EMULATOR` can explicitly select executable paths.

AVD directories follow the emulator's existing `ANDROID_AVD_HOME`,
`ANDROID_EMULATOR_HOME` and `ANDROID_USER_HOME` settings and current-user defaults.
The helper does not inject a cached directory from another computer. See the
[Android environment variable reference](https://developer.android.com/tools/variables).

When exactly one AVD is installed, it can be selected automatically. If several
are installed, set `IRONVELLUM_AVD` to the disposable test AVD you intend to use
before cold booting. Set it only in your terminal or private machine setup; do
not commit a workstation's choice. A missing configured AVD fails immediately.
An already-running emulator can still be used by serial. Tests never fall back
to a physical phone. The shared emulator lock and ten-minute gate budget apply.

Use the ordinary `python tools/gate.py` command; machine-specific import wrappers
are unnecessary. `python tools/test_android_paths.py` checks discovery with
temporary fixtures, and `python tools/test_portable_paths.py` checks tracked files
for workstation paths. Both checks also run in the gate.

## Build without a default backend

`./gradlew -PlocalOnly=true :app:assembleFossRelease` builds the FOSS app with empty Supabase URL, public key and Google web client ID, including when release signing or custom cloud credentials are configured. Use `gradlew.bat` on Windows. The APK appears under `app/build/outputs/apk/foss/release/`. Standard release signing still applies. An unsigned build needs signing before installation. The app retains its application ID, so an in-place update requires the same signing key.

Fresh installs can train, export, review and save offline. An existing complete custom backend choice is retained. Returning to the build default in this release disconnects the custom backend. Ordinary FOSS releases use the public defaults unless local.properties supplies the custom URL/key pair. Ordinary Play releases also require the Google web client ID. A single custom backend field leaves the cloud unavailable and fails ordinary release validation.

For the build configuration matrix, set `ANDROID_HOME` and run `python tools/test_release_backend.py`. The script invokes Gradle in a temporary project using synthetic settings.

### Verify AI training and draft flows

The Android gate covers offline training export, workout import, explicit draft
review and saving against isolated Room databases. Shared wire contracts and
byte fixtures live in [training-contracts](training-contracts/README.md).

Hosted MCP tests and deployment requirements are documented in the
[production service](../server/production_mcp/README.md). Run those separately
from the Android gate. The personal PC connector and its integration runner are
archived on [archive-personal-connector](https://github.com/AlexMollard/Ironvellum/tree/archive-personal-connector).

## Local development MCP

The [development MCP setup](../tools/development_mcp/README.md) covers device
selection, app status, screenshots, UI inspection, notification previews
and diagnostics from a local MCP client.

Validated on Windows on 2026-11-21: the full local gate passed in **8m21s**,
with 2,912 unit tests, 883 instrumented tests, all web checks and zero lint
issues. The MCP's 44 host tests and 18 shared-lock tests passed. All six tools
also passed real stdio client checks against the emulator in both supported
protocol modes, including the saved Codex configuration. Linux/macOS locking
has source review only.

Live workout status and notification previews require an already-running debug
app. MCP calls never launch it to obtain those responses. Each selected-device
call shares the test runner's lock, so even a phone call can wait up to 30 seconds
while another session uses the emulator.

## Notification previews

Debug builds can show six sample notification cards without creating workouts,
changing inbox delivery state or contacting another account:

```bash
adb -s <serial> shell am broadcast -n com.ironvellum.app/com.ironvellum.app.debug.NotificationPreviewReceiver -a com.ironvellum.app.debug.SHOW_NOTIFICATIONS
adb -s <serial> shell am broadcast -n com.ironvellum.app/com.ironvellum.app.debug.NotificationPreviewReceiver -a com.ironvellum.app.debug.CLEAR_NOTIFICATIONS
```

The receiver requires adb's `DUMP` permission and is absent from release builds.
Cards use the real social/rest builders, a separate preview tag, sample data,
harmless app-open taps and no workout actions. They are silent, labelled
“Preview · sample data” and expire after 15 minutes. They follow the current
wording, theme and lock-screen privacy settings; blocked system channels stay
blocked. Use the shared emulator lock helper around either command on an emulator.

Validated on 2026-11-21: the full FOSS gate passed in **9m21s**, with
2,912 unit tests, 875 instrumented tests, all web checks and no lint issues.
The six sample cards were also posted on the owner's phone and visually approved.

## Production MCP browser and app

The [production deployment guide](../server/production_mcp/DEPLOYMENT.md) covers the pinned container, private setup and pruning, canonical HTTPS ingress and recovery. The [receipt verification instructions](../server/production_mcp/README.md#sql-to-android-receipt) join the independent server and Android suites with a fresh SQL-issued draft and an Android-produced acknowledgement.

The production service serves the request-bound consent page after authorization and human connection management at `/connections`. It bundles the browser code, stylesheet and Chakra Petch font in the wheel. Configure the issuer, backend and publishable key as described in [the production service guide](../server/production_mcp/README.md). The Android client discovers the public MCP address through its authenticated connections RPC. An unconfigured or older backend shows an unavailable state.

Email/password uses the configured Supabase Auth password endpoint. To enable the optional Google button, enable Google on that Supabase backend, add the exact issuer URL ending in `/signin/callback` to its redirect allowlist and set `IRONVELLUM_MCP_GOOGLE_CALLBACK` to that exact URL. The original tab retains the PKCE verifier and completes the exchange for the popup's code. Closing or reloading the original tab abandons sign-in. Browsers that sever the popup reference cannot complete Google sign-in. The human token lives in tab memory. The app sends the consent decision to the bound API and navigates to the registered AI callback. Browser management uses only the fixed connections and revoke RPCs. Hosts and reverse proxies must disable query, body and authorization-header logging for these routes.

Use Account → AI connections or Train → Workout drafts. Reopen or refresh a screen to retry acknowledgements for locally saved drafts. Each device database can save a draft once. Synthetic local checks cover browser and phone flows. Hosted identity providers and public HTTPS require operator verification.

## Web viewer

The web dashboard lives in [`web/`](../web/README.md) (Vite and plain JavaScript).
Training history and derived statistics are read-only; social features allow
reactions, comments and group actions. The phone computes the training metrics.
Deployment to GitHub Pages is a manual workflow.

