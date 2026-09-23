# Ironvellum goes open source: roadmap

Ironvellum becomes free and open source. Donations pay for its upkeep. A public
ledger shows what the shared cloud costs and where every dollar goes. Anyone
who would rather not use the shared cloud can point the app at their own
Supabase project.

Written 2026-09-23 from the code as it stands. Every plan cites the files it
touches. Code steps are sized for one agent session each and are committed and
pushed as they land (`AGENTS.md`). Owner-only steps (consoles, credentials,
money, history rewrites, tags, releases, repository visibility) are marked
**OWNER** and come with the exact command.

## Decisions taken (2026-09-23)

| Question | Decision |
|---|---|
| Licence | **GPL-3.0-or-later.** Nobody can take the app closed and sell it |
| Distribution | **F-Droid + GitHub Releases first**, Google Play second |
| Cloud | **Free for everyone**, paid for by donations, with a public cost ledger |
| Maintainer cut | Allowed, but only out of surplus, and shown as its own line in the ledger |
| Donations | **GitHub Sponsors + Liberapay** |
| Hosting | **Supabase Pro** (US$25/month, which includes one Micro compute instance) |
| Self-hosting | **Bring your own backend**, as an in-app setting |

### The fork to watch

A shared instance only makes sense while donations roughly cover it. If the
ledger runs a deficit for three months in a row, fall back to **BYO-only**: the
shared instance goes read-only, then shuts down after the notice period in
[04](04-funding-and-costs.md#continuity-promise). BYO keeps the cloud features
alive at no cost to the maintainer. Plan 03 is what makes that fallback cheap,
so it ships before the shared cloud is promoted to strangers.

## Plans, in order

| # | Plan | Blocks | Who |
|---|---|---|---|
| 01 | [Go public safely](01-go-public-safely.md): live security fix, history sweep, licence, NOTICE | everything | OWNER + code |
| 02 | [Distribution and build flavours](02-distribution-and-flavours.md): `foss` / `play` flavours, F-Droid metadata, GitHub Releases | F-Droid listing | code, then OWNER |
| 03 | [Bring your own backend](03-bring-your-own-backend.md): runtime Supabase URL and key, schema version check | the BYO fallback | code |
| 04 | [Funding and costs](04-funding-and-costs.md): ledger, Support screen, donation platforms, Play policy | asking for money | code + OWNER |
| 05 | [Import from Strong and Hevy](05-import-from-strong-hevy.md): CSV merge import | nothing; biggest adoption lever | code |
| 06 | [Board fairness](06-board-fairness.md): idle cap, stale strength TODO, visibility | public leaderboards | OWNER decision + code |

01 must finish before the repository becomes public. 02, 03 and 05 are
independent of each other and can run in parallel, each owning its own files.
04 depends on the flavours from 02, because the Support screen differs per
flavour. 06 must land before the shared cloud is advertised, because strangers
will rank against each other on those boards.

## Deliberately not planned

- **Per-user billing.** Supabase bills per project, not per user, so one
  lifter's share is fractions of a cent. Play would take 15% of any
  subscription, and taking payment brings refunds and consumer-law obligations.
  The ledger delivers the transparency that billing would have, without the
  machinery.
- **Moving backups out of Postgres into Supabase Storage.** Storage is cheaper
  per GB ($0.0213 vs $0.125), but that only matters past the 8 GB of disk
  included in Pro. Revisit when the ledger shows disk above 6 GB.
- **iOS, localisation.** Neither has been asked for. `docs/TODO.md` already
  rules out string extraction.
- **Automatic CI.** Actions minutes cost nothing on a public repository, but
  the account rule stands: `workflow_dispatch` only, and `tools/gate.py`
  remains the gate.
