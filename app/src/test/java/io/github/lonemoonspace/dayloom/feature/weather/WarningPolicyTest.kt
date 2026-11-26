package io.github.lonemoonspace.dayloom.feature.weather

import io.github.lonemoonspace.dayloom.feature.weather.domain.WarningPolicy
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherWarning
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarningPolicyTest {

    private val zone = ZoneId.of("Europe/Oslo")
    private val now = ZonedDateTime.of(2026, 10, 20, 8, 20, 0, 0, zone)

    private fun at(hours: Long) = now.plusHours(hours).toInstant().toEpochMilli()

    private fun warning(event: String, level: Int, start: Long, end: Long, id: String = "$event-$level") =
        WeatherWarning(id = id, event = event, level = level, start = at(start), end = at(end))

    @Test
    fun `active drops ended and unknown levels and puts the most severe first`() {
        val over = warning("rain", 4, -10, -1)
        val unknown = warning("snow", 0, 1, 5)
        val yellowSoon = warning("ice", 2, 1, 5)
        val yellowLater = warning("rain", 2, 3, 9)
        val orange = warning("wind", 3, 20, 30)

        val active = WarningPolicy.active(listOf(over, unknown, yellowLater, orange, yellowSoon), now)

        assertEquals(listOf(orange, yellowSoon, yellowLater), active)
    }

    @Test
    fun `a warning already in force has no start`() {
        val window = WarningPolicy.window(warning("wind", 3, -2, 6), now)
        assertNull(window.start)
        assertEquals(now.plusHours(6), window.end)
        assertEquals(now.plusHours(2), WarningPolicy.window(warning("wind", 3, 2, 6), now).start)
    }

    @Test
    fun `an update with a new id is not told again but a raised level is`() {
        val first = warning("wind", 2, 4, 10, id = "a")
        val (fresh, told) = WarningPolicy.toNotify(listOf(first), emptySet(), now)
        assertEquals(listOf(first), fresh)

        val reworded = first.copy(id = "b", description = "Stronger gusts on the coast.")
        assertEquals(emptyList<WeatherWarning>(), WarningPolicy.toNotify(listOf(reworded), told, now).first)

        val raised = first.copy(id = "c", level = 3)
        assertEquals(listOf(raised), WarningPolicy.toNotify(listOf(raised), told, now).first)
    }

    @Test
    fun `only warnings still in force are remembered`() {
        val told = setOf(WarningPolicy.key(warning("rain", 2, -10, -1)))
        val (_, keys) = WarningPolicy.toNotify(listOf(warning("rain", 2, -10, -1)), told, now)
        assertEquals(emptySet<String>(), keys)
    }
}
