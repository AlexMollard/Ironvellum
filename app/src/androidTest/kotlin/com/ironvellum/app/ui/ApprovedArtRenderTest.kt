package com.ironvellum.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.Crests
import com.ironvellum.app.domain.RelicHouse
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.TitleRarity
import com.ironvellum.app.ui.components.CrestPlate
import com.ironvellum.app.ui.components.HOUSE_EMBLEMS
import com.ironvellum.app.ui.components.HouseEmblem
import com.ironvellum.app.ui.components.HouseRelicSigil
import com.ironvellum.app.ui.components.RELIC_SPECS
import com.ironvellum.app.ui.dashboard.LedgerMotif
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.titles.GlyphFamily
import com.ironvellum.app.ui.titles.SkillGlyph
import com.ironvellum.app.ui.titles.DeedSeal
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercise the real Compose renderers, including all catalogue paths, and save review sheets. */
@RunWith(AndroidJUnit4::class)
class ApprovedArtRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var layer: GraphicsLayer

    private fun gallery(prefix: String, names: List<String>, art: @Composable (String) -> Unit) {
        val page = mutableIntStateOf(0)
        compose.setContent {
            IronvellumTheme {
                layer = rememberGraphicsLayer()
                Column(Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }.background(Color(0xFF0C0C0B)).testTag("gallery")) {
                    names.chunked(12)[page.intValue].chunked(4).forEach { row ->
                        Row {
                            row.forEach { name ->
                                Column {
                                    Box(Modifier.size(80.dp).testTag(name)) { art(name) }
                                    Text(name.substringAfterLast('.'), color = Color.White, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        names.chunked(12).forEachIndexed { index, entries ->
            compose.runOnIdle { page.intValue = index }
            compose.waitForIdle()
            val snapshot = runBlocking { layer.toImageBitmap() }
            val pixels = snapshot.toPixelMap()
            val root = compose.onNodeWithTag("gallery").fetchSemanticsNode().boundsInRoot
            entries.forEach { name ->
                val bounds = compose.onNodeWithTag(name).fetchSemanticsNode().boundsInRoot.translate(-root.left, -root.top)
                var visible = 0
                for (y in bounds.top.toInt() until bounds.bottom.toInt().coerceAtMost(pixels.height)) for (x in bounds.left.toInt() until bounds.right.toInt().coerceAtMost(pixels.width)) {
                    val c = pixels[x, y]
                    if (maxOf(c.red, c.green, c.blue) > 0.25f) visible++
                }
                assertTrue("$name rendered no readable strokes", visible > 20)
            }
            val image = snapshot.asAndroidBitmap()
            val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            File(dir, "$prefix-$index.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun everyCrestRenders() = gallery("approved-crests", Crests.ALL.map { it.id }) {
        CrestPlate(it, Modifier.size(80.dp))
    }

    @Test fun relicsAndHouseEmblemsRender() = gallery("approved-relics", RELIC_SPECS.keys.toList() + HOUSE_EMBLEMS.keys.map { "house.${it.id}" }) {
        if (it.startsWith("house.")) HouseEmblem(RelicHouse.byId(it.substringAfter('.'))!!, Color.White, Modifier.size(80.dp), complete = true)
        else HouseRelicSigil(it, RewardRarity.Epic, Modifier.size(80.dp))
    }

    @Test fun deedFramesLeaveEveryCategoryVisible() {
        val categories = listOf("Trials", "Level", "Volume", "Strength", "Steps", "Recovery", "Mastery", "Activities")
        gallery("approved-deeds", TitleRarity.entries.flatMap { r -> categories.map { "${r.name}.$it" } }) {
            DeedSeal(TitleRarity.valueOf(it.substringBefore('.')), category = it.substringAfter('.'), size = 80.dp)
        }
    }

    @Test fun refinedLedgerRenders() = gallery("approved-ledger", listOf("ledger")) { LedgerMotif() }
    @Test fun smallMarksRemainVisible() = gallery("approved-small", listOf("aurora", "void", "volume", "relic", "mobility")) {
        when (it) {
            "aurora", "void" -> CrestPlate(it, Modifier.size(40.dp))
            "volume" -> DeedSeal(TitleRarity.Masterwork, category = "Volume", size = 40.dp)
            "relic" -> HouseRelicSigil("craft.compass", RewardRarity.Epic, Modifier.size(30.dp))
            "mobility" -> SkillGlyph(GlyphFamily.MOBILITY, Color(0xFFA3A099), Modifier.size(24.dp))
        }
    }
}
