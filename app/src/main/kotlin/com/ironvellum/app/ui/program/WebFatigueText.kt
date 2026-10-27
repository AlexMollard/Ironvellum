package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.CauseShare
import com.ironvellum.app.domain.FatigueText
import com.ironvellum.app.domain.LightBy
import java.time.ZoneId

/** Uses the same localized wording as the phone's fatigue pane. */
internal object WebFatigueText : FatigueText {
    override fun cause(c: CauseShare, nowMs: Long, zone: ZoneId): String = causeLine(c, nowMs, zone)
    override fun freshBy(by: LightBy): String? = freshByLine(by)
}
