package com.ironvellum.app.domain

/*
 * An invite-code warband: 3-8 allies sharing a weekly challenge, a
 * trained-this-week mark and a pooled banner line. The server owns the roster
 * (data/cloud/CloudSync.warband); this is the read-only shape the UI renders.
 */
data class Warband(
    val id: String,
    val name: String,
    val code: String,
    val ownerId: String,
    /** The owner's weekly challenge: total band workouts aimed for this week. */
    val weeklyGoal: Int = 12,
    /** Days trained by the whole band this week: one canonical number, identical for every viewer. */
    val total: Int = 0,
    /** Oldest member first — the server's order, kept as handed over. */
    val members: List<WarbandMember>,
)

/** One bandmate. [workoutsThisWeek] counts days trained in the current Monday-start week (UTC anchor, server-computed, the same for every viewer).
 *  [level] and [titleId] are null when the bandmate's profile is hidden from the caller. */
data class WarbandMember(
    val userId: String,
    val displayName: String,
    val level: Int?,
    /** The worn title id, null when bare; names resolve locally via Titles.byId. */
    val titleId: String?,
    val workoutsThisWeek: Int,
    val lastWorkoutAtMs: Long?,
)

/**
 * The invite-code alphabet create_warband() draws from: digits 2-9 and letters
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
 * What `join_warband` answers. A status, not an error: a refusal that raised
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
        "throttled" -> "Too many code attempts today — try again tomorrow."
        else -> "The circle did not take you in — try again"
    }
}
