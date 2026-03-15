package com.example.app.features.exposure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.exposure.domain.CreateExposureDefaultsUseCase
import com.example.app.features.exposure.domain.CreateExposureEventCommand
import com.example.app.features.exposure.domain.CreateExposureEventUseCase
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CreateExposureViewModel(
    private val createExposureDefaultsUseCase: CreateExposureDefaultsUseCase,
    private val createExposureEventUseCase: CreateExposureEventUseCase
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val mutableState = MutableStateFlow(buildInitialState())
    val state: StateFlow<CreateExposureUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CreateExposureUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onIntent(intent: CreateExposureUiIntent) {
        when (intent) {
            is CreateExposureUiIntent.UpdateExposureType -> updateState {
                it.copy(
                    exposureType = intent.value,
                    exposureTypeError = null,
                    formMessage = "",
                    canSave = intent.value.isNotBlank()
                )
            }

            is CreateExposureUiIntent.UpdateNotes -> updateState {
                it.copy(notes = intent.value)
            }

            is CreateExposureUiIntent.UpdateOccurredAtInput -> updateState {
                it.copy(
                    occurredAtInput = intent.value,
                    occurredAtError = null,
                    formMessage = ""
                )
            }

            is CreateExposureUiIntent.SelectTimePreset -> {
                val nextOccurredAt = createExposureDefaultsUseCase().occurredAtEpochMs - intent.preset.offsetMillis
                updateState {
                    it.copy(
                        selectedTimePreset = intent.preset,
                        occurredAtEpochMs = nextOccurredAt,
                        occurredAtInput = formatDateTimeInput(nextOccurredAt),
                        occurredAtLabel = formatTime(nextOccurredAt),
                        occurredAtError = null,
                        formMessage = ""
                    )
                }
            }

            CreateExposureUiIntent.Save -> save()
        }
    }

    private fun save() {
        val currentState = mutableState.value
        val parsedOccurredAt = parseOccurredAtInput(currentState.occurredAtInput)
        if (parsedOccurredAt == null) {
            updateState {
                it.copy(
                    isSaving = false,
                    occurredAtError = "Enter date and time as yyyy-MM-dd HH:mm.",
                    formMessage = "Please fix the highlighted fields."
                )
            }
            return
        }
        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = createExposureEventUseCase(
                    CreateExposureEventCommand(
                        exposureType = currentState.exposureType,
                        occurredAtEpochMs = parsedOccurredAt,
                        notes = currentState.notes
                    )
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(CreateExposureUiEffect.NavigateToRecords)
                }

                is AppResult.Error -> {
                    val error = result.error
                    updateState {
                        it.copy(
                            isSaving = false,
                            exposureTypeError = error.fieldError("exposureType"),
                            occurredAtError = error.fieldError("occurredAtEpochMs"),
                            formMessage = error.message,
                            canSave = it.exposureType.isNotBlank()
                        )
                    }
                }
            }
        }
    }

    private fun buildInitialState(): CreateExposureUiState {
        val defaults = createExposureDefaultsUseCase()
        return CreateExposureUiState(
            occurredAtEpochMs = defaults.occurredAtEpochMs,
            occurredAtInput = formatDateTimeInput(defaults.occurredAtEpochMs),
            occurredAtLabel = formatTime(defaults.occurredAtEpochMs),
            timeOptions = ExposureTimePreset.entries.map { preset ->
                ExposureTimeOptionUiModel(
                    key = preset.name,
                    label = preset.label,
                    offsetMillis = preset.offsetMillis
                )
            }
        )
    }

    private fun formatTime(epochMillis: Long): String {
        return timeFormatter.format(Instant.ofEpochMilli(epochMillis))
    }

    private fun formatDateTimeInput(epochMillis: Long): String {
        return dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime())
    }

    private fun parseOccurredAtInput(value: String): Long? {
        return try {
            LocalDateTime.parse(value.trim(), dateTimeFormatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun updateState(transform: (CreateExposureUiState) -> CreateExposureUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }
}
