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
import androidx.compose.ui.unit.dp
import com.ironvellum.app.BuildConfig
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
    if (BuildConfig.SUPPORT_LINKS) "SUPPORT IRONVELLUM" else "ABOUT"

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
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    var showMit by remember { mutableStateOf(false) }

    SettingsPage(panelTitle(), onBack) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Ironvellum is free and open source. It has no ads, no " +
                "trackers and no paid tier.",
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCaption(
            "The shared cloud the Tidings and the backups live on " +
                "costs real money every month — about \$25 for hosting. " +
                "Every cost is public: every invoice in, every " +
                "coin out.",
        )
        if (support) {
            Spacer(Modifier.height(10.dp))
            SettingsCaption(
                "If Ironvellum has earned it, you can help carry the " +
                    "cloud. There is no premium and there never will be " +
                    "— a donation buys nothing but the next month of " +
                    "backups for everyone.",
            )
        }
        Spacer(Modifier.height(16.dp))

        InkRowPanel(Modifier.fillMaxWidth()) {
            if (support) {
                ListRow("View the costs", onClick = { openUrl(SupportLinks.COSTS_LEDGER) })
                InkDivider()
                ListRow("Sponsor on GitHub", onClick = { openUrl(SupportLinks.GITHUB_SPONSORS) })
                InkDivider()
                ListRow("Donate on Liberapay", onClick = { openUrl(SupportLinks.LIBERAPAY) })
                InkDivider()
            }
            // The whole row is the link in the foss build; the play build shows the address as plain text.
            ListRow(
                "Source code",
                subline = SupportLinks.REPOSITORY,
                sublineColor = if (support) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
                onClick = if (support) ({ openUrl(SupportLinks.REPOSITORY) }) else null,
            )
            InkDivider()
            ListRow("Licence", subline = "GPL-3.0-or-later")
        }

        Spacer(Modifier.height(12.dp))
        SettingsCaption(
            "Chakra Petch font, SIL Open Font License 1.1. Artwork is " +
                "original or generated for Ironvellum, released under " +
                "the same licence.",
        )
        Spacer(Modifier.height(8.dp))
        SettingsCaption(OpenSourceNotices.BODY_MAP_CREDIT)
        IronvellumButton(
            label = if (showMit) "Hide the licence text" else "Show the licence text",
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
            "Ironvellum ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
