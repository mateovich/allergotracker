package com.example.app.features.ingestion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.features.compounds.domain.ObserveReusableCompoundsUseCase
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CreateIngestionDefaultsUseCase
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CreateIngestionEventUseCase
import com.example.app.features.ingestion.domain.IngestionQuantityOption
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingredients.domain.ObserveReusableIngredientsUseCase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CreateIngestionViewModel(
    private val createIngestionDefaultsUseCase: CreateIngestionDefaultsUseCase,
    private val createIngestionEventUseCase: CreateIngestionEventUseCase,
    private val observeReusableIngredientsUseCase: ObserveReusableIngredientsUseCase,
    private val observeReusableCompoundsUseCase: ObserveReusableCompoundsUseCase
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val mutableState = MutableStateFlow(buildInitialState())
    val state: StateFlow<CreateIngestionUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CreateIngestionUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeReusableIngredientsUseCase().collect { ingredients ->
                updateState {
                    it.copy(
                        availableIngredients = ingredients.map { ingredient ->
                            ReusableEntityOptionUiModel(
                                id = ingredient.id,
                                name = ingredient.name,
                                updatedAtEpochMs = ingredient.updatedAtEpochMs
                            )
                        }
                    ).withFilteredReusableEntities()
                }
            }
        }
        viewModelScope.launch {
            observeReusableCompoundsUseCase().collect { compounds ->
                updateState {
                    it.copy(
                        availableCompounds = compounds.map { compound ->
                            ReusableEntityOptionUiModel(
                                id = compound.id,
                                name = compound.name,
                                updatedAtEpochMs = compound.updatedAtEpochMs
                            )
                        }
                    ).withFilteredReusableEntities()
                }
            }
        }
    }

    fun onIntent(intent: CreateIngestionUiIntent) {
        when (intent) {
            is CreateIngestionUiIntent.UpdateReusableSearchQuery -> updateState {
                it.copy(reusableSearchQuery = intent.value, formMessage = "").withFilteredReusableEntities()
            }

            is CreateIngestionUiIntent.UpdateItemName -> updateState {
                it.copy(
                    itemName = intent.value,
                    selectedReusableEntitySummary = null,
                    selectedReusableEntityId = null,
                    selectedReusableEntityKind = null,
                    itemNameError = null,
                    formMessage = "",
                    canSave = canSave(intent.value, it.selectedQuantityLevel)
                )
            }

            is CreateIngestionUiIntent.UpdateNotes -> updateState {
                it.copy(notes = intent.value)
            }

            is CreateIngestionUiIntent.SelectQuantityLevel -> updateState {
                it.copy(
                    selectedQuantityLevel = intent.level,
                    quantityError = null,
                    formMessage = "",
                    canSave = canSave(it.itemName, intent.level)
                )
            }

            is CreateIngestionUiIntent.SelectTimePreset -> {
                val nextOccurredAt = createIngestionDefaultsUseCase().occurredAtEpochMs - intent.preset.offsetMillis
                updateState {
                    it.copy(
                        selectedTimePreset = intent.preset,
                        occurredAtEpochMs = nextOccurredAt,
                        occurredAtLabel = formatTime(nextOccurredAt),
                        occurredAtError = null,
                        formMessage = ""
                    )
                }
            }

            is CreateIngestionUiIntent.ApplyReusableSelection -> updateState {
                it.withReusableSelection(intent.result)
            }

            is CreateIngestionUiIntent.SelectReusableEntity -> updateState { it.withReusableSelection(intent.result) }

            CreateIngestionUiIntent.CreateNewIngredient -> {
                effectChannel.trySend(CreateIngestionUiEffect.NavigateToCreateIngredient)
            }

            CreateIngestionUiIntent.CreateNewCompound -> {
                effectChannel.trySend(CreateIngestionUiEffect.NavigateToCreateCompound)
            }

            CreateIngestionUiIntent.EditSelectedReusableEntity -> {
                val selectedKind = mutableState.value.selectedReusableEntityKind ?: return
                val selectedId = mutableState.value.selectedReusableEntityId ?: return
                effectChannel.trySend(CreateIngestionUiEffect.NavigateToEditReusableEntity(selectedKind, selectedId))
            }

            CreateIngestionUiIntent.Save -> save()
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (currentState.isSaving) {
            return
        }
        if (!currentState.canSave) {
            updateState {
                it.copy(
                    itemNameError = if (it.itemName.isBlank()) "Enter what you ingested." else it.itemNameError,
                    quantityError = if (it.selectedQuantityLevel == null) "Choose a quantity." else it.quantityError,
                    formMessage = "Please fix the highlighted fields.",
                    canSave = canSave(it.itemName, it.selectedQuantityLevel)
                )
            }
            return
        }
        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = createIngestionEventUseCase(
                    CreateIngestionEventCommand(
                        itemName = currentState.itemName,
                        quantityLevel = currentState.selectedQuantityLevel,
                        occurredAtEpochMs = currentState.occurredAtEpochMs,
                        notes = currentState.notes,
                        reusableEntityKind = currentState.selectedReusableEntityKind,
                        reusableEntityId = currentState.selectedReusableEntityId
                    )
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(CreateIngestionUiEffect.NavigateToRecords)
                }

                is AppResult.Error -> {
                    val error = result.error
                    updateState {
                        it.copy(
                            isSaving = false,
                            itemNameError = error.fieldError("itemName"),
                            quantityError = error.fieldError("quantityLevel"),
                            occurredAtError = error.fieldError("occurredAtEpochMs"),
                            formMessage = error.message,
                            canSave = canSave(it.itemName, it.selectedQuantityLevel)
                        )
                    }
                }
            }
        }
    }

    private fun buildInitialState(): CreateIngestionUiState {
        val defaults = createIngestionDefaultsUseCase()
        return CreateIngestionUiState(
            occurredAtEpochMs = defaults.occurredAtEpochMs,
            occurredAtLabel = formatTime(defaults.occurredAtEpochMs),
            quantityOptions = IngestionQuantityOption.entries.map { option ->
                QuantityOptionUiModel(level = option.level, label = option.label)
            }
        ).withFilteredReusableEntities()
    }

    private fun formatTime(epochMillis: Long): String {
        return timeFormatter.format(Instant.ofEpochMilli(epochMillis))
    }

    private fun updateState(transform: (CreateIngestionUiState) -> CreateIngestionUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun canSave(itemName: String, selectedQuantityLevel: Int?): Boolean {
        return itemName.isNotBlank() && selectedQuantityLevel != null
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }

    private fun CreateIngestionUiState.withReusableSelection(
        result: IngestionReusableSelectionResult
    ): CreateIngestionUiState {
        return copy(
            itemName = result.name,
            selectedReusableEntitySummary = "${result.kind.label} selected: ${result.name}",
            selectedReusableEntityId = result.id,
            selectedReusableEntityKind = result.kind,
            reusableSearchQuery = result.name,
            itemNameError = null,
            formMessage = "",
            canSave = canSave(result.name, selectedQuantityLevel)
        ).withFilteredReusableEntities()
    }

    private fun CreateIngestionUiState.withFilteredReusableEntities(): CreateIngestionUiState {
        val normalizedQuery = reusableSearchQuery.trim().lowercase()
        val combinedOptions = buildList {
            availableIngredients.forEach { ingredient ->
                add(
                    ReusableEntityPickerOptionUiModel(
                        id = ingredient.id,
                        name = ingredient.name,
                        kind = ReusableEntityKind.INGREDIENT,
                        supportingLabel = "Ingredient",
                        updatedAtEpochMs = ingredient.updatedAtEpochMs
                    )
                )
            }
            availableCompounds.forEach { compound ->
                add(
                    ReusableEntityPickerOptionUiModel(
                        id = compound.id,
                        name = compound.name,
                        kind = ReusableEntityKind.COMPOUND,
                        supportingLabel = "Compound",
                        updatedAtEpochMs = compound.updatedAtEpochMs
                    )
                )
            }
        }
        val filteredOptions = combinedOptions
            .filter { option ->
                normalizedQuery.isBlank() || option.name.lowercase().contains(normalizedQuery)
            }
            .sortedWith(
                compareBy<ReusableEntityPickerOptionUiModel> { option ->
                    option.relevanceBucket(normalizedQuery)
                }.thenByDescending { it.updatedAtEpochMs }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.kind.storageValue }
                    .thenBy { it.id }
            )
            .let { options ->
                if (normalizedQuery.isBlank()) options.take(MAX_RECENT_REUSABLE_SUGGESTIONS) else options
            }
        return copy(filteredReusableEntities = filteredOptions)
    }

    private fun ReusableEntityPickerOptionUiModel.relevanceBucket(normalizedQuery: String): Int {
        val normalizedName = name.lowercase()
        return when {
            normalizedQuery.isBlank() -> 0
            normalizedName == normalizedQuery -> 0
            normalizedName.startsWith(normalizedQuery) -> 1
            normalizedName.contains(normalizedQuery) -> 2
            else -> 3
        }
    }

    private companion object {
        const val MAX_RECENT_REUSABLE_SUGGESTIONS = 6
    }
}
