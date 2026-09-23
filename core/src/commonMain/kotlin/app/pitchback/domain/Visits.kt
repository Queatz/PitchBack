package app.pitchback.domain

import app.pitchback.api.VisitDayDto
import kotlin.math.roundToInt

const val VISIT_DAY_MS = 86_400_000L
const val VISIT_DAYS = 7

fun visitDayStarts(todayStart: Long, days: Int = VISIT_DAYS): List<Long> {
    val n = days.coerceAtLeast(1)
    return (n - 1 downTo 0).map { todayStart - it * VISIT_DAY_MS }
}

fun fillVisitDays(
    todayStart: Long,
    existing: List<VisitDayDto>,
    days: Int = VISIT_DAYS,
): List<VisitDayDto> {
    val byDay = existing.groupBy { it.dayStart }.mapValues { (start, rows) ->
        VisitDayDto(
            dayStart = start,
            users = rows.sumOf { it.users },
            guests = rows.sumOf { it.guests },
        )
    }
    return visitDayStarts(todayStart, days).map { start ->
        byDay[start] ?: VisitDayDto(dayStart = start)
    }
}

fun nearestVisitIndex(x: Double, width: Double, count: Int): Int {
    if (count <= 1) return 0
    val span = width.takeIf { it > 0.0 } ?: return 0
    val t = (x / span).coerceIn(0.0, 1.0)
    return (t * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

fun visitChartXPositions(
    count: Int,
    width: Double,
    padLeft: Double,
    padRight: Double,
): List<Double> {
    val inner = (width - padLeft - padRight).coerceAtLeast(0.0)
    if (count <= 1) return listOf(padLeft + inner / 2)
    return List(count) { i -> padLeft + inner * i / (count - 1) }
}

fun visitChartY(
    value: Int,
    max: Int,
    height: Double,
    padTop: Double,
    padBottom: Double,
): Double {
    val inner = (height - padTop - padBottom).coerceAtLeast(0.0)
    return padTop + inner * (1.0 - value.toDouble() / max.coerceAtLeast(1))
}
