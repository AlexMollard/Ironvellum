package com.ironvellum.app.ui.train

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ironvellum.app.R
import com.ironvellum.app.ui.components.CappedDialog
import com.ironvellum.app.ui.components.CelebrationDock
import com.ironvellum.app.ui.components.InkTextLink
import com.ironvellum.app.ui.components.rememberPhotoSource
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The optional "Add a photo?" step at the end of a sealed workout, after the summary. One tap on Skip leaves it, and
 * nothing asks again: the step is offered once per day ([com.ironvellum.app.domain.PhotoRules.workoutPhotoOffered]).
 * [saving] holds the buttons while the picture is scaled and written; [failed] says so once, quietly.
 */
@Composable
internal fun PhotoOfferPage(
    saving: Boolean,
    failed: Boolean,
    onPicked: (Uri) -> Unit,
    onSkip: () -> Unit,
) {
    val source = rememberPhotoSource(onPicked)
    CappedDialog(
        onDismissRequest = { if (!saving) onSkip() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(IronvellumColors.Abyss).statusBarsPadding()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = IronvellumColors.EmeraldBright, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.photo_offer_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = IronvellumColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.photo_offer_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                )
                if (failed) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.photo_offer_failed), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.DangerRed, textAlign = TextAlign.Center)
                }
                if (source.canTake) {
                    Spacer(Modifier.height(16.dp))
                    InkTextLink(stringResource(R.string.photo_offer_pick), onClick = source.pick, enabled = !saving)
                }
            }
            CelebrationDock(
                primary = stringResource(if (saving) R.string.photo_offer_saving else if (source.canTake) R.string.photo_offer_take else R.string.photo_offer_pick),
                onPrimary = { if (source.canTake) source.take() else source.pick() },
                primaryEnabled = !saving,
                link = stringResource(R.string.photo_offer_skip),
                linkOn = !saving,
                onLink = onSkip,
            )
        }
    }
}
