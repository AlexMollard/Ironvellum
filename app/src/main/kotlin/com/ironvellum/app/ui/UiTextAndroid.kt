package com.ironvellum.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.StringSource
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.resolve

/** [StringSource] over a context's resources, so the current language applies. */
internal class ResourcesStrings(private val context: Context) : StringSource {
    // getString(id) leaves the text alone; with arguments it formats, so "%%" means "%" only there. strings.xml follows the same rule.
    override fun string(id: Int, args: List<Any>): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())

    override fun plural(id: Int, count: Int, args: List<Any>): String =
        if (args.isEmpty()) context.resources.getQuantityString(id, count)
        else context.resources.getQuantityString(id, count, *args.toTypedArray())

    override val themedComplete: Boolean get() = themedStringsComplete(context)
}

/** True when the current language has the themed wording for every sentence (res/values/bools.xml). */
fun themedStringsComplete(context: Context): Boolean = context.resources.getBoolean(R.bool.themed_strings_complete)

/** The text for a notification, a widget or any other place outside composition; reads the Themed names switch as it is now. */
fun UiText.asString(context: Context): String = resolve(ResourcesStrings(context), Words.themed)

/**
 * The text for a screen. It reads [Words.themed] (Compose state) and the configuration, so flipping the switch or
 * changing the language recomposes whatever shows it.
 */
@Composable
fun UiText.asString(): String {
    val context = LocalContext.current
    LocalConfiguration.current
    return resolve(ResourcesStrings(context), Words.themed)
}

/**
 * Whether the Themed names switch should be offered: only a language whose themed strings are complete has a
 * themed wording to switch to.
 */
@Composable
fun rememberThemedStringsComplete(): Boolean {
    val context = LocalContext.current
    LocalConfiguration.current
    return themedStringsComplete(context)
}
