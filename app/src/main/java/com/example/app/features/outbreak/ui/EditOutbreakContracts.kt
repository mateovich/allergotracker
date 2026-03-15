package com.example.app.features.outbreak.ui

data class EditOutbreakUiState(
    val title: String = "Edit Outbreak",
    val outbreakDescriptor: String = "",
    val bodyLocation: String = "",
    val deleteRecordLabel: String = "",
    val notes: String = "",
    val occurredAtEpochMs: Long = 0L,
    val occurredAtInput: String = "",
    val occurredAtLabel: String = "",
    val selectedTimePreset: OutbreakTimePreset = OutbreakTimePreset.NOW,
    val timeOptions: List<OutbreakTimeOptionUiModel> = emptyList(),
    val outbreakDescriptorError: String? = null,
    val bodyLocationError: String? = null,
    val occurredAtError: String? = null,
    val formMessage: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleteConfirmationVisible: Boolean = false,
    val canSave: Boolean = false,
    val canDelete: Boolean = false
)

sealed interface EditOutbreakUiIntent {
    data object Load : EditOutbreakUiIntent
    data class UpdateOutbreakDescriptor(val value: String) : EditOutbreakUiIntent
    data class UpdateBodyLocation(val value: String) : EditOutbreakUiIntent
    data class UpdateNotes(val value: String) : EditOutbreakUiIntent
    data class UpdateOccurredAtInput(val value: String) : EditOutbreakUiIntent
    data class SelectTimePreset(val preset: OutbreakTimePreset) : EditOutbreakUiIntent
    data object ShowDeleteConfirmation : EditOutbreakUiIntent
    data object DismissDeleteConfirmation : EditOutbreakUiIntent
    data object ConfirmDelete : EditOutbreakUiIntent
    data object Save : EditOutbreakUiIntent
}

sealed interface EditOutbreakUiEffect {
    data object NavigateToRecords : EditOutbreakUiEffect
}
