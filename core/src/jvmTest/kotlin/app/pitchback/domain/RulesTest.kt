package app.pitchback.domain

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
        assertNull(normalizePhone("123"))
        assertNull(normalizePhone(""))
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
}
