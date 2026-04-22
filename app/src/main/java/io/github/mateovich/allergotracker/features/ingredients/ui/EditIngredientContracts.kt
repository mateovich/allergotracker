package io.github.mateovich.allergotracker.features.ingredients.ui

import io.github.mateovich.allergotracker.features.ingestion.ui.IngestionReusableSelectionResult
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientMergePreview
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRetirementImpact

data class EditIngredientUiState(
    val title: String = "Edit Ingredient",
    val name: String = "",
    val notes: String = "",
    val nameError: String? = null,
    val formMessage: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val isMergeDialogVisible: Boolean = false,
    val mergeDestinationQuery: String = "",
    val mergeDestinationOptions: List<ReusableIngredient> = emptyList(),
    val selectedMergeDestinationId: Long? = null,
    val mergePreview: ReusableIngredientMergePreview? = null,
    val isLoadingMergePreview: Boolean = false,
    val isMerging: Boolean = false,
    val mergeError: String? = null,
    val isRetiring: Boolean = false,
    val retirementImpact: ReusableIngredientRetirementImpact? = null,
    val isRetirementDialogVisible: Boolean = false
)

sealed interface EditIngredientUiIntent {
    data object Load : EditIngredientUiIntent
    data class UpdateName(val value: String) : EditIngredientUiIntent
    data class UpdateNotes(val value: String) : EditIngredientUiIntent
    data object Save : EditIngredientUiIntent
    data object RequestMergeFlow : EditIngredientUiIntent
    data class UpdateMergeDestinationQuery(val value: String) : EditIngredientUiIntent
    data class SelectMergeDestination(val id: Long) : EditIngredientUiIntent
    data object LoadMergePreview : EditIngredientUiIntent
    data object DismissMergeDialog : EditIngredientUiIntent
    data object ConfirmMerge : EditIngredientUiIntent
    data object RequestRetirementPreview : EditIngredientUiIntent
    data object DismissRetirementDialog : EditIngredientUiIntent
    data object ConfirmRetirement : EditIngredientUiIntent
}

sealed interface EditIngredientUiEffect {
    data class NavigateBackWithSelection(val result: IngestionReusableSelectionResult) : EditIngredientUiEffect
    data object NavigateBackAfterRetirement : EditIngredientUiEffect
}
