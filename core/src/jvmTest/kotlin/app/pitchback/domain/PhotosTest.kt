package app.pitchback.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotosTest {

    @Test
    fun scalesDownWhenWidthExceedsMax() {
        assertEquals(1200 to 800, scaledPhotoSize(2400, 1600, 1200))
    }

    @Test
    fun scalesDownWhenHeightExceedsMax() {
        assertEquals(480 to 1200, scaledPhotoSize(800, 2000, 1200))
    }

    @Test
    fun leavesSmallerPhotosUnchanged() {
        assertEquals(800 to 600, scaledPhotoSize(800, 600, 1200))
        assertEquals(1200 to 1200, scaledPhotoSize(1200, 1200, 1200))
    }

    @Test
    fun nullOrNonPositiveMaxSkipsScaling() {
        assertEquals(4000 to 3000, scaledPhotoSize(4000, 3000, null))
        assertEquals(4000 to 3000, scaledPhotoSize(4000, 3000, 0))
        assertEquals(4000 to 3000, scaledPhotoSize(4000, 3000, -1))
    }

    @Test
    fun ghostStaysUnderPointerWhenGrabOffsetIsApplied() {
        assertEquals(180.0 to 90.0, dragGhostTopLeft(220.0, 130.0, 40.0, 40.0))
    }

    @Test
    fun ghostSubtractsFixedContainingBlockOrigin() {
        assertEquals(50.0 to 40.0, dragGhostTopLeft(200.0, 160.0, 30.0, 40.0, 120.0, 80.0))
    }
}
