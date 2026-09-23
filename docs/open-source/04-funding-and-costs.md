# 04: Funding and costs

**Goal:** anyone can see what the shared cloud costs, who pays for it, and what
the maintainer keeps. Anyone who wants to help can donate in two taps from the
`foss` build, and the `play` build stays inside Google's payments policy.

**Done when:**

- `COSTS.md` is published with a real first month;
- `.github/FUNDING.yml` is live;
- the Support screen ships in both flavours with the right content for each;
- the continuity promise is written down.

## What the shared cloud costs

Source: https://supabase.com/pricing, read 2026-09-23. Figures are USD.

| Line | Cost | Notes |
|---|---|---|
| Supabase Pro | $25/month | Includes $10 compute credit, which covers one Micro instance (1 GB RAM, 200 pooler connections) |
| Database disk above 8 GB | $0.125/GB/month | Backups are capped at 8 MiB each (`0013_cloud_archives.sql:31`) |
| Egress above 250 GB | $0.09/GB | Uploads are ingress and free. Egress is restores and feed/board reads |
| Monthly active users above 100k | $0.00325 each | Not a realistic concern |
| Google Play registration | $25 once | Only if the Play build ships |
| F-Droid, GitHub, public CI | $0 | |

**Why not the free tier:** free projects pause after a week of inactivity and
have no backups. That is the wrong place for someone's only restore copy.

**Monthly cost:** `25 + max(0, diskGB - 8) × 0.125 + max(0, egressGB - 250) × 0.09`.

**Measure before promising numbers.** Export the owner's archive (Settings →
DATA → EXPORT ARCHIVE) and record its size. `lifters_before_disk_bill ≈
8 GB / (archive_size + synced_rows_size)`. The per-lifter synced rows are the
`sessions` and `session_sets` data, visible from
`select pg_total_relation_size('session_sets')` divided by the number of
lifters. Write the measured figure into `COSTS.md`. The README roadmap's
0.5 MB estimate is an assumption, not a measurement.

## The ledger: `COSTS.md` (code: template, OWNER: monthly figures)

A markdown file at the repository root, so GitHub renders it with no hosting,
no Pages build, and nothing that runs by itself.

```markdown
# What Ironvellum costs

Updated monthly. All amounts USD. Invoices: Supabase dashboard → Billing.

| Month | Hosting | Other | Donations in | Platform fees | Net | Reserve | Maintainer |
|---|---|---|---|---|---|---|---|
| 2026-10 | 25.00 | 0.00 | 0.00 | 0.00 | -25.00 | 0.00 | 0.00 |

Reserve target: 3 months of hosting (75.00). Surplus beyond the reserve may be
paid to the maintainer and is always shown in its own column.

Lifters on the shared cloud: N · Database disk: X GB of 8 · Egress: Y GB of 250
```

- **Maintainer cut rule:** nothing is taken until the reserve is full. After
  that, surplus may be taken, always in its own column. That delivers the
  "small cut, fully disclosed" idea without charging anyone.
- **Tax** [INFERENCE, not advice]: small, irregular donations to a hobby
  project are generally not assessable income in Australia. Ask an accountant
  if the maintainer column ever becomes regular.

## Donation platforms (OWNER)

| Platform | Why | Fees |
|---|---|---|
| GitHub Sponsors | The button sits on the repository itself, which is where contributors already are | No GitHub fee on sponsorships from personal accounts; confirm at signup |
| Liberapay | Non-profit and FOSS itself; the donor's choice for FOSS users | No platform fee, only Stripe/PayPal processing |

Open Collective was rejected: it shows a public ledger itself, but its fiscal
hosts take roughly 4-15%, and `COSTS.md` already does the transparency job.

`.github/FUNDING.yml` (code; it is a config file, not a workflow, so nothing
runs):

```yaml
github: AlexMollard
liberapay: <owner's liberapay username>
```

## Google Play: no donation links in the `play` build

- Play has removed FOSS apps for in-app donation links (WireGuard, 2019), and
  StreetComplete had to drop even a project-homepage link because that page
  mentioned donating (https://github.com/streetcomplete/StreetComplete/issues/3768).
- The October 2025 relaxation covers **US storefronts only**, under a
  compliance programme
  (https://support.google.com/googleplay/android-developer/answer/15582165).
- So the `play` build shows no donation button, no ledger link, and no
  tappable repository link. It carries the licence, the attributions, and the
  source location as plain text, which GPL permits. Donations live on the
  repository page, where Play policy does not reach.

## Support screen (code; needs `BuildConfig.SUPPORT_LINKS` from plan 02)

- New `Routes.SUPPORT` constant in `IronvellumNav.kt` (`Routes` at `:81-113`)
  and one `composable(Routes.SUPPORT) { SupportScreen() }` next to
  `composable(Routes.SETTINGS)` (`:342`).
- Entry point: a new `InkPanel` in `SettingsScreen.kt` after DIAGNOSTICS
  (ends `:750`), before the footer (`:752`). Title "SUPPORT IRONVELLUM" when
  `SUPPORT_LINKS`, otherwise "ABOUT".
- Open links with Compose's `LocalUriHandler.current.openUri(url)`. No
  `Intent` helper is needed, and nothing in the app opens a URL today.
- The screen text is written in the Ledger's voice, with no guilt and no nag.
  There are no pop-ups or reminders anywhere else in the app.

| Section | `foss` | `play` |
|---|---|---|
| "Ironvellum is free and open source. It has no ads, no trackers and no paid tier." | yes | yes |
| What the shared cloud costs: a short explanation and a VIEW THE LEDGER link to `COSTS.md` | yes | text only, no link |
| SPONSOR ON GITHUB / DONATE ON LIBERAPAY | yes | no |
| Source code | tappable link | plain text |
| Licence (GPL-3.0-or-later) and the Chakra Petch OFL credit from `NOTICE` | yes | yes |
| Version (`BuildConfig.VERSION_NAME`) | yes | yes |

This replaces the hard-coded footer version `Ironvellum v1.0` in
`SettingsScreen.kt:752-757` with the real `VERSION_NAME`.

**Checks:**

- Instrumented, emulator only: the `foss` build shows the donate buttons and
  the `play` build has no node containing "Sponsor", "Donate" or "Liberapay".
  Assert on the semantics tree.
- On the phone, tapping each link opens the right page.

## Continuity promise (code, in `COSTS.md` and `PRIVACY.md`)

A donor-funded service has to say what happens if the money stops:

1. Every lifter can always EXPORT ARCHIVE, offline, with no account needed.
2. If the ledger runs a deficit for three consecutive months, the shared cloud
   gets 60 days' notice through the Support screen and the README, then becomes
   read-only (restore still works), then shuts down.
3. Bring-your-own-backend (plan 03) keeps every cloud feature working for
   anyone who wants it afterwards.
