package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.Pattern
import com.ironvellum.app.domain.PatternScore
import com.ironvellum.app.domain.Rank
import com.ironvellum.app.domain.RankBreakdown
import com.ironvellum.app.ui.components.RankSheet
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.titles.SkillTreeGraph
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressDetailsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rankSeparatesCurrentStandingFromConcreteNextTargets() {
        compose.activityRule.scenario.onActivity { ShotRecorder.darkWindow(it) }
        compose.setContent {
            IronvellumTheme {
                RankSheet(
                    RankBreakdown(
                        Rank.NOVICE, 3,
                        listOf(PatternScore(Pattern.PUSH, 3, "Bench Press 5 × 60 kg", "+2.5 kg on Bench Press for 5 reps", "Push-up: 20 reps")),
                        Rank.INTERMEDIATE, 2, 0.25f,
                    ),
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("2 steps to Intermediate").assertIsDisplayed()
        compose.onNodeWithText("Novice, from Bench Press 5 × 60 kg").assertIsDisplayed()
        ShotRecorder.take(compose, "rank-overview")
        compose.mainClock.autoAdvance = true
        compose.onNode(hasText(Pattern.PUSH.label) and hasClickAction()).performClick()
        compose.onNodeWithText("Next lift: +2.5 kg on Bench Press for 5 reps").assertIsDisplayed()
        compose.onNodeWithText("Next technique: Push-up: 20 reps").assertIsDisplayed()
        ShotRecorder.take(compose, "rank-next-targets")
    }

    @Test
    fun crossPathPrerequisiteOpensItsNamedSkill() {
        var opened: String? = null
        compose.activityRule.scenario.onActivity { ShotRecorder.darkWindow(it) }
        compose.setContent {
            IronvellumTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SkillTreeGraph("Pull", emptySet(), onSelect = {}, onOpenPrerequisite = { opened = it })
                }
            }
        }
        ShotRecorder.take(compose, "skill-tree-pull")
        compose.mainClock.autoAdvance = true
        // The linked prerequisite belongs to Core, not the Pull skill that requires it.
        compose.onNodeWithText("needs L-sit (Core)").performScrollTo().performClick()
        val need = "L-sit"
        assertEquals(need, opened)
        ShotRecorder.take(compose, "skill-tree-linked-prerequisites")
    }
}
