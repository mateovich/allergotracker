package com.example.app.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundComponentKind
import com.example.app.features.compounds.domain.GetReusableCompoundRetirementImpactUseCase
import com.example.app.features.compounds.domain.GetReusableCompoundUseCase
import com.example.app.features.compounds.domain.ObserveReusableCompoundsUseCase
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
    private val getReusableCompoundRetirementImpactUseCase: GetReusableCompoundRetirementImpactUseCase,
    private val retireReusableCompoundUseCase: RetireReusableCompoundUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditCompoundUiState())
    val state: StateFlow<EditCompoundUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EditCompoundUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeReusableIngredients().collect { ingredients ->
                updateCatalogOptions(
                    ingredientOptions = ingredients.map { ingredient ->
                        CompoundComponentCatalogOptionUiModel(
                            id = ingredient.id,
                            name = ingredient.name,
                            kind = CompoundComponentKind.INGREDIENT.storageValue,
                            supportingLabel = "Ingredient",
                            updatedAtEpochMs = ingredient.updatedAtEpochMs
                        )
                    }
                )
            }
        }
        viewModelScope.launch {
            observeReusableCompounds().collect { compounds ->
                updateCatalogOptions(
                    compoundOptions = compounds
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
                )
            }
        }
    }

    fun onIntent(intent: EditCompoundUiIntent) {
        when (intent) {
            EditCompoundUiIntent.Load -> loadIfNeeded()
            is EditCompoundUiIntent.UpdateName -> updateState {
                val hasName = intent.value.trim().isNotBlank()
                it.copy(
                    name = intent.value,
                    nameError = if (hasName) null else "Enter a compound name.",
                    formMessage = ""
                ).withDerivedFields()
            }

            is EditCompoundUiIntent.UpdateNotes -> updateState { it.copy(notes = intent.value) }
            is EditCompoundUiIntent.UpdateComponentSearchQuery -> updateState {
                it.copy(componentSearchQuery = intent.value, formMessage = "").withDerivedFields()
            }

            is EditCompoundUiIntent.AddIngredientComponent -> addComponent(CompoundComponentKind.INGREDIENT, intent.reusableId)
            is EditCompoundUiIntent.AddCompoundComponent -> addComponent(CompoundComponentKind.COMPOUND, intent.reusableId)
            is EditCompoundUiIntent.ApplyPreset -> applyPreset(intent.preset)
            is EditCompoundUiIntent.UpdateComponentProportion -> updateComponentProportion(intent.index, intent.value)
            is EditCompoundUiIntent.AdjustComponentProportion -> applyAdjustedProportion(intent.index, intent.basisPoints)
            is EditCompoundUiIntent.RemoveComponent -> removeComponent(intent.index)
            EditCompoundUiIntent.Save -> save()
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
        updateState { it.copy(isLoading = true, formMessage = "") }
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
                        formMessage = ""
                    ).withDerivedFields()
                }

                is AppResult.Error -> updateState { it.copy(isLoading = false, formMessage = result.error.message) }
            }
        }
    }

    private fun addComponent(kind: CompoundComponentKind, reusableId: Long) {
        updateState { current ->
            val option = current.availableComponents.firstOrNull {
                it.kind == kind.storageValue && it.id == reusableId
            } ?: return@updateState current
            current.copy(
                components = current.components + CompoundComponentRowUiModel(
                    kind = kind.storageValue,
                    reusableEntityId = reusableId,
                    name = option.name
                ),
                selectedPreset = null,
                componentsError = null,
                formMessage = ""
            ).withDerivedFields()
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
                    formMessage = ""
                ).withDerivedFields()
            } else {
                current.applyAdjustedProportion(index, requestedBasisPoints)
            }
        }
    }

    private fun applyPreset(preset: CompoundCompositionPreset) {
        updateState { current ->
            val allocation = CompoundPresetAllocationEngine.allocate(
                preset = preset,
                componentCount = current.components.size
            ) ?: return@updateState current.copy(
                selectedPreset = null,
                componentsError = "Add no more than 10000 components before applying a preset.",
                formMessage = "Couldn't apply ${preset.label.lowercase()} preset to the current composition.",
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
                return@updateState current.copy(formMessage = "Set the component to 0% before removing it.")
            }
            current.copy(
                components = current.components.filterIndexed { componentIndex, _ -> componentIndex != index },
                touchedComponentOrder = current.touchedComponentOrder
                    .filter { it != index }
                    .map { touchedIndex -> if (touchedIndex > index) touchedIndex - 1 else touchedIndex },
                selectedPreset = null,
                formMessage = ""
            ).withDerivedFields()
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (!currentState.canSave || currentState.isLoading || currentState.isSaving) {
            return
        }
        updateState { it.copy(isSaving = true, formMessage = "") }
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
                        formMessage = result.error.message
                    ).withDerivedFields()
                }
            }
        }
    }

    private fun loadRetirementPreview() {
        val currentState = mutableState.value
        if (currentState.isLoading || currentState.isSaving || currentState.isRetiring) return
        updateState { it.copy(formMessage = "") }
        viewModelScope.launch {
            when (val result = getReusableCompoundRetirementImpactUseCase(compoundId)) {
                is AppResult.Success -> updateState {
                    it.copy(retirementImpact = result.value, isRetirementDialogVisible = true, formMessage = "")
                }

                is AppResult.Error -> updateState { it.copy(formMessage = result.error.message) }
            }
        }
    }

    private fun retire() {
        val currentState = mutableState.value
        val impact = currentState.retirementImpact ?: return
        if (currentState.isLoading || currentState.isSaving || currentState.isRetiring || impact.isBlocked) return
        updateState { it.copy(isRetiring = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = retireReusableCompoundUseCase(compoundId)) {
                is AppResult.Success -> {
                    updateState {
                        it.copy(
                            isRetiring = false,
                            isRetirementDialogVisible = false,
                            retirementImpact = result.value,
                            formMessage = ""
                        )
                    }
                    effectChannel.trySend(EditCompoundUiEffect.NavigateBackAfterRetirement)
                }

                is AppResult.Error -> updateState {
                    it.copy(
                        isRetiring = false,
                        isRetirementDialogVisible = true,
                        formMessage = result.error.message
                    )
                }
            }
        }
    }

    private fun updateCatalogOptions(
        ingredientOptions: List<CompoundComponentCatalogOptionUiModel>? = null,
        compoundOptions: List<CompoundComponentCatalogOptionUiModel>? = null
    ) {
        updateState { current ->
            val ingredients = ingredientOptions ?: current.availableComponents.filter {
                it.kind == CompoundComponentKind.INGREDIENT.storageValue
            }
            val compounds = compoundOptions ?: current.availableComponents.filter {
                it.kind == CompoundComponentKind.COMPOUND.storageValue
            }
            val availableComponents = (ingredients + compounds)
                .sortedWith(
                    compareByDescending<CompoundComponentCatalogOptionUiModel> { it.updatedAtEpochMs }
                        .thenBy { it.name.lowercase() }
                        .thenBy { it.kind }
                        .thenBy { it.id }
                )
            current.copy(
                availableComponents = availableComponents,
                components = current.components.map { component ->
                    component.copy(name = component.resolveName(availableComponents))
                }
            ).withDerivedFields()
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
            formMessage = ""
        ).withDerivedFields()
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
            formMessage = "Applied ${allocation.preset.label.lowercase()} preset. ${COMPOUND_TOTAL_BASIS_POINTS.toPercentLabel()} total remains valid."
        ).withDerivedFields()
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

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }
}
