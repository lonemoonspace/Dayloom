package io.github.lonemoonspace.dayloom.feature.transit.domain

import java.time.ZonedDateTime

/**
 * A public transport data provider. The first version only has Entur (Norway); another region means another implementation,
 * nothing else changes (design §11.2).
 * 公共交通数据提供方。第一版只有 Entur（挪威）；支持别的地区就是再写一个实现，其他都不用改（设计文档 §11.2）。
 */
interface TransitProvider {
    suspend fun searchStops(query: String): List<TransitStop>

    /**
     * Trip options from [from] to [to] leaving at or after [at] on [kind] vehicles only, soonest first.
     * 从 [from] 到 [to]、不早于 [at] 出发、只坐 [kind] 类车辆的方案，按时间先后。
     */
    suspend fun planTrips(from: TransitStop, to: TransitStop, at: ZonedDateTime, count: Int, kind: CommuteKind): List<TripOption>

    /** Upcoming departures per stop id; a stop the provider no longer knows is simply missing. / 每个站点的后续班次；提供方已不认识的站点直接缺席。 */
    suspend fun departures(stopIds: List<String>, at: ZonedDateTime): Map<String, List<BoardDeparture>>
}
