package com.ironvellum.app.data

import com.ironvellum.app.R
import com.ironvellum.app.domain.UiText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** A failure kept as [UiText] is worded when read, so a message held in state follows the Themed names switch. */
class MessageExceptionTest {

    @After fun restoreMode() = Words.useForTest(true)

    @Test
    fun `the message follows the Themed names switch after the failure was built`() {
        val failure = MessageException(UiText.themed(R.string.cloud_error_name_taken, R.string.cloud_error_name_taken__t))
        Words.useForTest(false)
        assertEquals("That display name is taken", failure.message)
        Words.useForTest(true)
        assertEquals("That true name is taken", failure.message)
    }

    @Test
    fun `messageText keeps a MessageException unresolved and wraps any other message as data`() {
        val text = UiText.res(R.string.cloud_error_offline)
        val unknown = UiText.res(R.string.cloud_error_unavailable)
        assertSame(text, MessageException(text).messageText(unknown))
        assertEquals(UiText.Raw("boom"), IllegalStateException("boom").messageText(unknown))
        assertEquals(unknown, IllegalStateException().messageText(unknown))
        assertEquals(unknown, IllegalStateException("  ").messageText(unknown))
    }

    @Test
    fun `reasonText names the exception class when there is no message`() {
        val unknown = UiText.res(R.string.cloud_error_unavailable)
        assertEquals(UiText.Raw("IllegalStateException"), IllegalStateException().reasonText(unknown))
    }

    @Test
    fun `joined text resolves each part in the mode of the moment`() {
        val line = UiText.join(
            listOf(UiText.themed(R.string.cloud_error_name_taken, R.string.cloud_error_name_taken__t), UiText.Raw("x")),
            " | ",
        )
        Words.useForTest(false)
        assertEquals("That display name is taken | x", Words.text(line))
        Words.useForTest(true)
        assertEquals("That true name is taken | x", Words.text(line))
    }
}
