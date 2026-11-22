# Personal local connector and optional hosting

## Intent and approval

The owner chose a personal connector on each user's PC, agent-driven execution,
zero hosting payments, and explicitly selected **Share training files and import
draft files**. No Ironvellum/server sign-in is required. All Ironvellum services
must remain optional and runnable/self-hostable by their users. This supersedes
the earlier assumption that personal AI access needs the production MCP server.
Existing optional cloud and social services remain supported.

## Approaches

The chosen offline-file bridge needs neither an always-on phone nor a public
endpoint. A live paired LAN service would add pairing, lifecycle and certificate
work and was not selected. A shared public MCP would impose hosting and operator
credentials and is not a prerequisite. The existing development MCP controls
debug devices and must not be used for consumer training access.

## User flow and UI

Train exposes **AI tools** while signed out or with no backend configured. It
opens an app-styled page with **Share training file**, **Import workout draft**,
and an explicitly optional **Cloud AI connections** link. Existing cloud draft
review remains separately accessible from this page. No account prompt gates
local actions. Share uses Android's normal file-sharing chooser; transfer to the
PC is explicit, not automatic synchronization. The same preview also offers
**Save file on this device**, using CreateDocument to save locally for Downloads/
USB transfer without requiring a sharing app or cloud-drive account. Both actions
use the same immutable export bytes and source identity; cancel changes nothing
and success claims only the operation actually completed. Import uses OpenDocument, not an
exported activity or the CSV/archive restore receiver.

The share preview describes the latest 100 completed workouts, supported saved
workouts and training-only summaries. It gives actual included/omitted counts,
the file time and excluded categories before sharing. Export creates no training
records. Context is a dated snapshot; updating it requires export/transfer and a
connector restart. No watcher, live networking, background upload or new storage
permission is added.

An imported proposal opens the existing style of movement/preparation review,
with its source-file date and unverified sender, exact kg and preferred units.
Only explicit **Save workout** creates a new unscheduled saved workout. **Discard**
and picker cancellation change no workouts. Success says saved on this device;
the PC/AI does not learn acceptance automatically. Missing/deleted/ambiguous
movements, wrong dataset/export, expired proposals, unsupported format, invalid
files and duplicate/conflicting proposals get clear resource-backed states.
Repeat import shows already saved without recreating a deleted preset.

Mockups using the real app's fonts, cut corners, rows, colours and review layout
must be shown before UI code. Verify dark/plain and Vellum/themed actual screens.
All new screen text is in string resources with both wording modes and all seven
existing translated locales; use the established plural APIs.

## Offline contract v1

Create a standalone contract under `tools/local_connector/contracts/`; it is
independent of SQL hashes and production OAuth envelopes. Task 1 owns the final
exact JSON field names/fixtures and records them before downstream work begins.
No downstream task invents its own shape. All objects reject unknown/duplicate
keys, wrong scalar types (including bool-as-number), nonfinite/overflowed numbers,
invalid UTF-8, unsupported versions and nesting over 16. Read byte caps before
JSON decoding: context 8 MiB; proposal 64 KiB. Never deserialize a full backup and
then redact it. Include no URLs, auth/device/account IDs or credentials.

Context discriminator is `ironvellum.training-context`, schemaVersion 1, with a
random persistent phone `datasetId`, new random `snapshotId`, `exportedAtMs`,
catalogue version/fingerprint, coverage and allowlisted training data. It is
generated in a coherent Room transaction from bounded projections, not SELECT *
entity serialization. Export the latest 100 completed workouts, at most 200 done
sets per workout, and at most 50 supported saved workouts with at most 20 entries.
All truncation/unsupported omissions are counted explicitly. Exporting more
than 8 MiB fails clearly rather than producing a partial/inconsistent document.

Workouts expose stable opaque `local:<uuid>` IDs, start/completion timestamps and
supported built-in REPS done sets with key, order, reps and external kg load.
Exclude session labels, public/private/exercise notes, custom movement names,
arbitrary modifier strings, bodyweight-derived estimates, health/body/profile,
photos, social/rewards, archives and unfinished workouts. Unsupported set rows
are counted, not silently represented as ordinary REPS. IDs use stable random
mapping rows, never raw Room IDs, device IDs or fabricated synced IDs.

