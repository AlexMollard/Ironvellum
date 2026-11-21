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

## Web viewer

The web dashboard lives in [`web/`](../web/README.md) (Vite and plain JavaScript).
Training history and derived statistics are read-only; social features allow
reactions, comments and group actions. The phone computes the training metrics.
Deployment to GitHub Pages is a manual workflow.

