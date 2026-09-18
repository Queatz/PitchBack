package app.pitchback.domain

const val PITCH_TEXT_MIN = 80
const val PITCH_BACK_TEXT_MIN = 40
const val PITCH_BACK_TTL_MS = 24L * 60L * 60L * 1000L
const val PASSWORD_MIN = 8
const val HIGHLIGHT_COUNT = 3
const val MAX_PHOTOS = 6

const val STATUS_PENDING = "pending"
const val STATUS_APPROVED = "approved"
const val STATUS_DISMISSED = "dismissed"
const val STATUS_ACCEPTED = "accepted"
const val STATUS_PASSED = "passed"
const val STATUS_CANCELLED = "cancelled"
const val STATUS_EXPIRED = "expired"
const val STATUS_ACTIVE = "active"
const val STATUS_ENDED = "ended"
const val STATUS_REVIEWED = "reviewed"

fun normalizePhone(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val digits = trimmed.filter { it.isDigit() }
    if (digits.length < 8 || digits.length > 15) return null
    return when {
        trimmed.startsWith("+") -> "+$digits"
        digits.startsWith("84") && digits.length >= 10 -> "+$digits"
        digits.startsWith("0") && digits.length >= 9 -> "+84${digits.drop(1)}"
        else -> "+$digits"
    }
}

fun phonesMatch(a: String, b: String): Boolean {
    val left = normalizePhone(a) ?: return false
    val right = normalizePhone(b) ?: return false
    return left == right
}

fun validatePassword(password: String): String? {
    if (password.length < PASSWORD_MIN) return "PASSWORD_SHORT"
    return null
}

fun validateName(name: String): String? {
    val t = name.trim()
    if (t.length < 2) return "NAME_SHORT"
    if (t.length > 40) return "NAME_LONG"
    return null
}

fun validatePitchText(text: String): String? {
    if (text.trim().length < PITCH_TEXT_MIN) return "PITCH_TEXT_SHORT"
    return null
}

fun validatePitchBackText(text: String): String? {
    if (text.trim().length < PITCH_BACK_TEXT_MIN) return "PITCH_BACK_TEXT_SHORT"
    return null
}

fun validatePhotos(photoUrls: List<String>): String? {
    if (photoUrls.isEmpty()) return "PHOTOS_REQUIRED"
    if (photoUrls.size > MAX_PHOTOS) return "PHOTOS_TOO_MANY"
    return null
}

fun validateRemainingOnCreate(remainingPitchBacks: Int): String? {
    if (remainingPitchBacks < 1 || remainingPitchBacks > 99) return "REMAINING"
    return null
}

fun validateHighlights(highlights: List<String>): String? {
    if (highlights.size != HIGHLIGHT_COUNT) return "HIGHLIGHTS_COUNT"
    if (highlights.any { it.trim().isEmpty() }) return "HIGHLIGHTS_EMPTY"
    return null
}

fun isAdminOnCreate(existingUserCount: Int): Boolean = existingUserCount == 0

fun pitchIsPublic(
    status: String,
    remainingPitchBacks: Int,
    availableUntil: Long?,
    authorDisabled: Boolean,
    now: Long,
): Boolean =
    status == STATUS_APPROVED &&
        remainingPitchBacks > 0 &&
        (availableUntil == null || availableUntil > now) &&
        !authorDisabled

fun pitchBackExpiresAt(createdAt: Long): Long = createdAt + PITCH_BACK_TTL_MS

fun isPitchBackExpired(status: String, expiresAt: Long, now: Long): Boolean =
    status == STATUS_PENDING && now >= expiresAt

fun canAcceptPitchBack(
    pitchBackStatus: String,
    expiresAt: Long,
    remainingPitchBacks: Int,
    now: Long,
): Boolean =
    pitchBackStatus == STATUS_PENDING &&
        now < expiresAt &&
        remainingPitchBacks > 0

fun remainingAfterAccept(current: Int): Int = (current - 1).coerceAtLeast(0)

fun canPitchBackOnOwnPitch(authorId: String, userId: String): Boolean = authorId != userId

fun canDisableAccount(actorId: String, targetId: String): Boolean = actorId != targetId

fun meetEndedBy(endedUserIds: List<String>, legacyEndedBy: String?): List<String> =
    (endedUserIds + listOfNotNull(legacyEndedBy?.takeIf { it.isNotBlank() })).distinct()

fun meetActiveForUser(status: String, endedUserIds: List<String>, userId: String): Boolean =
    status == STATUS_ACTIVE && userId !in endedUserIds

fun canEndMeet(status: String, endedUserIds: List<String>, userId: String): Boolean =
    meetActiveForUser(status, endedUserIds, userId)

fun canReportNoShow(alreadyReported: Boolean, meetStatus: String): Boolean =
    !alreadyReported && meetStatus != STATUS_CANCELLED

fun endMeetForUser(
    endedUserIds: List<String>,
    userId: String,
    ownerId: String,
    guestId: String,
): Pair<List<String>, String> {
    val next = (endedUserIds + userId).distinct()
    val status = if (ownerId in next && guestId in next) STATUS_ENDED else STATUS_ACTIVE
    return next to status
}
