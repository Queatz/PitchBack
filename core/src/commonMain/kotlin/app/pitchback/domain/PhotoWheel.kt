package app.pitchback.domain

import kotlin.math.abs
import kotlin.math.floor

fun horizontalScrollDelta(deltaX: Double, deltaY: Double, shiftKey: Boolean): Double? {
    val primary = if (shiftKey && abs(deltaX) <= abs(deltaY)) deltaY else deltaX
    if (abs(primary) < 0.5) return null
    if (!shiftKey && abs(deltaX) <= abs(deltaY)) return null
    return primary
}

fun wheelDeltaPx(delta: Double, deltaMode: Int, pageSize: Double): Double = when (deltaMode) {
    1 -> delta * 16.0
    2 -> delta * pageSize.coerceAtLeast(1.0)
    else -> delta
}

data class PhotoWheelScrub(val index: Int, val drag: Double)

fun applyPhotoWheel(
    index: Int,
    drag: Double,
    delta: Double,
    lastIndex: Int,
    width: Double,
): PhotoWheelScrub {
    if (lastIndex <= 0) return PhotoWheelScrub(0, 0.0)
    val w = width.coerceAtLeast(1.0)
    val pos = (index.toDouble() - drag / w + delta / w).coerceIn(0.0, lastIndex.toDouble())
    val nextIndex = pos.toInt().coerceIn(0, lastIndex)
    return PhotoWheelScrub(nextIndex, (nextIndex - pos) * w)
}

fun settlePhotoWheel(index: Int, drag: Double, lastIndex: Int, width: Double): Int {
    if (lastIndex <= 0) return 0
    val w = width.coerceAtLeast(1.0)
    val pos = (index.toDouble() - drag / w).coerceIn(0.0, lastIndex.toDouble())
    return floor(pos + 0.5).toInt().coerceIn(0, lastIndex)
}
