package io.github.lonemoonspace.dayloom.feature.weather.domain

import kotlinx.serialization.Serializable

/**
 * The snapshot of `weather.forecast`: MET's hourly series reduced to what the card uses. Symbol codes are MET's identifiers
 * (`lightrain_day`), not text; they are mapped to resources only when shown.
 * `weather.forecast` 的快照：把 MET 的逐小时序列精简为卡片要用的部分。天气符号是 MET 的标识符（`lightrain_day`），不是文案；
 * 只在显示时映射到资源。
 */
@Serializable
data class Forecast(
    /** When MET last updated the model output, epoch millis. / MET 最近一次更新预报的时刻，epoch 毫秒。 */
    val updatedAt: Long = 0,
    /** `Last-Modified` of the response, sent back as `If-Modified-Since` as MET's terms ask. / 响应的 `Last-Modified`，按 MET 条款以 `If-Modified-Since` 回传。 */
    val lastModified: String = "",
    /**
     * Time of the point picked as "current" when fetched. Not the fetch time: a fetch succeeds even when MET stopped updating,
     * so only this says how old the reading is; freshness is judged by it. 0 = unknown.
     * 抓取时被选为「当前」的数据点的时刻。不是抓取时刻：MET 断更时抓取照样成功，只有它能说明读数是什么时候的；新鲜度按它判定。0 = 未知。
     */
    val observedAt: Long = 0,
    val points: List<ForecastPoint> = emptyList(),
)

@Serializable
data class ForecastPoint(
    /** Start of the step, epoch millis. / 该步的起始时刻，epoch 毫秒。 */
    val time: Long = 0,
    /** °C. */
    val temperature: Double? = null,
    /** m/s. */
    val windSpeed: Double? = null,
    /** Symbol for the next hour; empty beyond the hourly range. / 未来一小时的天气符号；超出逐小时范围时为空。 */
    val symbol1h: String = "",
    /** mm in the next hour. / 未来一小时降水量（毫米）。 */
    val precipitation1h: Double? = null,
    val symbol6h: String = "",
    val precipitation6h: Double? = null,
) {
    /** The most detailed symbol available. / 可用的最细粒度符号。 */
    val symbol: String get() = symbol1h.ifEmpty { symbol6h }
}
