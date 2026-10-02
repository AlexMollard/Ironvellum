# 01: Go public safely

**Goal:** the repository can be made public without exposing a live security
hole, a secret, or unlicensed third-party work.

**Done when:** `is_friend` refuses `anon` on the hosted project, the history sweep below comes back clean, a
`LICENSE` and `NOTICE` exist, and the owner has flipped the repository to
public.

## 1. Close the live exposure first (OWNER)

Before the baseline, anyone holding the publishable key could enumerate the
accepted-friendship graph through `is_friend`, including for lifters set to
`private`. The key ships inside every APK, and a public repo makes it trivial
to find. The baseline closes the hole: Supabase grants functions to `anon`
directly, so every revoke in it names `anon` and `authenticated`, not only
`public`.

The hosted project now carries the baseline's grants: it is at schema 28
(owner-verified 2026-10-02), reached through the `supabase/hosted/` patches.
What is left is proof, not a migration:

1. Prove the schema still holds: `python3 tools/gate.py --backend`.
2. **Do not** run `supabase/reset.sql` on the hosted project to get here. It
   deletes every account, and the project is already on the baseline.
3. Confirm the revoke with a real call rather than trusting the editor.
   The editor runs the whole script as one transaction, so one failure rolls
   back everything before it. See skill `supabase-applied-state-from-device-jwt`.

**Check:** calling `is_friend` as `anon` returns a permission error
(`42501`).

## 2. History sweep

Read-only sweeps run on 2026-09-23 against `git log --all`:

| Search | Result |
|---|---|
| `local.properties`, `*.jks`, `*.keystore` ever committed | none |
| `service_role` next to a JWT | none |
| `sb_secret_` | none |
| `eyJhbGciOi` (JWT) | one hit: a fake `Bearer eyJhbGciOiJIUzI1NiJ9.secret-token` test fixture in `bea6e1f`. Harmless |
| a `*.supabase.co` project URL | none |

`git fsck` lists 8 dangling commits. They exist only in the local object store
and are never pushed, so they need no action.

**Re-run immediately before going public**, because history can change
between now and then:

```bash
git log --all --oneline -- local.properties '*.jks' '*.keystore'
git log --all --oneline -G "sb_secret_|service_role.{0,5}[=:].{0,5}eyJ"
git log --all --oneline -G "[a-z0-9]{20}\.supabase\.co"
```

## 3. Licence and notices (code)

Files to add at the repository root:

| File | Content |
|---|---|
| `LICENSE` | Verbatim GPL-3.0 text from https://www.gnu.org/licenses/gpl-3.0.txt |
| `NOTICE` | Copyright line (`Copyright (C) 2026 Alex Mollard`), the GPL-3.0-or-later statement, the Chakra Petch fonts under SIL OFL 1.1, and a note that all artwork is original or Gemini-generated for this project and shares its licence |
| `licenses/OFL-ChakraPetch.txt` | The OFL 1.1 text that ships with Chakra Petch. Not under `res/font/`: aapt rejects any non-font file there |

Checks before writing them:

- Chakra Petch's licence is confirmed by the OFL string in all three bundled
  TTFs' name tables and by the upstream `google/fonts` `ofl/chakrapetch/OFL.txt`.
- OFL 1.1 can be distributed inside a GPL-3.0 app, provided its licence text
  is kept. No artwork is third-party (`docs/ART_ATTRIBUTION.md`), so nothing
  else needs credit. The Support screen from plan 04 shows the font credit.

README changes:

- Add a Licence section that links `LICENSE` and `NOTICE`.
- The gate badge on README line 12 says **337 unit**. A grep of `@Test` counts
  **334 unit and 74 instrumented**. Correct it to match the next
  `tools/gate.py` run, rather than to either grep count.
- Add a Contributing paragraph: run `tools/gate.py` locally before opening a
  PR, since CI is manual. Keep the emulator-only rule from `AGENTS.md`.

## 4. Flip to public (OWNER)

Only after steps 1-3. GitHub → Settings → General → Danger Zone → Change
visibility. Then add `.github/FUNDING.yml` (plan 04) so the Sponsor button
appears.
