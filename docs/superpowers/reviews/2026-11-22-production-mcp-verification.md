# Production MCP v1: verified scope and decisions

Verified source: `1d512a6a`. Evidence recorded 2026-11-21 UTC; the design and
implementation plan retain their original 2026-11-22 filenames.

## Delivered scope

The standalone production service exposes seven tools: `list_workouts`,
`get_workout`, `get_progress`, `list_saved_workouts`, `search_movements`,
`create_workout_draft` and `get_workout_draft`. Its four independently consented
scopes are `training:read`, `progress:read`, `savedworkouts:read` and
`workoutdrafts:create`. AI clients receive opaque, narrowly scoped credentials,
not the person's ordinary Supabase session. Revocation is checked on each call.

Browser consent and connection management accompany Account → AI connections
and Train → Workout drafts in the app. A person reviews and saves or rejects a
draft. Approval precedes the atomic local preset/receipt commit; acknowledgement
follows it. Approved recovery remains accessible beyond the first 50 drafts.
Connection management does not wait for failed receipt acknowledgements.

Backend schema 36 is additive; existing core sync still requires 31. Room 50
adds the local import receipt. The full 584-movement manifest fingerprint is
`6b8bea45f8781884088d99fe8fc76c0df4c5755a8c2d1e9914629710bfa18a66`.

## Review and regression evidence

All five implementation tasks received scoped independent review. The final
whole-change review identified nine findings: Android client/config binding,
plural-resource lint, older-draft recovery, acknowledgements delaying Disconnect,
unused registrations, two preset selectors, the Veil return test, encoded path
aliases, and IPv6 canonicalization. All are addressed; no Critical or Important
finding remains parked.

The final client-return check alone did not cover background Android Auth work.
The resolved Supabase Android Auth 3.9.0 session-key mapping can collide for
distinct origins, and its default process observer survives SDK close. A retired
client could restore a successor's session and refresh it at the old origin.
No actual disclosure was observed. The final fix serializes startup/switch
construction, makes the retiring generation unavailable, closes and joins old
Auth work and removes its owned lifecycle observer before successor storage
access. The existing storage key format is preserved.

The original reviewer independently confirmed the guard at `1d512a6a`, including
SDK lifecycle semantics, account ordering and regression assertions. Production
factory tests use private persistent preferences, the installed SDK's colliding
keys, a real Android LifecycleRegistry and synthetic refresh transports. Both
already-initialized and paused-construction/read cases require old Auth completion
and zero old observers before successor persistence. Only the successor receives
the positive foreground refresh. The original-code retirement assertion failed;
removing foreground refresh also failed. Restored integration/startup runs passed
11 distinct cases without skips. All transports and identities were synthetic.

The unchanged full gate at this commit passed in 8m43s: 2,923 unit cases (one
pre-existing optional exporter assumption skip), 920 device cases without skips,
380 web tests, zero failures, no release-lint issues and a successful web build.
The separate complete service suite passed 38 tests; native PostgreSQL passed
27 groups. Earlier focused SQL verification passed 21 PGlite groups on unchanged
SQL. Gate budget/count checks were retained. Evidence paths and exact counts are
in the release checklist. Test runs touched only the locked emulator, never the
owner's phone. The earlier 8m40s gate at `48367d25` is not used to certify this fix.

## Decisions and their costs, in order

1. Use a standalone official Python MCP/OAuth service. It needs a container-capable
   public HTTPS host rather than only static hosting.
2. Draft only built-in REPS movements with fixed sets, reps and load initially.
   Timed/custom movements, complex schemes and explicit preparation routines
   cannot be drafted in v1.
3. Approve online before local saving, then acknowledge. The first Save needs
   connectivity.
4. Guarantee import once per local database. Two devices already reviewing may
   each save one copy; existing saved workouts are not globally synchronized.
5. Treat SQL's content hash as opaque while strictly checking the typed envelope
   and revision/hash identity. Clients trust the authenticated hash rather than
   reproduce PostgreSQL JSONB serialization.
6. Bound authorization admission to 20/client/minute and 120/service/minute.
   Busy legitimate clients may need to back off.
7. Use the official low-level server interface to preserve strict original tool
   arguments. Registration and its tests require manual maintenance.
8. Bind resource, catalogue and persistent key verification in one atomic setup.
   This adds a private gateway setup contract and operator step.
9. Generate the complete Seed catalogue, filtering REPS only at draft acceptance.
   The static manifest is larger without broadening draft capability.
10. Reject AI imports above 5,000 local movement rows, using one overflow sentinel
    in both validation passes. Manual training remains available; AI import needs
    a smaller catalogue or a reviewed redesign.
11. Add owner-scoped draft paging and Load older, preserving the legacy list RPC.
    An additional API and UI continuation need maintenance.
12. Retry receipts in batches of 20 independently from connection management.
    Large backlogs need further bounded passes.
13. Cap stored clients at 10,000 and prune bounded batches of never-consented,
    unused registrations aged at least 24 hours. Abandoned clients may register
    again; retained-client capacity growth needs operator review.
14. Keep the unresolved credential path blocking within the same final fix wave.
    Actual lifecycle/storage proof and a fresh full gate took additional time.

## Deployment boundary

The pinned Linux base digest and 29 wheel hashes were verified. A container
builder was unavailable, so no image build/runtime or OS image scan is claimed.
No approved public host exists yet. Hosted Auth/PostgREST, public TLS, real Google
login and named AI clients still need deployment verification. The package is not
a live public service. No hosted migration, real-user grant, paid host, release
tag or GitHub release was created.

The optional debug-only lint findings in unchanged tests/notification preview,
translation-length advisories and existing web chunk advisory remain recorded
maintenance observations. The required release lint and full gate are green.

See [deployment instructions](../../../server/production_mcp/DEPLOYMENT.md),
[design](../specs/2026-11-22-production-mcp-design.md), and
[implementation plan](../plans/2026-11-22-production-mcp.md).
