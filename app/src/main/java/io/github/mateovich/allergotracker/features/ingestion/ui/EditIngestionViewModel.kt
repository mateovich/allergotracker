package io.github.mateovich.allergotracker.features.ingestion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.compounds.domain.ObserveReusableCompoundsUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.DeleteIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.GetIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventCommand
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventUseCase
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class EditIngestionViewModel(
    private val recordId: Long,
    private val getIngestionEventUseCase: GetIngestionEventUseCase,
    private val deleteIngestionEventUseCase: DeleteIngestionEventUseCase,
    private val updateIngestionEventUseCase: UpdateIngestionEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider,
    private val reusableEntityExists: suspend (ReusableEntityKind, Long) -> Boolean = { _, _ -> true },
    private val observeReusableIngredientsUseCase: ObserveReusableIngredientsUseCase? = null,
    private val observeReusableCompoundsUseCase: ObserveReusableCompoundsUseCase? = null,
    private val createReusableIngredientUseCase: CreateReusableIngredientUseCase? = null
) : ViewModel() {
    private val zoneId = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val localTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val mutableState = MutableStateFlow(
        EditIngestionUiState(
            quantityOptions = IngestionQuantityOption.entries.map { QuantityOptionUiModel(it.level, it.label) }
        )
    )
    val state: StateFlow<EditIngestionUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EditIngestionUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    private var hasLoadedRecord = false
    private var isLoadInFlight = false
    private var pendingReusableSelection: IngestionReusableSelectionResult? = null

    init {
        observeReusableIngredientsUseCase?.let { observeIngredients ->
            viewModelScope.launch {
                observeIngredients().collect { ingredients ->
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
        }
        observeReusableCompoundsUseCase?.let { observeCompounds ->
            viewModelScope.launch {
                observeCompounds().collect { compounds ->
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
    }

    fun onIntent(intent: EditIngestionUiIntent) {
        when (intent) {
            EditIngestionUiIntent.Load -> loadIfNeeded()
            is EditIngestionUiIntent.UpdateReusableSearchQuery -> {
                val query = intent.value
                updateState { current ->
                    // Editing the field always drops the current link; a saved item must be picked again.
                    current.copy(
                        reusableSearchQuery = query,
                        itemName = query.trim(),
                        selectedReusableEntityKind = null,
                        selectedReusableEntityId = null,
                        itemNameError = null,
                        formMessage = "",
                        canSave = canSave(query.trim(), current.selectedQuantityLevel)
                    ).withFilteredReusableEntities()
                }
            }
            is EditIngestionUiIntent.UpdateItemName -> updateState { it.withItemNameUpdate(intent.value) }
            is EditIngestionUiIntent.UpdateNotes -> updateState { it.copy(notes = intent.value) }
            is EditIngestionUiIntent.SelectQuantityLevel -> updateState {
                it.copy(
                    selectedQuantityLevel = intent.level,
                    quantityError = null,
                    formMessage = "",
                    canSave = canSave(it.itemName, intent.level)
                )
            }
            is EditIngestionUiIntent.SelectDate -> updateState {
                it.withOccurredAt(composeOccurredAt(intent.value, it.selectedTime))
            }
            is EditIngestionUiIntent.SelectTime -> updateState {
                it.withOccurredAt(composeOccurredAt(it.selectedDate, intent.value))
            }
            is EditIngestionUiIntent.SelectTimePreset -> {
                val nextOccurredAt = currentTimeProvider.nowEpochMillis() - intent.preset.offsetMillis
                updateState {
                    it.withOccurredAt(nextOccurredAt, intent.preset)
                }
            }
            is EditIngestionUiIntent.ApplyReusableSelection -> applyReusableSelection(intent.result)
            is EditIngestionUiIntent.SelectReusableEntity -> updateState { it.withReusableSelection(intent.result) }
            is EditIngestionUiIntent.CreateInlineIngredient -> createInlineIngredient(intent.name)
            is EditIngestionUiIntent.UpdateInlineIngredientName -> updateState {
                it.copy(inlineIngredientName = intent.value, inlineIngredientError = null)
            }
            is EditIngestionUiIntent.ShowInlineIngredientDialog -> updateState {
                it.copy(
                    showInlineIngredientDialog = true,
                    inlineIngredientName = intent.prefillName,
                    inlineIngredientError = null
                )
            }
            EditIngestionUiIntent.CancelInlineIngredientCreation -> updateState {
                it.copy(
                    showInlineIngredientDialog = false,
                    inlineIngredientName = "",
                    inlineIngredientError = null,
                    isCreatingInlineIngredient = false
                )
            }
            EditIngestionUiIntent.ClearRetiredReusableSelection -> updateState {
                it.copy(
                    selectedReusableEntityKind = null,
                    selectedReusableEntityId = null,
                    reusableSearchQuery = it.itemName,
                    formMessage = RETIRED_REUSABLE_MESSAGE,
                    canSave = canSave(it.itemName, it.selectedQuantityLevel)
                ).withFilteredReusableEntities()
            }
            EditIngestionUiIntent.ShowDeleteConfirmation -> showDeleteConfirmation()
            EditIngestionUiIntent.DismissDeleteConfirmation -> updateState { it.copy(isDeleteConfirmationVisible = false) }
            EditIngestionUiIntent.ConfirmDelete -> delete()
            EditIngestionUiIntent.Save -> save()
        }
    }

    private fun createInlineIngredient(name: String) {
        val useCase = createReusableIngredientUseCase ?: return
        val currentState = mutableState.value
        if (currentState.isCreatingInlineIngredient) return

        updateState { it.copy(isCreatingInlineIngredient = true, inlineIngredientError = null) }
        viewModelScope.launch {
            when (
                val result = useCase(CreateReusableIngredientCommand(name = name, notes = null))
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
                            inlineIngredientError = result.error.fieldError("name") ?: result.error.message
                        )
                    }
                }
            }
        }
    }

    private fun loadIfNeeded() {
        if (hasLoadedRecord || isLoadInFlight) return
        isLoadInFlight = true
        updateState { it.copy(isLoading = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = getIngestionEventUseCase(recordId)) {
                is AppResult.Success -> {
                    val event = result.value
                    val hasCompleteReusableSelection = event.reusableEntityKind != null && event.reusableEntityId != null
                    val hasPartialReusableSelection = (event.reusableEntityKind != null) != (event.reusableEntityId != null)
                    val hasRetiredReusableSelection = hasCompleteReusableSelection &&
                        !reusableEntityExists(event.reusableEntityKind, event.reusableEntityId)
                    val shouldClearReusableSelection = hasPartialReusableSelection || hasRetiredReusableSelection
                    updateState {
                        val entityKind = if (shouldClearReusableSelection) null else event.reusableEntityKind
                        val entityId = if (shouldClearReusableSelection) null else event.reusableEntityId
                        val loadedState = it.copy(
                            itemName = event.itemName,
                            deleteRecordLabel = event.itemName,
                            notes = event.notes.orEmpty(),
                            selectedQuantityLevel = event.quantityLevel,
                            selectedReusableEntityKind = entityKind,
                            selectedReusableEntityId = entityId,
                            reusableSearchQuery = event.itemName,
                            isLoading = false,
                            canSave = canSave(event.itemName, event.quantityLevel),
                            canDelete = event.itemName.isNotBlank(),
                            formMessage = when {
                                shouldClearReusableSelection -> RETIRED_REUSABLE_MESSAGE
                                !hasCompleteReusableSelection -> UNLINKED_REUSABLE_MESSAGE
                                else -> it.formMessage
                            }
                        ).withOccurredAt(event.occurredAtEpochMs, selectedPreset = null, clearMessages = false)
                            .withFilteredReusableEntities()
                        pendingReusableSelection?.let { selection ->
                            pendingReusableSelection = null
                            loadedState.withReusableSelection(selection)
                        } ?: loadedState
                    }
                    hasLoadedRecord = true
                    isLoadInFlight = false
                }
                is AppResult.Error -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            formMessage = result.error.message
                        )
                    }
                    isLoadInFlight = false
                }
            }
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (!currentState.canSave || currentState.isSaving || currentState.isDeleting || currentState.isLoading) return
        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = updateIngestionEventUseCase(
                    UpdateIngestionEventCommand(
                        id = recordId,
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
                    effectChannel.trySend(EditIngestionUiEffect.NavigateToRecords)
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

    private fun delete() {
        val currentState = mutableState.value
        if (
            !currentState.isDeleteConfirmationVisible ||
            currentState.isDeleting ||
            currentState.isSaving ||
            currentState.isLoading
        ) return
        updateState {
            it.copy(
                isDeleting = true,
                isDeleteConfirmationVisible = false,
                formMessage = ""
            )
        }
        viewModelScope.launch {
            when (val result = deleteIngestionEventUseCase(recordId)) {
                is AppResult.Success -> {
                    updateState { it.copy(isDeleting = false) }
                    effectChannel.trySend(EditIngestionUiEffect.NavigateToRecords)
                }
                is AppResult.Error -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            formMessage = result.error.message,
                            canSave = canSave(it.itemName, it.selectedQuantityLevel)
                        )
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmation() {
        val currentState = mutableState.value
        if (currentState.isLoading || currentState.isSaving || currentState.isDeleting || !currentState.canDelete) return
        updateState { it.copy(isDeleteConfirmationVisible = true, formMessage = "") }
    }

    private fun composeOccurredAt(date: LocalDate, time: LocalTime): Long {
        return LocalDateTime.of(date, time.withSecond(0).withNano(0))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun formatSelectedDateLabel(value: LocalDate): String = dateFormatter.format(value)

    fun formatSelectedTimeLabel(value: LocalTime): String = value.withSecond(0).withNano(0).format(localTimeFormatter)

    private fun EditIngestionUiState.withOccurredAt(
        epochMillis: Long,
        selectedPreset: IngestionTimePreset? = null,
        clearMessages: Boolean = true
    ): EditIngestionUiState {
        val occurredAt = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        return copy(
            occurredAtEpochMs = epochMillis,
            occurredAtLabel = timeFormatter.format(occurredAt),
            selectedDate = occurredAt.toLocalDate(),
            selectedTime = occurredAt.toLocalTime().withSecond(0).withNano(0),
            selectedTimePreset = selectedPreset,
            occurredAtError = if (clearMessages) null else occurredAtError,
            formMessage = if (clearMessages) "" else formMessage
        )
    }

    private fun updateState(transform: (EditIngestionUiState) -> EditIngestionUiState) {
        // Free-text entries are never saveable: a saved reusable ingredient/compound must be linked.
        mutableState.value = transform(mutableState.value).let {
            it.copy(canSave = it.canSave && it.selectedReusableEntityId != null)
        }
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

    private fun EditIngestionUiState.withReusableSelection(result: IngestionReusableSelectionResult): EditIngestionUiState {
        return copy(
            itemName = result.name,
            selectedReusableEntityKind = result.kind,
            selectedReusableEntityId = result.id,
            reusableSearchQuery = result.name,
            itemNameError = null,
            formMessage = "",
            canSave = canSave(result.name, selectedQuantityLevel)
        ).withFilteredReusableEntities()
    }

    private fun applyReusableSelection(result: IngestionReusableSelectionResult) {
        if (!hasLoadedRecord) {
            pendingReusableSelection = result
            return
        }
        updateState { it.withReusableSelection(result) }
    }

    private fun EditIngestionUiState.withItemNameUpdate(value: String): EditIngestionUiState {
        return copy(
            itemName = value,
            selectedReusableEntityKind = null,
            selectedReusableEntityId = null,
            reusableSearchQuery = value,
            itemNameError = null,
            formMessage = "",
            canSave = canSave(value, selectedQuantityLevel)
        ).withFilteredReusableEntities()
    }

    private fun EditIngestionUiState.withFilteredReusableEntities(): EditIngestionUiState {
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
        const val RETIRED_REUSABLE_MESSAGE = "Saved reusable item was retired. Select another saved item before saving."
        const val UNLINKED_REUSABLE_MESSAGE = "Select a saved ingredient or compound to save changes."
    }
}
