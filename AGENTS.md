# Ironvellum — standing rules

## Git: commit and push as you go

The owner has authorised agents to `git commit` and `git push` to `main` as work
progresses (2026-09-23). Commit each coherent unit once it is verified, using the
house commit style, and push it.

Still the owner's call, every time: force-push, history rewrites (`rebase` of
pushed commits, `filter-repo`, `reset` of pushed work), tags and GitHub
releases, and changing repository visibility. Pushing triggers no workflow:
every workflow is `workflow_dispatch` only (see below).

## Tests run locally, never on GitHub Actions

This repository is public on purpose (the owner confirmed it 2026-10-22): F-Droid
builds from its tags and the README, store text and privacy policy link here.
Public repositories are not billed for Actions minutes. While it was private,
186 automatic runs in the 8 days to 2026-09-22 cost ~18 billed minutes each.

`.github/workflows/ci.yml` still has **no push or pull_request trigger** — it is
`workflow_dispatch` only, and the local gate below is the check that counts.
Adding a trigger back is the owner's call, not an agent's. `apk.yml` is likewise
manual, and exists to hand someone a sideloadable build.

The gate is local and covers all three CI jobs:

```bash
python3 tools/gate.py              # build, unit, lint, instrumented
python3 tools/gate.py --backend    # plus the Supabase schema assertions
python3 tools/gate.py --no-device  # no emulator: compiles instrumented instead
```

It finishes in 3.5 to 4 minutes on this machine (3m36s and 3m51s measured after a source edit on a warm
emulator, 3m58s with a cold boot in the run; 2026-10-16). The budget is 5 minutes and the
summary prints the wall clock and flags a miss. It goes red when any test fails, and also when fewer tests
ran than the sources declare: a test process that crashes on start reports zero tests and zero failures,
which used to read as green.

Phases of a warm run after an edit (379 instrumented, 2040 unit tests): build 10 to 20s, verify 206 to 211s.
Verify is the instrumented task alone; unit tests, lint and the other flavour's assembles finish inside it, so
only the instrumented time matters. Before the seeded-template change the same run took 5m19s (verify 299s,
the suite summing 277s). A cold boot (over 30 minutes up) adds about 34s when it overlaps nothing else, and an
instrumented retry doubles verify: that, not the suite, is what the 6 to 11 minute runs were.

How it gets there, so nobody undoes it by accident:

- Two Gradle invocations. The first assembles what the instrumented run needs while the emulator boots; the
  second runs unit tests, lint, the instrumented suite and the other flavour's assembles together. Gradle
  only runs independent tasks of one project in parallel under `--configuration-cache`, so the gate passes
  it. Gradle output goes to `.tmp/gate-*.log`; the console gets one line per phase.
- Data tests start from a copy of a once-seeded database (`SeededDb` in `app/src/androidTest`) instead of
  inserting the 580-exercise catalogue each time: 0.25s per test on 160 tests, about 80s of the suite on the
  emulator. Tests that check seeding, migration or an empty install still build theirs from nothing.
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

Where the remaining time went: `ShotRecorder.take` (31 calls, each sleeping 2s and saving a PNG for a human
to look at, about 40s of the suite). The captures are now opt-in and the gate skips them: take them with
`-Pandroid.testInstrumentationRunnerArguments.shots=true` (or `-e shots true` on `am instrument`), then
`adb pull /data/local/tmp/optimise-shots`. Measured in one worktree: the gate took 4m38s with captures
(verify 274s) and 4m19s / 4m25s without (verify 231s / 230s).

## Screen text goes in string resources

New screen text is a resource in `res/values/strings.xml` (plain `key`, themed `key__t`), never a literal in Kotlin.
`HardcodedUiLiteralTest` fails when a file has more literals than its checked-in baseline, and also when it has fewer
(lower the baseline). The guide, naming, plurals and the ratchet are in `docs/STRINGS.md`.

## One emulator, shared between sessions

Parallel sessions all use `emulator-5554`, and an install, `pm clear` or
uninstall from one kills another's suite part way through. Take the lock before
touching it. The lock is the directory `.tmp/emulator.lock` under the **main
checkout** (every worktree shares it, so a relative `.tmp` inside a worktree is
not the same lock). Run anything that touches the emulator through the helper,
which takes the lock, records who holds it, and releases it however the run ends:

```bash
python3 tools/device.py lock -- adb -s emulator-5554 install -r app.apk
python3 tools/device.py lock --label "my run" -- bash -c 'ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedFossDebugAndroidTest'
```

The command's exit code is the helper's. `tools/gate.py` takes the same lock itself while the instrumented
suite runs.

The lock heals itself, so a killed run no longer blocks everyone until a human clears it. A holder that uses
the helper (or the gate) writes `owner.json` inside the lock (pid, host, label, start time) and touches it
every 30s. A waiter takes the lock over, and says so, when the recorded pid is no longer running on this host,
or when the owner has not beaten for 10 minutes. Released on normal exit, an exception, Ctrl-C, Ctrl-Break and
SIGTERM; only a hard kill leaves the stale lock behind, and the next waiter clears it. A bare
`mkdir .tmp/emulator.lock` still takes the lock but cannot record a pid, so it is only taken over once it is 30
minutes old: prefer the helper. `python3 tools/test_device_lock.py` checks the takeover rules.

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
