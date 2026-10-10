package io.github.lonemoonspace.dayloom.feature.transit.domain

import kotlinx.serialization.Serializable

/**
 * A stop as the provider identifies it (Entur: `NSR:StopPlace:…`). [locality] is shown under the name to tell stops apart.
 * 提供方识别的站点（Entur：`NSR:StopPlace:…`）。[locality] 显示在名称下方，用来区分同名站点。
 */
@Serializable
data class TransitStop(val id: String = "", val name: String = "", val locality: String = "") {
    val isSet: Boolean get() = id.isNotBlank()
}

/**
 * Which vehicles a commute uses. Train and bus commutes are kept apart, each with its own stops, card and settings, because
 * people plan them differently (a fixed train vs. whichever bus comes).
 * 一段通勤坐什么车。火车与公交分开，各有自己的站点、卡片与设置，因为两者的安排方式不同（固定的火车班次 vs. 哪班公交先来坐哪班）。
 */
enum class CommuteKind { TRAIN, BUS }

/** One commute route: two stops and how many options to show. / 一条通勤路线：两个站点与显示几个方案。 */
@Serializable
data class CommuteRoute(
    val origin: TransitStop = TransitStop(),
    val destination: TransitStop = TransitStop(),
    val options: Int = TransitPolicy.DEFAULT_OPTIONS,
    /** Opt-in, off by default like every notification (design §7.4). / 选择加入，与所有通知一样默认关闭（设计文档 §7.4）。 */
    val notify: Boolean = false,
) {
    val isSet: Boolean get() = origin.isSet && destination.isSet
}

/**
 * One ride on one vehicle; walking between stops is not a leg. Times are epoch millis; aimed = timetable,
 * expected = real-time estimate (equal to aimed without real-time data).
 * 乘坐一辆车的一段；站间步行不算一段。时刻为 epoch 毫秒；aimed = 时刻表，expected = 实时预计（没有实时数据时等于 aimed）。
 */
@Serializable
data class TransitLeg(
    /** Entur transport mode, e.g. `rail`, `bus`, `metro`, `tram`, `water`. / Entur 交通方式。 */
    val mode: String = "",
    /** Public line code, e.g. `R13`, `280`; may be empty for some ferries. / 线路号，如 `R13`、`280`；部分渡轮为空。 */
    val line: String = "",
    val fromName: String = "",
    val toName: String = "",
    /** The sign on the vehicle. / 车前显示牌上的终点。 */
    val frontText: String = "",
    val aimedDeparture: Long = 0,
    val expectedDeparture: Long = 0,
    val aimedArrival: Long = 0,
    val expectedArrival: Long = 0,
    /** False means no real-time data: the times are only the timetable. / false 表示没有实时数据：时刻只是时刻表。 */
    val realtime: Boolean = false,
    val cancelled: Boolean = false,
    /** Platform or stop position the leg leaves from, e.g. `4` or `B`; empty when unknown. / 这一段出发的站台或候车位，如 `4`、`B`；未知时为空。 */
    val platform: String = "",
    /** A bus running in place of the train, e.g. during track work. / 代替火车运行的巴士，例如线路施工期间。 */
    val replacementBus: Boolean = false,
)

/** One way to make the trip: its legs in order; transfers happen between consecutive legs. / 一种出行方案：按顺序的各段；换乘发生在相邻两段之间。 */
@Serializable
data class TripOption(val legs: List<TransitLeg> = emptyList()) {
    val departure: Long get() = legs.firstOrNull()?.expectedDeparture ?: 0
    val arrival: Long get() = legs.lastOrNull()?.expectedArrival ?: 0
    val transfers: Int get() = (legs.size - 1).coerceAtLeast(0)
}

/** Which way the commute card looks. / 通勤卡片看哪个方向。 */
enum class CommuteMode {
    /** Before noon: options origin → destination. / 中午之前：起点 → 终点的方案。 */
    OUTBOUND,

    /** From noon: options destination → origin. / 中午起：终点 → 起点的方案。 */
    INBOUND,
}

/** The snapshot of `transit.train` and `transit.bus`. / `transit.train` 与 `transit.bus` 的快照。 */
@Serializable
data class CommuteTrips(
    val mode: CommuteMode = CommuteMode.OUTBOUND,
    val outbound: List<TripOption> = emptyList(),
    val inbound: List<TripOption> = emptyList(),
)

/**
 * A favourite stop with optional filters: only these lines, only towards these destinations. Empty means everything.
 * 收藏的站点及可选过滤条件：只看这些线路、只看开往这些终点的班次。为空表示不过滤。
 */
@Serializable
data class FavouriteBoard(
    /** `board<n>`. */
    val id: String = "",
    val stop: TransitStop = TransitStop(),
    val lines: List<String> = emptyList(),
    val destinations: List<String> = emptyList(),
)

@Serializable
data class BoardDeparture(
    val line: String = "",
    val mode: String = "",
    val frontText: String = "",
    /** Platform or stop position letter, e.g. `B` or `3`. / 站台或候车位编号，如 `B`、`3`。 */
    val platform: String = "",
    val aimed: Long = 0,
    val expected: Long = 0,
    val realtime: Boolean = false,
    val cancelled: Boolean = false,
    /** A bus running in place of the train; its line code is still the train's. / 代替火车运行的巴士；线路号仍是火车的。 */
    val replacementBus: Boolean = false,
)

@Serializable
data class BoardResult(val boardId: String = "", val stopName: String = "", val departures: List<BoardDeparture> = emptyList())

/** The snapshot of `transit.boards`. / `transit.boards` 的快照。 */
@Serializable
data class Boards(val boards: List<BoardResult> = emptyList())
