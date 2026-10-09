package io.github.lonemoonspace.dayloom.core.time

import java.time.ZonedDateTime

class FixedClock(var current: ZonedDateTime) : AppClock {
    override fun now(): ZonedDateTime = current
}
