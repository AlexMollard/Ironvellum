package com.monarch.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.domain.Skills
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

    private lateinit var db: MonarchDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = MonarchDatabase.create(context, TEST_DB)
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

    private companion object {
        const val TEST_DB = "skill_prerequisite_gate_test.db"
    }
}
