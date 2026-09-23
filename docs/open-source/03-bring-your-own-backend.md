# 03: Bring your own backend

**Goal:** a lifter can point Ironvellum at their own Supabase project, hosted
by Supabase or self-hosted with Docker, from inside the app. Their training
then never reaches the shared instance, and costs the maintainer nothing.

**Done when:** a lifter can:

1. enter a URL and publishable key in Settings → CLOUD;
2. see "backend ready" or a precise reason it isn't (unreachable, wrong key,
   schema out of date);
3. sign in and back up to that project;
4. switch back to the default without restarting the app.

After a switch, every session re-uploads to the new backend.

## Facts this rests on

- `Cloud` is a process-wide `object`. `configured` and `googleConfigured` are
  `val`s computed once from `BuildConfig` (`Cloud.kt:20-21`), and
  `client()` builds a single `SupabaseClient` under `synchronized`
  (`:26-39`). Nothing can rebuild it today.
- Every repository goes through `Cloud`:
  - `Cloud.requireConfigured` is used 15 times in `CloudSync.kt` and in
    `AccountRepository.kt:37`;
  - `Cloud.client()` is called directly at `AccountRepository.kt:123, 337, 344`;
  - `Cloud.configured` is read into UI state at `AccountScreen.kt:108`,
    `FeedScreen.kt:95` and `LeaderboardScreen.kt:73`.
- App preferences live as columns on the Room `profile` table
  (`Entities.kt:113-121`). **Do not store the backend there.** The profile
  syncs to the very backend it would describe, and `importArchive` replays it.
- The push watermark is the `sync_state` table. The existing reset seam is
  `CloudSync.forgetPushedState()` (`CloudSync.kt:55` →
  `Repository.clearPushWatermark()`, `:1131`), already used by erase
  (`AccountScreen.kt:231`) and "reupload everything" (`:282`). Switch backends
  without calling it and nothing ever re-uploads (skill
  `client-watermark-server-wipe`).
- The supabase-kt session is persisted by the `Auth` plugin. A session from
  the old project is meaningless against the new one.

## Design

`Cloud` keeps its role as the single entry point. Only its source of truth
changes:

```kotlin
data class CloudConfig(val url: String, val key: String, val isDefault: Boolean)

object Cloud {
    fun init(context: Context)              // called from IronvellumApp.onCreate
    val config: StateFlow<CloudConfig?>     // null = not configured
    val configured: Boolean get() = config.value != null
    val googleConfigured: Boolean get() = config.value?.isDefault == true &&
        BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    suspend fun reconfigure(next: CloudConfig?)  // null = back to BuildConfig default
    fun client(): SupabaseClient            // unchanged contract
    val requireConfigured: Result<SupabaseClient>  // unchanged contract
}
```

- **Storage:** a private `SharedPreferences` file, `cloud_config`, holding
  `url` and `key`. It is per install and never exported or synced. It holds no
  secret: a publishable key is public by definition. Rejected alternative: a
  Room table. It would need a migration and a migration test, for two strings
  that must deliberately stay out of the export archive.
- **Resolution order:** an override in prefs, then `BuildConfig`
  (`SUPABASE_URL` / `SUPABASE_KEY`), then not configured.
- **Google sign-in** only works against the default backend. The OAuth web
  client ID belongs to the maintainer's Google project and Supabase config, so
  custom backends offer email sign-in only.
- **UI readers:** the three screens that snapshot `Cloud.configured` into
  state instead collect `Cloud.config`. Then switching backends redraws
  Allies, Feed and Leaderboard without a restart.

### `reconfigure(next)`, in this order

1. If signed in, try `client().auth.signOut()` against the old project, then
   always call `client().auth.clearSession()`, so the local session goes even
   when the old server is unreachable. Check both names against supabase-kt
   3.8.0's `Auth` interface (read the sources jar) before writing the call.
2. `cloudSync.forgetPushedState()`.
3. Under the existing `synchronized(this)`, set `instance = null` and write
   the prefs.
4. Emit the new `config`.

Order matters: signing out after swapping the client would attempt the old
session against the new project.

### Schema check

A BYO project must have run `supabase/migrations/0001`-`0013` (and later
ones). Today, a missing table only surfaces as `PGRST205` deep inside a
feature (`Cloud.kt:63-67`).

- New migration `0014_schema_version.sql` (idempotent):
  `create or replace function schema_version() returns int language sql stable as $$ select 14 $$;`
  with `grant execute ... to anon, authenticated`. Every future migration
  bumps the literal.
- `Cloud.probe(config): ProbeResult` calls `rpc("schema_version")` with a
  throwaway client built from the candidate config, and returns one of:
  - `Ready(version)`;
  - `Outdated(have, need)`;
  - `NoSchema` (`PGRST202`);
  - `BadKey` (401);
  - `Unreachable`.
- Add a `supabase/test/assert_all.sql` assertion that `anon` can execute
  `schema_version()` and gets the current number.

### UI

A new `InkPanel` "CLOUD" in `SettingsScreen.kt`, between DATA (ends `:689`)
and DIAGNOSTICS (`:693`):

- Shows the current backend: "Ironvellum shared cloud", or the custom host.
- USE MY OWN BACKEND opens two `OutlinedTextField`s (URL, publishable key) and
  a TEST button that runs `probe` and shows the result in plain words. SAVE is
  enabled only on `Ready`.
- USE SHARED CLOUD is shown when an override is active.
- Before switching, a confirmation dialog: "You'll be signed out. Your
  training stays on this phone and uploads to the new backend when you sign
  in there."

## Self-hosting guide (code)

`supabase/SELF_HOSTING.md`:

1. Create a Supabase project (the free tier is fine for one person), or run
   the Docker stack (https://supabase.com/docs/guides/self-hosting/docker).
2. Apply every file in `supabase/migrations/` in numeric order, with psql or
   the SQL editor.
3. Copy the project URL and publishable key into the app.
4. Warn that free projects pause after a week of inactivity, and that a paused
   backend reads as "Unreachable" until it is resumed in the dashboard.

## Checks

- **Unit:** resolution order (prefs over BuildConfig over none), and
  `googleConfigured` false on a custom backend.
- **Instrumented** (emulator only): after `reconfigure`, `sync_state` is empty
  and `Cloud.client()` returns a new instance.
  - Mutation-prove it: remove the `forgetPushedState()` call and the test must
    fail (skill `gradle-mutation-proof-regression-test`).
- **Manual:** point the emulator build at a throwaway free project, sign up,
  back up, and confirm the `cloud_archives` row exists there and not on the
  shared project.
- `python3 tools/gate.py --backend` passes with `0014`.
