package com.ironvellum.app.domain

import com.ironvellum.app.domain.RoutineCode.SharedEntry
import com.ironvellum.app.domain.RoutineCode.SharedWorkout
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineCodeTest {

    private val routine = listOf(
        SharedWorkout(
            name = "Pull day",
            note = "Rest 2 min \u2014 keep \"strict\"",
            scheduledDay = 1,
            entries = listOf(
                SharedEntry("Weighted Pull-up", 4, 6, 20.0, "strict"),
                SharedEntry("Plank", 3, 45, null, ""),
            ),
        ),
        SharedWorkout(
            name = "Legs",
            note = "",
            scheduledDay = null,
            entries = listOf(SharedEntry("Back Squat", 5, 5, 100.5, "tempo, pause")),
        ),
    )

    /** Produced once in the v1 format for [routine]. If this stops decoding,
     *  every code already sitting in someone's chat history just broke -
     *  bump the prefix instead of editing the string. */
    private val pinned = "IVR1:eNotjrEOgkAQRH9lM_VKDgQLSmsLooUFUFxgowQ4Ue40hvDvHkKm2uTtm5nwRhoyPkjzCQYpMtd1VOsvGNafZxktRdQ3hgoXqTCmVmSgAqN9NZUt4Ln6rxCvyHGV5na3UtPi2bkBHPOBIxUo3l5Qco6s06YF7zlO2HiSgbKceZ1wktu41a_2lVgLjrpq6fJ02oITn1CpIPGw9MODadBulEVVzj8JBT9x"

    private fun failureOf(text: String): String {
        val result = RoutineCode.decode(text)
        assertTrue("expected failure for: ${text.take(40)}", result.isFailure)
        return result.exceptionOrNull()!!.message.orEmpty()
    }

    /** A well-formed code around arbitrary JSON, bypassing [RoutineCode.encode]. */
    private fun rawCode(json: String): String {
        val deflater = Deflater()
        deflater.setInput(json.toByteArray())
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return RoutineCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    @Test
    fun `round trip preserves every field`() {
        val code = RoutineCode.encode(routine)
        assertTrue(code.startsWith(RoutineCode.PREFIX))
        assertTrue("padding or unsafe chars", code.none { it == '=' || it == '+' || it == '/' || it.isWhitespace() })
        assertEquals(routine, RoutineCode.decode(code).getOrThrow())
    }

    @Test
    fun `round trip keeps null day and null load distinct from zero`() {
        val decoded = RoutineCode.decode(RoutineCode.encode(routine)).getOrThrow()
        assertNull(decoded[1].scheduledDay)
        assertNull(decoded[0].entries[1].targetWeightKg)
        assertEquals(20.0, decoded[0].entries[0].targetWeightKg!!, 0.0)
    }

    @Test
    fun `code inside chat text decodes`() {
        val code = RoutineCode.encode(routine)
        val chat = "hey, try my routine!\n\nIronvellum routine \u2014 paste into Train\n$code\n\nlmk how legs feel :)"
        assertEquals(routine, RoutineCode.decode(chat).getOrThrow())
        assertEquals(routine, RoutineCode.decode("  $code.").getOrThrow())
    }

    @Test
    fun `pinned code decodes identically later`() {
        assertEquals(routine, RoutineCode.decode(pinned).getOrThrow())
    }

    @Test
    fun `text without a code says so`() {
        assertTrue(failureOf("just some chat text").contains("No cycle code found"))
        assertTrue(failureOf("").contains("No cycle code found"))
    }

    @Test
    fun `a different prefix version is named as a version problem`() {
        assertTrue(failureOf(pinned.replace("IVR1:", "IVR2:")).contains("different version"))
    }

    @Test
    fun `truncated code fails as damaged`() {
        val cut = pinned.substring(0, pinned.length / 2)
        assertTrue(failureOf(cut).contains("damaged"))
    }

    @Test
    fun `valid base64 that is not deflate fails as damaged`() {
        assertTrue(failureOf("IVR1:AAAAAAAAAAAA").contains("damaged"))
        assertTrue(failureOf("IVR1:").contains("empty"))
    }

    @Test
    fun `deflate of something that is not a routine fails cleanly`() {
        assertTrue(failureOf(rawCode("[1,2,3]")).isNotBlank())
        assertTrue(failureOf(rawCode("{\"v\":1,\"w\":[]}")).contains("no rites"))
        assertTrue(failureOf(rawCode("{\"v\":9,\"w\":[]}")).contains("different version"))
        val badSets = "{\"v\":1,\"w\":[{\"n\":\"A\",\"t\":\"\",\"d\":null,\"e\":[[\"Plank\",0,5,null,\"\"]]}]}"
        assertTrue(failureOf(rawCode(badSets)).contains("set or rep"))
        val badDay = "{\"v\":1,\"w\":[{\"n\":\"A\",\"t\":\"\",\"d\":9,\"e\":[]}]}"
        assertTrue(failureOf(rawCode(badDay)).contains("day"))
    }

    @Test
    fun `oversize payload is refused without inflating it all`() {
        // ~200 KB of spaces deflates to a few hundred bytes: the classic bomb.
        val bomb = rawCode("{\"v\":1,\"w\":[" + " ".repeat(200_000) + "]}")
        assertTrue(bomb.length < 2_000)
        assertTrue(failureOf(bomb).contains("too large"))
    }

    @Test
    fun `more than 14 workouts or 20 exercises is refused`() {
        val entry = SharedEntry("Plank", 3, 30, null, "")
        val workout = SharedWorkout("W", "", null, listOf(entry))
        assertEquals(14, RoutineCode.decode(RoutineCode.encode(List(14) { workout })).getOrThrow().size)
        assertTrue(failureOf(RoutineCode.encode(List(15) { workout })).contains("14 rites"))
        val twenty = workout.copy(entries = List(20) { entry })
        assertEquals(20, RoutineCode.decode(RoutineCode.encode(listOf(twenty))).getOrThrow()[0].entries.size)
        val twentyOne = workout.copy(entries = List(21) { entry })
        assertTrue(failureOf(RoutineCode.encode(listOf(twentyOne))).contains("20 exercises"))
    }

    @Test
    fun `the sender is refused what the receiver would refuse`() {
        val entry = SharedEntry("Plank", 3, 30, null, "")
        val workout = SharedWorkout("Pull", "", null, listOf(entry))
        assertNull(RoutineCode.shareRefusal(List(14) { workout }))
        assertNull(RoutineCode.shareRefusal(listOf(workout.copy(entries = List(20) { entry }))))
        assertTrue(RoutineCode.shareRefusal(List(15) { workout })!!.contains("14 rites"))
        val refused = RoutineCode.shareRefusal(listOf(workout.copy(entries = List(21) { entry })))!!
        assertTrue(refused.contains("20 exercises") && refused.contains("Pull") && refused.contains("21"))
    }

    @Test
    fun `hostile nesting fails instead of overflowing the stack`() {
        assertTrue(failureOf(rawCode("[".repeat(5_000))).isNotBlank())
    }
}
