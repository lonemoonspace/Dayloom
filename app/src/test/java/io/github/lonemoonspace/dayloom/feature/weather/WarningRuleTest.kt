package io.github.lonemoonspace.dayloom.feature.weather

import io.github.lonemoonspace.dayloom.core.notify.RuleInput
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceResult
import io.github.lonemoonspace.dayloom.feature.weather.domain.WarningRule
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherWarning
import io.github.lonemoonspace.dayloom.feature.weather.domain.WeatherWarnings
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningRuleTest {

    private val id = SourceId("weather.warnings")
    private val start = ZonedDateTime.of(2026, 10, 20, 8, 0, 0, 0, ZoneId.of("Europe/Oslo"))
    private var feed = WeatherWarnings()
    private var refreshed = true

    private val rule = WarningRule(
        enabled = { true },
        refreshed = { refreshed },
        warnings = { feed },
        channelId = "weather.warnings",
        deepLink = "dayloom://weather",
        notificationId = 1000,
    )

    private fun at(hours: Long) = start.plusHours(hours).toInstant().toEpochMilli()

    private fun input(hours: Long) = RuleInput(RefreshReport(mapOf(id to SourceResult.Success(start.toInstant())), start.toInstant()), start.plusHours(hours))

    private val wind = WeatherWarning(id = "w1", event = "wind", level = 3, start = at(2), end = at(12))
    private val rain = WeatherWarning(id = "r1", event = "rain", level = 2, start = at(4), end = at(20))

    @Test
    fun `tells once, stays quiet for a reworded update, updates silently when one ends, cancels when all are over`() = runBlocking {
        feed = WeatherWarnings(listOf(wind, rain))
        val first = rule.evaluate(input(0), emptySet())
        assertEquals(1, first.notifications.size)
        assertFalse(first.notifications.single().alertOnce)

        feed = WeatherWarnings(listOf(wind.copy(id = "w2", description = "Reworded."), rain))
        val reworded = rule.evaluate(input(1), first.newState)
        assertTrue(reworded.notifications.isEmpty())
        assertTrue(reworded.cancel.isEmpty())

        // Wind ends at +12 h; rain goes on. / 大风在 +12 小时结束，雨还在。
        val oneEnded = rule.evaluate(input(13), reworded.newState)
        assertEquals(1, oneEnded.notifications.size)
        assertTrue(oneEnded.notifications.single().alertOnce)
        assertEquals(1, oneEnded.newState.size)

        val allOver = rule.evaluate(input(21), oneEnded.newState)
        assertEquals(listOf(1000), allOver.cancel)
        assertTrue(allOver.newState.isEmpty())
    }

    @Test
    fun `does nothing when the warnings were not refreshed this round`() = runBlocking {
        feed = WeatherWarnings(listOf(wind))
        refreshed = false
        val decision = rule.evaluate(input(0), emptySet())
        assertTrue(decision.notifications.isEmpty())
        assertEquals(emptySet<String>(), decision.newState)
    }
}
