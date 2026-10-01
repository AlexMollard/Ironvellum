package com.ironvellum.app.data

import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.CircleBonusDto
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.domain.Circle
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One read of the lifter's circle, and the bonus that read paid out, if any. */
data class CircleRead(val circle: Circle?, val paid: CircleBonusPaid?)

/**
 * What the circle screen needs from the cloud. An interface so the view model
 * runs against a fake in a unit test; [CloudCircleGateway] is the real one.
 */
interface CircleGateway {
    /** The signed-in lifter's id, or null; follows sign-in and sign-out. */
    val userId: Flow<String?>

    fun currentUserId(): String?

    /** Reads the circle and settles the weekly bonus: every circle read goes through here. */
    suspend fun read(force: Boolean = false): Result<CircleRead>

    suspend fun create(name: String): Result<*>

    suspend fun join(code: String): Result<*>

    suspend fun leave(): Result<*>

    suspend fun setGoal(perMember: Int): Result<*>

    suspend fun rename(name: String): Result<*>

    suspend fun rotateCode(): Result<*>

    suspend fun removeMember(userId: String): Result<*>
}

/**
 * The one door every circle read goes through, so the weekly bonus is paid
 * wherever the circle is read: the ALLIES tab, the Veil banner, a background
 * sync. A lifter never has to open ALLIES before the Monday reset to be paid.
 *
 * The bonus itself is the server's settled record of the week
 * ([CloudSync.circleBonuses]); [Repository.payCircleBonus] pays each
 * (lifter, week) once, atomically.
 */
class CircleBonus internal constructor(
    private val readCircle: suspend (force: Boolean) -> Result<Circle?>,
    private val myUserId: () -> String?,
    private val owedWeeks: suspend () -> Result<List<CircleBonusWeek>>,
    private val pay: suspend (me: String, owed: List<CircleBonusWeek>) -> CircleBonusPaid?,
    private val store: CirclePayoutStore,
    private val clock: () -> Long,
) {
    // The app's wiring. The primary constructor takes the four calls this class
    // makes, so a unit test can stand in for the cloud, the account and the
    // repository without a database or a network.
    constructor(
        cloud: CloudSync,
        accounts: AccountRepository,
        repository: Repository,
        store: CirclePayoutStore,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        readCircle = { force -> cloud.circle(force) },
        myUserId = { accounts.account.value?.userId },
        owedWeeks = { cloud.circleBonuses().map { weeks -> weeks.map(CircleBonusDto::toWeek) } },
        pay = { me, owed -> repository.payCircleBonus(me, owed, store) },
        store = store,
        clock = clock,
    )

    @Volatile
    private var lastCheckedMs = 0L

    suspend fun read(force: Boolean = false): Result<CircleRead> {
        val circle = readCircle(force).getOrElse { return Result.failure(it) }
        val me = myUserId()
        val paid = if (me != null) settle(me, circle != null, force) else null
        return Result.success(CircleRead(circle, paid))
    }

    /**
     * Asks the server what is owed (which also settles whatever is due) and
     * pays it. Skipped for a lifter who has never been in a circle, and
     * rate-limited the way the circle read itself is, so a screen that reads
     * the circle often does not hit the bonus every time.
     */
    private suspend fun settle(me: String, inCircle: Boolean, force: Boolean): CircleBonusPaid? {
        if (inCircle) store.markInCircle(me)
        if (!inCircle && !store.wasInCircle(me)) return null
        val now = clock()
        if (!force && now - lastCheckedMs < CHECK_EVERY_MS) return null
        lastCheckedMs = now
        // A failed bonus lookup or local write must not hide the circle that was read.
        return runCatching { pay(me, owedWeeks().getOrThrow()) }
            .onFailure { if (it is CancellationException) throw it }.getOrNull()
    }

    internal companion object {
        const val CHECK_EVERY_MS = 30_000L
    }
}

private fun CircleBonusDto.toWeek() = CircleBonusWeek(week = week, days = days, circleName = circleName)

class CloudCircleGateway(
    private val cloud: CloudSync,
    private val accounts: AccountRepository,
    private val bonus: CircleBonus,
) : CircleGateway {
    override val userId: Flow<String?> = accounts.account.map { it?.userId }

    override fun currentUserId(): String? = accounts.account.value?.userId

    override suspend fun read(force: Boolean): Result<CircleRead> = bonus.read(force)

    override suspend fun create(name: String): Result<*> = cloud.createCircle(name)

    override suspend fun join(code: String): Result<*> = cloud.joinCircle(code)

    override suspend fun leave(): Result<*> = cloud.leaveCircle()

    override suspend fun setGoal(perMember: Int): Result<*> = cloud.setCircleGoal(perMember)

    override suspend fun rename(name: String): Result<*> = cloud.renameCircle(name)

    override suspend fun rotateCode(): Result<*> = cloud.rotateCircleCode()

    override suspend fun removeMember(userId: String): Result<*> = cloud.removeCircleMember(userId)
}
