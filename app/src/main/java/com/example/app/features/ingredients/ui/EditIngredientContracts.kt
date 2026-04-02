package com.example.app.features.ingredients.ui

import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult
import com.example.app.features.ingredients.domain.ReusableIngredientRetirementImpact

data class EditIngredientUiState(
    val title: String = "Edit Ingredient",
    val name: String = "",
    val notes: String = "",
    val nameError: String? = null,
    val formMessage: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val isRetiring: Boolean = false,
    val retirementImpact: ReusableIngredientRetirementImpact? = null,
    val isRetirementDialogVisible: Boolean = false
)

sealed interface EditIngredientUiIntent {
    data object Load : EditIngredientUiIntent
    data class UpdateName(val value: String) : EditIngredientUiIntent
    data class UpdateNotes(val value: String) : EditIngredientUiIntent
    data object Save : EditIngredientUiIntent
    data object RequestRetirementPreview : EditIngredientUiIntent
    data object DismissRetirementDialog : EditIngredientUiIntent
    data object ConfirmRetirement : EditIngredientUiIntent
}

sealed interface EditIngredientUiEffect {
    data class NavigateBackWithSelection(val result: IngestionReusableSelectionResult) : EditIngredientUiEffect
    data object NavigateBackAfterRetirement : EditIngredientUiEffect
}
