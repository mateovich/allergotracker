package com.example.app.features.exposure.ui

data class EditExposureUiState(
    val title: String = "Edit Exposure",
    val exposureType: String = "",
    val deleteRecordLabel: String = "",
    val notes: String = "",
    val occurredAtEpochMs: Long = 0L,
    val occurredAtInput: String = "",
    val occurredAtLabel: String = "",
    val selectedTimePreset: ExposureTimePreset = ExposureTimePreset.NOW,
    val timeOptions: List<ExposureTimeOptionUiModel> = emptyList(),
    val exposureTypeError: String? = null,
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
    data class UpdateNotes(val value: String) : EditExposureUiIntent
    data class UpdateOccurredAtInput(val value: String) : EditExposureUiIntent
    data class SelectTimePreset(val preset: ExposureTimePreset) : EditExposureUiIntent
    data object ShowDeleteConfirmation : EditExposureUiIntent
    data object DismissDeleteConfirmation : EditExposureUiIntent
    data object ConfirmDelete : EditExposureUiIntent
    data object Save : EditExposureUiIntent
}

sealed interface EditExposureUiEffect {
    data object NavigateToRecords : EditExposureUiEffect
}
