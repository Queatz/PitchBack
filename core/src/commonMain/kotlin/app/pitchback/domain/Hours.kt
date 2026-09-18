package app.pitchback.domain

import app.pitchback.api.DayHours
import app.pitchback.api.OpeningHours

/** JS Date.getDay(): 0 = Sunday … 6 = Saturday */
fun hoursForJsDay(hours: OpeningHours, day: Int): DayHours = when (day.mod(7)) {
    0 -> hours.sun
    1 -> hours.mon
    2 -> hours.tue
    3 -> hours.wed
    4 -> hours.thu
    5 -> hours.fri
    else -> hours.sat
}

fun parseMinutes(hhmm: String?): Int? {
    val parts = hhmm?.trim()?.split(":") ?: return null
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].takeWhile { it.isDigit() }.toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

fun formatMinutes(total: Int): String {
    val wrapped = ((total % (24 * 60)) + (24 * 60)) % (24 * 60)
    val h = wrapped / 60
    val m = wrapped % 60
    return h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
}

fun isOpenAt(hours: OpeningHours, jsDay: Int, minutes: Int): Boolean {
    val prev = hoursForJsDay(hours, jsDay - 1)
    if (!prev.closed) {
        val po = parseMinutes(prev.open)
        val pc = parseMinutes(prev.close)
        if (po != null && pc != null && pc <= po && minutes < pc) return true
    }
    val today = hoursForJsDay(hours, jsDay)
    if (today.closed) return false
    val open = parseMinutes(today.open) ?: return false
    val close = parseMinutes(today.close) ?: return false
    return if (close > open) minutes in open until close else minutes >= open
}

data class OpenStatus(
    val open: Boolean,
    val nextMinutes: Int? = null,
    val nextDay: Int? = null,
)

fun openStatus(hours: OpeningHours, jsDay: Int, minutes: Int): OpenStatus {
    if (isOpenAt(hours, jsDay, minutes)) {
        val today = hoursForJsDay(hours, jsDay)
        if (!today.closed) {
            val open = parseMinutes(today.open)
            val close = parseMinutes(today.close)
            if (open != null && close != null) {
                when {
                    close > open && minutes < close -> return OpenStatus(true, close, jsDay)
                    close <= open && minutes >= open -> return OpenStatus(true, close, (jsDay + 1).mod(7))
                    close <= open && minutes < close -> return OpenStatus(true, close, jsDay)
                }
            }
        }
        val prevClose = parseMinutes(hoursForJsDay(hours, jsDay - 1).close)
        if (prevClose != null) return OpenStatus(true, prevClose, jsDay)
        return OpenStatus(true)
    }
    for (offset in 0..7) {
        val d = (jsDay + offset).mod(7)
        val h = hoursForJsDay(hours, d)
        if (h.closed) continue
        val open = parseMinutes(h.open) ?: continue
        if (offset == 0 && minutes >= open) continue
        return OpenStatus(false, open, d)
    }
    return OpenStatus(false)
}

data class HoursGroup(
    val startDay: Int,
    val endDay: Int,
    val closed: Boolean,
    val open: String?,
    val close: String?,
)

/** Days are Monday-first: 0 = Mon … 6 = Sun. */
fun condensedHours(hours: OpeningHours): List<HoursGroup> {
    val days = listOf(hours.mon, hours.tue, hours.wed, hours.thu, hours.fri, hours.sat, hours.sun)
    val groups = mutableListOf<HoursGroup>()
    days.forEachIndexed { i, h ->
        val last = groups.lastOrNull()
        val same = last != null && last.closed == h.closed && last.open == h.open && last.close == h.close
        if (same && last != null) {
            groups[groups.lastIndex] = last.copy(endDay = i)
        } else {
            groups += HoursGroup(i, i, h.closed, h.open, h.close)
        }
    }
    return groups
}
