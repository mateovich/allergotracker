package com.example.app.features.home.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeContractsTest {

    @Test
    fun ui_state_is_immutable_data_contract() {
        val initial = HomeUiState()
        val updated = initial.copy(title = "t", isLoading = true)

        assertEquals("", initial.title)
        assertFalse(initial.isLoading)
        assertEquals("t", updated.title)
    }
}
