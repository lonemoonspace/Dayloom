package io.github.lonemoonspace.dayloom.feature.transit.domain

import io.github.lonemoonspace.dayloom.core.routine.CommuteDirection
import io.github.lonemoonspace.dayloom.core.routine.RoutinePolicy
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Locale

/**
 * The four states a departure can be in. [NO_REALTIME] must never be shown as on time: saying "on time" when we do not
 * know is the unsafe direction.
 * 班次的四种状态。[NO_REALTIME] 绝不能显示成正点：不知道时说「准点」是不安全的方向。
 */
enum class LegState { ON_TIME, DELAYED, NO_REALTIME, CANCELLED }

data class LegStatus(val state: LegState, val delayMinutes: Int)

/** What a shown transfer gains over the direct option it beats. / 显示出来的换乘方案比它胜过的直达方案好在哪里。 */
enum class TransferGainKind { ARRIVE_EARLIER, LEAVE_LATER }

data class TransferGain(val kind: TransferGainKind, val minutes: Int)

/**
 * Pure transit decisions; the status rules and destination matching are ported from the original project's train and bus
 * cards.
 * 公共交通的纯判定逻辑；状态规则与终点匹配移植自原项目的火车与公交卡片。
 */
object TransitPolicy {

    /** How many options the commute card may show. / 通勤卡片最多显示几个方案。 */
    val OPTIONS_RANGE = 1..5
    const val DEFAULT_OPTIONS = 3

    /** Departures kept per favourite stop, more than shown so the card survives a while between refreshes. / 每个收藏站点保留的班次数，多于显示数，刷新间隔里卡片也不会空掉。 */
    const val BOARD_KEEP = 8
    const val BOARD_VISIBLE = 4

    /** A delay of at least this much counts as a disruption worth a notification. / 至少这么久的延误才算值得通知的异常。 */
    const val DISRUPTION_DELAY_MINUTES = 5

    /** Disruptions further ahead than this are left for the card. / 比这更远的异常留给卡片显示。 */
    val ALERT_AHEAD: Duration = Duration.ofMinutes(45)

    fun legStatus(cancelled: Boolean, realtime: Boolean, aimed: Long, expected: Long): LegStatus {
        val delay = Duration.ofMillis(expected - aimed).toMinutes().toInt().coerceAtLeast(0)
        return when {
            cancelled -> LegStatus(LegState.CANCELLED, 0)
            !realtime -> LegStatus(LegState.NO_REALTIME, 0)
            delay > 0 -> LegStatus(LegState.DELAYED, delay)
            else -> LegStatus(LegState.ON_TIME, 0)
        }
    }

    fun legStatus(leg: TransitLeg): LegStatus = legStatus(leg.cancelled, leg.realtime, leg.aimedDeparture, leg.expectedDeparture)

    fun legStatus(departure: BoardDeparture): LegStatus =
        legStatus(departure.cancelled, departure.realtime, departure.aimed, departure.expected)

    /**
     * The status that describes a whole option: a cancellation beats the longest delay, which beats missing real-time data.
     * Returns the leg it comes from, so the card can name the line.
     * 代表整个方案的状态：取消优先于最长的延误，延误优先于缺实时数据。同时返回来源的那一段，卡片可以说出是哪条线。
     */
    fun worst(option: TripOption): Pair<TransitLeg, LegStatus>? =
        option.legs.map { it to legStatus(it) }.maxWithOrNull(compareBy({ rank(it.second.state) }, { it.second.delayMinutes }))

    private fun rank(state: LegState) = when (state) {
        LegState.ON_TIME -> 0
        LegState.NO_REALTIME -> 1
        LegState.DELAYED -> 2
        LegState.CANCELLED -> 3
    }

    /** Mornings to work, from noon homewards ([RoutinePolicy.direction]). / 上午去上班，中午起回家（[RoutinePolicy.direction]）。 */
    fun commuteMode(now: ZonedDateTime): CommuteMode = when (RoutinePolicy.direction(now)) {
        CommuteDirection.TO_WORK -> CommuteMode.OUTBOUND
        CommuteDirection.BACK_HOME -> CommuteMode.INBOUND
    }

