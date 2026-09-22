# Ironvellum — standing rules

## No automatic git actions

Never run `git commit`, `git push`, `git tag`, `git merge`, `git rebase`, or any
other history- or remote-mutating command on the owner's behalf. Make the edits,
report what changed, and print the command for the owner to run. Read-only
inspection (`status`, `diff`, `log`, `show`) is fine.

This is not a style preference. Pushes trigger billed work and put code on a
remote the owner has not reviewed; both are the owner's decision to make, every
time.

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

It takes about 75 seconds on this machine.

## Instrumented tests never touch the owner's phone

The suite calls `pm clear`, deletes rows from the app's own database, and
uninstalls the app afterwards. Pointed at the owner's S25 Ultra (`R5GL14GXV3J`)
it destroys real training history.

`tools/gate.py` resolves an emulator serial itself and pins `ANDROID_SERIAL`; a
physical device is only used when named explicitly with `--serial`. Any manual
Gradle invocation must pin it by hand:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Installing a build on the phone (`:app:installDebug`) is safe and is how visual
changes get verified.
