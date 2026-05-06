package io.github.mateovich.allergotracker.features.exposure.ui

import io.github.mateovich.allergotracker.features.eventcatalog.ui.ReusableEventCatalogOptionUiModel
import java.time.LocalDate
import java.time.LocalTime

data class EditExposureUiState(
    val title: String = "Edit Exposure",
    val exposureType: String = "",
    val deleteRecordLabel: String = "",
    val place: String = "",
    val durationMinutes: String = "",
    val notes: String = "",
    val selectedIntensityLevel: Int = 3,
    val occurredAtEpochMs: Long = 0L,
    val occurredAtInput: String = "",
    val occurredAtLabel: String = "",
    val selectedOccurredOn: LocalDate = LocalDate.now(),
    val selectedOccurredTime: LocalTime = LocalTime.MIDNIGHT,
    val selectedTimePreset: ExposureTimePreset? = null,
    val timeOptions: List<ExposureTimeOptionUiModel> = emptyList(),
    val intensityOptions: List<ExposureIntensityOptionUiModel> = emptyList(),
    val selectedExposureTypeCatalogId: Long? = null,
    val selectedExposureTypeSummary: String? = null,
    val availableExposureTypes: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val filteredExposureTypes: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val showCreateExposureTypeOption: Boolean = false,
    val exposureTypeError: String? = null,
    val durationMinutesError: String? = null,
    val intensityLevelError: String? = null,
    val occurredAtError: String? = null,
    val formMessage: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleteConfirmationVisible: Boolean = false,
    val canSave: Boolean = false,
    val canDelete: Boolean = false
)

sealed interface EditExposureUiIntent {
    data object Load : EditExposureUiIntent
    data class UpdateExposureType(val value: String) : EditExposureUiIntent
    data class SelectExposureCatalogOption(val id: Long, val name: String) : EditExposureUiIntent
    data object UseTypedExposureType : EditExposureUiIntent
    data object ClearExposureCatalogSelection : EditExposureUiIntent
    data object ClearRetiredExposureCatalogSelection : EditExposureUiIntent
    data object EditSelectedExposureCatalog : EditExposureUiIntent
    data class UpdatePlace(val value: String) : EditExposureUiIntent
    data class UpdateDurationMinutes(val value: String) : EditExposureUiIntent
    data class UpdateNotes(val value: String) : EditExposureUiIntent
    data class SelectIntensityLevel(val level: Int) : EditExposureUiIntent
    data class SelectOccurredOn(val value: LocalDate) : EditExposureUiIntent
    data class SelectOccurredTime(val value: LocalTime) : EditExposureUiIntent
    data class SelectTimePreset(val preset: ExposureTimePreset) : EditExposureUiIntent
    data object ShowDeleteConfirmation : EditExposureUiIntent
    data object DismissDeleteConfirmation : EditExposureUiIntent
    data object ConfirmDelete : EditExposureUiIntent
    data object Save : EditExposureUiIntent
}

sealed interface EditExposureUiEffect {
    data object NavigateToRecords : EditExposureUiEffect
    data class NavigateToEditEventCatalog(val kindStorageValue: String, val id: Long) : EditExposureUiEffect
}
