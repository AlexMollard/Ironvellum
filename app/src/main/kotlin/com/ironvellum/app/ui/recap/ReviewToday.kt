package com.ironvellum.app.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.ReviewKind
import com.ironvellum.app.domain.ReviewSummary
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder

/**
 * Today's card for a finished month (a finished year, in January): "New · year in review", the period and three figures,
 * with a close button. It opens the review; the close is remembered by the caller.
 */
@Composable
fun ReviewTodayCard(summary: ReviewSummary, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val shape = MaterialTheme.shapes.medium
    val label = stringResource(if (summary.period.kind == ReviewKind.YEAR) R.string.review_today_label_year else R.string.review_today_label_month)
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(IronvellumColors.Emerald.copy(alpha = 0.12f), shape)
            .inkBorder(IronvellumColors.Emerald.copy(alpha = 0.45f), shape, 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .clickable(onClickLabel = stringResource(R.string.review_open_description), role = Role.Button, onClick = onOpen)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label.uppercase(locale),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = IronvellumTracking.InlineLabel),
                    color = IronvellumColors.EmeraldBright,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    UiText.res(R.string.review_today_title, ReviewText.short(summary.period, locale), ReviewText.summary(summary)).asString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(20.dp))
        }
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClickLabel = stringResource(R.string.review_today_dismiss_description), role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.review_today_dismiss_description), tint = IronvellumColors.InkMuted, modifier = Modifier.size(18.dp))
        }
    }
}
