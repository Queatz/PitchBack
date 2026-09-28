package app.pitchback.domain

import app.pitchback.api.DayHours
import app.pitchback.api.OpeningHours
import app.pitchback.api.WeeklyAvailability
import app.pitchback.i18n.ApiMessages
import java.time.DayOfWeek
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {

    @Test
    fun firstUserIsAdmin() {
        assertTrue(isAdminOnCreate(0))
        assertFalse(isAdminOnCreate(1))
        assertFalse(isAdminOnCreate(12))
    }

    @Test
    fun phoneNormalization() {
        assertEquals("+84912345678", normalizePhone("0912345678"))
        assertEquals("+84912345678", normalizePhone("+84 912 345 678"))
        assertEquals("+84912345678", normalizePhone("84912345678"))
        assertEquals("+84708739214", normalizePhone("0708739214"))
        assertEquals("+84708739214", normalizePhone("708739214"))
        assertEquals("+84708739214", normalizePhone("+84 070 873 9214"))
        assertEquals("+84708739214", normalizePhone("0084 708 739 214"))
        assertEquals("+84708739214", normalizePhone("084708739214"))
        assertNull(normalizePhone("123"))
        assertNull(normalizePhone(""))
        assertTrue(phonesMatch("0912345678", "+84 912 345 678"))
        assertTrue(phonesMatch("84912345678", "0912 345 678"))
        assertTrue(phonesMatch("0708739214", "708739214"))
        assertTrue(phonesMatch("+708739214", "0708739214"))
        assertFalse(phonesMatch("0912345678", "0987654321"))
    }

    @Test
    fun pitchTextMinimum() {
        assertEquals("PITCH_TEXT_SHORT", validatePitchText("too short"))
        assertNull(validatePitchText("A".repeat(80)))
        assertNull(validatePitchText("A".repeat(80) + " extra"))
    }

    @Test
    fun pitchBackTextMinimum() {
        assertEquals("PITCH_BACK_TEXT_SHORT", validatePitchBackText("hi"))
        assertNull(validatePitchBackText("A".repeat(40)))
    }

    @Test
    fun photosRequired() {
        assertEquals("PHOTOS_REQUIRED", validatePhotos(emptyList()))
        assertNull(validatePhotos(listOf("/uploads/a.jpg")))
        assertEquals("PHOTOS_TOO_MANY", validatePhotos(List(7) { "/x$it.jpg" }))
    }

    @Test
    fun remainingPitchBacksOnCreateMinimumIsOne() {
        assertEquals("REMAINING", validateRemainingOnCreate(0))
        assertEquals("REMAINING", validateRemainingOnCreate(100))
        assertNull(validateRemainingOnCreate(1))
        assertNull(validateRemainingOnCreate(99))
    }

    @Test
    fun publicPitchRules() {
        val now = 1_000_000L
        assertTrue(pitchIsPublic(STATUS_APPROVED, 2, null, false, now))
        assertFalse(pitchIsPublic(STATUS_PENDING, 2, null, false, now))
        assertFalse(pitchIsPublic(STATUS_APPROVED, 0, null, false, now))
        assertFalse(pitchIsPublic(STATUS_APPROVED, 2, now - 1, false, now))
        assertTrue(pitchIsPublic(STATUS_APPROVED, 2, now + 1, false, now))
        assertFalse(pitchIsPublic(STATUS_APPROVED, 2, null, true, now))
    }

    @Test
    fun pitchBackSentNoticeLastsADayAndIgnoresDismiss() {
        val created = 1_000L
        val now = created + 12L * 60L * 60L * 1000L
        val pending = pitchBackSentNotice(created, STATUS_PENDING, now)
        assertEquals(created, pending?.sentAt)
        assertFalse(pending?.accepted == true)
        val passed = pitchBackSentNotice(created, STATUS_PASSED, now)
        assertEquals(created, passed?.sentAt)
        assertFalse(passed?.accepted == true)
        val accepted = pitchBackSentNotice(created, STATUS_ACCEPTED, now)
        assertEquals(created, accepted?.sentAt)
        assertTrue(accepted?.accepted == true)
        assertNull(pitchBackSentNotice(created, STATUS_CANCELLED, now))
        assertNull(pitchBackSentNotice(created, STATUS_PENDING, created + PITCH_BACK_TTL_MS))
        assertNull(pitchBackSentNotice(created, STATUS_ACCEPTED, created + PITCH_BACK_TTL_MS))
        assertEquals(created, pitchBackSentNotice(created, STATUS_PENDING, created + PITCH_BACK_TTL_MS - 1)?.sentAt)
    }

    @Test
    fun pitchBackExpiryAndAccept() {
        val created = 1_000L
        val expires = pitchBackExpiresAt(created)
        assertEquals(created + PITCH_BACK_TTL_MS, expires)
        assertFalse(isPitchBackExpired(STATUS_PENDING, expires, expires - 1))
        assertTrue(isPitchBackExpired(STATUS_PENDING, expires, expires))
        assertFalse(isPitchBackExpired(STATUS_ACCEPTED, expires, expires + 1))
        assertTrue(canAcceptPitchBack(STATUS_PENDING, expires, 2, expires - 1))
        assertFalse(canAcceptPitchBack(STATUS_PENDING, expires, 2, expires))
        assertFalse(canAcceptPitchBack(STATUS_PENDING, expires, 0, expires - 1))
        assertFalse(canAcceptPitchBack(STATUS_PASSED, expires, 2, expires - 1))
        assertEquals(2, remainingAfterAccept(3))
        assertEquals(0, remainingAfterAccept(0))
    }

    @Test
    fun cannotPitchBackOwnPitch() {
        assertFalse(canPitchBackOnOwnPitch("u1", "u1"))
        assertTrue(canPitchBackOnOwnPitch("u1", "u2"))
    }

    @Test
    fun adminCannotDisableSelf() {
        assertFalse(canDisableAccount("a1", "a1"))
        assertTrue(canDisableAccount("a1", "u2"))
    }

    @Test
    fun bothUsersEndMeetSeparately() {
        val first = endMeetForUser(emptyList(), "owner", "owner", "guest")
        assertEquals(listOf("owner"), first.first)
        assertEquals(STATUS_ACTIVE, first.second)
        assertTrue(meetActiveForUser(first.second, first.first, "guest"))
        assertFalse(meetActiveForUser(first.second, first.first, "owner"))
        assertFalse(canEndMeet(first.second, first.first, "owner"))
        assertTrue(canEndMeet(first.second, first.first, "guest"))

        val second = endMeetForUser(first.first, "guest", "owner", "guest")
        assertEquals(listOf("owner", "guest"), second.first)
        assertEquals(STATUS_ENDED, second.second)
        assertFalse(meetActiveForUser(second.second, second.first, "guest"))
    }

    @Test
    fun otherFinalMessageIsBlankSafeAndSwappedForViewer() {
        assertEquals("thanks", otherFinalMessage("owner", "owner", "guest", "mine", "thanks"))
        assertEquals("mine", otherFinalMessage("guest", "owner", "guest", "mine", "thanks"))
        assertNull(otherFinalMessage("owner", "owner", "guest", "mine", "  "))
        assertNull(otherFinalMessage("guest", "owner", "guest", "", "thanks"))
        assertNull(otherFinalMessage("other", "owner", "guest", "mine", "thanks"))
    }

    @Test
    fun inboxHidesBlankOrDismissedFinalMessages() {
        assertTrue(shouldShowInboxFinalMessage("bye", "guest", emptyList()))
        assertFalse(shouldShowInboxFinalMessage("", "guest", emptyList()))
        assertFalse(shouldShowInboxFinalMessage("   ", "guest", emptyList()))
        assertFalse(shouldShowInboxFinalMessage(null, "guest", emptyList()))
        assertFalse(shouldShowInboxFinalMessage("bye", "guest", listOf("guest")))
        assertTrue(shouldShowInboxFinalMessage("bye", "guest", listOf("owner")))
    }

    @Test
    fun noShowCannotBeReportedTwiceOrOnCancelled() {
        assertTrue(canReportNoShow(false, STATUS_ACTIVE))
        assertTrue(canReportNoShow(false, STATUS_ENDED))
        assertFalse(canReportNoShow(true, STATUS_ACTIVE))
        assertFalse(canReportNoShow(false, STATUS_CANCELLED))
    }

    @Test
    fun endAndNoShowWaitUntilMeetTime() {
        val slot = 1_000L
        assertFalse(meetTimeHasStarted(slot, 999))
        assertTrue(meetTimeHasStarted(slot, 1_000))
        assertTrue(meetTimeHasStarted(slot, 1_001))
        assertTrue(meetTimeHasStarted(null, 0))
    }

    @Test
    fun hoursOpenNowAndCondensed() {
        val hours = OpeningHours()
        assertTrue(isOpenAt(hours, 1, 12 * 60))
        assertFalse(isOpenAt(hours, 1, 22 * 60))
        val status = openStatus(hours, 1, 12 * 60)
        assertTrue(status.open)
        assertEquals(21 * 60, status.nextMinutes)
        val groups = condensedHours(hours)
        assertEquals(1, groups.size)
        assertEquals(0, groups[0].startDay)
        assertEquals(6, groups[0].endDay)

        val mixed = hours.copy(sat = DayHours(closed = true), sun = DayHours(closed = true))
        val mixedGroups = condensedHours(mixed)
        assertEquals(2, mixedGroups.size)
        assertFalse(mixedGroups[0].closed)
        assertTrue(mixedGroups[1].closed)
        assertEquals(5, mixedGroups[1].startDay)
        assertEquals(6, mixedGroups[1].endDay)
    }

    @Test
    fun apiMessagesFollowAcceptLanguage() {
        assertEquals("vi", ApiMessages.langFromAccept("vi-VN,vi;q=0.9"))
        assertEquals("en", ApiMessages.langFromAccept("en-US,en;q=0.8"))
        assertEquals("Sign in required", ApiMessages.t("en", "AUTH_REQUIRED"))
        assertEquals("Cần đăng nhập", ApiMessages.t("vi", "AUTH_REQUIRED"))
        assertEquals("That phone is already registered", ApiMessages.t("en", "PHONE_TAKEN"))
        assertEquals("Số điện thoại này đã được đăng ký", ApiMessages.t("vi", "PHONE_TAKEN"))
    }

    @Test
    fun availabilityMustHaveAValidTime() {
        assertEquals("SLOTS_REQUIRED", validateAvailability(WeeklyAvailability()))
        assertEquals("SLOT_TIME", validateAvailability(WeeklyAvailability(fri = listOf("nope"))))
        assertNull(validateAvailability(WeeklyAvailability(fri = listOf("18:00"))))
        val normalized = normalizeAvailability(WeeklyAvailability(fri = listOf("18:00", "18:00", "9:05")))
        assertEquals(listOf("09:05", "18:00"), normalized.fri)
    }

    @Test
    fun nextSlotsSkipPastTimesAndStayWithinUntil() {
        val now = OffsetDateTime.of(2026, 9, 18, 21, 8, 0, 0, ZoneOffset.ofHours(7))
            .toInstant().toEpochMilli()
        val avail = WeeklyAvailability(fri = listOf("18:00", "22:00"), sat = listOf("10:00"))
        val slots = nextOfferedSlots(avail, now)
        assertEquals(3, slots.size)
        assertEquals("22:00", slots[0].time)
        assertEquals("10:00", slots[1].time)
        assertEquals("18:00", slots[2].time)

        val first = Instant.ofEpochMilli(slots[0].at).atOffset(ZoneOffset.ofHours(7))
        assertEquals(DayOfWeek.FRIDAY, first.dayOfWeek)
        assertEquals(22, first.hour)
        val second = Instant.ofEpochMilli(slots[1].at).atOffset(ZoneOffset.ofHours(7))
        assertEquals(DayOfWeek.SATURDAY, second.dayOfWeek)
        assertEquals(10, second.hour)

        val untilSoon = now + 3 * 60 * 60 * 1000L
        val limited = nextOfferedSlots(avail, now, availableUntil = untilSoon)
        assertEquals(1, limited.size)
        assertEquals("22:00", limited[0].time)

        assertEquals("SLOT_REQUIRED", validateChosenSlot(null, slots))
        assertEquals("SLOT_INVALID", validateChosenSlot(now + 1, slots))
        assertNull(validateChosenSlot(slots[0].at, slots))
    }

    @Test
    fun nextSlotsSkipTimesTakenByPitchBacksOrMeets() {
        val now = OffsetDateTime.of(2026, 9, 18, 21, 8, 0, 0, ZoneOffset.ofHours(7))
            .toInstant().toEpochMilli()
        val avail = WeeklyAvailability(fri = listOf("18:00", "22:00"), sat = listOf("10:00"))
        val open = nextOfferedSlots(avail, now)
        assertEquals(listOf("22:00", "10:00", "18:00"), open.map { it.time })

        val taken = setOf(open[0].at, open[1].at)
        val slots = nextOfferedSlots(avail, now, takenAts = taken)
        assertEquals(3, slots.size)
        assertEquals("18:00", slots[0].time)
        assertEquals("22:00", slots[1].time)
        assertEquals("10:00", slots[2].time)
        assertTrue(slots.none { it.at in taken })

        val nextFriday = Instant.ofEpochMilli(slots[1].at).atOffset(ZoneOffset.ofHours(7))
        assertEquals(DayOfWeek.FRIDAY, nextFriday.dayOfWeek)
        assertEquals(22, nextFriday.hour)
        val weekAhead = nextFriday.toInstant().toEpochMilli() - open[0].at
        assertEquals(7 * 86_400_000L, weekAhead)
    }
}
