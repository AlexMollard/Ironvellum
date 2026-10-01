package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.Skills
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `claimSkill` mints tier-scaled XP, so the prerequisite rule cannot live only
 * in the skill tree UI where the button is greyed out — an import restore or
 * any future caller would walk straight through and mint, say, 600 XP for a
 * tier V with nothing mastered. These tests pin the repository-side gate: a
 * locked skill refuses AND pays nothing, a root skill pays, and the gate opens
 * the moment its prerequisite is claimed.
 */
@RunWith(AndroidJUnit4::class)
class SkillPrerequisiteGateTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    /** "Scapular Pull" hangs off root skill "Dead Hang" on the Pull line. */
    private val locked = Skills.forName("Scapular Pull")!!
    private val root = Skills.forName("Dead Hang")!!

    @Test
    fun claimingASkillWhosePrerequisiteIsUnmetThrowsAndPaysNothing() = runBlocking {
        val xpBefore = db.profileDao().get()!!.totalXp

        val refused = runCatching { repo.claimSkill(locked.name) }
        assertTrue(
            "claiming ${locked.name} with nothing mastered must not succeed",
            refused.isFailure,
        )
        // The exception is not the point — the empty-handed ledger is.
        assertEquals(
            "a refused claim must mint no XP",
            xpBefore,
            db.profileDao().get()!!.totalXp,
        )
        assertEquals(
            "a refused claim must not record mastery",
            null,
            db.skillPracticeDao().claim(locked.name),
        )
    }

    @Test
    fun aRootSkillClaimsAndPaysWithoutAnyPrerequisite() = runBlocking {
        val claim = repo.claimSkill(root.name)
        assertEquals(root.xp.toLong(), claim.xpAwarded.toLong())
        assertEquals(
            "the root claim must reach the ledger",
            root.xp.toLong(),
            db.profileDao().get()!!.totalXp,
        )
    }

    @Test
    fun claimingAfterMasteringThePrerequisiteSucceeds() = runBlocking {
        repo.claimSkill(root.name)
        val xpAfterRoot = db.profileDao().get()!!.totalXp

        val claim = repo.claimSkill(locked.name)
        assertEquals(locked.xp.toLong(), claim.xpAwarded.toLong())
        assertEquals(
            "the unlocked claim must pay on top of its prerequisite",
            xpAfterRoot + locked.xp,
            db.profileDao().get()!!.totalXp,
        )
    }

    @Test
    fun aSecondClaimOfOneSkillStillThrows() = runBlocking {
        repo.claimSkill(root.name)
        val xpAfterFirst = db.profileDao().get()!!.totalXp

        val second = runCatching { repo.claimSkill(root.name) }
        assertTrue("an already-mastered skill must not claim again", second.isFailure)
        assertEquals(
            "the repeat claim must not mint XP again",
            xpAfterFirst,
            db.profileDao().get()!!.totalXp,
        )
    }

    /**
     * The suspected farm: unclaim pays the XP back, so claim -> unclaim ->
     * claim must land exactly where one claim did. If unclaim ever fails to
     * subtract (a clamp, a missed row), every cycle mints tier x 120 for free.
     */
    @Test
    fun unclaimingPaysTheXpBackSoReclaimingCannotMintItTwice() = runBlocking {
        repo.claimSkill(root.name)
        val xpAfterOneClaim = db.profileDao().get()!!.totalXp
        assertTrue("fixture must have paid XP", xpAfterOneClaim > 0)

        repo.unclaimSkill(root.name)
        assertEquals(
            "unclaim must take the XP back out",
            0L,
            db.profileDao().get()!!.totalXp,
        )

        repo.claimSkill(root.name)
        assertEquals(
            "re-claiming after unclaim must not double-pay",
            xpAfterOneClaim,
            db.profileDao().get()!!.totalXp,
        )
    }

    /**
     * The invariant the XP rules lean on: a claim may not stand while the
     * skill it requires has stood down. Dropping Dead Hang under a claimed
     * Scapular Pull would leave mastery recorded above a missing floor.
     */
    @Test
    fun unclaimingASkillItsDependentsStandOnIsRefused() = runBlocking {
        repo.claimSkill(root.name)
        repo.claimSkill(locked.name)

        val refused = runCatching { repo.unclaimSkill(root.name) }
        assertTrue("the floor skill must refuse to leave while supported", refused.isFailure)
        assertEquals(
            "the refusal must not have taken the XP out",
            root.xp + locked.xp.toLong(),
            db.profileDao().get()!!.totalXp,
        )
        // Standing the dependent down first opens the floor again.
        repo.unclaimSkill(locked.name)
        repo.unclaimSkill(root.name)
        assertEquals(0L, db.profileDao().get()!!.totalXp)
    }

    /**
     * The clamped-refund farm: with the ledger spent below the skill's XP (a
     * deleted workout clamps its own refund), an unclaim that took back only
     * what was left let every reclaim mint the difference. The unclaim must
     * refuse instead, leaving the claim and the ledger untouched.
     */
    @Test
    fun unclaimingWithLessXpThanTheSkillPaidIsRefused() = runBlocking {
        repo.claimSkill(root.name)
        db.profileDao().addXp(-(root.xp - 10).toLong())
        assertEquals(10L, db.profileDao().get()!!.totalXp)

        val refused = runCatching { repo.unclaimSkill(root.name) }
        assertTrue("an unclaim that cannot repay in full must be refused", refused.isFailure)
        assertEquals("the refusal must leave the ledger alone", 10L, db.profileDao().get()!!.totalXp)
        assertTrue(
            "the refusal must leave the claim standing",
            db.skillPracticeDao().claim(root.name) != null,
        )
    }

    /**
     * All-of prerequisites: Handstand-to-Bridge needs BOTH Freestanding
     * Handstand and Bridge. One of the two is not enough, and the claim that
     * completes the pair is what opens it.
     */
    @Test
    fun aSkillWithTwoPrerequisitesNeedsBoth() = runBlocking {
        repo.claimSkill("Wall Handstand")
        val handstand = repo.claimSkill("Freestanding Handstand")
        assertTrue(
            "Bridge is still missing, so the handstand claim opens nothing that needs it",
            handstand.unlockedNext.none { it.name == "Handstand-to-Bridge" },
        )
        val xpBefore = db.profileDao().get()!!.totalXp
        assertTrue(runCatching { repo.claimSkill("Handstand-to-Bridge") }.isFailure)
        assertEquals("a refused claim mints nothing", xpBefore, db.profileDao().get()!!.totalXp)

        val bridge = repo.claimSkill("Bridge")
        assertTrue(bridge.unlockedNext.any { it.name == "Handstand-to-Bridge" })
        repo.claimSkill("Handstand-to-Bridge")
        assertTrue(db.skillPracticeDao().claim("Handstand-to-Bridge") != null)
    }

    /**
     * The claim row stamps what it paid, and the unclaim refunds the stamp.
     * A row from before stamping (value 0) refunds the tier it was claimed
     * at, so the ledger lands back exactly where it started either way.
     */
    @Test
    fun unclaimRefundsTheStampedXp() = runBlocking {
        repo.claimSkill(root.name)
        assertEquals(root.xp, db.skillPracticeDao().claim(root.name)!!.value)
        repo.unclaimSkill(root.name)
        assertEquals(0L, db.profileDao().get()!!.totalXp)

        // An unstamped legacy claim, as an older build wrote it.
        db.skillPracticeDao().insert(
            com.ironvellum.app.data.db.SkillPracticeEntity(
                skillName = root.name, practicedAtMs = 1L, claimed = true,
            ),
        )
        db.profileDao().addXp(root.xp.toLong())
        repo.unclaimSkill(root.name)
        assertEquals(0L, db.profileDao().get()!!.totalXp)
    }

    private companion object {
        const val TEST_DB = "skill_prerequisite_gate_test.db"
    }
}
