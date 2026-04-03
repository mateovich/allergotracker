package com.example.app.features.compounds.ui

import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult

data class CompoundComponentCatalogOptionUiModel(
    val id: Long,
    val name: String,
    val kind: String,
    val supportingLabel: String,
    val updatedAtEpochMs: Long
)

data class CreateCompoundComponentRowUiModel(
    val kind: String,
    val reusableEntityId: Long,
    val name: String,
    val proportionPercent: String = "",
    val proportionError: String? = null,
    val targetError: String? = null
)

data class CreateCompoundUiState(
    val title: String = "Create Compound",
    val name: String = "",
    val notes: String = "",
    val helperMessage: String = "Add saved ingredients or compounds and assign proportions totaling 100% before saving.",
    val componentSearchQuery: String = "",
    val availableComponents: List<CompoundComponentCatalogOptionUiModel> = emptyList(),
    val filteredComponents: List<CompoundComponentCatalogOptionUiModel> = emptyList(),
    val components: List<CreateCompoundComponentRowUiModel> = emptyList(),
    val totalPercentLabel: String = "0% total",
    val componentsError: String? = null,
    val totalError: String? = null,
    val nameError: String? = null,
    val formMessage: String = "",
    val isSaving: Boolean = false,
    val canSave: Boolean = false
)

sealed interface CreateCompoundUiIntent {
    data class UpdateName(val value: String) : CreateCompoundUiIntent
    data class UpdateNotes(val value: String) : CreateCompoundUiIntent
    data class UpdateComponentSearchQuery(val value: String) : CreateCompoundUiIntent
    data class AddIngredientComponent(val reusableId: Long) : CreateCompoundUiIntent
    data class AddCompoundComponent(val reusableId: Long) : CreateCompoundUiIntent
    data class UpdateComponentProportion(val index: Int, val value: String) : CreateCompoundUiIntent
    data class RemoveComponent(val index: Int) : CreateCompoundUiIntent
    data object Save : CreateCompoundUiIntent
}

sealed interface CreateCompoundUiEffect {
    data class NavigateBackWithSelection(val result: IngestionReusableSelectionResult) : CreateCompoundUiEffect
}
