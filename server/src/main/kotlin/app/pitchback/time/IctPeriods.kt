package app.pitchback.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

object IctPeriods {
    val ZONE: ZoneOffset = ZoneOffset.ofHours(7)

    fun startOfDay(nowMs: Long): Long =
        Instant.ofEpochMilli(nowMs).atOffset(ZONE).toLocalDate()
            .atStartOfDay(ZONE).toInstant().toEpochMilli()

    fun startOfWeek(nowMs: Long): Long =
        Instant.ofEpochMilli(nowMs).atOffset(ZONE).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZONE).toInstant().toEpochMilli()

    fun startOfMonth(nowMs: Long): Long =
        Instant.ofEpochMilli(nowMs).atOffset(ZONE).toLocalDate()
            .withDayOfMonth(1)
            .atStartOfDay(ZONE).toInstant().toEpochMilli()
}
