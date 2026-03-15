package com.example.app.features.exposure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.exposure.domain.DeleteExposureEventUseCase
import com.example.app.features.exposure.domain.GetExposureEventUseCase
import com.example.app.features.exposure.domain.UpdateExposureEventCommand
import com.example.app.features.exposure.domain.UpdateExposureEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
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

class EditExposureViewModel(
    private val recordId: Long,
    private val getExposureEventUseCase: GetExposureEventUseCase,
    private val deleteExposureEventUseCase: DeleteExposureEventUseCase,
    private val updateExposureEventUseCase: UpdateExposureEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val mutableState = MutableStateFlow(
        EditExposureUiState(
            timeOptions = ExposureTimePreset.entries.map { ExposureTimeOptionUiModel(it.name, it.label, it.offsetMillis) }
        )
    )
    val state: StateFlow<EditExposureUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EditExposureUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onIntent(intent: EditExposureUiIntent) {
        when (intent) {
            EditExposureUiIntent.Load -> loadIfNeeded()
            is EditExposureUiIntent.UpdateExposureType -> updateState {
                it.copy(
                    exposureType = intent.value,
                    exposureTypeError = null,
                    formMessage = "",
                    canSave = intent.value.isNotBlank()
                )
            }
            is EditExposureUiIntent.UpdateNotes -> updateState { it.copy(notes = intent.value) }
            is EditExposureUiIntent.UpdateOccurredAtInput -> {
                val parsedOccurredAt = parseOccurredAtInput(intent.value)
                updateState {
                    it.copy(
                        occurredAtInput = intent.value,
                        occurredAtEpochMs = parsedOccurredAt ?: it.occurredAtEpochMs,
                        occurredAtLabel = parsedOccurredAt?.let(::formatTime).orEmpty(),
                        occurredAtError = null,
                        formMessage = ""
                    )
                }
            }
            is EditExposureUiIntent.SelectTimePreset -> {
                val nextOccurredAt = currentTimeProvider.nowEpochMillis() - intent.preset.offsetMillis
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
            EditExposureUiIntent.ShowDeleteConfirmation -> showDeleteConfirmation()
            EditExposureUiIntent.DismissDeleteConfirmation -> updateState { it.copy(isDeleteConfirmationVisible = false) }
            EditExposureUiIntent.ConfirmDelete -> delete()
            EditExposureUiIntent.Save -> save()
        }
    }

    private fun loadIfNeeded() {
        val currentState = mutableState.value
        if (currentState.exposureType.isNotBlank()) return
        updateState { it.copy(isLoading = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = getExposureEventUseCase(recordId)) {
                is AppResult.Success -> {
                    val event = result.value
                    updateState {
                        it.copy(
                            exposureType = event.exposureType,
                            notes = event.notes.orEmpty(),
                            occurredAtEpochMs = event.occurredAtEpochMs,
                            occurredAtInput = formatDateTimeInput(event.occurredAtEpochMs),
                            occurredAtLabel = formatTime(event.occurredAtEpochMs),
                            isLoading = false,
                            canSave = event.exposureType.isNotBlank()
                        )
                    }
                }
                is AppResult.Error -> updateState { it.copy(isLoading = false, formMessage = result.error.message) }
            }
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (!currentState.canSave || currentState.isSaving || currentState.isDeleting || currentState.isLoading) return
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
                val result = updateExposureEventUseCase(
                    UpdateExposureEventCommand(
                        id = recordId,
                        exposureType = currentState.exposureType,
                        occurredAtEpochMs = parsedOccurredAt,
                        notes = currentState.notes
                    )
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(EditExposureUiEffect.NavigateToRecords)
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
            when (val result = deleteExposureEventUseCase(recordId)) {
                is AppResult.Success -> {
                    updateState { it.copy(isDeleting = false) }
                    effectChannel.trySend(EditExposureUiEffect.NavigateToRecords)
                }
                is AppResult.Error -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            formMessage = result.error.message,
                            canSave = it.exposureType.isNotBlank()
                        )
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmation() {
        val currentState = mutableState.value
        if (currentState.isLoading || currentState.isSaving || currentState.isDeleting || currentState.exposureType.isBlank()) return
        updateState { it.copy(isDeleteConfirmationVisible = true, formMessage = "") }
    }

    private fun formatTime(epochMillis: Long): String = timeFormatter.format(Instant.ofEpochMilli(epochMillis))

    private fun formatDateTimeInput(epochMillis: Long): String {
        return dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime())
    }

    private fun parseOccurredAtInput(value: String): Long? {
        return try {
            LocalDateTime.parse(value.trim(), dateTimeFormatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun updateState(transform: (EditExposureUiState) -> EditExposureUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }
}
