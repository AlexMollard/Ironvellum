package com.ironvellum.app.ui.components

import android.content.Context
import android.provider.Settings

/** False when the lifter has turned system animations off (animator duration scale 0): skip decorative motion. */
internal fun animatorsOn(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
