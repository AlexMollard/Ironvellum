# What Ironvellum costs

Updated monthly. All amounts USD. Invoices: Supabase dashboard → Billing.

## Current state

No donations have been received yet, and no months have been recorded. The
table below starts filling in the month the shared cloud moves to Supabase Pro.

Until then the shared cloud runs on the Supabase free tier, which is a stopgap,
not a promise: free projects pause after a week of inactivity and have no
backups. That is the wrong place for someone's only restore copy, which is why
the target below exists.

## The bill

Source: https://supabase.com/pricing, read 2026-09-23.

| Line | Cost | Notes |
|---|---|---|
| Supabase Pro | $25/month | Includes $10 compute credit, which covers one Micro instance (1 GB RAM, 200 pooler connections) |
| Database disk above 8 GB | $0.125/GB/month | Backups are capped at 8 MiB each (`0013_cloud_archives.sql:31`) |
| Egress above 250 GB | $0.09/GB | Uploads are ingress and free. Egress is restores and feed/board reads |
| Monthly active users above 100k | $0.00325 each | Not a realistic concern |
| Google Play registration | $25 once | Only if the Play build ships |
| F-Droid, GitHub, public CI | $0 | |

Monthly cost: `25 + max(0, diskGB - 8) × 0.125 + max(0, egressGB - 250) × 0.09`.

## The ledger

| Month | Hosting | Other | Donations in | Platform fees | Net | Reserve | Maintainer |
|---|---|---|---|---|---|---|---|
| — | no months recorded yet | | | | | | |

Reserve target: 3 months of hosting (75.00). Surplus beyond the reserve may be
paid to the maintainer and is always shown in its own column. Nothing is taken
until the reserve is full.

Lifters on the shared cloud: N · Database disk: X GB of 8 · Egress: Y GB of 250
(These figures are filled in from the Supabase dashboard each month. The
README's earlier 0.5 MB-per-lifter estimate is an assumption, not a
measurement; a measured per-lifter figure replaces it as soon as real archives
exist to measure.)

## Donations

GitHub Sponsors and Liberapay are the only channels; the links live on the
repository page and in the foss build's Support screen. The Play build carries
no donation links, per Google Play policy. Open Collective was rejected: its
fiscal hosts take roughly 4–15%, and this ledger already does the transparency
job.

## The continuity promise

A donor-funded service has to say what happens if the money stops:

1. Every lifter can always EXPORT ARCHIVE, offline, with no account needed.
2. If this ledger runs a deficit for three consecutive months, the shared
   cloud gets 60 days' notice through the Support screen and the README, then
   becomes read-only (restore still works), then shuts down.
3. Bring-your-own-backend (see `docs/open-source/03`) keeps every cloud
   feature working for anyone who wants it afterwards.
