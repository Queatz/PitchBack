package app.pitchback.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoWheelTest {

    @Test
    fun horizontalScrollUsesDeltaXWhenDominant() {
        assertEquals(40.0, horizontalScrollDelta(40.0, 8.0, false))
        assertNull(horizontalScrollDelta(8.0, 40.0, false))
        assertNull(horizontalScrollDelta(0.0, 12.0, false))
    }

    @Test
    fun shiftTurnsVerticalWheelIntoHorizontalScrub() {
        assertEquals(30.0, horizontalScrollDelta(0.0, 30.0, true))
        assertEquals(-12.0, horizontalScrollDelta(-12.0, -4.0, true))
        assertNull(horizontalScrollDelta(0.2, 0.1, true))
    }

    @Test
    fun wheelDeltaConvertsLineAndPageModes() {
        assertEquals(48.0, wheelDeltaPx(3.0, 1, 200.0))
        assertEquals(200.0, wheelDeltaPx(1.0, 2, 200.0))
        assertEquals(18.0, wheelDeltaPx(18.0, 0, 200.0))
    }

    @Test
    fun applyPhotoWheelTracksPositionAndClampsEnds() {
        val mid = applyPhotoWheel(0, 0.0, 100.0, 2, 200.0)
        assertEquals(0, mid.index)
        assertEquals(-100.0, mid.drag)

        val next = applyPhotoWheel(0, 0.0, 250.0, 2, 200.0)
        assertEquals(1, next.index)
        assertEquals(-50.0, next.drag)

        val start = applyPhotoWheel(0, 0.0, -80.0, 2, 200.0)
        assertEquals(0, start.index)
        assertEquals(0.0, start.drag)

        val end = applyPhotoWheel(2, 0.0, 80.0, 2, 200.0)
        assertEquals(2, end.index)
        assertEquals(0.0, end.drag)
    }

    @Test
    fun settlePhotoWheelSnapsToNearest() {
        assertEquals(1, settlePhotoWheel(0, -100.0, 2, 200.0))
        assertEquals(0, settlePhotoWheel(0, -80.0, 2, 200.0))
        assertEquals(0, settlePhotoWheel(1, 200.0, 2, 200.0))
        assertEquals(0, settlePhotoWheel(0, 0.0, 0, 200.0))
    }
}
