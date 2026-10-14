# Ironvellum — standing rules

## Git: commit and push as you go

The owner has authorised agents to `git commit` and `git push` to `main` as work
progresses (2026-09-23). Commit each coherent unit once it is verified, using the
house commit style, and push it.

Still the owner's call, every time: force-push, history rewrites (`rebase` of
pushed commits, `filter-repo`, `reset` of pushed work), tags and GitHub
releases, and changing repository visibility. Pushing no longer triggers billed
work because every workflow is `workflow_dispatch` only (see below).

## Tests run locally, never on GitHub Actions

This repository is private, so every runner minute is billed. Measured over the
8 days to 2026-09-22: 186 automatic runs at ~18 billed minutes each.

`.github/workflows/ci.yml` therefore has **no push or pull_request trigger** —
it is `workflow_dispatch` only. Do not add one back. `apk.yml` is likewise
manual, and exists to hand someone a sideloadable build.

The gate is local and covers all three CI jobs:

```bash
python3 tools/gate.py              # build, unit, lint, instrumented
python3 tools/gate.py --backend    # plus the Supabase schema assertions
python3 tools/gate.py --no-device  # no emulator: compiles instrumented instead
```

It finishes in 4 to 4.5 minutes on this machine (4m11s to 4m31s measured after a source edit, with the
emulator already used for a full run; 3m45s when nothing needed compiling). The budget is 5 minutes and the
summary prints the wall clock and flags a miss. It goes red when any test fails, and also when fewer tests
ran than the sources declare: a test process that crashes on start reports zero tests and zero failures,
which used to read as green.

How it gets there, so nobody undoes it by accident:

- Two Gradle invocations. The first assembles what the instrumented run needs while the emulator boots; the
  second runs unit tests, lint, the instrumented suite and the other flavour's assembles together. Gradle
  only runs independent tasks of one project in parallel under `--configuration-cache`, so the gate passes
  it. Gradle output goes to `.tmp/gate-*.log`; the console gets one line per phase.
- The emulator is cold booted (about 30s) whenever it is not running, has been up over 30 minutes, or
  answers a shell call slowly. It degrades with use: after a few runs on the `host` GPU mode a suite went
  from 70s to 185s, with `graphics.composer3` and `allocator` burning CPU and adb dropping.
- If adb drops mid-run (three failed probes in a row) or the instrumented task fails, the gate restarts
  the emulator and reruns the whole instrumented suite once; a second failure is red. The whole suite is
  rerun because some tests lean on state an earlier class sets up (run alone on a fresh install,
  `AccessibilityChecksTest.theNavBarSaysWhichDestinationYouAreOn` and
  `everyTappableControlIsAnnounceableAndHittable` find no "Train" tab).
- `tools/device.py up` boots the emulator even with the phone plugged in (only emulators count).

Emulator settings (the AVD config is outside the repo: `D:\caches\android-avd\IronvellumEmu.avd\config.ini`):

| Key | Value |
|---|---|
| `hw.cpu.ncore` | 8 (the guest reports 6 CPUs) |
| `hw.ramSize` / `vm.heapSize` | 8192 / 576 |
| `hw.gpu.mode` | `angle_indirect`; `tools/device.py` also passes `-gpu angle_indirect` (override with `IRONVELLUM_GPU`) |
| `fastboot.forceColdBoot` / `forceFastBoot` | `yes` / `no`; `firstboot.saveToLocalSnapshot` and `bootFromLocalSnapshot` are `no`; boot also passes `-no-snapshot` |
| `hw.camera.back` / `hw.audioInput` / `hw.audioOutput` | `none` / `no` / `no` |

GPU mode measured on a five-class subset (62 tests), repeated on one emulator: `host` 70, 84, 89, 185s
(degrades); `swiftshader_indirect` 80, 78, 79, 78s; `angle_indirect` 61, 61, 60s. Window, transition and
animator animation scales are set to 0 and the screen kept awake after every boot (`device.tune`).

Where the remaining time goes: the instrumented suite is about 215s alone, of which about 50s is
`ShotRecorder.take` (31 calls, each sleeping 2s and saving a PNG for a human to look at). Skipping the
captures took the suite from 209s to 167s. They still run; that is the next lever if the budget gets tight.

## One emulator, shared between sessions

Parallel sessions all use `emulator-5554`, and an install, `pm clear` or
uninstall from one kills another's suite part way through. Take the lock before
touching it, and release it however the run ends:

```bash
until mkdir .tmp/emulator.lock 2>/dev/null; do sleep 30; done
trap 'rmdir .tmp/emulator.lock' EXIT
```

`tools/gate.py` takes this lock itself while the instrumented suite runs.

## Instrumented tests never touch the owner's phone

The suite calls `pm clear`, deletes rows from the app's own database, and
uninstalls the app afterwards. Pointed at the owner's S25 Ultra (`R5GL14GXV3J`)
it destroys real training history.

`tools/gate.py` resolves an emulator serial itself and pins `ANDROID_SERIAL`; a
physical device is only used when named explicitly with `--serial`. Any manual
Gradle invocation must pin it by hand:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedFossDebugAndroidTest
```

Installing a build on the phone (`:app:installFossDebug`) is safe and is how visual
changes get verified.
