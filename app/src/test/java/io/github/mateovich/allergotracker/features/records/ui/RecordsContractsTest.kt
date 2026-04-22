package io.github.mateovich.allergotracker.features.records.ui

import io.github.mateovich.allergotracker.features.records.domain.RecordEventType
import io.github.mateovich.allergotracker.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsContractsTest {

    @Test
    fun default_state_matches_records_launch_contract() {
        val state = RecordsUiState()

        assertEquals("Records", state.title)
        assertEquals("Add record", state.addActionLabel)
        assertEquals("Choose event type", state.quickPickerLabel)
        assertFalse(state.hasRecords)
        assertFalse(state.isQuickPickerVisible)
        assertEquals("No records yet", state.emptyStateTitle)
        assertEquals("Use + to create your first record.", state.emptyStateMessage)
    }

    @Test
    fun event_types_expose_exact_labels_and_routes() {
        assertEquals("Ingestion", EventType.INGESTION.label)
        assertEquals(AppRoutes.CREATE_INGESTION, EventType.INGESTION.route)
        assertEquals("Exposure", EventType.EXPOSURE.label)
        assertEquals(AppRoutes.CREATE_EXPOSURE, EventType.EXPOSURE.route)
        assertEquals("Outbreak", EventType.OUTBREAK.label)
        assertEquals(AppRoutes.CREATE_OUTBREAK, EventType.OUTBREAK.route)
    }

    @Test
    fun default_record_summary_ui_model_is_actionable_for_detail_drill_in() {
        val model = RecordSummaryUiModel(
            id = "ingestion-9",
            typeLabel = "Ingestion",
            title = "Tea",
            detailLabel = "2 Light snack",
            occurredAtLabel = "08:15",
            detailRoute = AppRoutes.recordDetail(RecordEventType.INGESTION, 9L)
        )

        assertTrue(model.isEditable)
        assertEquals("record-detail/ingestion/9", model.detailRoute)
    }
}
