package com.ironvellum.app.data.cloud

/**
 * Field limits in one place: those the SERVER enforces, plus the one
 * device-only field that has no server counterpart and so had no bound at all.
 *
 * Every value here has a matching `check (...)` in supabase/migrations. They
 * were previously duplicated as bare literals — `take(80)` in the repository,
 * `2..24` in three places in the account code — with nothing tying them to the
 * SQL. A tightened constraint would then be found by a sync that fails forever
 * on one lifter's row, silently, because the push is wrapped in `runCatching`.
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
     * that lifter's push fail forever inside `runCatching`.
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

    /**
     * `web_snapshots_size_cap`: the declared `size_bytes` is capped at 4 MiB, and so is what the client
     * measures. The client measures the text Postgres will hold, which is the compact JSON plus the space
     * jsonb puts after every `:` and `,` ([com.ironvellum.app.domain.WebSnapshotJson.jsonbTextBytes]).
     */
    const val SNAPSHOT_MAX_BYTES = 4_194_304

    /**
     * `web_snapshots_size_cap`: the server's own bound on `octet_length(snapshot::text)`. It sits at twice what
     * the client sends, so the text form's spacing can never make an accepted snapshot fail.
     */
    const val SNAPSHOT_TEXT_MAX_BYTES = 8_388_608

    /** `session_sets.modifiers` (`session_sets_mods_len`) and `imported_session_sets.modifiers`: 64 characters. */
    const val SET_MODIFIERS_MAX = 64

    /** `imported_session_sets.exercise_name`: `char_length(exercise_name) <= 64`. */
    const val IMPORTED_EXERCISE_NAME_MAX = 64

    /** `imported_sessions.label`: `char_length(label) <= 80`. */
    const val IMPORTED_LABEL_MAX = 80

    /**
     * [text] cut to at most [max] characters. Postgres counts code points and Kotlin counts UTF-16 units, so
     * the cut is never past [max] on the server's count, and never lands inside a surrogate pair.
     */
    fun clamp(text: String, max: Int): String {
        if (text.length <= max) return text
        val end = if (Character.isHighSurrogate(text[max - 1])) max - 1 else max
        return text.substring(0, end)
    }

    /**
     * `session_comments.body`: 1..280 after trim. The client trims and refuses
     * blank or longer text itself, so the lifter reads why instead of an
     * opaque 23514 from the check.
     */
    const val COMMENT_MAX = 280

    /** `reports.note`: `char_length(note) <= 280`. */
    const val REPORT_NOTE_MAX = 280
}
