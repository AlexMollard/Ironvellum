# Monarch

Monarch (SoloFit) is an Android fitness and training tracker: workout logging,
body measurements, Health Connect sync, skill progression, and optional social
features backed by Supabase.

- Kotlin 2.4 + Jetpack Compose, Room (local), Supabase (optional cloud)
- Package `com.monarch.app`; see `GOAL.md` for product direction
- Privacy: [PRIVACY.md](PRIVACY.md) — body measurements never leave the device
- Shipping: [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md)
- Play Data Safety answers: [docs/PLAY_DATA_SAFETY.md](docs/PLAY_DATA_SAFETY.md)
- Outstanding work and open decisions: [docs/TODO.md](docs/TODO.md)

## Checks, and what each one is for

| command | covers |
|---|---|
| `:app:testDebugUnitTest` | domain maths, the cloud wire format, crash journal, migration registry |
| `:app:connectedDebugAndroidTest` | Room migrations against real SQLite, navigation reachability, accessibility floors, and the three user journeys (workout loop, skill practice, preset auto-fill) |
| `:app:lintRelease` | release-variant lint; triage the SARIF report, not the HTML |
| `supabase/test/assert_all.sql` | what no Kotlin test can see: which tables have row security, who may execute which function, which columns a hunter may write, whether a feed row can pin itself |

## Developing without a phone attached

The instrumented suite and the UI flows run on a headless emulator, so nothing
here needs a USB cable:

```bash
python tools/device.py up        # boots the MonarchEmu AVD if nothing is attached
python tools/device.py install
python tools/device.py launch
python tools/device.py shot home # .tmp/shots/home.png
python tools/device.py labels    # visible text, for finding a tap target
```

`device.py` prefers a booted emulator over USB, so the same commands work
either way. Emulator screenshots are not pixel-comparable with a real device —
the software rasterizer differs and the ink seeds resolve per pixel size, so
compare within one target only.

The backend assertions need Docker rather than a device:

```bash
docker run -d --rm --name pg -e POSTGRES_PASSWORD=probe -p 5432:5432 postgres:16
psql -h localhost -U postgres -f supabase/test/supabase_stub.sql
for f in supabase/migrations/*.sql; do psql -h localhost -U postgres -v ON_ERROR_STOP=1 -f "$f"; done
psql -h localhost -U postgres -v ON_ERROR_STOP=1 -f supabase/test/assert_all.sql
```

Build: `.\gradlew.bat :app:assembleRelease`. Local signing config lives in
gitignored `local.properties`.
