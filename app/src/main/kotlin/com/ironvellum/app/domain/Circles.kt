package com.ironvellum.app.domain

/*
 * An invite-code circle: 2-8 allies sharing a weekly goal of days trained. The
 * server owns the roster, the counting and the settlement
 * (data/cloud/CloudSync.circle); this is the read-only shape the UI renders.
 */
data class Circle(
    val id: String,
    val name: String,
    val code: String,
    val ownerId: String,
    /** The UTC Monday that opens the week these figures are for, as an ISO date. */
    val week: String = "",
    /** Days each member aims to train this week (1-7): frozen when the week opened. */
    val perMember: Int = DEFAULT_PER_MEMBER,
    /** The Keeper's parked change, applied when next week opens; null when none. */
    val pendingPerMember: Int? = null,
    /**
     * Days the whole circle aims for this week: [perMember] times the roster the
     * week opened with. 0 while that roster holds fewer than two members: a
     * circle needs two before it has a goal.
     */
    val goal: Int = 0,
    /** Days trained by the roster so far this week: one canonical number, identical for every viewer. */
    val total: Int = 0,
    /** Members on this week's roster (those in the circle before the week began). */
    val roster: Int = 0,
    /** Weeks the circle has settled as met, over its whole life. */
    val weeksMet: Int = 0,
    /** Oldest member first — the server's order, kept as handed over. */
    val members: List<CircleMember>,
) {
    companion object {
        const val DEFAULT_PER_MEMBER = 3

        /** The server's accepted target: days per member per week. */
        val PER_MEMBER_RANGE = 1..7
    }
}

/**
 * One member. [daysThisWeek] counts days trained in the current Monday-start
 * week (UTC anchor, server-computed, capped at the member's share).
 * [counts] says whether they are on this week's roster: a lifter who joined
 * this week is not, and counts from next Monday. [level] and [titleId] are null
 * when the member's profile is hidden from the caller.
 */
data class CircleMember(
    val userId: String,
    val displayName: String,
    val level: Int?,
    /** The worn title id, null when bare; names resolve locally via Titles.byId. */
    val titleId: String?,
    val daysThisWeek: Int,
    val counts: Boolean = true,
    val lastWorkoutAtMs: Long?,
)

/**
 * The invite-code alphabet create_circle() draws from: digits 2-9 and letters
 * minus I, L and O — 31 unambiguous glyphs, so a code read aloud off a phone
 * screen survives. MUST stay in step with the alphabet literal and the
 * `invite_code ~ '^[2-9A-HJ-NP-Z]{8}$'` check in
 * supabase/migrations/0001_baseline.sql; InviteCodeTest proves the pair.
 */
const val InviteCodeAlphabet = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
const val InviteCodeLength = 8

/** True when [code] is exactly a valid invite code (case already folded by the caller). */
fun isValidInviteCode(code: String): Boolean =
    code.length == InviteCodeLength && code.all { it in InviteCodeAlphabet }

private const val CODE_CLASS = "[$InviteCodeAlphabet]{$InviteCodeLength}"

/** The token the share text puts after the word "code". */
private val CODE_AFTER_WORD = Regex("""\bCODE\b\W{0,3}($CODE_CLASS)(?![A-Z0-9])""")

/** A whole-token run of alphabet glyphs, never a slice of a longer word. */
private val CODE_BARE = Regex("""(?<![A-Z0-9])$CODE_CLASS(?![A-Z0-9])""")

/**
 * The invite code in [text] (a clipboard), or null. The share text reads
 * "Join my Ironvellum circle <name> — code <CODE>", and a circle named
 * "Strength" is itself eight glyphs from the alphabet, so the token after the
 * word "code" wins. Without that word only a token with a digit is taken: a
 * bare eight-letter word is far more likely a name than a code, and a wrong
 * guess pre-filled into the join box costs a throttled attempt.
 */
fun extractInviteCode(text: String): String? {
    val up = text.uppercase()
    CODE_AFTER_WORD.find(up)?.let { return it.groupValues[1] }
    return CODE_BARE.findAll(up).map { it.value }.firstOrNull { token -> token.any(Char::isDigit) }
}

/**
 * What `join_circle` answers. A status, not an error: a refusal that raised
 * would roll back the failed attempt the server had just logged for its
 * throttle. Only "joined" is a success; the rest read as the lifter's refusal.
 */
object JoinStatus {
    const val JOINED = "joined"

    /** The status inside the RPC's JSON scalar (a quoted string), unquoted. */
    fun parse(raw: String): String = raw.trim().trim('"')

    /** The refusal to show for [status], or null when the lifter joined. */
    fun refusal(status: String): String? = when (status) {
        JOINED -> null
        "no_such_code" -> "No circle answers to that code"
        "full" -> "That circle is full"
        // Deliberately neutral: it says nothing of who blocked whom.
        "closed" -> "That circle is closed to you"
        "throttled" -> "Too many code attempts today — try again tomorrow."
        else -> "The circle did not take you in — try again"
    }
}
