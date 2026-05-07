package io.github.mateovich.allergotracker.features.records.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordDetailContractsTest {

    @Test
    fun delete_hint_describes_confirmation_before_removal() {
        assertEquals(
            "Delete asks for confirmation before removing this record.",
            RecordDetailUiState().deleteHint
        )
    }
}
