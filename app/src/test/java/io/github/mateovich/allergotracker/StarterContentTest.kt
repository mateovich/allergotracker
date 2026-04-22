package io.github.mateovich.allergotracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterContentTest {

    @Test
    fun title_matches_baseline_text() {
        assertEquals("Android Native Starter", StarterContent.TITLE)
    }

    @Test
    fun title_is_not_blank() {
        assertTrue(StarterContent.TITLE.isNotBlank())
    }
}
