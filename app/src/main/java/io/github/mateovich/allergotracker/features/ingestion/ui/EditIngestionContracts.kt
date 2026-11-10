package io.github.mateovich.allergotracker.features.ingestion.ui

import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import java.time.LocalDate
import java.time.LocalTime

data class EditIngestionUiState(
    val title: String = "Edit Ingestion",
    val itemName: String = "",
    val deleteRecordLabel: String = "",
    val notes: String = "",
    val occurredAtEpochMs: Long = 0L,
    val occurredAtLabel: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedTime: LocalTime = LocalTime.MIDNIGHT,
    val selectedTimePreset: IngestionTimePreset? = IngestionTimePreset.NOW,
    val quantityOptions: List<QuantityOptionUiModel> = emptyList(),
    val selectedQuantityLevel: Int? = null,
    val selectedReusableEntityKind: ReusableEntityKind? = null,
    val selectedReusableEntityId: Long? = null,
    val reusableSearchQuery: String = "",
    val filteredReusableEntities: List<ReusableEntityPickerOptionUiModel> = emptyList(),
    val availableIngredients: List<ReusableEntityOptionUiModel> = emptyList(),
    val availableCompounds: List<ReusableEntityOptionUiModel> = emptyList(),
    val itemNameError: String? = null,
    val quantityError: String? = null,
    val occurredAtError: String? = null,
    val formMessage: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleteConfirmationVisible: Boolean = false,
    val canSave: Boolean = false,
    val canDelete: Boolean = false,
    val isCreatingInlineIngredient: Boolean = false,
    val inlineIngredientError: String? = null,
    val showInlineIngredientDialog: Boolean = false,
    val inlineIngredientName: String = ""
)

sealed interface EditIngestionUiIntent {
    data object Load : EditIngestionUiIntent
    data class UpdateReusableSearchQuery(val value: String) : EditIngestionUiIntent
    data class UpdateItemName(val value: String) : EditIngestionUiIntent
    data class UpdateNotes(val value: String) : EditIngestionUiIntent
    data class SelectQuantityLevel(val level: Int) : EditIngestionUiIntent
    data class SelectDate(val value: LocalDate) : EditIngestionUiIntent
    data class SelectTime(val value: LocalTime) : EditIngestionUiIntent
    data class SelectTimePreset(val preset: IngestionTimePreset) : EditIngestionUiIntent
    data class ApplyReusableSelection(val result: IngestionReusableSelectionResult) : EditIngestionUiIntent
    data class SelectReusableEntity(val result: IngestionReusableSelectionResult) : EditIngestionUiIntent
    data class CreateInlineIngredient(val name: String) : EditIngestionUiIntent
    data class UpdateInlineIngredientName(val value: String) : EditIngestionUiIntent
    data class ShowInlineIngredientDialog(val prefillName: String) : EditIngestionUiIntent
    data object CancelInlineIngredientCreation : EditIngestionUiIntent
    data object ClearRetiredReusableSelection : EditIngestionUiIntent
    data object ShowDeleteConfirmation : EditIngestionUiIntent
    data object DismissDeleteConfirmation : EditIngestionUiIntent
    data object ConfirmDelete : EditIngestionUiIntent
    data object Save : EditIngestionUiIntent
}

sealed interface EditIngestionUiEffect {
    data object NavigateToRecords : EditIngestionUiEffect
}
