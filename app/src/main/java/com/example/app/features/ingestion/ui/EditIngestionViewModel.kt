package com.example.app.features.ingestion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.GetIngestionEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.IngestionQuantityOption
import com.example.app.features.ingestion.domain.UpdateIngestionEventCommand
import com.example.app.features.ingestion.domain.UpdateIngestionEventUseCase
import java.time.Instant
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
    private val updateIngestionEventUseCase: UpdateIngestionEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
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
            is EditIngestionUiIntent.SelectTimePreset -> {
                val nextOccurredAt = currentTimeProvider.nowEpochMillis() - intent.preset.offsetMillis
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
                    updateState {
                        it.copy(
                            itemName = event.itemName,
                            notes = event.notes.orEmpty(),
                            occurredAtEpochMs = event.occurredAtEpochMs,
                            occurredAtLabel = formatTime(event.occurredAtEpochMs),
                            selectedQuantityLevel = event.quantityLevel,
                            isLoading = false,
                            canSave = canSave(event.itemName, event.quantityLevel)
                        )
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
        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = updateIngestionEventUseCase(
                    UpdateIngestionEventCommand(
                        id = recordId,
                        itemName = currentState.itemName,
                        quantityLevel = currentState.selectedQuantityLevel,
                        occurredAtEpochMs = currentState.occurredAtEpochMs,
                        notes = currentState.notes
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

    private fun formatTime(epochMillis: Long): String = timeFormatter.format(Instant.ofEpochMilli(epochMillis))

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
}
