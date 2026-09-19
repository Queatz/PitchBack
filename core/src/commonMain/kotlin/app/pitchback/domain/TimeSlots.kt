package app.pitchback.domain

import app.pitchback.api.WeeklyAvailability

const val ICT_OFFSET_MINUTES = 7 * 60
const val OFFERED_SLOT_COUNT = 3
const val SLOT_SEARCH_DAYS = 28
const val DEFAULT_MEET_TIME = "18:00"

data class OfferedSlot(
    val at: Long,
    val time: String,
)

fun WeeklyAvailability.timesForJsDay(jsDay: Int): List<String> = when (jsDay.mod(7)) {
    0 -> sun
    1 -> mon
    2 -> tue
    3 -> wed
    4 -> thu
    5 -> fri
    else -> sat
}

fun WeeklyAvailability.monFirstDays(): List<List<String>> =
    listOf(mon, tue, wed, thu, fri, sat, sun)

fun availabilityFromMonFirst(days: List<List<String>>): WeeklyAvailability {
    fun at(i: Int) = days.getOrElse(i) { emptyList() }
    return WeeklyAvailability(
        mon = at(0),
        tue = at(1),
        wed = at(2),
        thu = at(3),
        fri = at(4),
        sat = at(5),
        sun = at(6),
    )
}

fun normalizeAvailability(raw: WeeklyAvailability): WeeklyAvailability {
    fun norm(times: List<String>) =
        times.mapNotNull { parseMinutes(it)?.let(::formatMinutes) }.distinct().sorted()
    return WeeklyAvailability(
        mon = norm(raw.mon),
        tue = norm(raw.tue),
        wed = norm(raw.wed),
        thu = norm(raw.thu),
        fri = norm(raw.fri),
        sat = norm(raw.sat),
        sun = norm(raw.sun),
    )
}

fun validateAvailability(raw: WeeklyAvailability): String? {
    val days = raw.monFirstDays()
    var any = false
    for (times in days) {
        if (times.isEmpty()) continue
        any = true
        if (times.any { parseMinutes(it) == null }) return "SLOT_TIME"
    }
    if (!any) return "SLOTS_REQUIRED"
    return null
}

fun nextOfferedSlots(
    availability: WeeklyAvailability,
    nowMs: Long,
    count: Int = OFFERED_SLOT_COUNT,
    availableUntil: Long? = null,
    utcOffsetMinutes: Int = ICT_OFFSET_MINUTES,
): List<OfferedSlot> {
    val offsetMs = utcOffsetMinutes * 60_000L
    val dayMs = 86_400_000L
    val localNow = nowMs + offsetMs
    val dayIndex = localNow.floorDiv(dayMs)
    val minuteNow = ((localNow - dayIndex * dayMs) / 60_000L).toInt()
    val normalized = normalizeAvailability(availability)
    val out = ArrayList<OfferedSlot>(count)
    for (offset in 0 until SLOT_SEARCH_DAYS) {
        val idx = dayIndex + offset
        val jsDay = jsWeekdayFromEpochDay(idx)
        val times = normalized.timesForJsDay(jsDay)
            .mapNotNull { t -> parseMinutes(t)?.let { it to formatMinutes(it) } }
            .sortedBy { it.first }
        for ((mins, label) in times) {
            if (offset == 0 && mins <= minuteNow) continue
            val at = idx * dayMs + mins * 60_000L - offsetMs
            if (at <= nowMs) continue
            if (availableUntil != null && at > availableUntil) continue
            out += OfferedSlot(at, label)
            if (out.size >= count) return out
        }
    }
    return out
}

fun validateChosenSlot(slotAt: Long?, offered: List<OfferedSlot>): String? {
    if (slotAt == null) return "SLOT_REQUIRED"
    if (offered.none { it.at == slotAt }) return "SLOT_INVALID"
    return null
}

internal fun jsWeekdayFromEpochDay(epochDay: Long): Int = (epochDay + 4).mod(7)
