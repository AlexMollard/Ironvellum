# Ironvellum web viewer

The viewer is read-only. Sign in with the same backend and account as the app; workout and derived statistics are uploaded by the phone. The shared backend comes from the repository's public `cloud-defaults.properties`; a self-hosted backend can be entered on the sign-in page. Backend and appearance preferences stay in browser storage. No analytics or external fonts are used.

Google sign-in is off by default (email sign-in is the supported route; the Google provider is unverified). Build with `VITE_GOOGLE_SIGNIN=true` to show it on the shared backend, once its Google provider and OAuth client are configured and the deployed viewer URL is allowed in Supabase's redirect URLs. It uses the PKCE flow. Self-hosted backends use email and password.

Signing out clears this browser's session without signing the phone out. Backends older than schema 30 can still show synced workouts and sets, but need updating before phone snapshots and imported workouts are available.

Use Node 22.12 or newer: `npm ci`, `npm run dev`, `npm test`, and `npm run build`. In the development server, `?fixture=1` opens the signed-in screens offline from the synthetic "Fictional lifter" fixtures (plus `web/dev/screenshotOverlay.js`, which adds records, deeds and a streak so every card has content), `?fixture=signin` opens the sign-in page without contacting the backend, and `&theme=light` or `&theme=dark` (default dark) picks the theme. These entry points exist only where `import.meta.env.DEV` is true, so a production build contains none of the fixtures, the overlay or the entry code; `test/productionBuild.test.js` builds and checks that. The text is set in Chakra Petch, self-hosted from the pinned `@fontsource/chakra-petch` package (SIL OFL, licence in `public/licenses/`), four weights, latin only, because the page's content security policy allows no external font or stylesheet host.

To check a page against the real policy, build a fixture bundle and serve it: `NODE_ENV=development npx vite build --mode fixture --outDir <dir>`, then `npx vite preview --outDir <dir>`. (`npm run dev` relaxes `style-src` so the injected stylesheets load; builds never do.)

Regenerate the app-derived assets with `./gradlew :app:testFossDebugUnitTest --tests "*WebAssetsGoldenTest" -Dgolden.update=true`, then run `python3 tools/bodymap_web.py` and `python3 tools/web_art.py`. The art attribution is included in the viewer.

`art/` remains the only checked-in SVG source. `web/public/art/` is ignored,
generated build output: `npm run dev` and `npm run build` prepare it from the
pinned submodule. Do not add these copies to git. Vite packages them into `dist/`.

Deploy from GitHub's **Actions → Deploy website → Run workflow** on `main`,
or run `gh workflow run pages.yml --ref main`. This is manual-only; pushing
does not deploy. The workflow's `build` job (read-only token) tests and builds `web/` and uploads
`web/dist` as a Pages artifact retained for one day; a separate `deploy` job
(the only one with `pages: write`) publishes it. It uploads no test
reports or dependency caches.

## Data contract for design work

Keep the data/authentication layer intact while redesigning `src/screens/`,
`src/components/` and `src/theme.css`. Screens receive `{source, snapshot, router}`:

- `source.schemaVersion()` reads the backend beacon. Schema 30 supplies workout
  rows and private details; schema 31 is required by the phone snapshot uploader.
- `source.listWorkouts()` reads both synced and imported workouts, scoped to the
  signed-in owner and paged beyond the server's 1,000-row limit.
- `source.getSets(workout)` rechecks ownership, merges private kind/RPE/superset
  details, and preserves holds, activities, grades and repeated exercise blocks.
- `source.getSnapshot()` supplies phone-computed stats or `null`. Missing derived
  figures must stay unknown (`—`), not become zero or browser-computed estimates.
- `workoutPath(workout)` identifies details by origin and cloud ID:
  `#/workouts/<synced|imported>/<cloud-id>`. Never route by `localId` alone.
- `snapshotForWorkout(snapshot, workout)` checks the snapshot entry's origin
  before exposing derived figures for its local ID.

The phone owns the arithmetic in `WebSnapshotBuilder.kt`; the browser displays
it. Snapshot time and zone determine trend windows, stale-data notices and
calendar dates. Workouts use their completion date, falling back to start.
Authentication belongs in `src/auth.js`; signing out must stay local to this
browser and clear private page state.

Use `npm run dev` with `?fixture=1` for offline design work. The generated
`test/fixtures/snapshot-v1.json` and `rows.json` cover imported workouts,
variations, holds and activities without exposing real account data. Follow
links from the example workout list for valid detail URLs. Fixture mode is
development-only and must not become a production fallback.

Theme tokens, vocabulary, catalogues and body geometry in `src/generated/`
come from the app. Regenerate them rather than editing their output. Exercise
art stays in the pinned `art/` submodule; browser copies remain ignored output.
