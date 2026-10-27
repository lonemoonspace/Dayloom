package io.github.lonemoonspace.dayloom.feature.transit

import io.github.lonemoonspace.dayloom.feature.transit.domain.BoardDeparture
import io.github.lonemoonspace.dayloom.feature.transit.domain.OpposingHint
import io.github.lonemoonspace.dayloom.feature.transit.domain.OpposingPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitLeg
import io.github.lonemoonspace.dayloom.feature.transit.domain.TripOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpposingPolicyTest {

    private fun at(minute: Int) = minute * 60_000L

    private fun leg(line: String, dep: Int, late: Int, platform: String = "1", front: String = "Nordby", mode: String = "rail") =
        TransitLeg(
            mode = mode, line = line, frontText = front, platform = platform, realtime = true,
            aimedDeparture = at(dep), expectedDeparture = at(dep + late), aimedArrival = at(dep + 30), expectedArrival = at(dep + 30 + late),
        )

    private fun other(aimed: Int, late: Int, line: String = "L1", front: String = "Østby", platform: String = "2", mode: String = "rail") =
        BoardDeparture(line = line, mode = mode, frontText = front, platform = platform, aimed = at(aimed), expected = at(aimed + late), realtime = true)

    private fun option(late: Int, platform: String = "1") = TripOption(listOf(leg("L1", 24, late, platform)))

    @Test
    fun `a late train leaving right after a late opposing one is held for it`() {
        // :24 leaves :29, the opposing :25 arrives :29. / :24 那班 :29 开，对向 :25 那班 :29 到。
        assertEquals(OpposingHint("L1", 4), OpposingPolicy.hint(option(5), listOf(other(25, 4))))
        // Leaving a few seconds before the opposing train's expected time still counts. / 比对向车预计时刻早几秒开也算。
        val seconds = other(25, 4).let { it.copy(expected = it.expected + 30_000L) }
        assertEquals(OpposingHint("L1", 4), OpposingPolicy.hint(option(5), listOf(seconds)))
        // Up to two minutes after it still counts, three does not. / 晚两分钟内开仍算，三分钟就不算。
        assertEquals(OpposingHint("L1", 3), OpposingPolicy.hint(option(5), listOf(other(24, 3))))
        assertNull(OpposingPolicy.hint(option(6), listOf(other(24, 3))))
    }

    @Test
    fun `a train late for another reason gets no hint`() {
        // Leaves :33, five minutes after the opposing train passed at :28. / :33 开，对向车 :28 就过了。
        assertNull(OpposingPolicy.hint(option(9), listOf(other(25, 3))))
        // 3 min late but leaves well before the opposing train arrives: the crossing moved. / 晚 3 分，却远早于对向车到站就开：会车点改了。
        assertNull(OpposingPolicy.hint(option(3), listOf(other(25, 16))))
    }

    @Test
    fun `nothing to explain without a delay on both sides`() {
        // The first train is not late enough. / 本车晚得不够。
        assertNull(OpposingPolicy.hint(option(1), listOf(other(25, 4))))
        // Leaves with an opposing train that is on time, or under two minutes late: not the cause.
        // 和准点或晚不到两分钟的对向车同时开：不是原因。
        assertNull(OpposingPolicy.hint(option(5), listOf(other(29, 0))))
        assertNull(OpposingPolicy.hint(option(5), listOf(other(25, 4).copy(aimed = at(28) + 20_000L))))
    }

    @Test
    fun `the opposing train closest to the departure is named`() {
        assertEquals(OpposingHint("L1", 6), OpposingPolicy.hint(option(6), listOf(other(21, 7), other(24, 6))))
    }

    @Test
    fun `same direction, same platform, other lines and other modes are not opposing`() {
        assertNull(OpposingPolicy.hint(option(5), listOf(other(25, 4, front = "Nordby"))))
        assertNull(OpposingPolicy.hint(option(5), listOf(other(25, 4, platform = "1"))))
        assertNull(OpposingPolicy.hint(option(5), listOf(other(25, 4, line = "L2"))))
        assertNull(OpposingPolicy.hint(option(5), listOf(other(25, 4, mode = "bus"))))
        assertNull(OpposingPolicy.hint(option(5), listOf(other(25, 4).copy(cancelled = true))))
    }

    @Test
    fun `without both platforms or live data there is no hint`() {
        assertNull(OpposingPolicy.hint(option(5), listOf(other(25, 4, platform = ""))))
        assertNull(OpposingPolicy.hint(option(5, platform = ""), listOf(other(25, 4))))
        assertNull(OpposingPolicy.hint(option(5), listOf(other(25, 4).copy(realtime = false))))
        val noLive = TripOption(listOf(leg("L1", 24, 5).copy(realtime = false)))
        assertNull(OpposingPolicy.hint(noLive, listOf(other(25, 4))))
    }

    @Test
    fun `rail replacement buses neither get nor cause a hint`() {
        val replacement = TripOption(listOf(leg("L1", 24, 5).copy(replacementBus = true)))
        assertNull(OpposingPolicy.hint(replacement, listOf(other(25, 4))))
        assertNull(OpposingPolicy.hint(option(5), listOf(other(25, 4).copy(replacementBus = true))))
    }

    @Test
    fun `trains timetabled more than five minutes apart do not cross`() {
        assertNull(OpposingPolicy.hint(option(9), listOf(other(30, 3))))
        assertEquals(OpposingHint("L1", 4), OpposingPolicy.hint(option(9), listOf(other(29, 4))))
    }

    @Test
    fun `options starting by bus get no hint`() {
        val bus = TripOption(listOf(leg("L1", 24, 5, mode = "bus")))
        assertNull(OpposingPolicy.hint(bus, listOf(other(25, 4))))
        assertNull(OpposingPolicy.hint(TripOption(), listOf(other(25, 4))))
    }
}
