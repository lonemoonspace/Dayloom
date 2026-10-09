package io.github.lonemoonspace.dayloom.core.refresh

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.storage.InMemorySnapshotStore
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.time.FixedClock
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CachedSourceTest {

    private val now = ZonedDateTime.of(2026, 9, 29, 8, 0, 0, 0, ZoneId.of("Europe/Oslo"))
    private val clock = FixedClock(now)

    private fun snapshot(key: String, schema: Int = 1, fetchedAt: ZonedDateTime = now) =
        Snapshot("v-$key", fetchedAt.toInstant().toEpochMilli(), key, schema)

    private fun ready(param: String) = FakeSource.input(param) as SourceInput.Ready<String>

    @Test
    fun `observe only shows the snapshot matching the current inputs and follows changes`() = runTest {
        val store = InMemorySnapshotStore(snapshot("A"))
        val source = FakeSource("weather", clock, store)
        val seen = mutableListOf<Snapshot<String>?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().collect { seen += it } }

        assertEquals(listOf(snapshot("A")), seen)

        source.param.value = "B"
        assertNull("the old place's snapshot must not be shown", seen.last())

        store.write(snapshot("B"))
        assertEquals(snapshot("B"), seen.last())

        source.signal.value = 1
        assertEquals("a refresh-key-only change produces no new value", 3, seen.size)
    }

    @Test
    fun `observe hides snapshots of another schema and when inputs are missing`() = runTest {
        val store = InMemorySnapshotStore(snapshot("A", schema = 0))
        val source = FakeSource("weather", clock, store)
        val seen = mutableListOf<Snapshot<String>?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().collect { seen += it } }

        assertNull(seen.last())
        store.write(snapshot("A"))
        assertEquals(snapshot("A"), seen.last())

        source.param.value = ""
        assertNull(seen.last())
    }

    @Test
    fun `current reads the matching snapshot once`() = runTest {
        val source = FakeSource("weather", clock, InMemorySnapshotStore(snapshot("A")))
        assertEquals(snapshot("A"), source.current())
        source.param.value = "B"
        assertNull(source.current())
        source.param.value = ""
        assertNull(source.current())
    }

    @Test
    fun `isStale compares data time against maxAge`() {
        val source = FakeSource("weather", clock)
        assertFalse(source.isStale(snapshot("A", fetchedAt = now.minusMinutes(59)), now.toInstant()))
        assertTrue(source.isStale(snapshot("A", fetchedAt = now.minusMinutes(61)), now.toInstant()))
    }

    @Test
    fun `refresh writes a snapshot stamped with the clock and the params key`() = runTest {
        val source = FakeSource("weather", clock)

        val result = source.refresh(ready("  A  "))

        assertEquals(Snapshot("A#1", now.toInstant().toEpochMilli(), "A", 1), result)
        assertEquals(result, source.store.current)
    }

    @Test
    fun `previous is handed over only when params and schema match`() = runTest {
        val store = InMemorySnapshotStore(snapshot("A"))
        val source = FakeSource("weather", clock, store)

        source.refresh(ready("A"))
        source.refresh(ready("B"))
        store.write(snapshot("B", schema = 0))
        source.refresh(ready("B"))

        assertEquals(listOf(snapshot("A"), null, null), source.previousSeen)
    }

    @Test
    fun `a failed fetch leaves the stored snapshot untouched`() = runTest {
        val store = InMemorySnapshotStore(snapshot("A"))
        val source = FakeSource("weather", clock, store).apply { failWith = AppError.Offline() }

        runCatching { source.refresh(ready("A")) }

        assertEquals(snapshot("A"), store.current)
        assertEquals(0, store.writes)
    }
}
