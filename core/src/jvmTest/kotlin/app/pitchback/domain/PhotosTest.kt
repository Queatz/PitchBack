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
}
