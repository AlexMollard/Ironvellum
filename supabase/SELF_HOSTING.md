# Self-hosting / bring your own backend

Ironvellum's cloud is optional. You can use your own managed Supabase project
or run Supabase yourself with Docker. Configure it under **Settings → Advanced
→ Use my own backend**. Training then syncs to that project, not the shared
Ironvellum backend. No custom Android build is required.

This guide describes the current schema **33**. The app requires **31** for
core sync; **32** adds friend-safe workout cards and **33** adds rolling
seven-day workout-XP rankings.

## 1. Create and secure a Supabase project

**Managed (easiest):** create a project at
[supabase.com](https://supabase.com/dashboard). A free project can be enough
for personal use, but may pause after a week of inactivity and has no automatic
database backups. Keep an independent file backup.

**Docker:** follow Supabase's
[self-hosting guide](https://supabase.com/docs/guides/self-hosting/docker).
Ironvellum needs Supabase Auth, PostgREST and Postgres, not just a bare Postgres
database. Follow the upstream instructions for generating secrets, securing
Studio and configuring the public URLs before exposing the stack.

Use an **HTTPS API URL reachable from your phone** with a trusted certificate,
such as `https://supabase.example.org`. Do not enter a Studio-only address,
database connection string or `localhost`: on a phone, `localhost` means the
phone itself. A default Docker HTTP endpoint is not a production-ready app URL.

## 2. Install the schema on a fresh project

From the Ironvellum repository root, apply
[`migrations/0001_baseline.sql`](migrations/0001_baseline.sql) as the database
administrator. You can paste the file into Supabase Studio's SQL editor.
Alternatively, with `psql` installed, use your project's database connection
string (not its API URL):

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/migrations/0001_baseline.sql
```

In PowerShell, use `$env:DATABASE_URL` instead of `$DATABASE_URL`. Get the
connection string from your project's connection settings; if the direct
managed endpoint is not reachable over IPv6, use its session pooler connection.
Keep database passwords out of source control and shared logs.

The baseline installs tables, row-level security, functions and the
`auth.users` signup trigger that creates app profiles. Apply it **before**
creating app accounts. It is idempotent on the same schema, but **not a
universal upgrade migration for older installations**.

Verify in the SQL editor:

```sql
select public.schema_version();  -- 34
```

**Never run [`reset.sql`](reset.sql) on a project holding real data.** It
deletes every account and every app table. It is only for disposable projects
and the local SQL test harness; it is not an upgrade step.

### Upgrading an existing project

Back up the database first, then read `public.schema_version()` and the
prerequisites in [`hosted/`](hosted/). These in-place patches also apply to
self-hosted Supabase. Apply the applicable patches in chronological order,
not every historical patch on top of a current baseline.

- **Schema 31:** apply `2026-10-28-web-snapshot-hardening.sql` if it is not
  already installed (it does not bump the beacon), then
  `2026-10-29-feed-card.sql` for schema 32.
- **Schema 32:** apply `2026-11-12-workout-xp.sql` for schema 33. It requires
  the feed-card patch first and preserves accounts and workouts.
- **Schema 33:** apply `2026-11-17-hardening.sql` for schema 34. It repairs
  invalid session clocks/XP, preserves existing oversized backups while
  bounding new writes, and retains departed-circle bonuses for collection.
- **Earlier schemas:** review the earlier patches and their prerequisites
  before proceeding. Do not assume that re-running the baseline upgrades an
  old table shape or that the latest patch includes every earlier change.

Run each applicable patch with `ON_ERROR_STOP=1` or in the SQL editor, and
check the beacon afterward. A missing schema-33 view produces an explicit
error for recent rankings; lifetime rankings and core sync still work on
supported older backends.

## 3. Configure email authentication

Enable email/password signup in Supabase Auth. The app supports either
immediate sign-in or email confirmation. If confirmation is enabled, configure
SMTP and email delivery for your deployment and test the confirmation flow;
creating the SQL schema alone does not configure email.

Custom backends use **email and password**. The shipped app does not enable
Google sign-in on a custom backend, even if that project has a Google provider.
Accounts and friendships belong to each backend separately; switching does
not migrate an account or its social relationships.

## 4. Connect the app

Copy the **Project/API URL** and **publishable key** from your project. A
legacy public `anon` key is also supported. For Docker, use the public URL and
client key configured by your Supabase deployment. **Never use a secret or
`service_role` key in the app or web viewer**: those bypass row-level security.

1. Open **Settings → Advanced → Use my own backend**.
2. Enter the URL and public key, then press **Test**.
3. Expect **Backend ready** with schema 34. **Save** is enabled only for the
   tested URL/key pair on a supported schema.
4. Press **Save** and confirm **Switch**. You are signed out of the old
   backend; your local training stays on the phone.
5. Create an account or sign in on your own backend, then allow sync to finish.
   Completed sessions are re-uploaded to the selected backend. Switching back
   uses **Settings → Advanced → Use shared cloud** and signs you out again.

Switching does not erase data already uploaded to the previous backend.
Delete that cloud account separately if you want its remote data removed.

The URL and public key are stored in private app preferences, not in exported
training archives. Raw Health Connect data, measurements, private notes and
progress photos are not synced to either backend. The owner-only web snapshot
can contain strength estimates derived from body weight. See the
[privacy policy](../PRIVACY.md) for the complete upload boundary.

## 5. Verify and troubleshoot

The app's **Test** probes the public `schema_version()` RPC. It checks
reachability, the key and the beacon, not signup, every table or a complete sync.
Before relying on your deployment, create a disposable account, sync a completed
workout, and check it in the [web viewer](../web/README.md) using the same backend.
If you use manual cloud archives, also test backing up and restoring on a
disposable install. Do not clear or uninstall your real training app to test.

| Test result | Check |
|---|---|
| Could not reach the backend | URL, DNS, TLS certificate, network and gateway; resume a paused managed project |
| Key refused | Copy the project's public publishable/anon key, not a secret key |
| Migrations not applied | Install the schema in the same project the API URL points to |
| Schema out of date | Follow the applicable upgrade patches; core sync needs at least 31 |

For contributors, `python3 tools/gate.py --backend` runs the full local gate
plus [`test/assert_all.sql`](test/assert_all.sql) against disposable Postgres.
It checks SQL grants, RLS, signup triggers, patch replay and the reset round
trip using a Supabase stub. It does **not** deploy Supabase or prove real
GoTrue/PostgREST, email delivery or your server's TLS configuration.
