package com.example.app.features.compounds.ui

import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult
import com.example.app.features.compounds.domain.ReusableCompoundRetirementImpact

data class EditCompoundUiState(
    val title: String = "Edit Compound",
    val name: String = "",
    val notes: String = "",
    val helperMessage: String = "This story only edits reusable metadata. Composition stays unchanged.",
    val nameError: String? = null,
    val formMessage: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val isRetiring: Boolean = false,
    val retirementImpact: ReusableCompoundRetirementImpact? = null,
    val isRetirementDialogVisible: Boolean = false
)

sealed interface EditCompoundUiIntent {
    data object Load : EditCompoundUiIntent
    data class UpdateName(val value: String) : EditCompoundUiIntent
    data class UpdateNotes(val value: String) : EditCompoundUiIntent
    data object Save : EditCompoundUiIntent
    data object RequestRetirementPreview : EditCompoundUiIntent
    data object DismissRetirementDialog : EditCompoundUiIntent
    data object ConfirmRetirement : EditCompoundUiIntent
}

sealed interface EditCompoundUiEffect {
    data class NavigateBackWithSelection(val result: IngestionReusableSelectionResult) : EditCompoundUiEffect
    data object NavigateBackAfterRetirement : EditCompoundUiEffect
}
