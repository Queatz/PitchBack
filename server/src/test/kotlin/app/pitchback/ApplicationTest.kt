package app.pitchback

import app.pitchback.domain.isAdminOnCreate
import kotlin.test.Test
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun firstAccountBecomesAdmin() {
        assertTrue(isAdminOnCreate(0))
    }
}
