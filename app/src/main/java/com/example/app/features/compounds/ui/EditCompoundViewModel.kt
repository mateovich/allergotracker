package com.example.app.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundComponentKind
import com.example.app.features.compounds.domain.CompoundCompositionIntegrity
import com.example.app.features.compounds.domain.GetReusableCompoundMergePreviewUseCase
import com.example.app.features.compounds.domain.GetReusableCompoundRetirementImpactUseCase
import com.example.app.features.compounds.domain.GetReusableCompoundUseCase
import com.example.app.features.compounds.domain.MergeReusableCompoundUseCase
import com.example.app.features.compounds.domain.ObserveReusableCompoundsUseCase
import com.example.app.features.compounds.domain.ReusableCompound
import com.example.app.features.compounds.domain.RetireReusableCompoundUseCase
import com.example.app.features.compounds.domain.UpdateReusableCompoundCommand
import com.example.app.features.compounds.domain.UpdateReusableCompoundComponentCommand
import com.example.app.features.compounds.domain.UpdateReusableCompoundUseCase
import com.example.app.features.ingredients.domain.ObserveReusableIngredientsUseCase
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class EditCompoundViewModel(
    private val compoundId: Long,
    private val getReusableCompoundUseCase: GetReusableCompoundUseCase,
    private val updateReusableCompoundUseCase: UpdateReusableCompoundUseCase,
    private val observeReusableIngredients: ObserveReusableIngredientsUseCase,
    private val observeReusableCompounds: ObserveReusableCompoundsUseCase,
    private val getReusableCompoundMergePreviewUseCase: GetReusableCompoundMergePreviewUseCase,
    private val mergeReusableCompoundUseCase: MergeReusableCompoundUseCase,
    private val getReusableCompoundRetirementImpactUseCase: GetReusableCompoundRetirementImpactUseCase,
    private val retireReusableCompoundUseCase: RetireReusableCompoundUseCase
) : ViewModel() {
    private var ingredientCatalogOptions: List<CompoundComponentCatalogOptionUiModel> = emptyList()
    private var compoundCatalogOptions: List<CompoundComponentCatalogOptionUiModel> = emptyList()
    private var observedCompounds: List<ReusableCompound> = emptyList()

    private val mutableState = MutableStateFlow(EditCompoundUiState())
    val state: StateFlow<EditCompoundUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EditCompoundUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeReusableIngredients().collect { ingredients ->
                ingredientCatalogOptions = ingredients.map { ingredient ->
                        CompoundComponentCatalogOptionUiModel(
                            id = ingredient.id,
                            name = ingredient.name,
                            kind = CompoundComponentKind.INGREDIENT.storageValue,
                            supportingLabel = "Ingredient",
                            updatedAtEpochMs = ingredient.updatedAtEpochMs
                        )
                    }
                refreshCatalogOptions()
            }
        }
        viewModelScope.launch {
            observeReusableCompounds().collect { compounds ->
                observedCompounds = compounds
                compoundCatalogOptions = compounds
                    .filter {
                        it.id != compoundId &&
                            it.compositionStatus == com.example.app.features.compounds.domain.CompoundCompositionStatus.COMPLETE
                    }
                    .map { compound ->
                            CompoundComponentCatalogOptionUiModel(
                                id = compound.id,
                                name = compound.name,
                                kind = CompoundComponentKind.COMPOUND.storageValue,
                                supportingLabel = "Compound",
                                updatedAtEpochMs = compound.updatedAtEpochMs
                            )
                        }
                refreshCatalogOptions()
            }
        }
    }

    fun onIntent(intent: EditCompoundUiIntent) {
        when (intent) {
            EditCompoundUiIntent.Load -> loadIfNeeded()
            is EditCompoundUiIntent.UpdateName -> updateState {
                val hasName = intent.value.trim().isNotBlank()
                if (it.isFormMessageSticky) {
                    it.copy(
                        name = intent.value,
                        nameError = if (hasName) null else "Enter a compound name."
                    ).withDerivedFields()
                } else {
                    it.copy(
                        name = intent.value,
                        nameError = if (hasName) null else "Enter a compound name.",
                        formMessage = "",
                        isFormMessageError = false,
                        isFormMessageSticky = false
                    ).withDerivedFields()
                }
            }

            is EditCompoundUiIntent.UpdateNotes -> updateState {
                if (it.isFormMessageSticky) {
                    it.copy(notes = intent.value)
                } else {
                    it.copy(notes = intent.value, formMessage = "", isFormMessageError = false, isFormMessageSticky = false)
                }
            }
            is EditCompoundUiIntent.UpdateComponentSearchQuery -> updateState {
                if (it.isFormMessageSticky) {
                    it.copy(componentSearchQuery = intent.value).withDerivedFields()
                } else {
                    it.copy(
                        componentSearchQuery = intent.value,
                        formMessage = "",
                        isFormMessageError = false,
                        isFormMessageSticky = false
                    ).withDerivedFields()
                }
            }

            is EditCompoundUiIntent.AddIngredientComponent -> addComponent(CompoundComponentKind.INGREDIENT, intent.reusableId)
            is EditCompoundUiIntent.AddCompoundComponent -> addComponent(CompoundComponentKind.COMPOUND, intent.reusableId)
            is EditCompoundUiIntent.ApplyPreset -> applyPreset(intent.preset)
            is EditCompoundUiIntent.UpdateComponentProportion -> updateComponentProportion(intent.index, intent.value)
            is EditCompoundUiIntent.AdjustComponentProportion -> applyAdjustedProportion(intent.index, intent.basisPoints)
            is EditCompoundUiIntent.RemoveComponent -> removeComponent(intent.index)
            EditCompoundUiIntent.Save -> save()
            EditCompoundUiIntent.RequestMergeFlow -> openMergeDialog()
            is EditCompoundUiIntent.UpdateMergeDestinationQuery -> updateState {
                it.copy(
                    mergeDestinationQuery = intent.value,
                    mergeError = null,
                    mergePreview = null
                ).refreshMergeDestinationOptions()
            }
            is EditCompoundUiIntent.SelectMergeDestination -> updateState {
                it.copy(selectedMergeDestinationId = intent.id, mergePreview = null, mergeError = null)
                    .refreshMergeDestinationOptions()
            }
            EditCompoundUiIntent.LoadMergePreview -> loadMergePreview()
            EditCompoundUiIntent.DismissMergeDialog -> updateState {
                it.copy(
                    isMergeDialogVisible = false,
                    mergeDestinationQuery = "",
                    selectedMergeDestinationId = null,
                    mergePreview = null,
                    isLoadingMergePreview = false,
                    isMerging = false,
                    mergeError = null
                ).refreshMergeDestinationOptions()
            }
            EditCompoundUiIntent.ConfirmMerge -> merge()
            EditCompoundUiIntent.RequestRetirementPreview -> loadRetirementPreview()
            EditCompoundUiIntent.DismissRetirementDialog -> updateState {
                it.copy(isRetirementDialogVisible = false, retirementImpact = null, isRetiring = false)
            }

            EditCompoundUiIntent.ConfirmRetirement -> retire()
        }
    }

    private fun loadIfNeeded() {
        if (!mutableState.value.isLoading && mutableState.value.name.isNotBlank()) {
            return
        }
        updateState { it.copy(isLoading = true, formMessage = "", isFormMessageError = false, isFormMessageSticky = false) }
        viewModelScope.launch {
            when (val result = getReusableCompoundUseCase(compoundId)) {
                is AppResult.Success -> updateState { current ->
                    current.copy(
                        name = result.value.name,
                        notes = result.value.notes.orEmpty(),
                        components = result.value.components.sortedBy { it.position }.map { component ->
                            val reusableEntityId = component.ingredientId ?: component.childCompoundId ?: 0L
                            val kind = component.kind.storageValue
                            val basisPoints = component.proportionBasisPoints
                            CompoundComponentRowUiModel(
                                kind = kind,
                                reusableEntityId = reusableEntityId,
                                name = current.resolveComponentName(kind, reusableEntityId),
                                proportionBasisPoints = basisPoints,
                                proportionPercent = basisPoints?.toPercentInput().orEmpty()
                            )
                        },
                        selectedPreset = null,
                        isLoading = false,
                        formMessage = "",
                        isFormMessageError = false,
                        isFormMessageSticky = false
                    ).withDerivedFields().refreshAvailableComponents()
                }

                is AppResult.Error -> updateState {
                    it.copy(
                        isLoading = false,
                        formMessage = result.error.message,
                        isFormMessageError = true,
                        isFormMessageSticky = false
                    )
                }
            }
        }
    }

    private fun addComponent(kind: CompoundComponentKind, reusableId: Long) {
        updateState { current ->
            val option = allCatalogOptions().firstOrNull {
                it.kind == kind.storageValue && it.id == reusableId
            } ?: return@updateState current
            val addError = blockedAddMessage(current, kind, reusableId)
            if (addError != null) {
                return@updateState current.copy(
                    formMessage = addError,
                    isFormMessageError = true,
                    isFormMessageSticky = false
                )
            }
            current.copy(
                components = current.components + CompoundComponentRowUiModel(
                    kind = kind.storageValue,
                    reusableEntityId = reusableId,
                    name = option.name
                ),
                selectedPreset = null,
                componentsError = null,
                formMessage = "",
                isFormMessageError = false,
                isFormMessageSticky = false
            ).withDerivedFields().refreshAvailableComponents()
        }
    }

    private fun updateComponentProportion(index: Int, value: String) {
        updateState { current ->
            if (index !in current.components.indices) {
                return@updateState current
            }
            val sanitizedValue = sanitizePercentInput(value)
            val requestedBasisPoints = sanitizedValue.toBasisPointsOrNull()
            if (sanitizedValue.isBlank() || requestedBasisPoints == null) {
                current.copy(
                    components = current.components.mapIndexed { componentIndex, component ->
                        if (componentIndex == index) {
                            component.copy(
                                proportionBasisPoints = null,
                                proportionPercent = sanitizedValue,
                                proportionError = null,
                                targetError = null
                            )
                        } else {
                            component
                        }
                    },
                    selectedPreset = null,
                    totalError = null,
                    formMessage = "",
                    isFormMessageError = false,
                    isFormMessageSticky = false
                ).withDerivedFields()
            } else {
                current.applyAdjustedProportion(index, requestedBasisPoints)
            }
        }
    }

    private fun applyPreset(preset: CompoundCompositionPreset) {
        updateState { current ->
            if (current.components.isEmpty()) {
                return@updateState current.copy(
                    selectedPreset = null,
                    componentsError = "Add at least one component before applying a preset.",
                    formMessage = "Couldn't apply ${preset.label.lowercase()} preset to an empty composition.",
                    isFormMessageError = true,
                    isFormMessageSticky = true,
                    totalError = null
                ).withDerivedFields()
            }
            val allocation = CompoundPresetAllocationEngine.allocate(
                preset = preset,
                componentCount = current.components.size
            ) ?: return@updateState current.copy(
                selectedPreset = null,
                componentsError = "Add no more than 10000 components before applying a preset.",
                formMessage = "Couldn't apply ${preset.label.lowercase()} preset to the current composition.",
                isFormMessageError = true,
                isFormMessageSticky = false,
                totalError = null
            ).withDerivedFields()
            current.applyPreset(allocation)
        }
    }

    private fun applyAdjustedProportion(index: Int, requestedBasisPoints: Int) {
        updateState { current -> current.applyAdjustedProportion(index, requestedBasisPoints) }
    }

    private fun removeComponent(index: Int) {
        updateState { current ->
            if (index !in current.components.indices) {
                return@updateState current
            }
            val basisPoints = current.components[index].proportionBasisPoints ?: 0
            if (current.components.size > 1 && basisPoints != 0) {
                return@updateState current.copy(
                    formMessage = "Set the component to 0% before removing it.",
                    isFormMessageError = true,
                    isFormMessageSticky = false
                )
            }
            current.copy(
                components = current.components.filterIndexed { componentIndex, _ -> componentIndex != index },
                touchedComponentOrder = current.touchedComponentOrder
                    .filter { it != index }
                    .map { touchedIndex -> if (touchedIndex > index) touchedIndex - 1 else touchedIndex },
                selectedPreset = null,
                formMessage = "",
                isFormMessageError = false,
                isFormMessageSticky = false
            ).withDerivedFields().refreshAvailableComponents()
        }
    }

    private fun openMergeDialog() {
        val currentState = mutableState.value
        if (currentState.isLoading || currentState.isSaving || currentState.isRetiring || currentState.isMerging) return
        updateState {
            it.copy(
                isMergeDialogVisible = true,
                mergeDestinationQuery = "",
                selectedMergeDestinationId = null,
                mergePreview = null,
                isLoadingMergePreview = false,
                isMerging = false,
                mergeError = null
            ).refreshMergeDestinationOptions()
        }
    }

    private fun loadMergePreview() {
        val currentState = mutableState.value
        val destinationId = currentState.selectedMergeDestinationId
        if (!currentState.isMergeDialogVisible || currentState.isLoading || currentState.isSaving || currentState.isRetiring || currentState.isMerging) {
            return
        }
        if (destinationId == null) {
            updateState { it.copy(mergeError = "Choose a canonical compound to continue.") }
            return
        }
        updateState { it.copy(isLoadingMergePreview = true, mergePreview = null, mergeError = null) }
        viewModelScope.launch {
            when (val result = getReusableCompoundMergePreviewUseCase(compoundId, destinationId)) {
                is AppResult.Success -> updateState {
                    if (!it.isMergeDialogVisible || it.selectedMergeDestinationId != destinationId) {
                        it.copy(isLoadingMergePreview = false)
                    } else {
                        it.copy(isLoadingMergePreview = false, mergePreview = result.value, mergeError = null)
                    }
                }

                is AppResult.Error -> updateState {
                    if (!it.isMergeDialogVisible || it.selectedMergeDestinationId != destinationId) {
                        it.copy(isLoadingMergePreview = false)
                    } else {
                        it.copy(isLoadingMergePreview = false, mergePreview = null, mergeError = result.error.message)
                    }
                }
            }
        }
    }

    private fun merge() {
        val currentState = mutableState.value
        val destinationId = currentState.selectedMergeDestinationId ?: return
        val preview = currentState.mergePreview ?: return
        if (
            !currentState.isMergeDialogVisible ||
            currentState.isLoading ||
            currentState.isSaving ||
            currentState.isRetiring ||
            currentState.isLoadingMergePreview ||
            currentState.isMerging ||
            preview.isBlocked
        ) {
            return
        }
        updateState { it.copy(isMerging = true, mergeError = null) }
        viewModelScope.launch {
            when (val result = mergeReusableCompoundUseCase(compoundId, destinationId)) {
                is AppResult.Success -> {
                    updateState {
                        it.copy(
                            isMerging = false,
                            isMergeDialogVisible = false,
                            mergeDestinationQuery = "",
                            selectedMergeDestinationId = null,
                            mergePreview = null,
                            isLoadingMergePreview = false,
                            mergeError = null,
                            formMessage = "",
                            isFormMessageError = false,
                            isFormMessageSticky = false
                        ).refreshMergeDestinationOptions()
                    }
                    effectChannel.trySend(
                        EditCompoundUiEffect.NavigateBackWithSelection(
                            IngestionReusableSelectionResult(
                                result.value.destination.id,
                                result.value.destination.name,
                                ReusableEntityKind.COMPOUND
                            )
                        )
                    )
                }

                is AppResult.Error -> updateState {
                    it.copy(isMerging = false, mergeError = result.error.message)
                }
            }
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (!currentState.canSave || currentState.isLoading || currentState.isSaving) {
            return
        }
        updateState { it.copy(isSaving = true, formMessage = "", isFormMessageError = false, isFormMessageSticky = false) }
        viewModelScope.launch {
            when (
                val result = updateReusableCompoundUseCase(
                    UpdateReusableCompoundCommand(
                        id = compoundId,
                        name = currentState.name,
                        notes = currentState.notes,
                        components = currentState.components.map { component ->
                            UpdateReusableCompoundComponentCommand(
                                kind = CompoundComponentKind.fromStorageValue(component.kind),
                                reusableEntityId = component.reusableEntityId,
                                proportionBasisPoints = component.proportionBasisPoints
                            )
                        }
                    )
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(
                        EditCompoundUiEffect.NavigateBackWithSelection(
                            IngestionReusableSelectionResult(result.value.id, result.value.name, ReusableEntityKind.COMPOUND)
                        )
                    )
                }

                is AppResult.Error -> updateState { current ->
                    current.copy(
                        isSaving = false,
                        nameError = result.error.fieldError("name"),
                        componentsError = result.error.fieldError("components"),
                        totalError = result.error.fieldError("componentsTotal"),
                        components = current.components.mapIndexed { index, component ->
                            component.copy(
                                targetError = result.error.fieldError("components[$index].target"),
                                proportionError = result.error.fieldError("components[$index].proportion")
                            )
                        },
                        formMessage = result.error.message,
                        isFormMessageError = true,
                        isFormMessageSticky = false
                    ).withDerivedFields()
                }
            }
        }
    }

    private fun loadRetirementPreview() {
        val currentState = mutableState.value
        if (currentState.isLoading || currentState.isSaving || currentState.isRetiring) return
        updateState { it.copy(formMessage = "", isFormMessageError = false, isFormMessageSticky = false) }
        viewModelScope.launch {
            when (val result = getReusableCompoundRetirementImpactUseCase(compoundId)) {
                is AppResult.Success -> updateState {
                    it.copy(
                        retirementImpact = result.value,
                        isRetirementDialogVisible = true,
                        formMessage = "",
                        isFormMessageError = false,
                        isFormMessageSticky = false
                    )
                }

                is AppResult.Error -> updateState {
                    it.copy(formMessage = result.error.message, isFormMessageError = true, isFormMessageSticky = false)
                }
            }
        }
    }

    private fun retire() {
        val currentState = mutableState.value
        val impact = currentState.retirementImpact ?: return
        if (currentState.isLoading || currentState.isSaving || currentState.isRetiring || impact.isBlocked) return
        updateState { it.copy(isRetiring = true, formMessage = "", isFormMessageError = false, isFormMessageSticky = false) }
        viewModelScope.launch {
            when (val result = retireReusableCompoundUseCase(compoundId)) {
                is AppResult.Success -> {
                    updateState {
                        it.copy(
                            isRetiring = false,
                            isRetirementDialogVisible = false,
                            retirementImpact = result.value,
                            formMessage = "",
                            isFormMessageError = false,
                            isFormMessageSticky = false
                        )
                    }
                    effectChannel.trySend(EditCompoundUiEffect.NavigateBackAfterRetirement)
                }

                is AppResult.Error -> updateState {
                        it.copy(
                            isRetiring = false,
                            isRetirementDialogVisible = true,
                            formMessage = result.error.message,
                            isFormMessageError = true,
                            isFormMessageSticky = false
                        )
                    }
                }
        }
    }

    private fun refreshCatalogOptions() {
        updateState { current ->
            current.refreshAvailableComponents().refreshMergeDestinationOptions()
        }
    }

    private fun updateState(transform: (EditCompoundUiState) -> EditCompoundUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun EditCompoundUiState.applyAdjustedProportion(index: Int, requestedBasisPoints: Int): EditCompoundUiState {
        val adjustment = CompoundProportionAdjustmentEngine.adjust(
            values = components.normalizedValuesForAdjustment(),
            touchedOrder = touchedComponentOrder,
            index = index,
            requestedBasisPoints = requestedBasisPoints
        )
        return copy(
            components = components.mapIndexed { componentIndex, component ->
                val basisPoints = adjustment.values[componentIndex]
                component.copy(
                    proportionBasisPoints = basisPoints,
                    proportionPercent = basisPoints.toPercentInput(),
                    proportionError = null,
                    targetError = null,
                    isTouched = componentIndex in adjustment.touchedOrder
                )
            },
            selectedPreset = null,
            touchedComponentOrder = adjustment.touchedOrder,
            totalError = null,
            componentsError = null,
            formMessage = "",
            isFormMessageError = false,
            isFormMessageSticky = false
        ).withDerivedFields().refreshAvailableComponents()
    }

    private fun EditCompoundUiState.applyPreset(
        allocation: CompoundPresetAllocationResult
    ): EditCompoundUiState {
        return copy(
            components = components.mapIndexed { index, component ->
                val basisPoints = allocation.values[index]
                component.copy(
                    proportionBasisPoints = basisPoints,
                    proportionPercent = basisPoints.toPercentInput(),
                    proportionError = null,
                    targetError = null,
                    isTouched = false
                )
            },
            touchedComponentOrder = emptyList(),
            selectedPreset = allocation.preset,
            totalError = null,
            componentsError = null,
            formMessage = "Applied ${allocation.preset.label.lowercase()} preset. ${COMPOUND_TOTAL_BASIS_POINTS.toPercentLabel()} total remains valid.",
            isFormMessageError = false,
            isFormMessageSticky = false
        ).withDerivedFields().refreshAvailableComponents()
    }

    private fun EditCompoundUiState.resolveComponentName(kind: String, reusableId: Long): String {
        return availableComponents.firstOrNull { it.kind == kind && it.id == reusableId }?.name
            ?: when (kind) {
                CompoundComponentKind.INGREDIENT.storageValue -> "Ingredient #$reusableId"
                else -> "Compound #$reusableId"
            }
    }

    private fun CompoundComponentRowUiModel.resolveName(
        availableComponents: List<CompoundComponentCatalogOptionUiModel>
    ): String {
        return availableComponents.firstOrNull { it.kind == kind && it.id == reusableEntityId }?.name ?: name
    }

    private fun EditCompoundUiState.withDerivedFields(): EditCompoundUiState {
        val totalBasisPoints = components.sumOf { it.proportionBasisPoints ?: 0 }
        return copy(
            totalPercentLabel = "${totalBasisPoints.toPercentLabel()} total",
            canSave = !isLoading && name.isNotBlank() && components.hasCompleteValidComposition(),
            filteredComponents = availableComponents.filterByQuery(componentSearchQuery)
        )
    }

    private fun EditCompoundUiState.refreshAvailableComponents(): EditCompoundUiState {
        val selectedKeys = components.map { it.kind to it.reusableEntityId }.toSet()
        val cycleChildCompoundIds = CompoundCompositionIntegrity.invalidChildCompoundIds(
            parentCompoundId = compoundId,
            candidateIds = observedCompounds.map { it.id },
            childEdges = CompoundCompositionIntegrity.childEdgesFromCompounds(observedCompounds)
        )
        val availableComponents = allCatalogOptions()
            .filterNot { option ->
                (option.kind to option.id) in selectedKeys ||
                    (option.kind == CompoundComponentKind.COMPOUND.storageValue && option.id in cycleChildCompoundIds)
            }
            .sortedWith(
                compareByDescending<CompoundComponentCatalogOptionUiModel> { it.updatedAtEpochMs }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.kind }
                    .thenBy { it.id }
            )
        return copy(
            availableComponents = availableComponents,
            components = components.map { component ->
                component.copy(name = component.resolveName(allCatalogOptions()))
            }
        ).withDerivedFields()
    }

    private fun EditCompoundUiState.refreshMergeDestinationOptions(): EditCompoundUiState {
        val normalizedQuery = mergeDestinationQuery.trim().lowercase()
        val filteredOptions = observedCompounds.filter { compound ->
            compound.id != compoundId &&
                compound.compositionStatus == com.example.app.features.compounds.domain.CompoundCompositionStatus.COMPLETE &&
                (normalizedQuery.isBlank() || compound.name.lowercase().contains(normalizedQuery))
        }
        val hasSelectedDestination = filteredOptions.any { it.id == selectedMergeDestinationId }
        return copy(
            mergeDestinationOptions = filteredOptions,
            selectedMergeDestinationId = selectedMergeDestinationId?.takeIf { hasSelectedDestination },
            mergePreview = mergePreview?.takeIf { preview ->
                preview.destination.id == selectedMergeDestinationId && hasSelectedDestination
            }
        )
    }

    private fun blockedAddMessage(
        current: EditCompoundUiState,
        kind: CompoundComponentKind,
        reusableId: Long
    ): String? {
        if (current.components.any { it.kind == kind.storageValue && it.reusableEntityId == reusableId }) {
            return when (kind) {
                CompoundComponentKind.INGREDIENT -> "This ingredient is already in the composition. Edit the existing row instead."
                CompoundComponentKind.COMPOUND -> "This compound is already in the composition. Edit the existing row instead."
            }
        }
        if (
            kind == CompoundComponentKind.COMPOUND &&
            reusableId in CompoundCompositionIntegrity.invalidChildCompoundIds(
                parentCompoundId = compoundId,
                candidateIds = listOf(reusableId),
                childEdges = CompoundCompositionIntegrity.childEdgesFromCompounds(observedCompounds)
            )
        ) {
            return "This compound would create a recursive composition."
        }
        return null
    }

    private fun allCatalogOptions(): List<CompoundComponentCatalogOptionUiModel> {
        return ingredientCatalogOptions + compoundCatalogOptions
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }
}
