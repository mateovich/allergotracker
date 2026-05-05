package io.github.mateovich.allergotracker.features.ingestion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.ingestion.domain.DeleteIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.GetIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventCommand
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventUseCase
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
    private val reusableEntityExists: suspend (ReusableEntityKind, Long) -> Boolean = { _, _ -> true }
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

    fun onIntent(intent: EditIngestionUiIntent) {
        when (intent) {
            EditIngestionUiIntent.Load -> loadIfNeeded()
            is EditIngestionUiIntent.UpdateItemName -> updateState {
                it.copy(
                    itemName = intent.value,
                    selectedReusableEntityKind = null,
                    selectedReusableEntityId = null,
                    selectedReusableEntitySummary = null,
                    itemNameError = null,
                    formMessage = "",
                    canSave = canSave(intent.value, it.selectedQuantityLevel)
                )
            }
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
            is EditIngestionUiIntent.ApplyReusableSelection -> updateState { it.withReusableSelection(intent.result) }
            EditIngestionUiIntent.EditSelectedReusableEntity -> {
                val selectedKind = mutableState.value.selectedReusableEntityKind ?: return
                val selectedId = mutableState.value.selectedReusableEntityId ?: return
                effectChannel.trySend(EditIngestionUiEffect.NavigateToEditReusableEntity(selectedKind, selectedId))
            }
            EditIngestionUiIntent.ClearRetiredReusableSelection -> updateState {
                it.copy(
                    selectedReusableEntityKind = null,
                    selectedReusableEntityId = null,
                    selectedReusableEntitySummary = null,
                    formMessage = "Saved reusable item was retired. Review the text value before saving.",
                    canSave = canSave(it.itemName, it.selectedQuantityLevel)
                )
            }
            EditIngestionUiIntent.ShowDeleteConfirmation -> showDeleteConfirmation()
            EditIngestionUiIntent.DismissDeleteConfirmation -> updateState { it.copy(isDeleteConfirmationVisible = false) }
            EditIngestionUiIntent.ConfirmDelete -> delete()
            EditIngestionUiIntent.Save -> save()
        }
    }

    private fun loadIfNeeded() {
        val currentState = mutableState.value
        if (currentState.itemName.isNotBlank()) return
        updateState { it.copy(isLoading = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = getIngestionEventUseCase(recordId)) {
                is AppResult.Success -> {
                    val event = result.value
                    val hasRetiredReusableSelection = event.reusableEntityKind != null &&
                        event.reusableEntityId != null &&
                        !reusableEntityExists(event.reusableEntityKind, event.reusableEntityId)
                    updateState {
                        it.copy(
                            itemName = event.itemName,
                            deleteRecordLabel = event.itemName,
                            notes = event.notes.orEmpty(),
                            selectedQuantityLevel = event.quantityLevel,
                            selectedReusableEntityKind = if (hasRetiredReusableSelection) null else event.reusableEntityKind,
                            selectedReusableEntityId = if (hasRetiredReusableSelection) null else event.reusableEntityId,
                            selectedReusableEntitySummary = if (hasRetiredReusableSelection) {
                                null
                            } else {
                                event.reusableEntityKind?.let { kind -> "${kind.label} selected: ${event.itemName}" }
                            },
                            isLoading = false,
                            canSave = canSave(event.itemName, event.quantityLevel),
                            canDelete = event.itemName.isNotBlank(),
                            formMessage = if (hasRetiredReusableSelection) {
                                "Saved reusable item was retired. Review the text value before saving."
                            } else {
                                it.formMessage
                            }
                        ).withOccurredAt(event.occurredAtEpochMs, selectedPreset = null, clearMessages = false)
                    }
                }
                is AppResult.Error -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            formMessage = result.error.message
                        )
                    }
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

    private fun EditIngestionUiState.withReusableSelection(result: IngestionReusableSelectionResult): EditIngestionUiState {
        return copy(
            itemName = result.name,
            selectedReusableEntityKind = result.kind,
            selectedReusableEntityId = result.id,
            selectedReusableEntitySummary = "${result.kind.label} selected: ${result.name}",
            itemNameError = null,
            formMessage = "",
            canSave = canSave(result.name, selectedQuantityLevel)
        )
    }
}
