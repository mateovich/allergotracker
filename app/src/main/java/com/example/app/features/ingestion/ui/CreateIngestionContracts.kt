package com.example.app.features.ingestion.ui

import com.example.app.features.ingestion.domain.ReusableEntityKind

data class IngestionReusableSelectionResult(
    val id: Long,
    val name: String,
    val kind: ReusableEntityKind
)

data class ReusableEntityOptionUiModel(
    val id: Long,
    val name: String,
    val updatedAtEpochMs: Long
)

data class ReusableEntityPickerOptionUiModel(
    val id: Long,
    val name: String,
    val kind: ReusableEntityKind,
    val supportingLabel: String,
    val updatedAtEpochMs: Long
)

data class QuantityOptionUiModel(
    val level: Int,
    val label: String
)

enum class IngestionTimePreset(
    val label: String,
    val offsetMillis: Long
) {
    NOW(label = "Now", offsetMillis = 0L),
    THIRTY_MINUTES_AGO(label = "30m ago", offsetMillis = 30L * 60L * 1000L),
    ONE_HOUR_AGO(label = "1h ago", offsetMillis = 60L * 60L * 1000L)
}

data class CreateIngestionUiState(
    val title: String = "Create Ingestion",
    val itemName: String = "",
    val notes: String = "",
    val occurredAtEpochMs: Long = 0L,
    val occurredAtLabel: String = "",
    val selectedTimePreset: IngestionTimePreset = IngestionTimePreset.NOW,
    val quantityOptions: List<QuantityOptionUiModel> = emptyList(),
    val selectedQuantityLevel: Int? = null,
    val selectedReusableEntitySummary: String? = null,
    val selectedReusableEntityId: Long? = null,
    val selectedReusableEntityKind: ReusableEntityKind? = null,
    val reusableSearchQuery: String = "",
    val filteredReusableEntities: List<ReusableEntityPickerOptionUiModel> = emptyList(),
    val availableIngredients: List<ReusableEntityOptionUiModel> = emptyList(),
    val availableCompounds: List<ReusableEntityOptionUiModel> = emptyList(),
    val itemNameError: String? = null,
    val quantityError: String? = null,
    val occurredAtError: String? = null,
    val formMessage: String = "",
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val isCreatingInlineIngredient: Boolean = false,
    val inlineIngredientError: String? = null,
    val showInlineIngredientDialog: Boolean = false,
    val inlineIngredientName: String = ""
)

sealed interface CreateIngestionUiIntent {
    data class UpdateReusableSearchQuery(val value: String) : CreateIngestionUiIntent
    data class UpdateItemName(val value: String) : CreateIngestionUiIntent
    data class UpdateNotes(val value: String) : CreateIngestionUiIntent
    data class SelectQuantityLevel(val level: Int) : CreateIngestionUiIntent
    data class SelectTimePreset(val preset: IngestionTimePreset) : CreateIngestionUiIntent
    data class ApplyReusableSelection(val result: IngestionReusableSelectionResult) : CreateIngestionUiIntent
    data class SelectReusableEntity(val result: IngestionReusableSelectionResult) : CreateIngestionUiIntent
    data class CreateInlineIngredient(val name: String) : CreateIngestionUiIntent
    data class UpdateInlineIngredientName(val value: String) : CreateIngestionUiIntent
    data class ShowInlineIngredientDialog(val prefillName: String) : CreateIngestionUiIntent
    data object CancelInlineIngredientCreation : CreateIngestionUiIntent
    data object CreateNewCompound : CreateIngestionUiIntent
    data object EditSelectedReusableEntity : CreateIngestionUiIntent
    data object ClearRetiredReusableSelection : CreateIngestionUiIntent
    data object Save : CreateIngestionUiIntent
}

sealed interface CreateIngestionUiEffect {
    data object NavigateToRecords : CreateIngestionUiEffect
    data object NavigateToCreateCompound : CreateIngestionUiEffect
    data class NavigateToEditReusableEntity(val kind: ReusableEntityKind, val id: Long) : CreateIngestionUiEffect
}
