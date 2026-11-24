# Ironvellum web viewer

Training history and statistics are read-only; social features support reactions,
comments and group actions. Sign in with the same backend and account as the app;
the phone uploads workouts and computes the derived training statistics. The shared
backend comes from the public `cloud-defaults.properties`; a custom backend can
be entered on the sign-in page. See [self-hosting](../supabase/SELF_HOSTING.md)
for setup. Backend and appearance preferences stay in browser storage. No
analytics or external fonts are used.

Google sign-in is off by default (email sign-in is the supported route; the Google provider is unverified). Build with `VITE_GOOGLE_SIGNIN=true` to show it on the shared backend, once its Google provider and OAuth client are configured and the deployed viewer URL is allowed in Supabase's redirect URLs. It uses the PKCE flow. Self-hosted backends use email and password.

Signing out clears this browser's session without signing the phone out. Backends older than schema 30 can still show synced workouts and sets, but need updating before phone snapshots and imported workouts are available.

Schema 31 supplies owner-only phone snapshots; 32 supplies friend-safe feed cards,
and 33 supplies rolling workout-XP standings. Core phone sync requires at least 31.
Social permissions still follow server-side profile, workout and relationship rules.

## Components on your computer

| Component | How it runs | Data source |
|---|---|---|
| Android training app | Android device or emulator | Local Room database |
| Web viewer | Local Node development server or static web host | Signed-in user's chosen Supabase backend |
| Optional cloud and social backend | Local Docker Compose stack or your managed project | Your backend's database and Auth |
| Optional hosted production MCP | Separate HTTPS deployment for compatible online AI clients | Permissions granted for synced data |

Android file sharing and workout import work offline after installation. The signed-in viewer uses the backend selected on its sign-in page. See [private backend setup](../tools/self_host/README.md) and [hosted MCP deployment](../server/production_mcp/DEPLOYMENT.md). The former personal PC connector is preserved on [archive-personal-connector](https://github.com/AlexMollard/Ironvellum/tree/archive-personal-connector).

## Local development

From the repository root, initialize the pinned art source and enter the viewer:

```bash
git submodule update --init art
cd web
npm ci
npm run dev
```

Run the following npm/Vite commands from `web/`; run the Gradle and Python
asset-generation commands from the repository root.

Use Node 22.12 or newer: `npm ci`, `npm run dev`, `npm test`, and `npm run build`. In the development server, `?fixture=1` opens the signed-in screens offline from the synthetic "Fictional lifter" fixtures (plus `web/dev/screenshotOverlay.js`, which adds records, deeds and a streak so every card has content), `?fixture=signin` opens the sign-in page without contacting the backend, and `&theme=light` or `&theme=dark` (default dark) picks the theme. These entry points exist only where `import.meta.env.DEV` is true, so a production build contains none of the fixtures, the overlay or the entry code; `test/productionBuild.test.js` builds and checks that. The text is set in Chakra Petch, self-hosted from the pinned `@fontsource/chakra-petch` package (SIL OFL, licence in `public/licenses/`), four weights, latin only, because the page's content security policy allows no external font or stylesheet host.

To check a page against the real policy, build a fixture bundle and serve it: `NODE_ENV=development npx vite build --mode fixture --outDir <dir>`, then `npx vite preview --outDir <dir>`. (`npm run dev` relaxes `style-src` so the injected stylesheets load; builds never do.)

Regenerate the app-derived assets with `./gradlew :app:testFossDebugUnitTest --tests "*WebAssetsGoldenTest" -Dgolden.update=true`, then run `python3 tools/bodymap_web.py` and `python3 tools/web_art.py`. The art attribution is included in the viewer.

`art/` remains the only checked-in SVG source. `web/public/art/` is ignored,
generated build output: `npm run dev` and `npm run build` prepare it from the
pinned submodule. Do not add these copies to git. Vite packages them into `dist/`.

## Public website and dashboard

The public landing page lives in `site/` at the repository root. Preview the
exact static files that will be published:

```bash
python tools/build_landing.py
python -m http.server 8843 --bind 127.0.0.1 --directory .tmp/landing
```

The bench demo runs in the browser without storage or network calls. It takes
10% off the selected load and rounds down to the 2.5 kg step, matching the
barbell branch in `SchemePlan.backoff`. Without JavaScript the example remains readable.

The exercise wall is generated from the Android app’s `own_*.paths` assets
at build time; it needs neither the art submodule nor a network connection.
It displays two SVG sheets at a time and stays still with reduced motion.

The product tour uses native radio controls to switch screens without
JavaScript. `site/screens/` contains uncropped, lossless WebP captures from
the disposable Android emulator, not store artwork or personal data. Its
three sample screens come from `ExerciseInfoSheetFitsTest.aboutKeepsFactsAndActionsReachableWithItsWhyAtTheCaps`,
`SetsSheetShotsTest.topAndBackoffsBench`, and `RecapShotsTest.fullWeekDark`.
Run those cases with the repository emulator lock and `shots=true`; use
`pullup-dark-about`, `sets_top_bench_a`, and `recap_full_dark_a` respectively.

### Publish both sites

From [Actions → Deploy website](https://github.com/AlexMollard/Ironvellum/actions/workflows/pages.yml),
select `main` and click **Run workflow**. There is no site selector. The workflow
tests and builds the dashboard, then publishes `web/dist` to the `ironvellum`
Cloudflare Pages project at `https://app.ironvellum.org/`. Only after Cloudflare
reports a production deployment does GitHub Pages publish `.tmp/landing` to
`https://ironvellum.org/`. If that second deployment fails, the updated dashboard
stays live; rerun the failed job to finish publishing the landing page.

One-time setup:

1. In Cloudflare Pages, check that `app.ironvellum.org` belongs to project
   `ironvellum` and its production branch is `main`. If Git integration also
   deploys on push, disable automatic branch deployments to avoid two publishers.
2. Under [GitHub Actions repository secrets](https://github.com/AlexMollard/Ironvellum/settings/secrets/actions),
   set `CLOUDFLARE_ACCOUNT_ID` and `CLOUDFLARE_API_TOKEN`. Use a token with
   **Account → Cloudflare Pages → Edit** restricted to the account hosting the
   project, not a Global API Key. See [Cloudflare's token instructions](https://developers.cloudflare.com/pages/how-to/use-direct-upload-with-continuous-integration/#generate-an-api-token).
3. If the dashboard already enables Google sign-in, set the GitHub Actions
   **variable** `VITE_GOOGLE_SIGNIN=true` to preserve that build setting.
   Email/password sign-in needs no extra variable. Keep existing Supabase and DNS settings.

Pushing to GitHub does not trigger this workflow. Only the landing page is
uploaded as a temporary GitHub Pages artifact (one day); the dashboard is
uploaded directly to Cloudflare.

## Clickable boxes

A row, card or cell that opens something is one link or button, not a box with a link inside it. Put `is-link` on the box and `stretch` on its one link or button (`link()` from `components/statCard.js` takes the class as its last argument): `.is-link .stretch::after` in `src/theme.css` covers the whole box, so the box is the target, hover and the focus ring, with one tab stop. Other controls inside the box (a copy button, reactions, a chip that links somewhere else) are given `z-index` above it by the same rule. A box that does nothing is drawn flat: no border, no hover (`.chip`, `.tile`, an empty calendar day, a rest day). `link()` leaves Ctrl, Cmd, Shift and middle clicks to the browser, so every link can open in a new tab; use it, not a button with `router.go`. Exercise names go through `liftPath()` so they link to the lift page wherever one exists. Touch targets are 44px at 720px and below (the last block of `theme.css`).

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
