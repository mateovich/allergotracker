package com.example.app.features.ingestion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CreateIngestionDefaultsUseCase
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CreateIngestionEventUseCase
import com.example.app.features.ingestion.domain.IngestionQuantityOption
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
    private val createIngestionEventUseCase: CreateIngestionEventUseCase
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val mutableState = MutableStateFlow(buildInitialState())
    val state: StateFlow<CreateIngestionUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CreateIngestionUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onIntent(intent: CreateIngestionUiIntent) {
        when (intent) {
            is CreateIngestionUiIntent.UpdateItemName -> updateState {
                it.copy(
                    itemName = intent.value,
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

            CreateIngestionUiIntent.Save -> save()
        }
    }

    private fun save() {
        val currentState = mutableState.value
        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = createIngestionEventUseCase(
                    CreateIngestionEventCommand(
                        itemName = currentState.itemName,
                        quantityLevel = currentState.selectedQuantityLevel,
                        occurredAtEpochMs = currentState.occurredAtEpochMs,
                        notes = currentState.notes
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
        )
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
}
