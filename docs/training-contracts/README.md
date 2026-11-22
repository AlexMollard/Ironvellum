# Training exchange contracts

These schemas, movement policies and byte fixtures support phone training export and workout import. The former personal PC runtime is preserved on [archive-personal-connector](https://github.com/AlexMollard/Ironvellum/tree/archive-personal-connector), not shipped on main. They are reusable wire references, not the hosted MCP authentication or API contract; see [the production service](../../server/production_mcp/README.md) for that boundary.

## Files and provenance

Use [context-v1.schema.json](context-v1.schema.json) and [proposal-v1.schema.json](proposal-v1.schema.json) with the relative definitions in [files-v1.schema.json](files-v1.schema.json). All members shown are required, including nullable loads. Objects are closed at every level. Reject duplicate decoded member names, unpaired surrogates, invalid UTF-8, trailing material, non-JSON syntax and nesting beyond 16 containers. Cap bytes before parsing at 8 MiB for contexts and 64 KiB for proposals. Numeric values use JSON numbers. Strings and booleans never coerce to numbers. Numeric tokens have at most 128 ASCII characters. Nonzero numbers must survive conversion to a finite nonzero binary64 value. The outer schema version and epoch-millisecond timestamps use integer tokens. The draft body follows the existing numeric contract and accepts mathematically integral target counts.

UUIDs are lowercase RFC 4122 version 4 with the standard variant. The dataset UUID persists until full archive replacement. A new snapshot UUID identifies each generated context. The phone records its SHA-256 over the original UTF-8 file bytes before returning them to the sharing flow. A proposal refers to those bytes through `contextSha256`. The separate receipt's proposal hash covers its original bytes, including all whitespace. The proposal file has no self-hash. These identifiers detect mix-ups. Sender labels and saved workout names are untrusted display text.

Timestamps range from 0 to 253402300799999 inclusive. The proposal must expire strictly after creation and within 2592000000 ms. At new save it must be unexpired and created no more than 300000 ms in the future. A prior receipt for the current dataset and draft UUID takes precedence over expiry. Identical bytes return its historical preset ID, including after preset deletion. Changed bytes conflict. The phone accepts a known older snapshot of the same dataset and includes its date in review. A full archive replacement clears all four local tables inside the restore transaction.

## Context and coverage

- Select the latest 100 completed sessions by completion timestamp descending and database ID descending at the selection boundary. Return them by completion timestamp descending then opaque ID ascending. Ignore unfinished sessions. Timestamps must be ordered and within the wire range.
- Use at most 5000 local movement rows to establish unique canonical keys. A 5001st row refuses the operation. Never accept a partial resolution. Export requires a unique noncustom built-in REPS movement. Machine-assistance movements whose recorded weight represents assistance are unsupported in the external-load projections.
- Within the selected workouts, count all done sets. Eligible rows have ordinary WORKING kind, empty modifiers, no activity fields, reps 1 through 1000, and finite external load null or 0 through 1000 kg. Return the first 200 eligible sets per workout by exercise position, set index and database ID. The exposed order is their zero-based ordinal. No free-text modifier or exercise name crosses the boundary.
- Select the latest 50 eligible saved workouts by local preset ID descending. Return them by opaque ID ascending. Exact display names are included, limited to 1 through 80 Unicode scalar values with no ISO control characters or leading/trailing ASCII spaces. Unsupported names omit the whole preset. Accept 1 through 20 fixed entries in position/ID order, with draft target bounds, no modifiers, schemes, supersets or warm-up entries. Missing preparation choices default to AUTO as they do in the app. Only AUTO/NONE choices without routine references qualify. Notes and scheduling stay local.
- Coverage counts all excluded completed workouts and all excluded saved workouts. Set omission counts apply to done rows of the included workouts. `Truncated` counts supported rows beyond a cap. `Unsupported` counts ineligible rows before applying the cap. Coverage does not imply all-time set or rep totals.
- Progress uses only the emitted sets and workouts. Volume sums external kg times reps using decimal representations of the emitted loads, rounding the total once to binary64. Null load contributes zero volume. Completion bounds are null in an empty context. Per-lift maximum load is null when every included load for that lift is null. Sort lifts by completed-set count descending and canonical key ascending. Emit the first 20 and count the remainder in `liftsTruncated`.
- Each workout/preset has a stable random `local:<uuid>` ID. The file has no raw database ID, session labels, notes, custom movement names, body/health fields or account identifiers. A saved workout's display name can contain personal information authored by the lifter.

The [catalogue](movements-v1.json) contains all 584 Seed movements. Fingerprint SHA-256 covers the UTF-8 bytes of `ironvellum-movements-v1`, NUL, followed by ascending-key records, each with key, name and metric terminated by NUL. The phone verifies the supplied catalogue against Seed in tests. For contexts, check the exact catalogue identity, unique opaque IDs, canonical eligible movement keys, deterministic ordering, time relationships, set ordinals, included coverage and recomputed progress. Never infer data outside the file.

Use the [v1 draft movement policy](draft-movement-policy-v1.json) with that catalogue. A key qualifies when its catalogue metric equals `requiredMetric` and its key is absent from `excludedKeys`. The excluded machines store kilograms of assistance, which decrease effective load. They are ineligible even when a proposal provides null load. Use this same rule for the search result's `draftable` field, creation and context movement validation. The phone applies it at inspection and Save. An identical historical receipt still returns the saved result.

## Legacy tool wire reference

[tools-v1.json](tools-v1.json) freezes the seven successful input and output shapes. Resolve its relative references against this directory and the common [tool types](tool-types-v1.schema.json). Success returns the result object as structured data. Every result includes the immutable source snapshot identity, export time, age in milliseconds (floored at zero) and coverage. A refused call reports a classified tool error with no rejected values, absolute paths or input content.

| Tool | Behavior |
| --- | --- |
| list_workouts | Page the context's ordered workout summaries. |
| get_workout | Look up one opaque workout ID in this snapshot. |
| get_progress | Return the context's versioned included-workout progress. |
| list_saved_workouts | Page the ordered saved workout projections. |
| search_movements | Search canonical keys/display names by case-insensitive substring, in ascending key order. Empty query returns the first matches. Expose each actual metric and whether it accepts drafts. |
| create_workout_draft | Check every entry against the v1 draft movement policy, then create a proposed file using the strict draft and ASCII idempotency key. Choose a UUID and a bounded unverified sender label. |
| get_workout_draft | Return a journaled proposal for this dataset and snapshot. Return only its generated basename and original proposal content. |

Optional limits default to 20. Pages admit 1–50 and search admits 1–20. Return null nextCursor at the end. Cursors bind snapshot, listing kind and ordering position. Limit every rendered result to 1 MiB and each incoming stdio frame to 64 KiB before JSON decoding. The basename is the draft UUID followed by `.json`. No tool accepts a path, scope switch, executable, or approval. Neither proposal tool can infer whether the phone saved anything.

Namespace idempotency by dataset UUID, snapshot UUID and the case-sensitive key. Compare the validated draft body, preserving entry order, names and decimal value. Numerically equivalent target spellings identify the same payload. Return identical stored proposal bytes on retry, including across a restart. A changed body conflicts. At 1000 journaled proposals, existing retries remain available and new admission fails. Never overwrite an existing unrelated file.

## Shared byte vectors

[fixtures](fixtures/) contains a context from the real Kotlin encoder, an intentionally space-prefixed CRLF-terminated proposal, and SHA256SUMS over both files. They use synthetic dated data, a Unicode saved name, a high-precision numeric target and null load. Their historical timestamps make a fixed test clock necessary when exercising new save. The regular JVM test compares the files byte for byte and checks all reviewed Seed records. Maintainers can regenerate the vectors by setting `IRONVELLUM_EXPORT_LOCAL_VECTORS=1` and executing LocalConnectorVectorTest.
