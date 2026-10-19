package com.ironvellum.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ironvellum.app.BuildConfig
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.theme.IronvellumColors

/** Every external URL the Support screen can open, in one place. */
private object SupportLinks {
    const val REPOSITORY = "https://github.com/AlexMollard/Ironvellum"
    const val COSTS_LEDGER =
        "https://github.com/AlexMollard/Ironvellum/blob/main/COSTS.md"
    const val GITHUB_SPONSORS = "https://github.com/sponsors/AlexMollard"
    const val LIBERAPAY = "https://liberapay.com/AlexMollard"
}

private fun panelTitle(): String =
    Words.text(UiText.res(if (BuildConfig.SUPPORT_LINKS) R.string.settings_support_title else R.string.settings_support_title_about))

/**
 * The Ledger, plainly: what Ironvellum is, what the shared cloud costs, and —
 * in the foss flavour only — where to help carry it. No guilt, no nag; this
 * screen is reachable only from Settings and nothing anywhere else in the app
 * mentions donations. The play flavour shows no tappable link at all (Play
 * policy on donation links); it carries the licence, attributions and source
 * location as plain text, which GPL permits.
 */
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val support = BuildConfig.SUPPORT_LINKS
    // An explicit ACTION_VIEW, so a phone with no browser is a no-op, not a crash.
    val openUrl: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }
    var showMit by remember { mutableStateOf(false) }

    SettingsPage(panelTitle(), onBack) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_support_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCaption(
            UiText.themed(R.string.settings_support_costs, R.string.settings_support_costs__t).asString(),
        )
        if (support) {
            Spacer(Modifier.height(10.dp))
            SettingsCaption(
                stringResource(R.string.settings_support_ask),
            )
        }
        Spacer(Modifier.height(16.dp))

        InkRowPanel(Modifier.fillMaxWidth()) {
            if (support) {
                ListRow(stringResource(R.string.settings_support_costs_row), onClick = { openUrl(SupportLinks.COSTS_LEDGER) })
                InkDivider()
                ListRow(stringResource(R.string.settings_support_sponsor_row), onClick = { openUrl(SupportLinks.GITHUB_SPONSORS) })
                InkDivider()
                ListRow(stringResource(R.string.settings_support_liberapay_row), onClick = { openUrl(SupportLinks.LIBERAPAY) })
                InkDivider()
            }
            // The whole row is the link in the foss build; the play build shows the address as plain text.
            ListRow(
                stringResource(R.string.settings_support_source_row),
                subline = SupportLinks.REPOSITORY,
                sublineColor = if (support) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
                onClick = if (support) ({ openUrl(SupportLinks.REPOSITORY) }) else null,
            )
            InkDivider()
            ListRow(stringResource(R.string.settings_support_licence_row), subline = stringResource(R.string.settings_support_licence_value))
        }

        Spacer(Modifier.height(12.dp))
        SettingsCaption(
            stringResource(R.string.settings_support_credits),
        )
        Spacer(Modifier.height(8.dp))
        SettingsCaption(OpenSourceNotices.BODY_MAP_CREDIT)
        Spacer(Modifier.height(8.dp))
        SettingsCaption(OpenSourceNotices.EXERCISE_ART_CREDIT)
        IronvellumButton(
            label = stringResource(if (showMit) R.string.settings_support_licence_hide else R.string.settings_support_licence_show),
            onClick = { showMit = !showMit },
            modifier = Modifier.fillMaxWidth(),
            quiet = true,
        )
        if (showMit) {
            Spacer(Modifier.height(8.dp))
            SettingsCaption(OpenSourceNotices.BODY_MAP_SOURCE + "\n\n" + OpenSourceNotices.BODY_MAP_MIT)
        }

        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.settings_support_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
