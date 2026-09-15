package com.monarch.app.data.cloud

/**
 * Field limits the SERVER enforces, in one place.
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
}
