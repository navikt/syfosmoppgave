package no.nav.syfo.retry.util

import java.time.OffsetDateTime
import java.time.OffsetTime

fun getNextRunTime(now: OffsetDateTime, rerunTimes: List<OffsetTime>): Long {
    val nextTime = rerunTimes.map {
        var dateTime = it.atDate(now.toLocalDate())
        if (dateTime.isBefore(now)) {
            dateTime = dateTime.plusDays(1)
        }
        dateTime.toInstant().toEpochMilli()
    }.min()!!
    return nextTime - now.toInstant().toEpochMilli()
}
