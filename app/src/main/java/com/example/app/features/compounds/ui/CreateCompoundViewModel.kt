package com.example.app.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundComponentKind
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
    private val mutableState = MutableStateFlow(CreateCompoundUiState())
    val state: StateFlow<CreateCompoundUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CreateCompoundUiEffect>(capacity = Channel.BUFFERED)
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
                )
            }
        }
    }

    fun onIntent(intent: CreateCompoundUiIntent) {
        when (intent) {
            is CreateCompoundUiIntent.UpdateName -> updateState {
                val nextState = it.copy(name = intent.value, nameError = null, formMessage = "")
                nextState.withDerivedFields()
            }

            is CreateCompoundUiIntent.UpdateNotes -> updateState { it.copy(notes = intent.value) }
            is CreateCompoundUiIntent.UpdateComponentSearchQuery -> updateState {
                it.copy(componentSearchQuery = intent.value, formMessage = "").withFilteredComponents()
            }

            is CreateCompoundUiIntent.AddIngredientComponent -> addComponent(CompoundComponentKind.INGREDIENT, intent.reusableId)
            is CreateCompoundUiIntent.AddCompoundComponent -> addComponent(CompoundComponentKind.COMPOUND, intent.reusableId)
            is CreateCompoundUiIntent.UpdateComponentProportion -> updateComponentProportion(intent.index, intent.value)
            is CreateCompoundUiIntent.RemoveComponent -> removeComponent(intent.index)
            CreateCompoundUiIntent.Save -> save()
        }
    }

    private fun addComponent(kind: CompoundComponentKind, reusableId: Long) {
        updateState { current ->
            val option = current.availableComponents.firstOrNull {
                it.kind == kind.storageValue && it.id == reusableId
            } ?: return@updateState current
            current.copy(
                components = current.components + CreateCompoundComponentRowUiModel(
                    kind = kind.storageValue,
                    reusableEntityId = reusableId,
                    name = option.name
                ),
                componentsError = null,
                formMessage = ""
            ).withDerivedFields()
        }
    }

    private fun updateComponentProportion(index: Int, value: String) {
        updateState { current ->
            if (index !in current.components.indices) return@updateState current
            val sanitizedValue = value.filter { it.isDigit() || it == '.' }
            current.copy(
                components = current.components.mapIndexed { componentIndex, component ->
                    if (componentIndex == index) {
                        component.copy(proportionPercent = sanitizedValue, proportionError = null, targetError = null)
                    } else {
                        component
                    }
                },
                totalError = null,
                formMessage = ""
            ).withDerivedFields()
        }
    }

    private fun removeComponent(index: Int) {
        updateState { current ->
            if (index !in current.components.indices) return@updateState current
            current.copy(
                components = current.components.filterIndexed { componentIndex, _ -> componentIndex != index },
                formMessage = ""
            ).withDerivedFields()
        }
    }

    private fun save() {
        val currentState = mutableState.value
        updateState { it.copy(isSaving = true, formMessage = "") }
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
                                proportionBasisPoints = component.proportionPercent.toBasisPointsOrNull()
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
                            formMessage = result.error.message
                        ).withDerivedFields()
                    }
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
            current.copy(
                availableComponents = (ingredients + compounds)
                    .sortedWith(
                        compareByDescending<CompoundComponentCatalogOptionUiModel> { it.updatedAtEpochMs }
                            .thenBy { it.name.lowercase() }
                            .thenBy { it.kind }
                            .thenBy { it.id }
                    )
            ).withFilteredComponents()
        }
    }

    private fun updateState(transform: (CreateCompoundUiState) -> CreateCompoundUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun CreateCompoundUiState.withDerivedFields(): CreateCompoundUiState {
        val totalBasisPoints = components.sumOf { it.proportionPercent.toBasisPointsOrNull() ?: 0 }
        return copy(
            totalPercentLabel = "${totalBasisPoints.toPercentLabel()} total",
            canSave = name.isNotBlank() && components.isNotEmpty() && totalBasisPoints == 10_000
        ).withFilteredComponents()
    }

    private fun CreateCompoundUiState.withFilteredComponents(): CreateCompoundUiState {
        val normalizedQuery = componentSearchQuery.trim().lowercase()
        return copy(
            filteredComponents = availableComponents
                .filter { option ->
                    normalizedQuery.isBlank() || option.name.lowercase().contains(normalizedQuery)
                }
                .take(if (normalizedQuery.isBlank()) MAX_VISIBLE_COMPONENT_OPTIONS else Int.MAX_VALUE)
        )
    }

    private fun String.toBasisPointsOrNull(): Int? {
        if (isBlank()) return null
        val percent = toBigDecimalOrNull() ?: return null
        val basisPoints = percent.multiply(java.math.BigDecimal(100)).toInt()
        return basisPoints.takeIf { it in 1..10_000 }
    }

    private fun Int.toPercentLabel(): String {
        val whole = this / 100
        val fractional = this % 100
        return if (fractional == 0) {
            whole.toString() + "%"
        } else {
            "%d.%02d%%".format(whole, fractional)
        }
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
