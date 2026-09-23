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

It takes about 75 seconds on this machine.

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
