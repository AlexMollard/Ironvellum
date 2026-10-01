package com.ironvellum.app.data

import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.domain.Warband
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One read of the lifter's circle, and the bonus XP that read paid out, if any. */
data class CircleRead(val circle: Warband?, val paidXp: Int?)

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

    suspend fun setGoal(goal: Int): Result<*>
}

/**
 * The one door every circle read goes through, so the weekly bonus is settled
 * wherever the circle is read: the ALLIES tab, the Veil banner, a background
 * sync. A lifter never has to open ALLIES before the Monday reset to be paid.
 */
class CircleBonus(
    private val cloud: CloudSync,
    private val accounts: AccountRepository,
    private val repository: Repository,
    private val store: CirclePayoutStore,
) {
    suspend fun read(force: Boolean = false): Result<CircleRead> =
        cloud.warband(force).map { circle ->
            val me = accounts.account.value?.userId
            val paid = if (circle != null && me != null) {
                // A failed local write must not hide the circle that was read.
                runCatching { repository.maybePayBandGoalBonus(circle, me, store) }.getOrNull()
            } else {
                null
            }
            CircleRead(circle, paid)
        }
}

class CloudCircleGateway(
    private val cloud: CloudSync,
    private val accounts: AccountRepository,
    private val bonus: CircleBonus,
) : CircleGateway {
    override val userId: Flow<String?> = accounts.account.map { it?.userId }

    override fun currentUserId(): String? = accounts.account.value?.userId

    override suspend fun read(force: Boolean): Result<CircleRead> = bonus.read(force)

    override suspend fun create(name: String): Result<*> = cloud.createWarband(name)

    override suspend fun join(code: String): Result<*> = cloud.joinWarband(code)

    override suspend fun leave(): Result<*> = cloud.leaveWarband()

    override suspend fun setGoal(goal: Int): Result<*> = cloud.setWarbandGoal(goal)
}
