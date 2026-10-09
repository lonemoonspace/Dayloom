package io.github.lonemoonspace.dayloom.core.time

import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The current time now and then at every minute boundary, for flows whose result depends on the clock (a card pinned while
 * an item is about to expire, a traffic direction that follows the daily windows).
 * 立即发出当前时刻，之后每到整分钟再发一次；用于结果取决于时钟的流（快到期时置顶的卡片、随日常时间窗切换方向的路况）。
 */
fun AppClock.minuteTicks(): Flow<ZonedDateTime> = flow {
    while (true) {
        val now = now()
        emit(now)
        delay(Duration.between(now, now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)).toMillis().coerceAtLeast(1))
    }
}
