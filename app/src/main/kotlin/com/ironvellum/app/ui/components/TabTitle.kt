package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The title of a tab's page, the same style at the same height on every tab: bold upper case Chakra Petch,
 * in a 48dp row that also holds an optional [trailing] action (Train's "Open trial"), so a title never
 * moves when that action comes and goes. The caller puts a 12dp spacer above it.
 */
@Composable
fun TabTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke(this)
    }
}
