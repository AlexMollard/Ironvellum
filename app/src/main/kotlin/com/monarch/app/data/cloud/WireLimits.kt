package com.monarch.app.data.cloud

/**
 * Field limits in one place: those the SERVER enforces, plus the one
 * device-only field that has no server counterpart and so had no bound at all.
 *
 * Every value here has a matching `check (...)` in supabase/migrations. They
 * were previously duplicated as bare literals — `take(80)` in the repository,
 * `2..24` in three places in the account code — with nothing tying them to the
 * SQL. A tightened constraint would then be found by a sync that fails forever
 * on one hunter's row, silently, because the push is wrapped in `runCatching`.
 *
 * `WireNamesMatchSchemaTest` reads the migrations and fails if these drift.
 */
object WireLimits {

    /** `profiles.display_name`: `char_length(trim(display_name)) between 2 and 24`. */
    const val DISPLAY_NAME_MIN = 2
    const val DISPLAY_NAME_MAX = 24

    /** `sessions.title`: `char_length(title) <= 80`. */
    const val SESSION_TITLE_MAX = 80

    /** `sessions.note`: `char_length(note) <= 500`. */
    const val SESSION_NOTE_MAX = 500

    /**
     * `session_sets.grade`: `grade is null or char_length(grade) <= 12`.
     * Free text on purpose — V4, 6C+ and 5.11a disagree — so the only bound
     * is length, and the client must respect it or a single long grade makes
     * that hunter's push fail forever inside `runCatching`.
     */
    const val GRADE_MAX = 12

    /**
     * The private note never leaves the device, so no SQL `check` bounds it —
     * which is why it was the only text field with no cap. It is a training
     * journal rather than a caption, so the ceiling is generous; it exists so
     * that a stray paste cannot put an unbounded blob in every export and row.
     * Not covered by the schema guard: there is no constraint to compare to.
     */
    const val PRIVATE_NOTE_MAX = 2_000

    /**
     * `cloud_archives.size_bytes`: `size_bytes <= 8388608` (8 MiB). The server
     * check would reject an over-ceiling archive with an opaque 23514, so the
     * client refuses the same number BEFORE uploading and can say what to do
     * about it. Guarded by WireNamesMatchSchemaTest like the field limits.
     */
    const val ARCHIVE_MAX_BYTES = 8_388_608
}
