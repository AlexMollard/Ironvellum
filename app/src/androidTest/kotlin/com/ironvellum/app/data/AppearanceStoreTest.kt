package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.ui.theme.AccentPalette
import com.ironvellum.app.ui.theme.AccentPresets
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var prefs: SharedPreferences
    private lateinit var originalDark: AccentPalette
    private lateinit var originalLight: AccentPalette
    private var originalIsLight = false

    @Before fun prepare() {
        originalDark = IronvellumColors.darkAccents
        originalLight = IronvellumColors.lightAccents
        originalIsLight = IronvellumColors.light
        prefs = context
            .getSharedPreferences("appearance-store-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After fun restore() {
        prefs.edit().clear().commit()
        IronvellumColors.darkAccents = originalDark
        IronvellumColors.lightAccents = originalLight
        IronvellumColors.light = originalIsLight
    }

    @Test fun freshStoreUsesEmeraldAndGold() {
        assertEquals(AccentPalette.Default, AppearanceStore(prefs).palette.value)
    }

    @Test fun freshInstallFollowsTheSystemAndAnExistingOneKeepsDark() {
        assertEquals(ThemeMode.SYSTEM, AppearanceStore(prefs).mode.value)
        prefs.edit().clear().commit()
        assertEquals(ThemeMode.DARK, AppearanceStore(prefs, existingInstall = true).mode.value)
        // The first answer is written down: finishing the welcome must not flip a System install to Dark.
        prefs.edit().clear().commit()
        AppearanceStore(prefs)
        assertEquals(ThemeMode.SYSTEM, AppearanceStore(prefs, existingInstall = true).mode.value)
    }

    @Test fun eachThemeKeepsItsOwnAccentPair() {
        val store = AppearanceStore(prefs)
        assertEquals(AccentPalette.LightDefault, store.lightPalette.value)
        store.setMode(ThemeMode.LIGHT)
        val copper = AccentPresets.light[3].palette
        store.set(copper)
        store.setMode(ThemeMode.DARK)
        val sapphire = AccentPresets.entries[1].palette
        store.set(sapphire)

        val reloaded = AppearanceStore(prefs)
        assertEquals(ThemeMode.DARK, reloaded.mode.value)
        assertEquals(sapphire, reloaded.palette.value)
        assertEquals(copper, reloaded.lightPalette.value)
    }

    @Test fun customAccentsSurviveStoreReload() {
        val custom = AccentPalette(0xFF13579B.toInt(), 0xFF2468AC.toInt())
        val store = AppearanceStore(prefs)
        store.set(custom)

        assertEquals(custom, store.palette.value)
        assertEquals(custom, IronvellumColors.accents)
        assertEquals(custom, AppearanceStore(prefs).palette.value)
    }

    @Test fun resetReplacesSavedPresetWithDefaults() {
        val store = AppearanceStore(prefs)
        store.set(AccentPresets.entries[1].palette)
        store.set(AccentPalette.Default)

        assertEquals(AccentPalette.Default, AppearanceStore(prefs).palette.value)
        assertEquals(AccentPalette.Default, IronvellumColors.accents)
    }

    @Test fun malformedPrimaryFallsBackWithoutLosingValidSecondary() {
        prefs.edit().putString("primary", "#ZZZZZZ").putString("secondary", "#2468AC").commit()

        assertEquals(
            AccentPalette(AccentPalette.Default.primary, 0xFF2468AC.toInt()),
            AppearanceStore(prefs).palette.value,
        )
    }

    @Test fun wrongPreferenceTypeAndTransparentInputRecoverSafely() {
        prefs.edit().putInt("primary", 42).putString("secondary", "#123").commit()
        val store = AppearanceStore(prefs)
        assertEquals(AccentPalette.Default, store.palette.value)

        store.set(AccentPalette(0x0013579B, 0x002468AC))
        val expected = AccentPalette(0xFF13579B.toInt(), 0xFF2468AC.toInt())
        assertEquals(expected, store.palette.value)
        assertEquals(expected, AppearanceStore(prefs).palette.value)
    }
}
