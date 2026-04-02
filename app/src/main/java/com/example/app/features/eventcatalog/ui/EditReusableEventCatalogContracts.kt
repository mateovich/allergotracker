package com.example.app.features.eventcatalog.ui

import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogRetirementImpact

data class EditedEventCatalogSelectionResult(
    val id: Long,
    val kind: ReusableEventCatalogKind,
    val name: String
)

data class EditReusableEventCatalogUiState(
    val title: String = "Edit Saved Value",
    val nameLabel: String = "Saved value",
    val name: String = "",
    val kind: ReusableEventCatalogKind = ReusableEventCatalogKind.EXPOSURE_TYPE,
    val nameError: String? = null,
    val formMessage: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val isRetiring: Boolean = false,
    val retirementImpact: ReusableEventCatalogRetirementImpact? = null,
    val isRetirementDialogVisible: Boolean = false
)

sealed interface EditReusableEventCatalogUiIntent {
    data object Load : EditReusableEventCatalogUiIntent
    data class UpdateName(val value: String) : EditReusableEventCatalogUiIntent
    data object Save : EditReusableEventCatalogUiIntent
    data object RequestRetirementPreview : EditReusableEventCatalogUiIntent
    data object DismissRetirementDialog : EditReusableEventCatalogUiIntent
    data object ConfirmRetirement : EditReusableEventCatalogUiIntent
}

sealed interface EditReusableEventCatalogUiEffect {
    data class NavigateBackWithSelection(val result: EditedEventCatalogSelectionResult) : EditReusableEventCatalogUiEffect
    data object NavigateBackAfterRetirement : EditReusableEventCatalogUiEffect
}
