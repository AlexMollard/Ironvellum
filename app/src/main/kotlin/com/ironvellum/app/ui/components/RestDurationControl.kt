package com.ironvellum.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ironvellum.app.R
import com.ironvellum.app.domain.RestRules
import com.ironvellum.app.domain.RestTimer

/** Null follows automatic category or exercise rules. */
@Composable
internal fun RestDurationControl(label: String, value: Int?, onChange: (Int?) -> Unit) {
    SettingsSwitchRow(
        label = label,
        caption = stringResource(R.string.rest_custom_caption),
        checked = value != null,
        onCheckedChange = { onChange(if (it) 90 else null) },
    )
    if (value != null) {
        StepperRow(
            label = stringResource(R.string.rest_duration_label),
            value = RestTimer.clock(value),
            unit = null,
            less = stringResource(R.string.rest_duration_less),
            more = stringResource(R.string.rest_duration_more),
            error = false,
            decimal = false,
            maxDigits = 0,
            onValue = null,
            onLess = { onChange((value - 15).coerceAtLeast(RestRules.CUSTOM_SECONDS.first)) },
            onMore = { onChange((value + 15).coerceAtMost(RestRules.CUSTOM_SECONDS.last)) },
        )
    }
}
