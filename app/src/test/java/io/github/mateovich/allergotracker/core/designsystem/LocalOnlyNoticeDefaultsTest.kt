package io.github.mateovich.allergotracker.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalOnlyNoticeDefaultsTest {

    @Test
    fun offline_notice_copy_stays_calm_and_device_local() {
        assertEquals(
            "Offline-ready. Export to share your data.",
            LocalOnlyNoticeDefaults.message
        )
    }
}
