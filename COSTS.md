# What Ironvellum costs

Updated monthly. All amounts USD. Invoices: Supabase dashboard → Billing.

## Current state

Personal training and the personal file connector incur no Ironvellum hosting charge. The connector runs on the lifter's computer. Initial dependency downloads need internet access, and the chosen AI provider sets any separate pricing. Running the optional backend on a personal computer uses that computer's storage, power and network. [Local setup](tools/self_host/README.md).

No donations, invoices or monthly totals have been recorded in this repository.
The last recorded hosting plan was the Supabase free tier; this document is not
evidence of the project's current billing dashboard or donation balances.

Free projects may pause after a week of inactivity and have no automatic
database backups. Keep a separate file backup rather than relying on cloud
sync as your only restore copy. Pro remains the budget target below.

## The bill

Source: [Supabase pricing](https://supabase.com/pricing), checked 2026-11-17.

| Line | Cost | Notes |
|---|---|---|
| Supabase Pro | $25/month | Includes $10 compute credit, which covers one Micro instance (1 GB RAM, 200 pooler connections) |
| Database disk above 8 GB | $0.125/GB/month | Backups are capped at 8 MiB each (`cloud_archives_size_cap` in `0001_baseline.sql`) |
| Egress above 250 GB | $0.09/GB | Uploads are ingress and free. Egress is restores and feed/board reads |
| Monthly active users above 100k | $0.00325 each | Not a realistic concern |
| Google Play registration | $25 once | Only if the Play build ships |
| F-Droid, GitHub, public CI | $0 | |

Budget estimate for one Micro project, before tax or optional add-ons:
`25 + max(0, diskGB - 8) × 0.125 + max(0, egressGB - 250) × 0.09`.
Additional projects add compute charges; this is not a complete invoice formula.

## The ledger

| Month | Hosting | Other | Donations in | Platform fees | Net | Reserve | Maintainer |
|---|---|---|---|---|---|---|---|
| — | no months recorded yet | | | | | | |

Reserve target: 3 months of hosting (75.00). Surplus beyond the reserve may be
paid to the maintainer and is always shown in its own column. Nothing is taken
until the reserve is full.

Active lifters, database size, egress and a measured per-lifter storage figure
are not recorded yet. Fill them from the dashboard and actual archives when
publishing monthly figures; do not substitute the old 0.5 MB estimate.

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
3. [Bring your own backend](supabase/SELF_HOSTING.md) keeps cloud features
   available on an independently operated project. Accounts and social
   relationships do not migrate automatically between backends.
