# Development

Kotlin 2.4.21, Jetpack Compose with Material 3, Room 2.8.5, WorkManager,
Health Connect 1.1.0, and Supabase 3.8.0 over Ktor 3.5.2. AGP
9.4.1, minSdk 29, targetSdk 36. The domain layer is pure Kotlin, which is why
most of it is covered by fast unit tests. Room is the source of truth; the
cloud is a backup, never an authority.

**Tests run locally.** The workflows are manual (`workflow_dispatch`) only;
the gate runs on your machine:

```bash
python3 tools/gate.py              # build, unit, lint, instrumented
python3 tools/gate.py --backend    # plus the Supabase schema assertions (Docker)
python3 tools/gate.py --no-device  # no emulator: compiles instrumented instead
```

> [!WARNING]
> The instrumented suite calls `pm clear`, deletes rows from the app's own
> database and uninstalls the app. Pointed at a phone with real training on it,
> it destroys that history. `tools/gate.py` pins the run to an emulator and
> refuses a physical device unless you pass `--serial`. If you run Gradle by
> hand, pin it yourself:
> ```bash
> ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedFossDebugAndroidTest
> ```

`python tools/device.py up` boots the emulator, and `install`, `launch` and
`shot` drive it. Cloud defaults live in `cloud-defaults.properties`;
`local.properties` (gitignored) overrides them and holds the Google sign-in
and release signing keys.

More in `docs/`:

- [docs/DESIGN.md](DESIGN.md) - the Clean look
- [docs/GLOSSARY.md](GLOSSARY.md) - every word the app shows
- [docs/RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) - what ships before a release
- [docs/TODO.md](TODO.md) - outstanding work
- [docs/open-source/](open-source/README.md) - open-sourcing, funding and self-hosting plans
- [COSTS.md](../COSTS.md) - what the shared cloud costs
- [AGENTS.md](../AGENTS.md) - standing rules for agents working in the repo
- [docs/archive/GOAL.md](archive/GOAL.md) - the original v1 brief (historical)

## Build from source

Needs a JDK and the Android SDK.

```bash
git clone https://github.com/AlexMollard/Ironvellum.git
cd Ironvellum
./gradlew :app:assembleFossDebug
```

Two flavours share one application id. `foss` is the F-Droid and GitHub build and shows support links. `play` adds Google sign-in and shows none; build it with `assemblePlayDebug`. The app needs Android 10 (API 29) or newer.

## Web viewer

The read-only web dashboard lives in [`web/`](../web/README.md) (Vite and plain JavaScript). It renders snapshots the app uploads and computes nothing itself. Deployment to GitHub Pages is a manual workflow.

