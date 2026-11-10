package io.github.mateovich.allergotracker.features.ingestion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateovich.allergotracker.features.compounds.domain.ObserveReusableCompoundsUseCase
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionDefaultsUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CreateIngestionViewModel(
    private val createIngestionDefaultsUseCase: CreateIngestionDefaultsUseCase,
    private val createIngestionEventUseCase: CreateIngestionEventUseCase,
    private val observeReusableIngredientsUseCase: ObserveReusableIngredientsUseCase,
    private val observeReusableCompoundsUseCase: ObserveReusableCompoundsUseCase,
    private val createReusableIngredientUseCase: CreateReusableIngredientUseCase
) : ViewModel() {
    private val zoneId = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val localTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val mutableState = MutableStateFlow(buildInitialState())
    val state: StateFlow<CreateIngestionUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CreateIngestionUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    // Debounced search query channel: UI writes here immediately; debounced collector updates filter
    private val searchQueryChannel = MutableStateFlow("")

    init {
        viewModelScope.launch {
            observeReusableIngredientsUseCase().collect { ingredients ->
                updateState {
                    it.copy(
                        availableIngredients = ingredients.map { ingredient ->
                            ReusableEntityOptionUiModel(
                                id = ingredient.id,
                                name = ingredient.name,
                                updatedAtEpochMs = ingredient.updatedAtEpochMs,
                                isProvisional = ingredient.isProvisional
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
        // 300ms debounce: update filteredReusableEntities after typing settles
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            searchQueryChannel
                .debounce(SEARCH_DEBOUNCE_MS)
                .collect { query ->
                    updateState { it.copy(reusableSearchQuery = query).withFilteredReusableEntities() }
                }
        }
    }

    fun onIntent(intent: CreateIngestionUiIntent) {
        when (intent) {
            is CreateIngestionUiIntent.UpdateReusableSearchQuery -> {
                val query = intent.value
                // Update query display and itemName immediately (no debounce on visible text)
                updateState { current ->
                    val base = current.copy(reusableSearchQuery = query, formMessage = "")
                    if (current.selectedReusableEntityId == null) {
                        // No entity selected: sync itemName with whatever is typed (blank → clear)
                        base.copy(
                            itemName = query.trim(),
                            itemNameError = null,
                            canSave = canSave(query.trim(), current.selectedQuantityLevel)
                        )
                    } else {
                        base
                    }
                }
                // Debounced: update filtered list after 300ms
                searchQueryChannel.value = query
            }

            is CreateIngestionUiIntent.UpdateItemName -> updateState {
                it.copy(
                    itemName = intent.value,
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

            is CreateIngestionUiIntent.SelectDate -> updateState {
                it.withOccurredAt(composeOccurredAt(intent.value, it.selectedTime))
            }

            is CreateIngestionUiIntent.SelectTime -> updateState {
                it.withOccurredAt(composeOccurredAt(it.selectedDate, intent.value))
            }

            is CreateIngestionUiIntent.SelectTimePreset -> {
                val nextOccurredAt = createIngestionDefaultsUseCase().occurredAtEpochMs - intent.preset.offsetMillis
                updateState {
                    it.withOccurredAt(nextOccurredAt, intent.preset)
                }
            }

            is CreateIngestionUiIntent.ApplyReusableSelection -> updateState {
                it.withReusableSelection(intent.result)
            }

            is CreateIngestionUiIntent.SelectReusableEntity -> updateState { it.withReusableSelection(intent.result) }

            is CreateIngestionUiIntent.CreateInlineIngredient -> createInlineIngredient(intent.name)
            is CreateIngestionUiIntent.CreateProvisionalIngredient -> createInlineIngredient(intent.name, isProvisional = true)

            is CreateIngestionUiIntent.UpdateInlineIngredientName -> updateState {
                // Only update name; caller is responsible for opening the dialog via ShowInlineIngredientDialog
                it.copy(
                    inlineIngredientName = intent.value,
                    inlineIngredientError = null
                )
            }

            is CreateIngestionUiIntent.ShowInlineIngredientDialog -> updateState {
                it.copy(
                    showInlineIngredientDialog = true,
                    inlineIngredientName = intent.prefillName,
                    inlineIngredientError = null
                )
            }

            CreateIngestionUiIntent.CancelInlineIngredientCreation -> updateState {
                it.copy(
                    showInlineIngredientDialog = false,
                    inlineIngredientName = "",
                    inlineIngredientError = null,
                    isCreatingInlineIngredient = false
                )
            }

            CreateIngestionUiIntent.CreateNewCompound -> {
                effectChannel.trySend(
                    CreateIngestionUiEffect.NavigateToCreateCompound(
                        prefillName = mutableState.value.reusableSearchQuery.trim()
                    )
                )
            }

            CreateIngestionUiIntent.EditSelectedReusableEntity -> {
                val selectedKind = mutableState.value.selectedReusableEntityKind ?: return
                val selectedId = mutableState.value.selectedReusableEntityId ?: return
                effectChannel.trySend(CreateIngestionUiEffect.NavigateToEditReusableEntity(selectedKind, selectedId))
            }

            CreateIngestionUiIntent.ClearRetiredReusableSelection -> updateState {
                // Keep itemName in reusableSearchQuery so the unified field shows the name for review.
                it.copy(
                    selectedReusableEntityId = null,
                    selectedReusableEntityKind = null,
                    reusableSearchQuery = it.itemName,
                    formMessage = "Saved reusable item was retired. Review the text value before saving."
                ).withFilteredReusableEntities()
            }

            CreateIngestionUiIntent.Save -> save()
        }
    }

    private fun createInlineIngredient(name: String, isProvisional: Boolean = false) {
        val currentState = mutableState.value
        if (currentState.isCreatingInlineIngredient) return

        updateState { it.copy(isCreatingInlineIngredient = true, inlineIngredientError = null) }
        viewModelScope.launch {
            when (
                val result = createReusableIngredientUseCase(
                    CreateReusableIngredientCommand(name = name, notes = null, isProvisional = isProvisional)
                )
            ) {
                is AppResult.Success -> {
                    val selection = IngestionReusableSelectionResult(
                        id = result.value.id,
                        name = result.value.name,
                        kind = ReusableEntityKind.INGREDIENT
                    )
                    updateState {
                        it.copy(
                            isCreatingInlineIngredient = false,
                            showInlineIngredientDialog = false,
                            inlineIngredientName = "",
                            inlineIngredientError = null
                        ).withReusableSelection(selection)
                    }
                }
                is AppResult.Error -> {
                    updateState {
                        it.copy(
                            isCreatingInlineIngredient = false,
                            inlineIngredientError = result.error.fieldError("name") ?: result.error.message,
                            formMessage = if (isProvisional) result.error.message else it.formMessage
                        )
                    }
                }
            }
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (currentState.isSaving) return

        // If no reusable entity is selected and query text is non-blank with no exact match,
        // create the ingredient inline first, then save on success.
        if (currentState.selectedReusableEntityId == null &&
            currentState.reusableSearchQuery.isNotBlank() &&
            !currentState.hasExactMatchInAvailable()
        ) {
            saveWithAutoCreateIngredient(currentState.reusableSearchQuery.trim())
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
        dispatchSave(currentState)
    }

    private fun saveWithAutoCreateIngredient(name: String) {
        if (mutableState.value.isCreatingInlineIngredient) return
        updateState { it.copy(isCreatingInlineIngredient = true, inlineIngredientError = null, isSaving = true) }
        viewModelScope.launch {
            when (
                val result = createReusableIngredientUseCase(
                    CreateReusableIngredientCommand(name = name, notes = null)
                )
            ) {
                is AppResult.Success -> {
                    val selection = IngestionReusableSelectionResult(
                        id = result.value.id,
                        name = result.value.name,
                        kind = ReusableEntityKind.INGREDIENT
                    )
                    val stateWithSelection = mutableState.value
                        .copy(isCreatingInlineIngredient = false)
                        .withReusableSelection(selection)
                    mutableState.value = stateWithSelection
                    dispatchSave(stateWithSelection)
                }
                is AppResult.Error -> {
                    updateState {
                        it.copy(
                            isCreatingInlineIngredient = false,
                            isSaving = false,
                            inlineIngredientError = result.error.fieldError("name") ?: result.error.message,
                            formMessage = result.error.fieldError("name") ?: result.error.message
                        )
                    }
                }
            }
        }
    }

    private fun dispatchSave(currentState: CreateIngestionUiState) {
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
            quantityOptions = IngestionQuantityOption.entries.map { option ->
                QuantityOptionUiModel(level = option.level, label = option.label)
            }
        ).withOccurredAt(defaults.occurredAtEpochMs, IngestionTimePreset.NOW)
            .withFilteredReusableEntities()
    }

    private fun composeOccurredAt(date: LocalDate, time: LocalTime): Long {
        return LocalDateTime.of(date, time.withSecond(0).withNano(0))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun formatSelectedDateLabel(value: LocalDate): String = dateFormatter.format(value)

    fun formatSelectedTimeLabel(value: LocalTime): String = value.withSecond(0).withNano(0).format(localTimeFormatter)

    private fun CreateIngestionUiState.withOccurredAt(
        epochMillis: Long,
        selectedPreset: IngestionTimePreset? = null
    ): CreateIngestionUiState {
        val occurredAt = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        return copy(
            occurredAtEpochMs = epochMillis,
            occurredAtLabel = timeFormatter.format(occurredAt),
            selectedDate = occurredAt.toLocalDate(),
            selectedTime = occurredAt.toLocalTime().withSecond(0).withNano(0),
            selectedTimePreset = selectedPreset,
            occurredAtError = null,
            formMessage = ""
        )
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
                        supportingLabel = if (ingredient.isProvisional) "Provisional compound" else "Ingredient",
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

    // True when the available catalog (not just filtered) contains a name that normalizes to the query.
    // Used by Save to decide whether to auto-create or save free-text.
    private fun CreateIngestionUiState.hasExactMatchInAvailable(): Boolean {
        val normalizedQuery = reusableSearchQuery.trim().replace(Regex("\\s+"), " ").lowercase()
        if (normalizedQuery.isBlank()) return false
        val allEntities = availableIngredients.map { it.name } + availableCompounds.map { it.name }
        return allEntities.any { it.trim().replace(Regex("\\s+"), " ").lowercase() == normalizedQuery }
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
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
