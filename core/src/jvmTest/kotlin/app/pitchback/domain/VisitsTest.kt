package app.pitchback.domain

import app.pitchback.api.VisitDayDto
import kotlin.test.Test
import kotlin.test.assertEquals

class VisitsTest {

    private val today = 1_000_000_000_000L

    @Test
    fun visitDayStartsEndsOnTodayAndSpansSevenDays() {
        val days = visitDayStarts(today)
        assertEquals(7, days.size)
        assertEquals(today - 6 * VISIT_DAY_MS, days.first())
        assertEquals(today, days.last())
        assertEquals(days, days.distinct().sorted())
        days.zipWithNext().forEach { (a, b) ->
            assertEquals(VISIT_DAY_MS, b - a)
        }
    }

    @Test
    fun fillVisitDaysZerosMissingAndDropsOutsideWindow() {
        val inside = today - 2 * VISIT_DAY_MS
        val outside = today - 9 * VISIT_DAY_MS
        val filled = fillVisitDays(
            today,
            listOf(
                VisitDayDto(dayStart = inside, users = 4, guests = 1),
                VisitDayDto(dayStart = outside, users = 99, guests = 99),
            ),
        )
        assertEquals(7, filled.size)
        assertEquals(VisitDayDto(inside, 4, 1), filled[4])
        assertEquals(listOf(0, 0, 0, 0, 4, 0, 0), filled.map { it.users })
        assertEquals(listOf(0, 0, 0, 0, 1, 0, 0), filled.map { it.guests })
        assertEquals(visitDayStarts(today), filled.map { it.dayStart })
    }

    @Test
    fun fillVisitDaysSumsDuplicateDaysAndKeepsUserGuestSplit() {
        val day = today - VISIT_DAY_MS
        val filled = fillVisitDays(
            today,
            listOf(
                VisitDayDto(dayStart = day, users = 2, guests = 3),
                VisitDayDto(dayStart = day, users = 5, guests = 1),
                VisitDayDto(dayStart = today, users = 8, guests = 0),
            ),
        )
        assertEquals(VisitDayDto(day, 7, 4), filled[5])
        assertEquals(VisitDayDto(today, 8, 0), filled[6])
        filled.take(5).forEach { row ->
            assertEquals(0, row.users)
            assertEquals(0, row.guests)
        }
    }

    @Test
    fun nearestVisitIndexSnapsToEndsAndMiddle() {
        assertEquals(0, nearestVisitIndex(-10.0, 100.0, 7))
        assertEquals(0, nearestVisitIndex(0.0, 100.0, 7))
        assertEquals(6, nearestVisitIndex(100.0, 100.0, 7))
        assertEquals(6, nearestVisitIndex(140.0, 100.0, 7))
        assertEquals(3, nearestVisitIndex(50.0, 100.0, 7))
        assertEquals(0, nearestVisitIndex(40.0, 0.0, 7))
        assertEquals(0, nearestVisitIndex(80.0, 100.0, 1))
        assertEquals(0, nearestVisitIndex(80.0, 100.0, 0))
    }

    @Test
    fun visitChartXPositionsScaleWithWidth() {
        val narrow = visitChartXPositions(7, 100.0, 10.0, 10.0)
        val wide = visitChartXPositions(7, 400.0, 10.0, 10.0)
        assertEquals(7, narrow.size)
        assertEquals(10.0, narrow.first())
        assertEquals(90.0, narrow.last())
        assertEquals(50.0, narrow[3])
        assertEquals(10.0, wide.first())
        assertEquals(390.0, wide.last())
        assertEquals(200.0, wide[3])
        assertEquals(listOf(50.0), visitChartXPositions(1, 100.0, 10.0, 10.0))
    }

    @Test
    fun visitChartYScalesWithHeight() {
        assertEquals(18.0, visitChartY(10, 10, 180.0, 18.0, 18.0))
        assertEquals(162.0, visitChartY(0, 10, 180.0, 18.0, 18.0))
        assertEquals(90.0, visitChartY(5, 10, 180.0, 18.0, 18.0))
        assertEquals(18.0, visitChartY(10, 10, 360.0, 18.0, 18.0))
        assertEquals(342.0, visitChartY(0, 10, 360.0, 18.0, 18.0))
    }
}
