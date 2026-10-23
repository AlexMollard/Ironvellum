package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SkillPracticeEntity
import com.ironvellum.app.domain.Skills
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Passed skills: claiming a skill marks every prerequisite, across lines, as passed and pays each its claim XP;
 * a passed skill claims for real later without paying twice; unclaiming reverts what it passed unless another
 * claim still implies it; and the catch-up for claims made before passing existed is idempotent.
 */
@RunWith(AndroidJUnit4::class)
class SkillPassedTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private val line = listOf("Dead Hang", "Scapular Pull", "Australian Pull-up", "Negative Pull-up", "Pull-up")

    private suspend fun xp() = db.profileDao().get()!!.totalXp

    private suspend fun rows() = db.skillPracticeDao().observeAll().first().filter { it.claimed }

    private fun xpOf(names: List<String>) = names.sumOf { Skills.forName(it)!!.xp }

    @Test
    fun claimingWeightedPullUpWithNothingClaimedPassesTheLineAndPaysTheSum() = runBlocking {
        val result = repo.claimSkill("Weighted Pull-up")

        val expected = xpOf(line + "Weighted Pull-up")
        assertEquals(expected, result.xpAwarded)
        assertEquals(expected.toLong(), xp())
        assertEquals(line, result.passed.map { it.name })
        assertEquals(line.toSet(), rows().filter { it.passed }.map { it.skillName }.toSet())
        assertEquals(listOf("Weighted Pull-up"), rows().filter { !it.passed }.map { it.skillName })
        assertTrue("Archer Pull-up opens", result.unlockedNext.any { it.name == "Archer Pull-up" })
        // Each passed row is stamped with what it paid, so giving it back refunds exactly that.
        line.forEach { assertEquals(Skills.forName(it)!!.xp, db.skillPracticeDao().claim(it)!!.value) }
    }

    @Test
    fun aClaimPassesPrerequisitesAcrossLines() = runBlocking {
        repo.claimSkill("Tuck Front Lever")
        val passed = rows().filter { it.passed }.map { it.skillName }.toSet()
        assertTrue(passed.containsAll(setOf("Front Row Hold", "Pull-up", "Hollow Hold", "Dead Hang")))
    }

    @Test
    fun claimingAPassedSkillLaterPaysNothingMore() = runBlocking {
        repo.claimSkill("Weighted Pull-up")
        val before = xp()

        val result = repo.claimSkill("Pull-up")

        assertEquals(0, result.xpAwarded)
        assertEquals(before, xp())
        val row = db.skillPracticeDao().claim("Pull-up")!!
        assertFalse("now a real claim", row.passed)
        assertEquals("what it paid is still the stamp", Skills.forName("Pull-up")!!.xp, row.value)
        assertEquals("one row, not two", 1, rows().count { it.skillName == "Pull-up" })
        assertTrue("a real claim cannot be repeated", runCatching { repo.claimSkill("Pull-up") }.isFailure)
    }

    @Test
    fun unclaimingTheHarderSkillRevertsWhatItPassedAndRefundsIt() = runBlocking {
        repo.claimSkill("Weighted Pull-up")

        repo.unclaimSkill("Weighted Pull-up")

        assertEquals("every XP the claim paid is back", 0L, xp())
        assertTrue(rows().isEmpty())
    }

    @Test
    fun aSkillAnotherClaimStillImpliesStaysPassed() = runBlocking {
        repo.claimSkill("Weighted Pull-up")
        repo.claimSkill("Archer Pull-up")
        val afterArcher = xp()

        repo.unclaimSkill("Weighted Pull-up")

        assertEquals("only its own XP comes back", afterArcher - Skills.forName("Weighted Pull-up")!!.xp, xp())
        assertEquals(line.toSet(), rows().filter { it.passed }.map { it.skillName }.toSet())
        assertEquals(listOf("Archer Pull-up"), rows().filter { !it.passed }.map { it.skillName })
    }

    @Test
    fun aRealClaimStillImpliedByAnotherStandsDownToPassed() = runBlocking {
        // Pull-up claimed for real under Weighted Pull-up; Scapular Pull stays passed in between.
        repo.claimSkill("Weighted Pull-up")
        repo.claimSkill("Scapular Pull")
        val before = xp()

        repo.unclaimSkill("Scapular Pull")

        assertEquals("it paid nothing of its own, so nothing comes back", before, xp())
        assertTrue(db.skillPracticeDao().claim("Scapular Pull")!!.passed)
        repo.unclaimSkill("Dead Hang")
        assertTrue("a passed skill cannot be given back on its own", db.skillPracticeDao().claim("Dead Hang")!!.passed)
        assertEquals(before, xp())
    }

    @Test
    fun theCascadeIsRefusedWholeWhenTheLedgerCannotPayIt() = runBlocking {
        repo.claimSkill("Weighted Pull-up")
        val total = xp()
        db.profileDao().addXp(-(total - 10))
        assertEquals(10L, xp())

        val refused = runCatching { repo.unclaimSkill("Weighted Pull-up") }

        assertTrue("not enough XP left to pay it all back", refused.isFailure)
        assertEquals(10L, xp())
        assertEquals(line.size + 1, rows().size)
        assertTrue("nothing reverted", rows().count { it.passed } == line.size)
    }

    @Test
    fun aRealClaimThatIsRequiredStillBlocksTheUnclaim() = runBlocking {
        repo.claimSkill("Weighted Pull-up")
        repo.claimSkill("Pull-up")

        val refused = runCatching { repo.unclaimSkill("Pull-up") }

        assertTrue("Weighted Pull-up is claimed on top of it", refused.isFailure)
    }

    @Test
    fun theCatchUpPassesWhatClaimsFromBeforePassingImplyAndIsIdempotent() = runBlocking {
        // A ledger as the previous version left it: Weighted Pull-up claimed with nothing under it, its XP paid.
        db.skillPracticeDao().insert(
            SkillPracticeEntity(skillName = "Weighted Pull-up", practicedAtMs = 1L, claimed = true, value = 480),
        )
        db.skillPracticeDao().insert(
            SkillPracticeEntity(skillName = "Pull-up", practicedAtMs = 2L, claimed = true, value = 360),
        )
        db.profileDao().addXp(840)

        val first = repo.reconcileSkillPasses()

        assertEquals(listOf("Dead Hang", "Scapular Pull", "Australian Pull-up", "Negative Pull-up"), first.map { it.name })
        assertEquals(840L + xpOf(first.map { it.name }), xp())
        assertTrue("the claims it found are untouched", rows().filter { !it.passed }.map { it.skillName }.toSet() == setOf("Weighted Pull-up", "Pull-up"))
        val snapshot = rows().map { Triple(it.skillName, it.passed, it.value) }.toSet()
        val xpAfter = xp()

        assertTrue(repo.reconcileSkillPasses().isEmpty())
        assertEquals("a second run pays nothing", xpAfter, xp())
        assertEquals(snapshot, rows().map { Triple(it.skillName, it.passed, it.value) }.toSet())
    }

    private companion object {
        const val TEST_DB = "skill_passed_test.db"
    }
}
