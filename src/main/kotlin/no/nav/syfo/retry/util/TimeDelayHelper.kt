package no.nav.syfo.retry.util

import java.time.OffsetDateTime
import java.time.OffsetTime

fun getNextRunTime(now: OffsetDateTime, rerunTimes: List<OffsetTime>): Long {
    val nextTime =
        rerunTimes.minOf {
            var dateTime = it.atDate(now.toLocalDate())
            if (dateTime.isBefore(now)) {
                dateTime = dateTime.plusDays(1)
            }
            dateTime.toInstant().toEpochMilli()
        }
    return nextTime - now.toInstant().toEpochMilli()
}
