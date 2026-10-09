package io.github.lonemoonspace.dayloom.core.notify

import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.refresh.RefreshReport
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NotificationEngineTest {

    private object IntCodec : StateCodec<Int> {
        override fun decode(raw: String?): Int = raw?.toIntOrNull() ?: 0
        override fun encode(state: Int): String = state.toString()
    }

    private class FakeRule(
        override val name: String,
        private val enabled: Boolean = true,
        private val onEvaluate: (previous: Int) -> RuleDecision<Int>,
    ) : NotificationRule<Int> {
        override val codec: StateCodec<Int> = IntCodec
        override suspend fun isEnabled() = enabled
        override suspend fun evaluate(input: RuleInput, previous: Int) = onEvaluate(previous)
    }

    private class FakeStateStore(initial: Map<String, String> = emptyMap()) : NotificationStateStore {
        val values = initial.toMutableMap()
        val reads = mutableListOf<String>()
        val writes = mutableListOf<Pair<String, String>>()
        override suspend fun read(key: String): String? = values[key].also { reads += key }
        override suspend fun write(key: String, value: String) {
            writes += key to value
            values[key] = value
        }
    }

    private class RecordingNotifier : Notifier {
        val sent = mutableListOf<AppNotification>()
        override fun send(notification: AppNotification) {
            sent += notification
        }
    }

    private fun note(id: Int) = AppNotification(id, "m.ch", UiText.Raw("t$id"), UiText.Raw("b$id"), "dayloom://home")

    private val input = RuleInput(
        RefreshReport(emptyMap(), Instant.EPOCH),
        ZonedDateTime.of(2026, 10, 9, 8, 0, 0, 0, ZoneId.of("UTC")),
    )

    private fun scoped(rule: FakeRule) = ScopedRule("m.${rule.name}", rule)

    @Test
    fun `a throwing rule does not stop the others and is reported`() = runTest {
        val store = FakeStateStore()
        val notifier = RecordingNotifier()
        val errors = mutableListOf<String>()
        val boom = FakeRule("a") { error("boom") }
        val ok = FakeRule("b") { RuleDecision(it + 1, listOf(note(7))) }

        NotificationEngine(store, notifier) { rule, _ -> errors += rule.stateKey }.run(listOf(scoped(boom), scoped(ok)), input)

        assertEquals(listOf("m.a"), errors)
        assertEquals(listOf(7), notifier.sent.map { it.id })
        assertEquals(listOf("m.b" to "1"), store.writes)
    }

    @Test
    fun `an exception while sending is isolated per rule`() = runTest {
        val sent = mutableListOf<Int>()
        val notifier = object : Notifier {
            override fun send(notification: AppNotification) {
                if (notification.id == 1) error("send failed")
                sent += notification.id
            }
        }
        val first = FakeRule("a") { RuleDecision(1, listOf(note(1))) }
        val second = FakeRule("b") { RuleDecision(1, listOf(note(2))) }

        NotificationEngine(FakeStateStore(), notifier).run(listOf(scoped(first), scoped(second)), input)

        assertEquals(listOf(2), sent)
    }

    @Test
    fun `cancellation is rethrown and not swallowed`() = runTest {
        val store = FakeStateStore()
        val cancelling = FakeRule("a") { throw CancellationException("cancelled") }
        val later = FakeRule("b") { RuleDecision(1) }
        try {
            NotificationEngine(store, RecordingNotifier()).run(listOf(scoped(cancelling), scoped(later)), input)
            fail("CancellationException expected")
        } catch (_: CancellationException) {
            assertTrue(store.writes.isEmpty())
        }
    }

    @Test
    fun `unchanged state is not written but notifications are still sent`() = runTest {
        val store = FakeStateStore(mapOf("m.a" to "5"))
        val notifier = RecordingNotifier()

        NotificationEngine(store, notifier).run(listOf(scoped(FakeRule("a") { RuleDecision(it, listOf(note(3))) })), input)

        assertTrue(store.writes.isEmpty())
        assertEquals(listOf(3), notifier.sent.map { it.id })
    }

    @Test
    fun `changed state is written under the namespaced key`() = runTest {
        val store = FakeStateStore(mapOf("m.a" to "5"))

        NotificationEngine(store, RecordingNotifier()).run(listOf(scoped(FakeRule("a") { RuleDecision(it + 1) })), input)

        assertEquals(listOf("m.a" to "6"), store.writes)
    }

    @Test
    fun `a disabled rule neither reads nor writes state nor sends`() = runTest {
        val store = FakeStateStore(mapOf("m.a" to "5"))
        val notifier = RecordingNotifier()
        var evaluated = false
        val rule = FakeRule("a", enabled = false) { evaluated = true; RuleDecision(9, listOf(note(1))) }

        NotificationEngine(store, notifier).run(listOf(scoped(rule)), input)

        assertFalse(evaluated)
        assertTrue(store.reads.isEmpty())
        assertTrue(store.writes.isEmpty())
        assertTrue(notifier.sent.isEmpty())
    }
}
