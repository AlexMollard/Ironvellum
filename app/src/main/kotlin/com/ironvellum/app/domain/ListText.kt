package com.ironvellum.app.domain

import com.ironvellum.app.R
import com.ironvellum.app.data.Words

/** Lists a person reads, in the app's language: "a", "a and b", "a, b and c". */
object ListText {

    fun and(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> Words.text(
            UiText.res(
                R.string.list_last,
                items.dropLast(1).reduce { head, next -> Words.text(UiText.res(R.string.list_join, head, next)) },
                items.last(),
            ),
        )
    }
}
