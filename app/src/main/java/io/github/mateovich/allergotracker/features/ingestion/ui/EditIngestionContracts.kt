package io.github.mateovich.allergotracker.features.ingestion.ui

import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind

data class EditIngestionUiState(
    val title: String = "Edit Ingestion",
    val itemName: String = "",
    val deleteRecordLabel: String = "",
    val notes: String = "",
    val occurredAtEpochMs: Long = 0L,
    val occurredAtLabel: String = "",
    val selectedTimePreset: IngestionTimePreset = IngestionTimePreset.NOW,
    val quantityOptions: List<QuantityOptionUiModel> = emptyList(),
    val selectedQuantityLevel: Int? = null,
    val selectedReusableEntityKind: ReusableEntityKind? = null,
    val selectedReusableEntityId: Long? = null,
    val selectedReusableEntitySummary: String? = null,
    val itemNameError: String? = null,
    val quantityError: String? = null,
    val occurredAtError: String? = null,
    val formMessage: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleteConfirmationVisible: Boolean = false,
    val canSave: Boolean = false,
    val canDelete: Boolean = false
)

sealed interface EditIngestionUiIntent {
    data object Load : EditIngestionUiIntent
    data class UpdateItemName(val value: String) : EditIngestionUiIntent
    data class UpdateNotes(val value: String) : EditIngestionUiIntent
    data class SelectQuantityLevel(val level: Int) : EditIngestionUiIntent
    data class SelectTimePreset(val preset: IngestionTimePreset) : EditIngestionUiIntent
    data class ApplyReusableSelection(val result: IngestionReusableSelectionResult) : EditIngestionUiIntent
    data object EditSelectedReusableEntity : EditIngestionUiIntent
    data object ClearRetiredReusableSelection : EditIngestionUiIntent
    data object ShowDeleteConfirmation : EditIngestionUiIntent
    data object DismissDeleteConfirmation : EditIngestionUiIntent
    data object ConfirmDelete : EditIngestionUiIntent
    data object Save : EditIngestionUiIntent
}

sealed interface EditIngestionUiEffect {
    data object NavigateToRecords : EditIngestionUiEffect
    data class NavigateToEditReusableEntity(val kind: ReusableEntityKind, val id: Long) : EditIngestionUiEffect
}
