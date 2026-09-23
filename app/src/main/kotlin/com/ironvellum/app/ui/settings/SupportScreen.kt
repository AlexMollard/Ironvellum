package com.ironvellum.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.BuildConfig
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.theme.ChakraPetch
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
fun SupportScreen() {
    val uriHandler = LocalUriHandler.current
    val support = BuildConfig.SUPPORT_LINKS

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            panelTitle(),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(14.dp))

        InkPanel(Modifier.fillMaxWidth()) {
            Text(
                "Ironvellum is free and open source. It has no ads, no " +
                    "trackers and no paid tier.",
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "The shared cloud the Allies feed and the backups live on " +
                    "costs real money every month — about \$25 for hosting. " +
                    "The whole ledger is public: every invoice in, every " +
                    "coin out.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            if (support) {
                Spacer(Modifier.height(10.dp))
                IronvellumButton(
                    label = "View the Ledger",
                    onClick = { uriHandler.openUri(SupportLinks.COSTS_LEDGER) },
                    quiet = true,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (support) {
            InkPanel(Modifier.fillMaxWidth()) {
                Text(
                    "If Ironvellum has earned it, you can help carry the " +
                        "cloud. There is no premium and there never will be " +
                        "— a donation buys nothing but the next month of " +
                        "backups for everyone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                Spacer(Modifier.height(10.dp))
                IronvellumButton(
                    label = "Sponsor on GitHub",
                    onClick = { uriHandler.openUri(SupportLinks.GITHUB_SPONSORS) },
                )
                Spacer(Modifier.height(10.dp))
                IronvellumButton(
                    label = "Donate on Liberapay",
                    onClick = { uriHandler.openUri(SupportLinks.LIBERAPAY) },
                    quiet = true,
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        InkPanel(Modifier.fillMaxWidth()) {
            Text(
                "SOURCE",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            if (support) {
                Text(
                    SupportLinks.REPOSITORY,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.SystemGreen,
                    modifier = Modifier.clickable { uriHandler.openUri(SupportLinks.REPOSITORY) },
                )
            } else {
                Text(
                    SupportLinks.REPOSITORY,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        InkPanel(Modifier.fillMaxWidth()) {
            Text(
                "LICENCE",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "GPL-3.0-or-later",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.Ink,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Chakra Petch font, SIL Open Font License 1.1. Artwork is " +
                    "original or generated for Ironvellum, released under " +
                    "the same licence.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Ironvellum ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}