    /**
     * Whether a disruption of [option] is worth a notification now: only in the daytime, and only when it leaves within
     * [ALERT_AHEAD]. Without commute windows the direction is always set, so these limits keep alerts to trips the user
     * may be about to take rather than every disruption all day.
     * [option] 的异常现在是否值得通知：只在白天，且只在它 [ALERT_AHEAD] 内发车时。没有通勤时间窗后方向总是确定的，靠这两个
     * 限制把提醒留给用户可能马上要坐的车，而不是全天每一次异常。
     */
    fun isAlertable(option: TripOption, now: ZonedDateTime): Boolean =
        RoutinePolicy.isDaytime(now) && (option.departure - now.toInstant().toEpochMilli()).let { it >= 0 && it <= ALERT_AHEAD.toMillis() }

    /**
     * Options not yet departed, at most [limit], after [withTransfers]; a cancelled first leg still shows, so the user sees
     * why it is missing.
     * 经 [withTransfers] 处理后尚未发车的方案，最多 [limit] 个；第一段被取消的方案照样显示，用户才知道它为什么没了。
     */
    fun visibleOptions(options: List<TripOption>, now: ZonedDateTime, limit: Int): List<TripOption> {
        val nowMillis = now.toInstant().toEpochMilli()
        // A limit from a damaged settings file must not crash the card. / 设置文件损坏得来的上限不能让卡片崩溃。
        return withTransfers(options).filter { it.legs.isNotEmpty() && it.departure >= nowMillis }.sortedBy { it.departure }
            .take(limit.coerceAtLeast(0))
    }

    /**
     * Keeps an [TripOption.alternative] only when nothing else leaves at least as late and arrives at least as early, and it
     * is not cancelled. A kept transfer that starts on the same ride as a direct option and arrives earlier replaces it:
     * staying on board at the transfer stop is that direct option, so two rows would repeat the same train.
     * 只有当没有别的方案出发不早于它、到达又不晚于它，且它没被取消时，才保留 [TripOption.alternative]。保留下来的换乘方案若和
     * 某个直达方案第一段是同一班车、又到得更早，就取代那个直达：在换乘站不下车就是那个直达，两行会重复同一班车。
     */
    fun withTransfers(options: List<TripOption>): List<TripOption> {
        if (options.none { it.alternative }) return options
        val kept = options.withIndex().filter { (i, option) ->
            option.alternative && !option.isCancelled() &&
                options.withIndex().none { (j, other) -> j != i && beats(other, option, otherFirst = j < i) }
        }.map { it.value }
        return options.filter { option ->
            if (option.alternative) {
                kept.any { it === option }
            } else {
                kept.none { sameFirstRide(it, option) && it.arrival < option.arrival }
            }
        }
    }

    /**
     * What [option] gains over the latest direct option it beats in [options] (the raw list, before [withTransfers]); null
     * for ordinary options. Arriving earlier is named first; otherwise the same arrival with a later start.
     * [option] 比 [options]（[withTransfers] 之前的原始列表）里它胜过的最晚出发的直达方案好在哪里；普通方案返回 null。
     * 先看能否早到；否则是同时到、但可以晚走。
     */
    fun transferGain(option: TripOption, options: List<TripOption>): TransferGain? {
        if (!option.alternative) return null
        val direct = options.filter { !it.alternative && !it.isCancelled() && it.departure <= option.departure && it.arrival >= option.arrival }
            .maxByOrNull { it.departure } ?: return null
        val earlier = Duration.ofMillis(direct.arrival - option.arrival).toMinutes().toInt()
        val later = Duration.ofMillis(option.departure - direct.departure).toMinutes().toInt()
        return when {
            earlier > 0 -> TransferGain(TransferGainKind.ARRIVE_EARLIER, earlier)
            later > 0 -> TransferGain(TransferGainKind.LEAVE_LATER, later)
            else -> null
        }
    }

