package com.example.app.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalOnlyNoticeDefaultsTest {

    @Test
    fun offline_notice_copy_stays_calm_and_device_local() {
        assertEquals(
            "Works offline. Saved only on this device.",
            LocalOnlyNoticeDefaults.message
        )
    }
}
