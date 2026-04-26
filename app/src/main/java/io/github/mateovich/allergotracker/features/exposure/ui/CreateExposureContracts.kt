package io.github.mateovich.allergotracker.features.exposure.ui

import io.github.mateovich.allergotracker.features.eventcatalog.ui.ReusableEventCatalogOptionUiModel
import java.time.LocalDate
import java.time.LocalTime

data class ExposureTimeOptionUiModel(
    val key: String,
    val label: String,
    val offsetMillis: Long
)

data class ExposureIntensityOptionUiModel(
    val level: Int,
    val label: String
)

enum class ExposureTimePreset(
    val label: String,
    val offsetMillis: Long
) {
    NOW(label = "Now", offsetMillis = 0L),
    THIRTY_MINUTES_AGO(label = "30m ago", offsetMillis = 30L * 60L * 1000L),
    ONE_HOUR_AGO(label = "1h ago", offsetMillis = 60L * 60L * 1000L)
}

data class CreateExposureUiState(
    val title: String = "Create Exposure",
    val exposureType: String = "",
    val notes: String = "",
    val selectedIntensityLevel: Int = 3,
    val occurredAtEpochMs: Long = 0L,
    val occurredAtInput: String = "",
    val occurredAtLabel: String = "",
    val selectedOccurredOn: LocalDate = LocalDate.now(),
    val selectedOccurredTime: LocalTime = LocalTime.MIDNIGHT,
    val selectedTimePreset: ExposureTimePreset? = ExposureTimePreset.NOW,
    val timeOptions: List<ExposureTimeOptionUiModel> = emptyList(),
    val intensityOptions: List<ExposureIntensityOptionUiModel> = emptyList(),
    val selectedExposureTypeCatalogId: Long? = null,
    val selectedExposureTypeSummary: String? = null,
    val availableExposureTypes: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val filteredExposureTypes: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val showCreateExposureTypeOption: Boolean = false,
    val exposureTypeError: String? = null,
    val intensityLevelError: String? = null,
    val occurredAtError: String? = null,
    val formMessage: String = "",
    val isSaving: Boolean = false,
    val canSave: Boolean = false
)

sealed interface CreateExposureUiIntent {
    data class UpdateExposureType(val value: String) : CreateExposureUiIntent
    data class SelectExposureCatalogOption(val id: Long, val name: String) : CreateExposureUiIntent
    data object UseTypedExposureType : CreateExposureUiIntent
    data object ClearExposureCatalogSelection : CreateExposureUiIntent
    data object ClearRetiredExposureCatalogSelection : CreateExposureUiIntent
    data object EditSelectedExposureCatalog : CreateExposureUiIntent
    data class UpdateNotes(val value: String) : CreateExposureUiIntent
    data class SelectIntensityLevel(val level: Int) : CreateExposureUiIntent
    data class SelectOccurredOn(val value: LocalDate) : CreateExposureUiIntent
    data class SelectOccurredTime(val value: LocalTime) : CreateExposureUiIntent
    data class SelectTimePreset(val preset: ExposureTimePreset) : CreateExposureUiIntent
    data object Save : CreateExposureUiIntent
}

sealed interface CreateExposureUiEffect {
    data object NavigateToRecords : CreateExposureUiEffect
    data class NavigateToEditEventCatalog(val kindStorageValue: String, val id: Long) : CreateExposureUiEffect
}
