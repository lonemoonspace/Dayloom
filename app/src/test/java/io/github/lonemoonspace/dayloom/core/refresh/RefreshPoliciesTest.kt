package io.github.lonemoonspace.dayloom.core.refresh

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.module.ModuleIds
import java.io.IOException
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPoliciesTest {

    private val now = Instant.parse("2026-10-09T08:00:00Z")
    private val cadence = RefreshCadence(
        interval = Duration.ofMinutes(60),
        busyInterval = Duration.ofMinutes(15),
        busyInWindows = true,
    )

    @Test
    fun `never fetched is always due`() {
        assertTrue(RefreshCadencePolicy.isDue(cadence, inWindow = false, lastFetchedAt = null, now = now))
    }

    @Test
    fun `outside windows the normal interval applies, inside the busy one`() {
        val twentyMinutesAgo = now.minus(Duration.ofMinutes(20))
        assertFalse(RefreshCadencePolicy.isDue(cadence, inWindow = false, lastFetchedAt = twentyMinutesAgo, now = now))
        assertTrue(RefreshCadencePolicy.isDue(cadence, inWindow = true, lastFetchedAt = twentyMinutesAgo, now = now))
    }

    @Test
    fun `the busy interval is ignored for sources that do not ask for it`() {
        val plain = cadence.copy(busyInWindows = false)
        assertFalse(RefreshCadencePolicy.isDue(plain, inWindow = true, lastFetchedAt = now.minus(Duration.ofMinutes(20)), now = now))
    }

    @Test
    fun `a worker firing a few seconds early does not skip a period`() {
        val almost = now.minus(Duration.ofMinutes(15)).plusSeconds(30)
        assertTrue(RefreshCadencePolicy.isDue(cadence, inWindow = true, lastFetchedAt = almost, now = now))
    }

    @Test
    fun `a timestamp in the future never blocks refreshing`() {
        assertTrue(RefreshCadencePolicy.isDue(cadence, inWindow = false, lastFetchedAt = now.plusSeconds(3_600), now = now))
    }

    @Test
    fun `retry only when every attempted source failed`() {
        val failed = SourceResult.Failed(AppError.Network(IOException()))
        val ok = SourceResult.Success(now)
        val offline = SourceResult.Skipped(SkipReason.OFFLINE)
        val unset = SourceResult.Skipped(SkipReason.NOT_CONFIGURED)

        assertTrue(BackgroundRefreshPolicy.shouldRetry(listOf(failed, failed)))
        assertTrue("skipped sources are left out", BackgroundRefreshPolicy.shouldRetry(listOf(failed, unset, offline)))
        assertFalse(BackgroundRefreshPolicy.shouldRetry(listOf(failed, ok)))
        assertFalse("nothing attempted, nothing to retry", BackgroundRefreshPolicy.shouldRetry(listOf(unset, offline)))
        assertFalse(BackgroundRefreshPolicy.shouldRetry(emptyList()))
    }

    @Test
    fun `source ids are namespaced and name their snapshot file`() {
        val id = SourceId("weather.forecast")
        assertEquals("weather", id.moduleId)
        assertEquals("snapshot_weather_forecast", id.snapshotFileName)
        listOf("weather", "Weather.x", "weather.", ".x", "a.b.c", "1a.b").forEach { bad ->
            assertThrows(bad, IllegalArgumentException::class.java) { SourceId(bad) }
        }
    }

    @Test
    fun `source ids accept exactly the module ids the registry accepts`() {
        listOf("weather", "w2", "news_feed", "Weather", "2fa", "a-b", "_x", "").forEach { moduleId ->
            val validModule = ModuleIds.MODULE_ID.matches(moduleId)
            val validSource = runCatching { SourceId("$moduleId.main") }.isSuccess
            assertEquals(moduleId, validModule, validSource)
        }
    }
}
