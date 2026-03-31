package com.example.app.features.compounds.ui

import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult

data class CreateCompoundUiState(
    val title: String = "Create Compound",
    val name: String = "",
    val notes: String = "",
    val helperMessage: String = "This creates a reusable draft now. Composition will be added in a later story.",
    val nameError: String? = null,
    val formMessage: String = "",
    val isSaving: Boolean = false,
    val canSave: Boolean = false
)

sealed interface CreateCompoundUiIntent {
    data class UpdateName(val value: String) : CreateCompoundUiIntent
    data class UpdateNotes(val value: String) : CreateCompoundUiIntent
    data object Save : CreateCompoundUiIntent
}

sealed interface CreateCompoundUiEffect {
    data class NavigateBackWithSelection(val result: IngestionReusableSelectionResult) : CreateCompoundUiEffect
}
