package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import com.ironvellum.app.ui.theme.AccentPalette
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.accentHex
import com.ironvellum.app.ui.theme.parseAccentHex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One saved pair for the device, independent of accounts and training history. */
class AppearanceStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("appearance", Context.MODE_PRIVATE))

    private val current = MutableStateFlow(AccentPalette(
        read("primary", AccentPalette.Default.primary),
        read("secondary", AccentPalette.Default.secondary),
    ))
    val palette = current.asStateFlow()

    init { IronvellumColors.accents = current.value }

    fun set(palette: AccentPalette) {
        val opaque = AccentPalette(palette.primary or 0xFF000000.toInt(), palette.secondary or 0xFF000000.toInt())
        prefs.edit().putString("primary", accentHex(opaque.primary)).putString("secondary", accentHex(opaque.secondary)).apply()
        IronvellumColors.accents = opaque
        current.value = opaque
    }

    private fun read(key: String, fallback: Int): Int =
        runCatching { prefs.getString(key, null)?.let(::parseAccentHex) }.getOrNull() ?: fallback
}
