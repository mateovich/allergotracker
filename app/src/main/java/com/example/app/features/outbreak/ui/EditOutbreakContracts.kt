package com.example.app.features.outbreak.ui

import com.example.app.features.eventcatalog.ui.ReusableEventCatalogOptionUiModel

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
    val selectedOutbreakDescriptorCatalogId: Long? = null,
    val selectedOutbreakDescriptorSummary: String? = null,
    val availableOutbreakDescriptors: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val filteredOutbreakDescriptors: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val showCreateOutbreakDescriptorOption: Boolean = false,
    val selectedBodyLocationCatalogId: Long? = null,
    val selectedBodyLocationSummary: String? = null,
    val availableBodyLocations: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val filteredBodyLocations: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val showCreateBodyLocationOption: Boolean = false,
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
    data class SelectOutbreakDescriptorCatalogOption(val id: Long, val name: String) : EditOutbreakUiIntent
    data object UseTypedOutbreakDescriptor : EditOutbreakUiIntent
    data object ClearOutbreakDescriptorCatalogSelection : EditOutbreakUiIntent
    data class UpdateBodyLocation(val value: String) : EditOutbreakUiIntent
    data class SelectBodyLocationCatalogOption(val id: Long, val name: String) : EditOutbreakUiIntent
    data object UseTypedBodyLocation : EditOutbreakUiIntent
    data object ClearBodyLocationCatalogSelection : EditOutbreakUiIntent
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
