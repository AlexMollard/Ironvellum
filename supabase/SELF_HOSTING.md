# Self-hosting / bring your own backend

Ironvellum's cloud is optional, and it does not have to be ours. Settings →
CLOUD accepts any Supabase project you control: a hosted free-tier project or
a fully self-hosted Docker stack. Once a custom backend is configured, your
training syncs to YOUR project and never to the shared Ironvellum instance.

## 1. Create a project

**Hosted (easiest, free tier is fine for one person):** create a project at
[supabase.com](https://supabase.com/dashboard).

**Self-hosted:** follow the official Docker guide at
https://supabase.com/docs/guides/self-hosting/docker — it stands up the whole
stack (Postgres, GoTrue auth, PostgREST, Studio) behind a single host.

## 2. Apply every migration, in order

The app expects the schema built by `supabase/migrations/`, applied in numeric
order (`0001_init.sql` → `0015_function_grants.sql`, and any later files). With
psql:

```bash
for f in supabase/migrations/*.sql; do
  psql "$DATABASE_URL" -f "$f"
done
```

or paste each file, in order, into the project's SQL editor. `0014` adds the
`schema_version()` beacon the app uses to check your project is ready; if it
is missing, the app reports "migrations have not been applied" instead of
sending data into a half-built schema.

Afterwards you can verify with the SQL editor:

```sql
select schema_version();  -- should return the newest migration number
```

## 3. Point the app at it

Copy two values from the project dashboard (Project Settings → API, or your
Docker `.env` for self-hosting):

- the **Project URL** (`https://<your-project>.supabase.co`)
- the **publishable ("anon") key** — the public one, never the service secret

Then in Ironvellum: Settings → **CLOUD** → **USE MY OWN BACKEND**, paste both,
press **TEST** (it should say "Backend ready"), then **SAVE**. You'll be
signed out of the shared cloud; your training stays on this phone and uploads
to your project once you sign in there (email sign-in — Google sign-in is tied
to the shared backend). Switching back is **USE SHARED CLOUD**, and every
session re-uploads to whichever backend you land on.

## 4. Free projects pause — that reads as "Unreachable"

A hosted free-tier project pauses after about a week of inactivity. While it
is paused, the app's TEST (and every sync) reads as **Unreachable**. Resume
the project in the Supabase dashboard and it answers again; nothing is lost.

## Notes

- The publishable key is public by definition — storing it in the app is safe.
  It is kept in the app's private preferences, is never exported with your
  data archive, and never leaves your device except to your own project.
- Body measurements never sync to any backend, yours or ours — the schema has
  no table for them, by design.
- The SQL assertions the project tests its own backend with live in
  `supabase/test/assert_all.sql` and run against a throwaway Postgres via
  `python tools/gate.py --backend`.
