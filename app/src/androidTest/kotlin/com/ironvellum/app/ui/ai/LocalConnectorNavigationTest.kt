package com.ironvellum.app.ui.ai

import android.content.ClipData
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.ironvellum.app.MainActivity
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.SharedWorkoutFixture
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.TestProfile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocalConnectorNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val oldWording = Words.themed

    @Before fun setup() {
        TestProfile.ensureSetUp()
        compose.activityRule.scenario.onActivity { Words.setThemed(it, false) }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_200)
    }

    @After fun resetWording() {
        compose.activityRule.scenario.onActivity { Words.setThemed(it, oldWording) }
    }

    @Test fun signedOutTrainOpensLocalToolsAndCloudIsAnOptionalDestination() {
        compose.onNodeWithContentDescription("Train").performClick()
        compose.mainClock.advanceTimeBy(1_200)
        shot("train_dark_plain")
        compose.onNodeWithText("AI tools").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)
        shot("tools_dark_plain")
        shown("Share with AI")
        shown("Add an AI workout")
        compose.onNodeWithText("Optional cloud services").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(8_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Cloud AI connections").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Cloud AI connections").performScrollTo().performClick()
        val status = if (Cloud.configured) R.string.ai_signed_out else R.string.ai_unconfigured
        shown(compose.activity.getString(status))
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1_200)
        shown("Share training").performClick()
        compose.waitUntil(8_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Other apps").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Other apps").performScrollTo().assertIsDisplayed()
    }

    @Test fun fileIntentsReplaceTheReviewAndRecreationRetainsOnlyTheCurrentEdits() {
        val context = compose.activity.applicationContext
        SharedWorkoutFixture(context).use { sender ->
            val uri = sender.uri
            sender.write(portableCode("First incoming"))
            sender.grant()
            fun launch(action: String, mime: String) {
                val intent = Intent(action).apply {
                    setPackage(context.packageName)
                    if (action == Intent.ACTION_VIEW) setDataAndType(uri, mime) else {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                    }
                    clipData = ClipData.newRawUri("workout", uri)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                compose.activityRule.scenario.onActivity { it.startActivity(intent) }
            }
            launch(Intent.ACTION_SEND, "application/json")
            shown("First incoming")
            shown("Workout name").performTextReplacement("Changed on this phone")
            compose.activityRule.scenario.recreate()
            shown("Changed on this phone")
            sender.write(portableCode("Second incoming"))
            launch(Intent.ACTION_VIEW, "application/octet-stream")
            shown("Second incoming")
            compose.mainClock.advanceTimeBy(1_200)
            compose.onNodeWithText("Changed on this phone").assertDoesNotExist()
            sender.revoke()
            launch(Intent.ACTION_VIEW, "text/plain")
            shown("Couldn’t read the file")
            compose.mainClock.advanceTimeBy(1_200)
            compose.onNodeWithText("Save workout").assertDoesNotExist()
            sender.grant()
            sender.write(portableCode("Retry incoming"))
            launch(Intent.ACTION_SEND, "text/plain")
            shown("Retry incoming")
            sender.write("title,start_time,exercise_title,set_index,weight_kg,reps\n" +
                "CSV fixture,2025-12-22T08:00:00,Bench Press,0,40,8\n")
            launch(Intent.ACTION_SEND, "text/plain")
            shown("Import 1 workout")
            compose.mainClock.advanceTimeBy(1_200)
            compose.onNodeWithText("Save workout").assertDoesNotExist()
        }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).also { node ->
        compose.waitUntil("the screen contains $text", 8_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText(text).fetchSemanticsNodes().size == 1
        }
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed()
    }

    private fun shot(name: String) = ShotRecorder.take(compose, "local_connector_$name")
}

internal fun portableCode(name: String, key: String = "bench press"): String = """
    {"type":"ironvellum.workout","schemaVersion":1,"name":"$name","warmup":"AUTO","cooldown":"NONE",
     "entries":[{"exerciseKey":"$key","sets":3,"reps":8,"loadKg":40.01234}]}
""".trimIndent()
