package app.pitchback.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareCardTest {

    @Test
    fun photoRowIsEmptyWhenThereAreNoPhotos() {
        assertEquals(emptyList(), sharePhotoRowSlots(0, 720.0, 8.0))
    }

    @Test
    fun singlePhotoFillsTheTopHalf() {
        assertEquals(
            listOf(ShareRect(0.0, 0.0, 720.0, 360.0)),
            sharePhotoRowSlots(1, 720.0, 8.0),
        )
    }

    @Test
    fun photosSitInASpacedHorizontalRow() {
        val slots = sharePhotoRowSlots(2, 720.0, 8.0)
        assertEquals(2, slots.size)
        assertEquals(ShareRect(0.0, 0.0, 356.0, 360.0), slots[0])
        assertEquals(ShareRect(364.0, 0.0, 356.0, 360.0), slots[1])
    }

    @Test
    fun avatarSitsInTheCanvasCenter() {
        val (cx, cy, r) = shareAvatarCircle(720.0, 0.18)
        assertEquals(360.0, cx)
        assertEquals(360.0, cy)
        assertEquals(129.6, r, 0.0001)
    }

    @Test
    fun shareUrlUsesThePitchPath() {
        assertEquals("https://ranh.app/pitch/abc", pitchShareUrl("https://ranh.app/", "abc"))
        assertEquals("https://ranh.app/pitch/abc", pitchShareUrl("https://ranh.app", "abc"))
    }

    @Test
    fun wrapKeepsWordsThatFit() {
        val lines = wrapShareLines("hello there friend", maxWidth = 12.0, maxLines = 3) { it.length.toDouble() }
        assertEquals(listOf("hello there", "friend"), lines)
    }

    @Test
    fun wrapEllipsizesWhenTextExceedsMaxLines() {
        val lines = wrapShareLines("one two three four five", maxWidth = 8.0, maxLines = 2) { it.length.toDouble() }
        assertEquals(2, lines.size)
        assertTrue(lines.last().endsWith("…"))
    }

    @Test
    fun wrapBreaksLongWords() {
        val lines = wrapShareLines("abcdefghij", maxWidth = 4.0, maxLines = 4) { it.length.toDouble() }
        assertEquals(listOf("abcd", "efgh", "ij"), lines)
    }
}
