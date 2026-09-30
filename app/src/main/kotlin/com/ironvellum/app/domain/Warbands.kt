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
    /** Oldest member first — the server's order, kept as handed over. */
    val members: List<WarbandMember>,
)

/** One bandmate. [workoutsThisWeek] counts completed workouts in the current Monday-start week (UTC anchor, server-computed). */
data class WarbandMember(
    val userId: String,
    val displayName: String,
    val level: Int,
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
