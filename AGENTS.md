# Ironvellum — standing rules

## Local first; hosted services are optional

The owner requires every Ironvellum component to be runnable or self-hostable on
the user's own computer (2026-11-22). Personal training, data export/import and
the personal AI connector must work without an account, an Ironvellum-operated
server, paid hosting, developer mode or ADB. A chosen AI provider may have its own
account requirements; the connector must not impose an Ironvellum account.

Cloud sync, social features and the hosted production MCP remain opt-in. Users
must be able to choose their own backend. Do not make maintainer credentials,
services or availability prerequisites for local features. Provide an explicit
backend-free release build; local-only choices must never silently fall back to
the shared backend. Preserve existing users' local data and optional connections.

The personal PC connector is archived on `archive-personal-connector`; do not
restore its desktop app or file-watching runtime on main without a new request.
Keep the hosted production MCP, its scoped permissions and explicit workout-draft
review, and independent phone sharing/import functionality. Never distribute
private server credentials or turn device-control tools into consumer dependencies.

## Git: commit and push as you go

The owner has authorised agents to `git commit` and `git push` to `main` as work
progresses (2026-09-23). Commit each coherent unit once it is verified, using the
house commit style, and push it.

Still the owner's call, every time: force-push, history rewrites (`rebase` of
pushed commits, `filter-repo`, `reset` of pushed work), tags and GitHub
releases, and changing repository visibility.

## Tests run locally, never on GitHub Actions

The repository is public on purpose: F-Droid builds from its tags, and the README,
store text and privacy policy link here. Every workflow is `workflow_dispatch` only:
`ci.yml` has no push or pull_request trigger, and adding one is the owner's call.
`apk.yml` exists to hand someone a sideloadable build.

The gate is local and covers all three CI jobs:

```bash
python3 tools/gate.py              # build, unit, lint, instrumented
python3 tools/gate.py --backend    # plus the Supabase schema assertions
python3 tools/gate.py --no-device  # no emulator: compiles instrumented instead
```

The budget is strictly under 10 minutes (owner, 2026-11-13). The summary prints the
wall clock and goes red on a budget miss, any failed test, or fewer tests run than
the sources declare (a test process that crashes on start reports zero tests and
zero failures). Run the gate alone: other builds on the machine push it over budget.
Release evidence lives in `docs/RELEASE_CHECKLIST.md`; passing counts alone do not
make the gate green.

How it stays fast, so nobody undoes it by accident:

- Two Gradle invocations. The first assembles what the instrumented run needs while
  the emulator boots; the second runs unit tests, lint, the instrumented suite and
  the other flavour's assembles together under `--configuration-cache` (Gradle only
  parallelises one project's tasks with it). Gradle output goes to
  `.tmp/gate-*.log`; the console gets one line per phase. Only the instrumented
  time matters: everything else finishes inside it.
- Data tests start from a copy of a once-seeded database (`SeededDb` in
  `app/src/androidTest`) instead of inserting the 580-exercise catalogue each time.
  Tests that check seeding, migration or an empty install build theirs from nothing.
- The emulator is cold booted whenever it is not running, has been up over 30
  minutes, or answers a shell call slowly: it degrades with use.
- If adb drops mid-run (three failed probes in a row) or the instrumented task
  fails, the gate restarts the emulator and reruns the whole suite once; a second
  failure is red. The whole suite reruns because some tests lean on state an
  earlier class sets up (run alone on a fresh install,
  `AccessibilityChecksTest.theNavBarSaysWhichDestinationYouAreOn` and
  `everyTappableControlIsAnnounceableAndHittable` find no "Train" tab).
- `ShotRecorder.take` captures are opt-in and the gate skips them. Take them with
  `-Pandroid.testInstrumentationRunnerArguments.shots=true` (or `-e shots true` on
  `am instrument`), then `adb pull /data/local/tmp/optimise-shots`.
- `tools/device.py up` boots the emulator even with the phone plugged in (only
  emulators count).

Emulator settings (the AVD config is outside the repo:
`D:\caches\android-avd\IronvellumEmu.avd\config.ini`):

|Key|Value|
|---|---|
|`hw.cpu.ncore`|8 (the guest reports 6 CPUs)|
|`hw.ramSize` / `vm.heapSize`|8192 / 576|
|GPU|`tools/device.py` pins the `swangle` software backend via `-gpu` (override with `IRONVELLUM_GPU`). `host` degrades over repeated runs; `angle_indirect` is deprecated|
|`fastboot.forceColdBoot` / `forceFastBoot`|`yes` / `no`; `firstboot.saveToLocalSnapshot` and `bootFromLocalSnapshot` are `no`; boot also passes `-no-snapshot`|
|`hw.camera.back` / `hw.audioInput` / `hw.audioOutput`|`none` / `no` / `no`|

Window, transition and animator scales are set to 0 and the screen kept awake after
every boot (`device.tune`).

## Screen text goes in string resources

New screen text is a resource in `res/values/strings.xml` (plain `key`, themed
`key__t`), never a literal in Kotlin. The guide, naming and plurals are in
`docs/STRINGS.md`. Review changed copy in both wording modes and verify the running
screen; resource-contract tests cover XML escaping and placeholders.

## One emulator, shared between sessions

Parallel sessions all use `emulator-5554`, and an install, `pm clear` or uninstall
from one kills another's suite part way through. Take the lock before touching it:
the directory `.tmp/emulator.lock` under the **main checkout** (worktrees share it,
so a relative `.tmp` inside a worktree is not the same lock). Run anything that
touches the emulator through the helper, which takes the lock, records the holder,
and releases it however the run ends:

```bash
python3 tools/device.py lock -- adb -s emulator-5554 install -r app.apk
python3 tools/device.py lock --label "my run" -- bash -c 'ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedFossDebugAndroidTest'
```

The exit code is the command's. The helper pins `ANDROID_SERIAL` to an emulator when
it is unset and refuses one that names a phone. `tools/gate.py` takes the same lock
itself.

The lock heals itself: the holder writes `owner.json` (pid, host, label, start
time) and touches it every 30s, and a waiter takes over when that pid is gone from
this host or the owner hasn't beaten for 10 minutes. A bare
`mkdir .tmp/emulator.lock` records no pid, so it is only taken over at 30 minutes
old: prefer the helper. `python3 tools/test_device_lock.py` checks the takeover rules.

## Instrumented tests never touch the owner's phone

The suite calls `pm clear`, deletes rows from the app's database, and uninstalls
the app afterwards. Pointed at the owner's S25 Ultra (`R5GL14GXV3J`) it destroys
real training history; on 2026-10-27 an unpinned Gradle run did exactly that and
the data was restored from backup.

`tools/gate.py` resolves an emulator serial itself and pins `ANDROID_SERIAL`; a
physical device is used only when named with `--serial`. Any manual Gradle
invocation must pin it by hand:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedFossDebugAndroidTest
```

Installing a build on the phone (`:app:installFossDebug`) is safe and is how visual
changes get verified.