Saved workout projection may contain its bounded display name (80 code points),
opaque ID, fixed supported built-in REPS targets and AUTO/NONE prep choices.
Names are untrusted data, not instructions. Unsupported timed/custom/variable
scheme/preparation routines are omitted with explicit coverage counts; no lossy
conversion. The reviewed movement catalogue is the full 584 Seed manifest,
fingerprint `6b8bea45f8781884088d99fe8fc76c0df4c5755a8c2d1e9914629710bfa18a66`.
Draft admission accepts built-in REPS only. Machine-assisted movements are not
draftable in this external-load-only v1, including with a null load target. This
keeps local admission consistent with the export projection and avoids treating
prescribed lifted kilograms as assistance. The shared contract must identify the
same excluded canonical keys for Android admission, PC creation and movement
search's `draftable` flag. Existing production MCP admission is unchanged.

The privacy promise is structural: no health/body/notes fields are exported.
Saved-workout display names are explicitly included and may contain anything
their author typed; do not claim semantic removal of sensitive words from them.
The share preview must say saved-workout names are included. Names outside the
80-code-point/nonempty contract make that preset unsupported, not silently cut.

Progress is explicitly **from the included workouts**, not all-time or live.
Versioned training-only fields are completed-workout/set/rep counts, external
volume kg and first/last completion timestamps, plus up to 20 lifts (canonical
key, completed sets, maximum recorded external load and maximum recorded reps).
Calculate from the same included rows; no new estimated-1RM/bodyweight ratios.
Task 1 defines exact numeric/time bounds and deterministic lift ordering in the
shared contract. Empty states are explicit; do not invent unavailable data.

The phone records each generated `(datasetId,snapshotId,sha256(exact UTF-8 file
bytes),exportedAtMs)` before sharing. The hash is not included in its own context
file. This is provenance/mix-up detection, not cryptographic trust in an editable
AI file or proof the PC received it. Indexed export metadata survives subsequent
exports. No per-user export quota or automatic deletion of live receipts.

Proposal discriminator is `ironvellum.workout-draft`, schemaVersion 1, and binds
the dataset, snapshot, exact context SHA-256, unverified sender label, new draft
UUID, created/expires timestamps and the strict existing WorkoutDraft v1 body.
Lifetime is at most 30 days; new save checks expiry at commit and accepts at most
five minutes future clock skew. No status, approval flag, schedule, preset ID or
account scope is accepted. Existing bounds apply: name 1..80 code points,
1..20 entries, sets 1..50, reps 1..1000, null or finite load 0..1000 kg, and only
AUTO/NONE warmup/cooldown. Phone revalidates real local movement rows before and
inside Save, with the existing 5,000+1 overflow protection.

Local receipt identity is `(datasetId,draftId)`, with SHA-256 of the **exact
imported proposal bytes**. No cross-language JSONB/canonical JSON hashing is
needed. Same bytes return the receipt; same identity with changed bytes conflicts.
Formatting changes count as changed bytes. The connector always returns the
same stored bytes for an idempotent retry. A matching prior receipt is historical
truth even after expiry or preset deletion; it never enables another insertion.

## Phone persistence and lifecycle

Add separate local identity, object-ID mapping, generated-export and import-
receipt tables in one additive Room migration 50→51. Never fake a cloud backend
or bypass AccountRepository guards. An immutable private review handle pins the
file bytes/hash and parsed content. Preset, fixed entries, AUTO/NONE choices and
receipt commit atomically; receipt has no cascade from preset deletion. No
session, schedule, profile, XP, reward or cloud state is written by import.
Concurrent/repeated saves mint at most one preset.

Full archive replacement/restore resets connector identity, mappings, exported
snapshots and receipts coherently, invalidating previously opened reviews and
old files. CSV/history merges do not reset it. Save rechecks current dataset and
known snapshot/hash inside its transaction, fencing a concurrent full restore.
Local metadata is not slipped into the existing broad archive format. Database
migration preserves all existing training and cloud receipts. Export staging
stays in FileProvider's existing cache directory, with temporary read grants;
no payloads through large Intent extras and no private-directory exposure.

## PC connector

Separate `tools/local_connector` package, CPython >=3.11, pinned official
`mcp==2.3.0` stdio and compatible strict Pydantic. No production settings imports,
network calls/listeners, OAuth, backend, admin key, developer mode, ADB, shell
execution, automatic installation or public hosting. Reuse pure validation
where practical; preserve existing production transport/schema behavior.

Launch uses argument arrays with human-chosen absolute `--context` and `--outbox`
paths. No paths/owners/accounts in AI tool arguments. Load one strictly validated
context into immutable memory at startup; replacement on disk cannot switch the
running AI's dataset. Package the verified catalogue. State snapshot age/coverage
in results. Support Windows, Linux and macOS without shell wrappers; be honest
about platforms actually tested.

