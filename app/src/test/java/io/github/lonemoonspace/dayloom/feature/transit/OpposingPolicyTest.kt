package io.github.lonemoonspace.dayloom.feature.transit

import io.github.lonemoonspace.dayloom.feature.transit.domain.BoardDeparture
import io.github.lonemoonspace.dayloom.feature.transit.domain.OpposingEffect
import io.github.lonemoonspace.dayloom.feature.transit.domain.OpposingHint
import io.github.lonemoonspace.dayloom.feature.transit.domain.OpposingPolicy
import io.github.lonemoonspace.dayloom.feature.transit.domain.TransitLeg
import io.github.lonemoonspace.dayloom.feature.transit.domain.TripOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpposingPolicyTest {

    private fun at(minute: Int) = minute * 60_000L

    private fun leg(line: String, dep: Int, arr: Int, late: Int = 0, platform: String = "1", front: String = "Nordby", mode: String = "rail") =
        TransitLeg(
            mode = mode, line = line, frontText = front, platform = platform,
            aimedDeparture = at(dep), expectedDeparture = at(dep + late), aimedArrival = at(arr), expectedArrival = at(arr + late),
        )

    private fun other(aimed: Int, late: Int, line: String = "L1", front: String = "Østby", platform: String = "2", mode: String = "rail") =
        BoardDeparture(line = line, mode = mode, frontText = front, platform = platform, aimed = at(aimed), expected = at(aimed + late))

    /** L1 at :24 (expected :24) to the change at :36, then R12 at :40. / L1 :24 出发，:36 到换乘站，再换 :40 的 R12。 */
    private val transfer = TripOption(listOf(leg("L1", 24, 36), leg("R12", 40, 50)))
    private val direct = TripOption(listOf(leg("L1", 24, 52)))

    @Test
    fun `a late opposing train that eats the change time warns about the change`() {
        // Waits until :30, 6 min; 4 min of slack is gone. / 等到 :30，6 分钟，4 分钟的换乘余量没了。
        assertEquals(
            OpposingHint("L1", 6, OpposingEffect.MissTransfer("R12")),
            OpposingPolicy.hint(transfer, listOf(other(24, 6))),
        )
    }

    @Test
    fun `with enough slack left the change holds`() {
        val roomy = TripOption(listOf(leg("L1", 24, 36), leg("R12", 50, 60)))
        assertEquals(OpposingHint("L1", 4, OpposingEffect.TransferHolds), OpposingPolicy.hint(roomy, listOf(other(25, 4))))
    }

    @Test
    fun `a direct train arrives later by the wait`() {
        // Opposing aimed :26, 5 late, so :31; the first train waits 7 min. / 对向 :26 晚 5 分到 :31，本车等 7 分钟。
        assertEquals(OpposingHint("L1", 5, OpposingEffect.LateArrival(7)), OpposingPolicy.hint(direct, listOf(other(26, 5))))
    }

    @Test
    fun `the latest opposing train in the window counts`() {
        val hint = OpposingPolicy.hint(direct, listOf(other(22, 3), other(27, 8)))
        assertEquals(OpposingHint("L1", 8, OpposingEffect.LateArrival(11)), hint)
    }

    @Test
    fun `no hint when the wait is short or the first train's own delay already covers it`() {
        assertNull(OpposingPolicy.hint(direct, listOf(other(24, 1))))
        // First train already expected at :30, opposing at :31. / 本车已预计 :30 开，对向 :31。
        val late = TripOption(listOf(leg("L1", 24, 52, late = 6)))
        assertNull(OpposingPolicy.hint(late, listOf(other(24, 7))))
        // Opposing on time but leaving after the first train is not a delay. / 对向准点只是开得晚，不算晚点。
        assertNull(OpposingPolicy.hint(direct, listOf(other(28, 0))))
    }

    @Test
    fun `same direction, same platform, other lines and other modes are not opposing`() {
        assertNull(OpposingPolicy.hint(direct, listOf(other(24, 6, front = "Nordby"))))
        assertNull(OpposingPolicy.hint(direct, listOf(other(24, 6, platform = "1"))))
        assertNull(OpposingPolicy.hint(direct, listOf(other(24, 6, line = "L2"))))
        assertNull(OpposingPolicy.hint(direct, listOf(other(24, 6, mode = "bus"))))
        assertNull(OpposingPolicy.hint(direct, listOf(other(24, 6).copy(cancelled = true))))
    }

    @Test
    fun `an unknown platform does not rule a train out`() {
        assertEquals(OpposingHint("L1", 6, OpposingEffect.LateArrival(6)), OpposingPolicy.hint(direct, listOf(other(24, 6, platform = ""))))
    }

    @Test
    fun `trains timetabled more than five minutes apart do not cross`() {
        assertNull(OpposingPolicy.hint(direct, listOf(other(30, 6))))
        assertEquals(OpposingEffect.LateArrival(10), OpposingPolicy.hint(direct, listOf(other(29, 5)))?.effect)
    }

    @Test
    fun `options starting by bus get no hint`() {
        val bus = TripOption(listOf(leg("L1", 24, 52, mode = "bus")))
        assertNull(OpposingPolicy.hint(bus, listOf(other(24, 6))))
        assertNull(OpposingPolicy.hint(TripOption(), listOf(other(24, 6))))
    }
}
