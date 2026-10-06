package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.ui.theme.AccentPalette
import com.ironvellum.app.ui.theme.AccentPresets
import com.ironvellum.app.ui.theme.IronvellumColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceStoreTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var originalPalette: AccentPalette

    @Before fun prepare() {
        originalPalette = IronvellumColors.accents
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("appearance-store-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After fun restore() {
        prefs.edit().clear().commit()
        IronvellumColors.accents = originalPalette
    }

    @Test fun freshStoreUsesEmeraldAndGold() {
        assertEquals(AccentPalette.Default, AppearanceStore(prefs).palette.value)
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
