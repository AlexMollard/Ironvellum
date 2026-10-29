package com.ironvellum.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ironvellum.app.R
import com.ironvellum.app.StringsXml
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.components.CREST_SHAPES
import com.ironvellum.app.ui.titles.SealPoint
import com.ironvellum.app.ui.titles.sealGeometry
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden assets are evaluated from the same code and resources as the phone. */
class WebAssetsGoldenTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "supabase/migrations").isDirectory }

    private fun json(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to json(it.value) })
        is Iterable<*> -> JsonArray(value.map(::json))
        is FloatArray -> JsonArray(value.map(::json))
        else -> error("Unsupported JSON value: ${value.javaClass}")
    }
    private fun hex(c: Color) = accentHex(c.toArgb())
    private fun paper(p: Paper): Map<String, Any> = linkedMapOf(
        "Abyss" to hex(p.Abyss), "Vault" to hex(p.Vault), "VaultHigh" to hex(p.VaultHigh), "Raised" to hex(p.Raised),
        "Ink" to hex(p.Ink), "InkMuted" to hex(p.InkMuted), "Rune" to hex(p.Rune),
        "Bracket" to hex(p.Bracket), "DangerRed" to hex(p.DangerRed), "Caution" to hex(p.Caution),
        "PlateGround" to hex(p.PlateGround), "PlateEdge" to hex(p.PlateEdge), "PlateDeep" to hex(p.PlateDeep),
        "Silhouette" to hex(p.Silhouette), "SilhouetteGround" to hex(p.SilhouetteGround),
        "UnheldName" to hex(p.UnheldName), "UnheldNameBright" to hex(p.UnheldNameBright),
        "RingTick" to hex(p.RingTick), "FillGlow" to hex(p.FillGlow), "LevelGlow" to hex(p.LevelGlow),
        "OnGoldFill" to hex(p.OnGoldFill), "Scrim" to "#%08X".format(p.Scrim.toArgb()),
        "wells" to linkedMapOf("top" to hex(p.Wells.top), "bottom" to hex(p.Wells.bottom),
            "disabledTop" to hex(p.Wells.disabledTop), "disabledBottom" to hex(p.Wells.disabledBottom),
            "quietTop" to hex(p.Wells.quietTop), "quietBottom" to hex(p.Wells.quietBottom),
            "readout" to hex(p.Wells.readout), "flat" to hex(p.Wells.flat))
    )

    private fun tokens(): Map<String, Any> {
        val old = IronvellumColors.light
        try {
            val modes = linkedMapOf<String, Any>()
            for (light in listOf(false, true)) {
                IronvellumColors.light = light
                val ramps = CrestTone.entries.associate { tone ->
                    val metal = Metal.of(tone)
                    val method = Metal::class.java.getDeclaredMethod("stops", Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    method.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val stops = method.invoke(metal, 1f, false) as Array<Pair<Float, Color>>
                    tone.name to mapOf("colors" to stops.map { hex(it.second) }, "stops" to stops.map { it.first }, "prism" to metal.prism)
                }
                modes[if (light) "light" else "dark"] = mapOf(
                    "paper" to paper(if (light) LightPaper else DarkPaper),
                    "rarity" to TitleRarity.entries.associate { it.name to hex(RarityTint.of(it)) }, "metal" to ramps)
            }
            modes["ladder"] = ArmyClass.LADDER.map { mapOf("level" to it.level, "title" to it.title) }
            return modes
        } finally { IronvellumColors.light = old }
    }

    private fun accents(): Map<String, Any> = listOf(false, true).associate { light ->
        val presets = if (light) AccentPresets.light else AccentPresets.entries
        (if (light) "light" else "dark") to presets.map { preset ->
            Words.useForTest(false); val plain = preset.name
            Words.useForTest(true); val themed = preset.name
            val s = WebAppearance.accentSet(preset.palette, light, presets)
            fun colour(s: String) = (0xff000000L or s.removePrefix("#").toLong(16)).toInt()
            mapOf("id" to preset.id, "name" to mapOf("plain" to plain, "themed" to themed),
                "tokens" to mapOf("preset" to s.preset, "primary" to s.primary, "secondary" to s.secondary,
                    "bright" to s.bright, "deep" to s.deep, "muted" to s.muted, "panelTint" to s.panelTint,
                    "panelTintBorder" to s.panelTintBorder, "fill" to s.fill, "text" to s.text, "tint" to s.tint,
                    "border" to s.border, "reward" to s.reward, "onPrimary" to s.onPrimary, "onSecondary" to s.onSecondary),
                "contrast" to mapOf("primary" to contrastRatio(colour(s.fill), colour(s.onPrimary)),
                    "secondary" to contrastRatio(colour(s.reward), colour(s.onSecondary))))
        }
    }

    private fun words(): Map<String, Any> {
        val entries = StringsXml.read(File(root, "app/src/main/res/values/strings.xml"))
            .filter { it.text != null }.associateBy { it.name }
        val requested = File(root, "web/words-keys.txt").readLines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        val keys = (entries.keys.filter { key -> !key.endsWith("__t") &&
            listOf("concept_", "always_", "accent_", "cloud_error_").any(key::startsWith) } + requested).distinct().sorted()
        val result = linkedMapOf<String, Any>()
        for (key in keys) {
            require(key in entries) { "Missing web string $key" }
            val id = R.string::class.java.getField(key).getInt(null)
            val themedId = runCatching { R.string::class.java.getField(key + "__t").getInt(null) }.getOrDefault(id)
            fun resolve(themed: Boolean): String {
                Words.useForTest(themed)
                return Words.text(UiText.Themed(UiText.Res(id), UiText.Res(themedId)))
            }
            result[key] = mapOf("plain" to resolve(false), "themed" to resolve(true))
        }
        val method = Words::class.java.getDeclaredMethod("getServerTerms").apply { isAccessible = true }
        val terms = method.invoke(Words) as List<*>
        result["serverTerms"] = terms.map { term ->
            val obj = requireNotNull(term)
            fun field(name: String): Any = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj)
            val concept = field("concept") as Words.Concept
            mapOf("themed" to field("themed"), "themedPl" to field("themedPl"),
                "plain" to concept.plainText("s"), "plainPl" to concept.plainText("pl"))
        }
        return result
    }

    private fun catalogue(): Map<String, Any> {
        Words.useForTest(false)
        val crests = Crests.ALL.map { c -> mapOf("id" to c.id, "name" to c.name, "group" to c.group.name,
            "kind" to c.kind.name, "perk" to c.perk, "short" to c.short, "art" to c.art.name, "ring" to c.ring.name,
            "double" to c.double, "shapes" to CREST_SHAPES.getValue(c.id).map { s -> mapOf("d" to s.d,
                "wash" to s.wash, "strokeAlpha" to s.strokeAlpha, "width" to s.width, "roundCap" to s.roundCap,
                "roundJoin" to s.roundJoin, "dash" to s.dash, "darkGround" to s.darkGround) }) }
        val deeds = Titles.ALL.map { d -> mapOf("id" to d.id, "name" to d.name, "rarity" to d.rarity.name,
            "category" to Titles.category(d.rule), "description" to d.description, "descriptionFemale" to d.descriptionFemale) }
        fun points(items: List<SealPoint>) = items.map { listOf(it.x, it.y) }
        val seals = TitleRarity.entries.associate { r ->
            val s = sealGeometry(r)
            r.name to mapOf("outer" to points(s.outer), "rim" to points(s.rim), "leaves" to s.leaves,
                "branches" to s.branches, "core" to points(s.core), "glyphSize" to s.glyphSize)
        }
        val owner = Class.forName("com.ironvellum.app.ui.titles.DeedSealKt")
        fun field(name: String) = owner.getDeclaredField(name).apply { isAccessible = true }.get(null)
        @Suppress("UNCHECKED_CAST")
        val glyphs = (field("GlyphPaths") as Map<String, List<String>>).toMutableMap()
        @Suppress("UNCHECKED_CAST")
        glyphs[Words.trial.cpl] = field("TrialGlyph") as List<String>
        return mapOf("crests" to crests, "deeds" to deeds, "seals" to seals, "glyphs" to glyphs,
            "lifts" to Lift.entries.map { mapOf("wire" to it.wire, "label" to it.label, "group" to it.group.name, "kind" to it.kind.name) },
            "tiers" to (0..LiftBoards.MAX_STEP).map(LiftBoards::tierName))
    }

    private fun output(name: String, value: Any) {
        val file = File(root, "web/src/generated/$name")
        val bytes = (Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), json(value))
            .replace("\n", "\r\n") + "\r\n").toByteArray(Charsets.UTF_8)
        if (System.getProperty("golden.update") == "true") {
            file.parentFile.mkdirs(); file.writeBytes(bytes)
        } else {
            assertTrue("Missing $file; run with -Dgolden.update=true", file.isFile)
            assertTrue("Stale $file; run with -Dgolden.update=true", file.readBytes().contentEquals(bytes))
        }
    }

    @Test fun generatedFilesAreCurrent() {
        val old = Words.themed
        try {
            output("tokens.json", tokens())
            output("accents.json", accents())
            output("words.json", words())
            output("catalogue.json", catalogue())
            output("art-index.json", ExerciseArtIndex.names.sorted().associateWith {
                ExerciseArtIndex.forName(it)!!.id.removePrefix("own_")
            })
        } finally { Words.useForTest(old) }
    }

    @Test fun accentsCoverEveryPreset() {
        val old = Words.themed
        try { assertEquals(8, (accents().values.flatMap { it as List<*> }).size) } finally { Words.useForTest(old) }
    }
    @Test fun catalogueCoversEveryCrestAndDeed() {
        val old = Words.themed
        try {
            assertEquals(28, Crests.ALL.size)
            val c = catalogue()
            assertEquals(Crests.ALL.size, (c.getValue("crests") as List<*>).size)
            assertEquals(Titles.ALL.size, (c.getValue("deeds") as List<*>).size)
            assertEquals(4, (c.getValue("seals") as Map<*, *>).size)
        } finally { Words.useForTest(old) }
    }
    @Test fun wordsCoverEveryConceptForm() {
        val old = Words.themed
        try {
            assertTrue(words().keys.containsAll(StringsXml.read(File(root, "app/src/main/res/values/strings.xml"))
                .map { it.name }.filter { it.startsWith("concept_") && !it.endsWith("__t") }))
        } finally { Words.useForTest(old) }
    }
}