    /**
     * [other] makes [option] pointless: it leaves no earlier and arrives no later. On a tie an ordinary option, or the one
     * listed first, wins, so equal options do not hide each other. A cancelled option beats nothing.
     * [other] 让 [option] 没有意义：出发不更早、到达不更晚。打平时普通方案或排在前面的胜出，相同的方案不会互相隐藏。
     * 被取消的方案胜不过任何方案。
     */
    private fun beats(other: TripOption, option: TripOption, otherFirst: Boolean): Boolean {
        if (other.legs.isEmpty() || other.isCancelled() || other.departure < option.departure || other.arrival > option.arrival) return false
        return other.departure > option.departure || other.arrival < option.arrival || !other.alternative || otherFirst
    }

    private fun sameFirstRide(a: TripOption, b: TripOption): Boolean {
        val x = a.legs.firstOrNull() ?: return false
        val y = b.legs.firstOrNull() ?: return false
        return x.line == y.line && x.fromName == y.fromName && x.aimedDeparture == y.aimedDeparture
    }

    private fun TripOption.isCancelled() = legs.any { it.cancelled }

    /**
     * Whole minutes until a board departure when it leaves within the hour, else null: later ones show only their clock time.
     * 一小时内发车时返回还有几分钟，否则返回 null：更晚的班次只显示时刻。
     */
    fun minutesUntil(expected: Long, now: ZonedDateTime): Int? =
        Duration.between(now.toInstant(), Instant.ofEpochMilli(expected)).toMinutes().coerceAtLeast(0).takeIf { it < 60 }?.toInt()

    fun visibleDepartures(departures: List<BoardDeparture>, now: ZonedDateTime, limit: Int = BOARD_VISIBLE): List<BoardDeparture> {
        val nowMillis = now.toInstant().toEpochMilli()
        return departures.filter { it.expected >= nowMillis }.sortedBy { it.expected }.take(limit)
    }

    /**
     * A favourite stop's departures: only the chosen lines and destinations, not yet departed, soonest first.
     * 收藏站点的班次：只留所选线路与终点、尚未发车的，按时间先后排列。
     */
    fun filterBoard(board: FavouriteBoard, departures: List<BoardDeparture>, now: ZonedDateTime): List<BoardDeparture> {
        val lines = board.lines.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()
        val nowMillis = now.toInstant().toEpochMilli()
        return departures.asSequence()
            .filter { lines.isEmpty() || it.line.lowercase(Locale.ROOT) in lines }
            .filter { board.destinations.none { d -> d.isNotBlank() } || board.destinations.any { d -> destinationMatches(it.frontText, d) } }
            .filter { it.expected >= nowMillis }
            .sortedBy { it.expected }
            .take(BOARD_KEEP)
            .toList()
    }

    /**
     * Matches the sign on the vehicle against a wanted destination. Tolerates "Sentrum stasjon" and "Sentrum via X", but is
     * not a substring match, which would mistake unrelated termini for the same direction.
     * 车前显示牌与想要的终点是否匹配。容忍「Sentrum stasjon」「Sentrum via X」这类写法，但不做子串匹配——那会把无关的终点
     * 误判成同一方向。
     */
    fun destinationMatches(frontText: String?, wanted: String): Boolean {
        val candidate = normalize(frontText) ?: return false
        val target = normalize(wanted) ?: return false
        return candidate == target || candidate.startsWith("$target ")
    }

    private fun normalize(raw: String?): String? = raw
        ?.lowercase(Locale.ROOT)
        ?.replace(" stasjon", "")
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotEmpty() }

    /** Splits a comma-separated filter as typed in settings. / 拆分设置里以逗号分隔的过滤条件。 */
    fun parseList(text: String): List<String> = text.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** A fresh id: `board<n>` with the smallest unused n. / 新 id：`board<n>`，n 取最小未用的数。 */
    fun newBoardId(boards: List<FavouriteBoard>): String {
        val used = boards.mapTo(mutableSetOf()) { it.id }
        return generateSequence(1) { it + 1 }.map { "board$it" }.first { it !in used }
    }
}
