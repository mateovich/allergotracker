package io.github.mateovich.allergotracker.features.compounds.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EditCompoundContractsTest {

    @Test
    fun helper_message_describes_editing_without_locked_copy() {
        assertEquals(
            "Update components and proportions while keeping the total at 100% before saving.",
            EditCompoundUiState().helperMessage
        )
    }
}
