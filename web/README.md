# Ironvellum web viewer

The viewer is read-only. Sign in with the same backend and account as the app; workout and derived statistics are uploaded by the phone. The shared backend comes from the repository's public `cloud-defaults.properties`; a self-hosted backend can be entered on the sign-in page. Backend and appearance preferences stay in browser storage. No analytics or external fonts are used.

Google sign-in is available on the shared backend once its Google provider and OAuth client are configured, and the deployed viewer URL is allowed in Supabase's redirect URLs. Self-hosted backends use email and password.

Signing out clears this browser's session without signing the phone out. Backends older than schema 30 can still show synced workouts and sets, but need updating before phone snapshots and imported workouts are available.

Use Node 22.12 or newer: `npm ci`, `npm run dev`, `npm test`, and `npm run build`. In the development server, `?fixture=1` opens the offline example; fixtures are not included in production builds.

Regenerate the app-derived assets with `./gradlew :app:testFossDebugUnitTest --tests "*WebAssetsGoldenTest" -Dgolden.update=true`, then run `python3 tools/bodymap_web.py` and `python3 tools/web_art.py`. The art attribution is included in the viewer.

`art/` remains the only checked-in SVG source. `web/public/art/` is ignored,
generated build output: `npm run dev` and `npm run build` prepare it from the
pinned submodule. Do not add these copies to git. Vite packages them into `dist/`.

Deploy from GitHub's **Actions → Deploy website → Run workflow** on `main`,
or run `gh workflow run pages.yml --ref main`. This is manual-only; pushing
does not deploy. The workflow tests and builds `web/`, then publishes only
`web/dist` using a Pages artifact retained for one day. It uploads no test
reports or dependency caches.
