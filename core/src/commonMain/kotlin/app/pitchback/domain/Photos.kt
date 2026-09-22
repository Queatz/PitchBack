package app.pitchback.domain

import kotlin.math.max
import kotlin.math.round

fun scaledPhotoSize(width: Int, height: Int, maxPixelSize: Int?): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return width to height
    val limit = maxPixelSize ?: return width to height
    if (limit <= 0) return width to height
    val longest = max(width, height)
    if (longest <= limit) return width to height
    val scale = limit.toDouble() / longest.toDouble()
    return round(width * scale).toInt().coerceAtLeast(1) to
            round(height * scale).toInt().coerceAtLeast(1)
}

fun dragGhostTopLeft(
    pointerX: Double,
    pointerY: Double,
    grabX: Double,
    grabY: Double,
    originX: Double = 0.0,
    originY: Double = 0.0,
): Pair<Double, Double> =
    (pointerX - grabX - originX) to (pointerY - grabY - originY)

fun movedPhotoIndex(from: Int, to: Int, index: Int): Int {
    if (from == to) return index
    return when {
        index == from -> to
        from < to && index in (from + 1)..to -> index - 1
        to < from && index in to until from -> index + 1
        else -> index
    }
}

fun photoThumbShift(
    from: Int,
    to: Int,
    index: Int,
    slots: List<Pair<Double, Double>>,
): Pair<Double, Double> {
    val dest = movedPhotoIndex(from, to, index)
    val start = slots.getOrNull(index) ?: return 0.0 to 0.0
    val end = slots.getOrNull(dest) ?: return 0.0 to 0.0
    return (end.first - start.first) to (end.second - start.second)
}
