package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which scheme suits a lift, and the load a back-off takes off the body that lift really moves. */
class SchemeFitTest {

    private fun suggested(name: String, bodyweight: Boolean = false, inSuperset: Boolean = false) =
        SchemeSuggestion.forLift(name, inSuperset, bodyweight).type

    // ---- the suggested scheme

    @Test
    fun `a mobility drill is straight, never drops`() {
        assertEquals(SchemeType.STRAIGHT, suggested("Knee-to-Wall Dorsiflexion", bodyweight = true))
        assertEquals(SchemeType.STRAIGHT, suggested("Knee-to-Wall Dorsiflexion"))
    }

    @Test
    fun `balance and technique limited one sided bodyweight lifts are straight`() {
        for (name in listOf("Pistol Squat", "Shrimp Squat", "Box Pistol Squat", "Elevated Pistol Squat", "Advanced Shrimp Squat", "Dragon Squat")) {
            assertEquals(name, SchemeType.STRAIGHT, suggested(name, bodyweight = true))
        }
        assertEquals(SchemeType.STRAIGHT, suggested("Archer Push-up", bodyweight = true))
        assertEquals(SchemeType.STRAIGHT, suggested("One-Arm Push-up", bodyweight = true))
    }

    @Test
    fun `loaded compounds stay top plus back-offs and isolation stays drops`() {
        assertEquals(SchemeType.TOP_BACKOFF, suggested("Back Squat"))
        assertEquals(SchemeType.TOP_BACKOFF, suggested("Bench Press"))
        assertEquals(SchemeType.TOP_BACKOFF, suggested("Pull-up", bodyweight = true))
        assertEquals(SchemeType.TOP_BACKOFF, suggested("Push-up", bodyweight = true))
        assertEquals(SchemeType.TOP_BACKOFF, suggested("Dumbbell Row"))
        assertEquals(SchemeType.DROPS, suggested("Lateral Raise"))
        // A calf raise has no leading muscle by design but is isolation work, not a mobility drill.
        assertEquals(SchemeType.DROPS, suggested("Seated Calf Raise"))
        assertEquals(SchemeType.STRAIGHT, suggested("Lateral Raise", inSuperset = true))
    }

    // ---- the body a back-off comes off

    private fun lift(name: String, step: Double = 2.5, worn: LoadKit? = null, body: Double = 80.0) = LiftLoading(
        stepKg = step, worn = worn, bodyweightKg = body, bodyweightLift = true, bodyShare = MovementDifficulty.bodyShare(name),
    )

    @Test
    fun `a push-up moves about two thirds of the body and a pull-up or dip all of it`() {
        assertEquals(0.65, MovementDifficulty.bodyShare("Push-up"), 1e-9)
        assertEquals(0.65, MovementDifficulty.bodyShare("Archer Push-up"), 1e-9)
        assertEquals(1.0, MovementDifficulty.bodyShare("Pull-up"), 1e-9)
        assertEquals(1.0, MovementDifficulty.bodyShare("Dip"), 1e-9)
        assertEquals(1.0, MovementDifficulty.bodyShare("Pike Push-up"), 1e-9)
        assertEquals(1.0, MovementDifficulty.bodyShare("One-Arm Push-up"), 1e-9)
        assertEquals(1.0, MovementDifficulty.bodyShare("Back Squat"), 1e-9)
    }

    @Test
    fun `an archer push-up top set at plus 10 kg gives a back-off that still has load on it`() {
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF)
        val archer = lift("Archer Push-up")
        // Moved: 0.65 x 80 = 52 + 10 = 62 kg; 10% lighter 55.8; added 55.8 - 52 = 3.8, down to 2.5 on the 2.5 kg grid.
        assertEquals(2.5, SchemePlan.backoff(10.0, 5, scheme, archer).weightKg!!, 1e-9)
        // Taken off all 80 kg of body it was +1 kg, under a plate: bodyweight.
        assertNull(SchemePlan.backoff(10.0, 5, scheme, archer.copy(bodyShare = 1.0)).weightKg)
        // The same on a vest of 1 kg blocks lands on 3 kg.
        val vested = archer.copy(worn = LoadKit(vestMaxKg = 20.0, vestBlockKg = 1.0))
        assertEquals(3.0, SchemePlan.backoff(10.0, 5, scheme, vested).weightKg!!, 1e-9)
    }

    @Test
    fun `a pull-up top set at plus 22 kg at 80 kg of body is unchanged`() {
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF)
        // (80 + 22) x 0.9 = 91.8; added 11.8, down to 10 on the 2.5 kg grid.
        assertEquals(10.0, SchemePlan.backoff(22.0, 4, scheme, lift("Pull-up")).weightKg!!, 1e-9)
        assertEquals(10.0, SchemePlan.backoff(22.0, 4, scheme, lift("Dip")).weightKg!!, 1e-9)
    }

    @Test
    fun `the preview, the live fill and a switch to fixed all take the same load`() {
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF)
        val push = lift("Push-up")
        val preview = SchemePlan.plan(scheme, 3, 5, 10.0, push).last().weightKg
        assertEquals(2.5, preview!!, 1e-9)
        assertEquals(preview, SchemePlan.backoff(10.0, 5, scheme, push).weightKg)
        assertEquals(preview, SchemePlan.seedFixedBackoff(10.0, scheme, push), 1e-9)
    }

    @Test
    fun `a drop comes off the moved body too`() {
        // 0.65 x 80 = 52 + 20 = 72; 20% lighter 57.6; added 5.6, down to 5.
        assertEquals(5.0, SchemePlan.drop(20.0, 20, lift("Push-up"))!!, 1e-9)
    }
}
