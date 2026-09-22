package app.pitchback.domain

data class ShareRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

fun sharePhotoRowSlots(count: Int, canvasSize: Double, gap: Double): List<ShareRect> {
    if (count <= 0 || canvasSize <= 0) return emptyList()
    val height = canvasSize / 2.0
    val totalGap = gap * (count - 1).coerceAtLeast(0)
    val width = ((canvasSize - totalGap) / count).coerceAtLeast(0.0)
    return List(count) { i ->
        ShareRect(
            x = i * (width + gap),
            y = 0.0,
            width = width,
            height = height,
        )
    }
}

fun shareAvatarCircle(canvasSize: Double, radiusFraction: Double = 0.18): Triple<Double, Double, Double> {
    val r = canvasSize * radiusFraction
    return Triple(canvasSize / 2.0, canvasSize / 2.0, r)
}

fun pitchShareUrl(origin: String, pitchId: String): String =
    origin.trimEnd('/') + "/pitch/" + pitchId

fun wrapShareLines(
    text: String,
    maxWidth: Double,
    maxLines: Int,
    measure: (String) -> Double,
): List<String> {
    if (maxLines <= 0 || maxWidth <= 0) return emptyList()
    val raw = text.trim().replace("\r\n", "\n")
    if (raw.isEmpty()) return emptyList()

    val unlimited = mutableListOf<String>()
    for (para in raw.split('\n')) {
        val words = para.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) {
            unlimited += ""
            continue
        }
        var current = ""
        fun pushCurrent() {
            if (current.isNotEmpty()) {
                unlimited += current
                current = ""
            }
        }
        for (word in words) {
            val pieces = breakShareWord(word, maxWidth, measure)
            for (piece in pieces) {
                val candidate = if (current.isEmpty()) piece else "$current $piece"
                if (current.isEmpty() || measure(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    pushCurrent()
                    current = piece
                }
            }
        }
        pushCurrent()
    }

    if (unlimited.size <= maxLines) return unlimited
    val kept = unlimited.take(maxLines).toMutableList()
    val rest = unlimited.drop(maxLines - 1).joinToString(" ")
    kept[maxLines - 1] = ellipsizeShareLine(rest, maxWidth, measure)
    return kept
}

internal fun breakShareWord(
    word: String,
    maxWidth: Double,
    measure: (String) -> Double,
): List<String> {
    if (measure(word) <= maxWidth) return listOf(word)
    val out = mutableListOf<String>()
    val chunk = StringBuilder()
    for (ch in word) {
        val next = chunk.toString() + ch
        if (chunk.isNotEmpty() && measure(next) > maxWidth) {
            out += chunk.toString()
            chunk.clear()
            chunk.append(ch)
        } else {
            chunk.append(ch)
        }
    }
    if (chunk.isNotEmpty()) out += chunk.toString()
    return out.ifEmpty { listOf(word) }
}

internal fun ellipsizeShareLine(
    text: String,
    maxWidth: Double,
    measure: (String) -> Double,
): String {
    val ellipsis = "…"
    if (measure(text) <= maxWidth) return text
    if (measure(ellipsis) > maxWidth) return ""
    var lo = 0
    var hi = text.length
    var best = ellipsis
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val candidate = text.take(mid).trimEnd() + ellipsis
        if (measure(candidate) <= maxWidth) {
            best = candidate
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return best
}
