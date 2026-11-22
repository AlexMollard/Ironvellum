package com.ironvellum.app.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.ui.components.SettingsGroup

/** Train opens the device tools without looking up a cloud account or fetching drafts. */
@Composable
internal fun DraftEntry(onOpen: () -> Unit) {
    SettingsGroup(null, rows = true, topSpace = 12.dp) {
        LocalAiRow(R.string.local_ai_tools, R.string.local_ai_train_hint, R.string.local_ai_train_hint__t, onOpen)
    }
}
