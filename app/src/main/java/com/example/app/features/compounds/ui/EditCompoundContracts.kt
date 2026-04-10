package com.example.app.features.compounds.ui

import com.example.app.features.compounds.domain.ReusableCompound
import com.example.app.features.compounds.domain.ReusableCompoundMergePreview
import com.example.app.features.compounds.domain.ReusableCompoundRetirementImpact
import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult

data class EditCompoundUiState(
    val title: String = "Edit Compound",
    val name: String = "",
    val notes: String = "",
    val helperMessage: String = "Adjust saved components and keep the total locked at 100% before saving.",
    val componentSearchQuery: String = "",
    val availableComponents: List<CompoundComponentCatalogOptionUiModel> = emptyList(),
    val filteredComponents: List<CompoundComponentCatalogOptionUiModel> = emptyList(),
    val components: List<CompoundComponentRowUiModel> = emptyList(),
    val touchedComponentOrder: List<Int> = emptyList(),
    val selectedPreset: CompoundCompositionPreset? = null,
    val totalPercentLabel: String = "0% total",
    val componentsError: String? = null,
    val totalError: String? = null,
    val nameError: String? = null,
    val formMessage: String = "",
    val isFormMessageError: Boolean = false,
    val isFormMessageSticky: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val isMergeDialogVisible: Boolean = false,
    val mergeDestinationQuery: String = "",
    val mergeDestinationOptions: List<ReusableCompound> = emptyList(),
    val selectedMergeDestinationId: Long? = null,
    val mergePreview: ReusableCompoundMergePreview? = null,
    val isLoadingMergePreview: Boolean = false,
    val isMerging: Boolean = false,
    val mergeError: String? = null,
    val isRetiring: Boolean = false,
    val retirementImpact: ReusableCompoundRetirementImpact? = null,
    val isRetirementDialogVisible: Boolean = false
)

sealed interface EditCompoundUiIntent {
    data object Load : EditCompoundUiIntent
    data class UpdateName(val value: String) : EditCompoundUiIntent
    data class UpdateNotes(val value: String) : EditCompoundUiIntent
    data class UpdateComponentSearchQuery(val value: String) : EditCompoundUiIntent
    data class AddIngredientComponent(val reusableId: Long) : EditCompoundUiIntent
    data class AddCompoundComponent(val reusableId: Long) : EditCompoundUiIntent
    data class ApplyPreset(val preset: CompoundCompositionPreset) : EditCompoundUiIntent
    data class UpdateComponentProportion(val index: Int, val value: String) : EditCompoundUiIntent
    data class AdjustComponentProportion(val index: Int, val basisPoints: Int) : EditCompoundUiIntent
    data class RemoveComponent(val index: Int) : EditCompoundUiIntent
    data object Save : EditCompoundUiIntent
    data object RequestMergeFlow : EditCompoundUiIntent
    data class UpdateMergeDestinationQuery(val value: String) : EditCompoundUiIntent
    data class SelectMergeDestination(val id: Long) : EditCompoundUiIntent
    data object LoadMergePreview : EditCompoundUiIntent
    data object DismissMergeDialog : EditCompoundUiIntent
    data object ConfirmMerge : EditCompoundUiIntent
    data object RequestRetirementPreview : EditCompoundUiIntent
    data object DismissRetirementDialog : EditCompoundUiIntent
    data object ConfirmRetirement : EditCompoundUiIntent
}

sealed interface EditCompoundUiEffect {
    data class NavigateBackWithSelection(val result: IngestionReusableSelectionResult) : EditCompoundUiEffect
    data object NavigateBackAfterRetirement : EditCompoundUiEffect
}
