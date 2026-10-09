package io.github.lonemoonspace.dayloom.core.notify

import java.time.LocalDate
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StateCodecsTest {

    @Serializable
    data class Seen(val ids: List<Int> = emptyList())

    @Test
    fun `string codec treats empty as null`() {
        assertNull(StringStateCodec.decode(null))
        assertNull(StringStateCodec.decode(""))
        assertEquals("x", StringStateCodec.decode(StringStateCodec.encode("x")))
        assertEquals("", StringStateCodec.encode(null))
    }

    @Test
    fun `date codec round-trips and falls back on garbage`() {
        val date = LocalDate.of(2026, 10, 9)
        assertEquals(date, LocalDateStateCodec.decode(LocalDateStateCodec.encode(date)))
        assertNull(LocalDateStateCodec.decode("not a date"))
    }

    @Test
    fun `json codec round-trips and falls back to the initial state`() {
        val codec = JsonStateCodec(Seen.serializer(), Seen())
        assertEquals(Seen(listOf(1, 2)), codec.decode(codec.encode(Seen(listOf(1, 2)))))
        assertEquals(Seen(), codec.decode("{ broken"))
        assertEquals(Seen(), codec.decode(null))
    }
}
