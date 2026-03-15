package com.example.app.features.records.ui

import com.example.app.features.records.domain.RecordSummary
import com.example.app.navigation.AppRoutes

data class RecordSummaryUiModel(
    val id: String,
    val typeLabel: String,
    val title: String,
    val detailLabel: String,
    val occurredAtLabel: String,
    val editRoute: String
) {
    val isEditable: Boolean = editRoute.isNotBlank()
}

data class RecordsUiState(
    val title: String = "Records",
    val addActionLabel: String = "Add record",
    val quickPickerLabel: String = "Choose event type",
    val hasRecords: Boolean = false,
    val isQuickPickerVisible: Boolean = false,
    val records: List<RecordSummaryUiModel> = emptyList(),
    val emptyStateTitle: String = "No records yet",
    val emptyStateMessage: String = "Use + to create your first record."
)

enum class EventType(val label: String, val route: String) {
    INGESTION(label = "Ingestion", route = AppRoutes.CREATE_INGESTION),
    EXPOSURE(label = "Exposure", route = AppRoutes.CREATE_EXPOSURE),
    OUTBREAK(label = "Outbreak", route = AppRoutes.CREATE_OUTBREAK)
}

sealed interface RecordsUiIntent {
    data object Load : RecordsUiIntent
    data object OpenAddEntryPoint : RecordsUiIntent
    data object DismissAddEntryPoint : RecordsUiIntent
    data class SelectEventType(val eventType: EventType) : RecordsUiIntent
    data class OpenRecord(val recordId: String) : RecordsUiIntent
}

sealed interface RecordsUiEffect {
    data class NavigateToCreateForm(val route: String) : RecordsUiEffect
    data class NavigateToEditForm(val route: String) : RecordsUiEffect
}
