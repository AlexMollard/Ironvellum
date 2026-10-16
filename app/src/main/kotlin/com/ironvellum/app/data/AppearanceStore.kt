package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.ui.theme.AccentPalette
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.ThemeMode
import com.ironvellum.app.ui.theme.accentHex
import com.ironvellum.app.ui.theme.isSystemDark
import com.ironvellum.app.ui.theme.parseAccentHex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the device keeps of Appearance, independent of accounts and training history: the theme choice and one saved
 * accent pair per theme, so switching theme keeps each one's colours. The dark pair keeps the keys it always had.
 */
class AppearanceStore(private val prefs: SharedPreferences, private val existingInstall: Boolean = false, private val systemDark: () -> Boolean = { true }, private val onChange: () -> Unit = {}) {
    constructor(context: Context) : this(
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE),
        existingInstall = FirstRunPrefs.welcomeSeen(context),
        systemDark = { isSystemDark(context) },
        onChange = { com.ironvellum.app.widget.TodayWidget.refresh(context.applicationContext) },
    )

    private val currentMode = MutableStateFlow(readMode())
    private val current = MutableStateFlow(readPair("primary", "secondary", AccentPalette.Default))
    private val currentLight = MutableStateFlow(readPair("light_primary", "light_secondary", AccentPalette.LightDefault))

    /** The theme choice. */
    val mode = currentMode.asStateFlow()

    /** The dark pair. */
    val palette = current.asStateFlow()

    /** The Vellum pair. */
    val lightPalette = currentLight.asStateFlow()

    init {
        IronvellumColors.darkAccents = current.value
        IronvellumColors.lightAccents = currentLight.value
        // Before any window exists, so a notification or widget asking for a token gets the right theme.
        IronvellumColors.light = currentMode.value.isLight(systemDark())
    }

    fun setMode(mode: ThemeMode) {
        prefs.edit { putString("theme", mode.name) }
        currentMode.value = mode
        IronvellumColors.light = mode.isLight(systemDark())
        onChange()
    }

    /** Saves the pair for the theme in force, leaving the other theme's pair as it was. */
    fun set(palette: AccentPalette) {
        val opaque = AccentPalette(palette.primary or 0xFF000000.toInt(), palette.secondary or 0xFF000000.toInt())
        if (IronvellumColors.light) {
            write("light_primary", "light_secondary", opaque)
            IronvellumColors.lightAccents = opaque
            currentLight.value = opaque
        } else {
            write("primary", "secondary", opaque)
            IronvellumColors.darkAccents = opaque
            current.value = opaque
        }
        onChange()
    }

    private fun write(primaryKey: String, secondaryKey: String, pair: AccentPalette) {
        prefs.edit {
            putString(primaryKey, accentHex(pair.primary))
            putString(secondaryKey, accentHex(pair.secondary))
        }
    }

    /**
     * A device that already ran the app keeps Dark, so an update changes nothing; a fresh install follows the
     * phone. The first answer is written down at once, or finishing the welcome (which makes the install look
     * existing) would flip a System install to Dark.
     */
    private fun readMode(): ThemeMode {
        val saved = runCatching { prefs.getString("theme", null)?.let { ThemeMode.valueOf(it) } }.getOrNull()
        if (saved != null) return saved
        val first = if (existingInstall) ThemeMode.DARK else ThemeMode.SYSTEM
        prefs.edit { putString("theme", first.name) }
        return first
    }

    private fun readPair(primaryKey: String, secondaryKey: String, fallback: AccentPalette) = AccentPalette(
        read(primaryKey, fallback.primary),
        read(secondaryKey, fallback.secondary),
    )

    private fun read(key: String, fallback: Int): Int =
        runCatching { prefs.getString(key, null)?.let(::parseAccentHex) }.getOrNull() ?: fallback
}
