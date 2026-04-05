package com.example.app.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundComponentKind
import com.example.app.features.compounds.domain.CompoundCompositionIntegrity
import com.example.app.features.compounds.domain.ReusableCompound
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
import com.example.app.features.compounds.domain.CreateReusableCompoundComponentCommand
import com.example.app.features.compounds.domain.CreateReusableCompoundUseCase
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CreateCompoundViewModel(
    private val createReusableCompoundUseCase: CreateReusableCompoundUseCase,
    private val observeReusableIngredients: () -> Flow<List<com.example.app.features.ingredients.domain.ReusableIngredient>> = { emptyFlow() },
    private val observeReusableCompounds: () -> Flow<List<com.example.app.features.compounds.domain.ReusableCompound>> = { emptyFlow() }
) : ViewModel() {
    private var ingredientCatalogOptions: List<CompoundComponentCatalogOptionUiModel> = emptyList()
    private var compoundCatalogOptions: List<CompoundComponentCatalogOptionUiModel> = emptyList()
    private var observedCompounds: List<ReusableCompound> = emptyList()

    private val mutableState = MutableStateFlow(CreateCompoundUiState())
    val state: StateFlow<CreateCompoundUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CreateCompoundUiEffect>(capacity = Channel.BUFFERED)
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
                    .filter { it.compositionStatus == com.example.app.features.compounds.domain.CompoundCompositionStatus.COMPLETE }
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

    fun onIntent(intent: CreateCompoundUiIntent) {
        when (intent) {
            is CreateCompoundUiIntent.UpdateName -> updateState {
                if (it.isFormMessageSticky) {
                    it.copy(name = intent.value, nameError = null).withDerivedFields()
                } else {
                    it.copy(
                        name = intent.value,
                        nameError = null,
                        formMessage = "",
                        isFormMessageError = false,
                        isFormMessageSticky = false
                    ).withDerivedFields()
                }
            }

            is CreateCompoundUiIntent.UpdateNotes -> updateState {
                if (it.isFormMessageSticky) {
                    it.copy(notes = intent.value)
                } else {
                    it.copy(notes = intent.value, formMessage = "", isFormMessageError = false, isFormMessageSticky = false)
                }
            }
            is CreateCompoundUiIntent.UpdateComponentSearchQuery -> updateState {
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

            is CreateCompoundUiIntent.AddIngredientComponent -> addComponent(CompoundComponentKind.INGREDIENT, intent.reusableId)
            is CreateCompoundUiIntent.AddCompoundComponent -> addComponent(CompoundComponentKind.COMPOUND, intent.reusableId)
            is CreateCompoundUiIntent.ApplyPreset -> applyPreset(intent.preset)
            is CreateCompoundUiIntent.UpdateComponentProportion -> updateComponentProportion(intent.index, intent.value)
            is CreateCompoundUiIntent.AdjustComponentProportion -> applyAdjustedProportion(intent.index, intent.basisPoints)
            is CreateCompoundUiIntent.RemoveComponent -> removeComponent(intent.index)
            CreateCompoundUiIntent.Save -> save()
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

    private fun save() {
        val currentState = mutableState.value
        updateState { it.copy(isSaving = true, formMessage = "", isFormMessageError = false, isFormMessageSticky = false) }
        viewModelScope.launch {
            when (
                val result = createReusableCompoundUseCase(
                    CreateReusableCompoundCommand(
                        name = currentState.name,
                        notes = currentState.notes,
                        components = currentState.components.map { component ->
                            CreateReusableCompoundComponentCommand(
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
                        CreateCompoundUiEffect.NavigateBackWithSelection(
                            IngestionReusableSelectionResult(
                                id = result.value.id,
                                name = result.value.name,
                                kind = ReusableEntityKind.COMPOUND
                            )
                        )
                    )
                }

                is AppResult.Error -> {
                    updateState { state ->
                        state.copy(
                            isSaving = false,
                            nameError = result.error.fieldError("name"),
                            componentsError = result.error.fieldError("components"),
                            totalError = result.error.fieldError("componentsTotal"),
                            components = state.components.mapIndexed { index, component ->
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
    }

    private fun refreshCatalogOptions() {
        updateState { current ->
            current.refreshAvailableComponents()
        }
    }

    private fun updateState(transform: (CreateCompoundUiState) -> CreateCompoundUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun CreateCompoundUiState.applyAdjustedProportion(index: Int, requestedBasisPoints: Int): CreateCompoundUiState {
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

    private fun CreateCompoundUiState.applyPreset(
        allocation: CompoundPresetAllocationResult
    ): CreateCompoundUiState {
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

    private fun CreateCompoundUiState.withDerivedFields(): CreateCompoundUiState {
        val totalBasisPoints = components.sumOf { it.proportionBasisPoints ?: 0 }
        return copy(
            totalPercentLabel = "${totalBasisPoints.toPercentLabel()} total",
            canSave = name.isNotBlank() && components.hasCompleteValidComposition(),
            filteredComponents = availableComponents
                .filterByQuery(componentSearchQuery)
                .take(if (componentSearchQuery.isBlank()) MAX_VISIBLE_COMPONENT_OPTIONS else Int.MAX_VALUE)
        )
    }

    private fun CreateCompoundUiState.refreshAvailableComponents(): CreateCompoundUiState {
        val selectedKeys = components.map { it.kind to it.reusableEntityId }.toSet()
        val childEdges = CompoundCompositionIntegrity.childEdgesFromCompounds(observedCompounds)
        val selectedCompoundIds = components
            .filter { it.kind == CompoundComponentKind.COMPOUND.storageValue }
            .mapNotNull { it.reusableEntityId }
        val cycleChildCompoundIds = selectedCompoundIds.flatMapTo(linkedSetOf()) { selectedId ->
            CompoundCompositionIntegrity.invalidChildCompoundIds(
                parentCompoundId = selectedId,
                candidateIds = observedCompounds.map { it.id },
                childEdges = childEdges
            )
        }
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
        return copy(availableComponents = availableComponents).withDerivedFields()
    }

    private fun blockedAddMessage(
        current: CreateCompoundUiState,
        kind: CompoundComponentKind,
        reusableId: Long
    ): String? {
        if (current.components.any { it.kind == kind.storageValue && it.reusableEntityId == reusableId }) {
            return when (kind) {
                CompoundComponentKind.INGREDIENT -> "This ingredient is already in the composition. Edit the existing row instead."
                CompoundComponentKind.COMPOUND -> "This compound is already in the composition. Edit the existing row instead."
            }
        }
        if (kind == CompoundComponentKind.COMPOUND) {
            val childEdges = CompoundCompositionIntegrity.childEdgesFromCompounds(observedCompounds)
            val selectedCompoundIds = current.components
                .filter { it.kind == CompoundComponentKind.COMPOUND.storageValue }
                .mapNotNull { it.reusableEntityId }
            val wouldCycle = selectedCompoundIds.any { selectedId ->
                CompoundCompositionIntegrity.wouldCreateCycle(selectedId, reusableId, childEdges)
            }
            if (wouldCycle) {
                return "This compound would create a recursive composition."
            }
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

    private companion object {
        const val MAX_VISIBLE_COMPONENT_OPTIONS = 8
    }
}
