package app.pitchback.time

import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IctPeriodsTest {
    private val ict: ZoneOffset = ZoneOffset.ofHours(7)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        OffsetDateTime.of(year, month, day, hour, minute, 0, 0, ict).toInstant().toEpochMilli()

    @Test
    fun startOfDayIsMidnightIct() {
        val now = at(2026, 9, 20, 15, 43)
        val start = IctPeriods.startOfDay(now)
        val local = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(start), ict)
        assertEquals(2026, local.year)
        assertEquals(9, local.monthValue)
        assertEquals(20, local.dayOfMonth)
        assertEquals(0, local.hour)
        assertEquals(0, local.minute)
        assertTrue(start <= now)
        assertEquals(start, IctPeriods.startOfDay(start))
        assertEquals(start, IctPeriods.startOfDay(start + 1))
    }

    @Test
    fun startOfDayRollsBackJustAfterMidnight() {
        val midnight = at(2026, 9, 20, 0)
        assertEquals(midnight, IctPeriods.startOfDay(midnight))
        val previous = IctPeriods.startOfDay(midnight - 1)
        val local = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(previous), ict)
        assertEquals(19, local.dayOfMonth)
        assertEquals(0, local.hour)
    }

    @Test
    fun startOfWeekIsMondayOnSunday() {
        val sunday = at(2026, 9, 20, 15, 43)
        val start = IctPeriods.startOfWeek(sunday)
        val local = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(start), ict)
        assertEquals(DayOfWeek.MONDAY, local.dayOfWeek)
        assertEquals(14, local.dayOfMonth)
        assertEquals(0, local.hour)
        assertTrue(start <= sunday)
        assertEquals(start, IctPeriods.startOfWeek(at(2026, 9, 14, 0)))
        assertEquals(start, IctPeriods.startOfWeek(at(2026, 9, 14, 8)))
    }

    @Test
    fun startOfWeekOnMondayStaysPut() {
        val monday = at(2026, 9, 14, 0)
        assertEquals(monday, IctPeriods.startOfWeek(monday))
        assertEquals(monday, IctPeriods.startOfWeek(at(2026, 9, 14, 23, 59)))
    }

    @Test
    fun startOfMonthIsFirstAtMidnight() {
        val now = at(2026, 9, 20, 15, 43)
        val start = IctPeriods.startOfMonth(now)
        val local = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(start), ict)
        assertEquals(1, local.dayOfMonth)
        assertEquals(9, local.monthValue)
        assertEquals(2026, local.year)
        assertEquals(0, local.hour)
        assertTrue(start <= now)
        assertEquals(start, IctPeriods.startOfMonth(at(2026, 9, 1, 0)))
    }
}
