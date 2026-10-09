package io.github.lonemoonspace.dayloom.core.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The only way business code reads the current time; tests inject a fixed clock.
 * 业务代码读取当前时间的唯一入口；测试注入固定时钟。
 */
interface AppClock {
    /**
     * Current time in the app's time zone (the device zone unless overridden in settings).
     * 应用时区下的当前时刻（默认设备时区，设置里可覆盖）。
     */
    fun now(): ZonedDateTime

    fun instant(): Instant = now().toInstant()

    fun zone(): ZoneId = now().zone
}

/**
 * Production clock. [zoneProvider] is read on every call so a time zone change applies immediately.
 * 生产用时钟。每次调用都重新读取 [zoneProvider]，时区一变立即生效。
 */
class SystemAppClock(
    // Not named `zone`: inside the class `zone()` would resolve to [AppClock.zone] and recurse forever.
    // 不能命名为 `zone`：类内的 `zone()` 会解析成 [AppClock.zone]，造成无限递归。
    private val zoneProvider: () -> ZoneId,
    private val base: Clock = Clock.systemUTC(),
) : AppClock {
    override fun now(): ZonedDateTime = base.instant().atZone(zoneProvider())

    override fun zone(): ZoneId = zoneProvider()
}
