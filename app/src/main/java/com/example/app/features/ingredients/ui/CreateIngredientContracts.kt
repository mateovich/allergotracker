package com.example.app.features.ingredients.ui

import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult

data class CreateIngredientUiState(
    val title: String = "Create Ingredient",
    val name: String = "",
    val notes: String = "",
    val nameError: String? = null,
    val formMessage: String = "",
    val isSaving: Boolean = false,
    val canSave: Boolean = false
)

sealed interface CreateIngredientUiIntent {
    data class UpdateName(val value: String) : CreateIngredientUiIntent
    data class UpdateNotes(val value: String) : CreateIngredientUiIntent
    data object Save : CreateIngredientUiIntent
}

sealed interface CreateIngredientUiEffect {
    data class NavigateBackWithSelection(val result: IngestionReusableSelectionResult) : CreateIngredientUiEffect
}
