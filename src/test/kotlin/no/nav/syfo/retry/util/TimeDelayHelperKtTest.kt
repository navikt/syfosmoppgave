package no.nav.syfo.retry.util

import java.time.Duration
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TimeDelayHelperKtTest : Spek({
    describe("Test delay time") {
        it("should get same day delay") {
            val rerunTimes = listOf(OffsetTime.of(3, 0, 0, 0, ZoneOffset.UTC),
                    OffsetTime.of(12, 0, 0, 0, ZoneOffset.UTC))

            val dateTime = OffsetDateTime.of(2020, 1, 1, 11, 0, 0, 0, ZoneOffset.UTC)
            val correctDelayTime = Duration.ofHours(1).toMillis()
            val delayTime = getNextRunTime(dateTime, rerunTimes)
            delayTime shouldEqual correctDelayTime
        }
        it("should get next day delay") {
            val rerunTimes = listOf(OffsetTime.of(3, 0, 0, 0, ZoneOffset.UTC),
                    OffsetTime.of(12, 0, 0, 0, ZoneOffset.UTC))

            val dateTime = OffsetDateTime.of(2020, 1, 1, 13, 0, 0, 0, ZoneOffset.UTC)
            val correctDelayTime = Duration.ofHours(14).toMillis()
            val delayTime = getNextRunTime(dateTime, rerunTimes)
            delayTime shouldEqual correctDelayTime
        }
    }
})
