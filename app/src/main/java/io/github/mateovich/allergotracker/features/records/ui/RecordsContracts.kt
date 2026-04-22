package io.github.mateovich.allergotracker.features.records.ui

import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import io.github.mateovich.allergotracker.features.records.domain.RecordEventType
import io.github.mateovich.allergotracker.features.records.domain.RecordsFilter
import io.github.mateovich.allergotracker.navigation.AppRoutes
import java.time.LocalDate

data class RecordSummaryUiModel(
    val id: String,
    val typeLabel: String,
    val title: String,
    val detailLabel: String,
    val occurredAtLabel: String,
    val detailRoute: String
) {
    val isEditable: Boolean = detailRoute.isNotBlank()
}

data class RecordsFilterDraftUiState(
    val startDateInput: String = "",
    val endDateInput: String = "",
    val selectedEventTypes: Set<RecordEventType> = emptySet(),
    val selectedIntensityLevels: Set<IngestionQuantityOption> = emptySet(),
    val errorMessage: String? = null
)

data class ActiveRecordsFilterUiModel(
    val summaryLabels: List<String> = emptyList(),
    val resetLabel: String = "Clear filters"
) {
    val hasActiveFilters: Boolean = summaryLabels.isNotEmpty()
}

data class RecordsUiState(
    val title: String = "Records",
    val addActionLabel: String = "Add record",
    val quickPickerLabel: String = "Choose event type",
    val filterActionLabel: String = "Filter records",
    val hasRecords: Boolean = false,
    val hasStoredRecords: Boolean = false,
    val isQuickPickerVisible: Boolean = false,
    val isFilterDialogVisible: Boolean = false,
    val activeFilters: ActiveRecordsFilterUiModel = ActiveRecordsFilterUiModel(),
    val filterDraft: RecordsFilterDraftUiState = RecordsFilterDraftUiState(),
    val records: List<RecordSummaryUiModel> = emptyList(),
    val emptyStateTitle: String = "No records yet",
    val emptyStateMessage: String = "Use + to create your first record.",
    val emptyStateActionLabel: String? = null,
    val intensityHint: String = "Intensity applies to ingestion, exposure, and outbreak records."
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
    data object OpenFilterDialog : RecordsUiIntent
    data object DismissFilterDialog : RecordsUiIntent
    data object ApplyFilters : RecordsUiIntent
    data object ClearFilters : RecordsUiIntent
    data class UpdateFilterStartDate(val value: String) : RecordsUiIntent
    data class UpdateFilterEndDate(val value: String) : RecordsUiIntent
    data class ToggleFilterEventType(val eventType: RecordEventType) : RecordsUiIntent
    data class ToggleFilterIntensity(val option: IngestionQuantityOption) : RecordsUiIntent
    data class SelectEventType(val eventType: EventType) : RecordsUiIntent
    data class OpenRecord(val recordId: String) : RecordsUiIntent
}

sealed interface RecordsUiEffect {
    data class NavigateToCreateForm(val route: String) : RecordsUiEffect
    data class NavigateToRecordDetail(val route: String) : RecordsUiEffect
}

internal fun RecordsFilter.toDraftUiState(): RecordsFilterDraftUiState {
    return RecordsFilterDraftUiState(
        startDateInput = startDate?.toString().orEmpty(),
        endDateInput = endDate?.toString().orEmpty(),
        selectedEventTypes = eventTypes,
        selectedIntensityLevels = intensityLevels
    )
}

internal fun RecordsFilter.toActiveUiModel(): ActiveRecordsFilterUiModel {
    val labels = buildList {
        when {
            startDate != null && endDate != null -> add("$startDate to $endDate")
            startDate != null -> add("From $startDate")
            endDate != null -> add("Until $endDate")
        }
        if (eventTypes.isNotEmpty()) {
            add(eventTypes.sortedBy { it.name }.joinToString(prefix = "Types: ") { it.label })
        }
        if (intensityLevels.isNotEmpty()) {
            add(intensityLevels.sortedBy { it.level }.joinToString(prefix = "Intensity: ") { it.level.toString() })
        }
    }
    return ActiveRecordsFilterUiModel(summaryLabels = labels)
}

internal val RecordEventType.label: String
    get() = when (this) {
        RecordEventType.INGESTION -> "Ingestion"
        RecordEventType.EXPOSURE -> "Exposure"
        RecordEventType.OUTBREAK -> "Outbreak"
    }

internal fun parseFilterDate(value: String): LocalDate? {
    return value.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
}
