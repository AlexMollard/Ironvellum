package com.ironvellum.app.data

import com.ironvellum.app.domain.UiText

/**
 * A failure whose message is still [UiText]: it is turned into words when [message] is read, not when the failure is
 * built, so a screen that keeps the failure and shows it later follows the language and the Themed names switch.
 * State that holds a failure for the screen keeps [text] (see [messageText]), not the resolved `String`.
 */
open class MessageException(val text: UiText, cause: Throwable? = null) : IllegalStateException(cause) {
    override val message: String get() = Words.text(text)
}

/** [messageText] for a screen's "unknown failure" line: with no message it names the exception class first. */
fun Throwable.reasonText(unknown: UiText): UiText = messageText(this::class.simpleName?.let { UiText.Raw(it) } ?: unknown)

/**
 * The words for this failure, still unresolved: [MessageException.text] as it is, any other message as [UiText.Raw]
 * (exception text is data, not copy), and [fallback] when there is none.
 */
fun Throwable.messageText(fallback: UiText): UiText =
    (this as? MessageException)?.text ?: message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: fallback
