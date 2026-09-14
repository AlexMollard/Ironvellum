# Monarch

Monarch (SoloFit) is an Android fitness and training tracker: workout logging,
body measurements, Health Connect sync, skill progression, and optional social
features backed by Supabase.

- Kotlin 2.4 + Jetpack Compose, Room (local), Supabase (optional cloud)
- Package `com.monarch.app`; see `GOAL.md` for product direction
- Privacy: [PRIVACY.md](PRIVACY.md) — body measurements never leave the device
- Shipping: [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md)
- Play Data Safety answers: [docs/PLAY_DATA_SAFETY.md](docs/PLAY_DATA_SAFETY.md)

Build: `.\gradlew.bat :app:assembleRelease`. Local signing config lives in
gitignored `local.properties`.
