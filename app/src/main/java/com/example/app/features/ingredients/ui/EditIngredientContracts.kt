package com.example.app.features.ingredients.ui

import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult

data class EditIngredientUiState(
    val title: String = "Edit Ingredient",
    val name: String = "",
    val notes: String = "",
    val nameError: String? = null,
    val formMessage: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val canSave: Boolean = false
)

sealed interface EditIngredientUiIntent {
    data object Load : EditIngredientUiIntent
    data class UpdateName(val value: String) : EditIngredientUiIntent
    data class UpdateNotes(val value: String) : EditIngredientUiIntent
    data object Save : EditIngredientUiIntent
}

sealed interface EditIngredientUiEffect {
    data class NavigateBackWithSelection(val result: IngestionReusableSelectionResult) : EditIngredientUiEffect
}