Expose exactly seven tools with local semantics: list_workouts, get_workout,
get_progress, list_saved_workouts, search_movements, create_workout_draft,
get_workout_draft. Pages max 50, movement search max 20, rendered result max 1 MiB.
Cursors bind the immutable snapshot and deterministic timestamp/opaque-ID order.
Proposal tools never approve/apply/save. `get_workout_draft` reports only a local
proposal, never inferred accepted/rejected phone state. No arbitrary read/write
or executable tools. Task 1's shared contract freezes exact argument/result shapes.

Draft lookup is limited to the active dataset **and snapshot**. Restarting with
a newer context does not expose old-snapshot journal entries through tools;
their existing immutable files remain importable on the phone against the saved
export provenance until proposal expiry. No restart retargets old proposals.

Create uses a 1..128 ASCII `[A-Za-z0-9._-]+` idempotency key, strict WorkoutDraft
and generated UUID filename. Namespace idempotency by dataset/snapshot/key:
same normalized payload returns identical stored file bytes across restart;
changed payload conflicts. The local SQLite journal is bookkeeping only, never
the phone DB. Serialize admission; max 1,000 journaled proposals, clear refusal
at capacity while existing retries remain possible. No copied server grant/
registration/day quotas. An explicit new outbox is a new journal; phone receipts
still protect identical proposal IDs. No automatic deletion of user proposals.

Require local regular input and private local outbox; reject network/UNC/device/
ADS paths, symlinks/junctions/reparse ancestors and special files. Read from
bounded checked handles, avoid path-check/write races; same-user malicious OS
processes are not a security sandbox. No context/output overlap. UUID-only output
names; atomic complete no-clobber publication and journal recovery for concurrent
creation, interruption or existing conflicting file. Do not overwrite unrelated
files. Errors/logs contain classifications, not rejected values, context content
or absolute paths. stdout is MCP protocol only, stderr minimal diagnostics;
EOF/shutdown exits without orphan processes. Verify pinned SDK stdio preparse
bounds: cap frame before decoding to 64 KiB while retaining official protocol
handling, and bound typed arguments. Do not assume HTTP middleware covers stdio.

## Optional services and self-hostability

Add explicit `-PlocalOnly=true` release builds with empty generated backend/Google
fields and no fallback to committed defaults, while preserving ordinary release
validation and existing custom backend behavior. No sign-in or unavailable server
may block local onboarding, logging/completing workouts, reopening, sharing or
draft saving. Verify this signed out/unconfigured and with a failed optional
backend. Keep proprietary Google dependencies out of FOSS.

Document the component map: Android local core; personal local connector; optional
static web viewer using a user-selected backend; optional self-hostable Supabase;
optional operator production MCP. Initial dependency downloads and the chosen AI
app's own requirements are separate from runtime Ironvellum accounts. Document
Windows/Linux/macOS PC prerequisites and local files' explicit transfer.

Improve optional Supabase instructions with a pinned upstream stack bootstrap,
private generated settings, persisted storage and safe fresh-install baseline
application that refuses existing/nonempty data. Never auto-run destructive reset.
Explain stop/backup/restore and phone-reachable trusted HTTPS; localhost on a phone
is not the PC. A domain, paid host, maintainer account or production MCP must not
be required for the offline path. Do not claim the complete stack was executed
without an available container runtime. Do not install system services or alter
the owner's network/security settings as a side effect of this work.

## Verification and delivery

Use agent-driven serial implementation and scoped independent reviews; no worker
subagents. Show all new UI before changing UI code. Shared cross-language fixtures
exercise real phone export → official SDK subprocess tools → proposal bytes →
phone parse/review/save. Negative controls prove no save before explicit action,
unknown/private field rejection and duplicate/altered identity handling. Privacy
canaries cover nested notes, body/health and credentials; test realistic bounded
data, coherent snapshots and exact-byte provenance.

Meaningful tests cover migration/restore fences, receipt tombstones, concurrent
save/rollback, URI cancellation/oversize, no-network stdio startup/calls/EOF,
filesystem publication interruption and platform boundaries. Cross-layer runtime
evidence uses only synthetic data and isolated emulator databases; no owner phone,
real training, hosted SQL changes, public deployment, new account or payment.

Run required Android/web gate alone under the shared emulator lock, unchanged
counts/budget and strictly under 600 seconds. Separate local connector/production
service/SQL checks from the gate. Commit/push verified coherent units to main per
standing authorization; no history rewrite, tags/releases or automatic Actions.
Final evidence separates source/package verification from unexecuted optional
container/cloud/provider/platform checks.
