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
