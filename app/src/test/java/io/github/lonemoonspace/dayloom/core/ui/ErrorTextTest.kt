package io.github.lonemoonspace.dayloom.core.ui

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.error.toAppError
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTextTest {

    private fun res(text: UiText) = text as UiText.Res

    @Test
    fun `each error maps to its own string`() {
        assertEquals(R.string.error_offline, res(AppError.Offline().userMessage()).id)
        assertEquals(R.string.error_network, res(AppError.Network(IOException("timeout")).userMessage()).id)
        assertEquals(listOf("timeout"), res(AppError.Network(IOException("timeout")).userMessage()).args)
        assertEquals(R.string.error_bad_data, res(AppError.BadData("MET Norway", "x").userMessage()).id)
    }

    @Test
    fun `401 and 403 point the user at the key, other codes do not`() {
        assertEquals(R.string.error_http_auth, res(AppError.Http("Google Routes", 403).userMessage()).id)
        assertEquals(R.string.error_http_auth, res(AppError.Http("Google Routes", 401).userMessage()).id)
        val other = res(AppError.Http("Entur", 503).userMessage())
        assertEquals(R.string.error_http, other.id)
        assertEquals(listOf<Any>("Entur", 503), other.args)
    }

    @Test
    fun `not configured carries what is missing as a nested text`() {
        val what = UiText.Raw("Home")
        val message = res(AppError.NotConfigured(what).userMessage())
        assertEquals(R.string.error_not_configured, message.id)
        assertSame(what, message.args.single())
    }

    @Test
    fun `toAppError keeps AppErrors, wraps IO and parse failures, and rethrows cancellation`() {
        val original = AppError.Offline()
        assertSame(original, original.toAppError())
        assertTrue(IOException().toAppError() is AppError.Network)
        assertTrue(SerializationException("x").toAppError("MET Norway") is AppError.BadData)
        assertTrue(SerializationException("x").toAppError() is AppError.Unexpected)
        assertThrows(CancellationException::class.java) { CancellationException().toAppError() }
    }
}
