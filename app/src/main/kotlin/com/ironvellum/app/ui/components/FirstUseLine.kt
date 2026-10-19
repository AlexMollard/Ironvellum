package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ironvellum.app.data.FirstRunPrefs
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.R
import androidx.compose.ui.res.stringResource

/**
 * A one-line explanation of a word the app keeps in its own voice (a deed, a rank title, BMI), shown the first
 * time its screen opens and gone for good once the lifter says "Got it". [key] names the word; each is
 * remembered on its own (see [FirstRunPrefs.termSeen]). A reader who already knows it spends one tap.
 */
@Composable
fun FirstUseLine(key: String, text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var seen by remember(key) { mutableStateOf(FirstRunPrefs.termSeen(context, key)) }
    if (seen) return
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.weight(1f),
        )
        InkTextLink(stringResource(R.string.firstuse_got_it_button), onClick = {
            FirstRunPrefs.setTermSeen(context, key)
            seen = true
        })
    }
}
