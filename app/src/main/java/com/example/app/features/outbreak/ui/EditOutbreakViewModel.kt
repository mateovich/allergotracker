package com.example.app.features.outbreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.outbreak.domain.DeleteOutbreakEventUseCase
import com.example.app.features.outbreak.domain.GetOutbreakEventUseCase
import com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand
import com.example.app.features.outbreak.domain.UpdateOutbreakEventUseCase
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

class EditOutbreakViewModel(
    private val recordId: Long,
    private val getOutbreakEventUseCase: GetOutbreakEventUseCase,
    private val deleteOutbreakEventUseCase: DeleteOutbreakEventUseCase,
    private val updateOutbreakEventUseCase: UpdateOutbreakEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val mutableState = MutableStateFlow(
        EditOutbreakUiState(
            timeOptions = OutbreakTimePreset.entries.map { OutbreakTimeOptionUiModel(it.name, it.label, it.offsetMillis) }
        )
    )
    val state: StateFlow<EditOutbreakUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EditOutbreakUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onIntent(intent: EditOutbreakUiIntent) {
        when (intent) {
            EditOutbreakUiIntent.Load -> loadIfNeeded()
            is EditOutbreakUiIntent.UpdateOutbreakDescriptor -> updateState {
                it.copy(
                    outbreakDescriptor = intent.value,
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(intent.value, it.bodyLocation)
                )
            }
            is EditOutbreakUiIntent.UpdateBodyLocation -> updateState {
                it.copy(
                    bodyLocation = intent.value,
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, intent.value)
                )
            }
            is EditOutbreakUiIntent.UpdateNotes -> updateState { it.copy(notes = intent.value) }
            is EditOutbreakUiIntent.UpdateOccurredAtInput -> {
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
            is EditOutbreakUiIntent.SelectTimePreset -> {
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
            EditOutbreakUiIntent.ShowDeleteConfirmation -> showDeleteConfirmation()
            EditOutbreakUiIntent.DismissDeleteConfirmation -> updateState { it.copy(isDeleteConfirmationVisible = false) }
            EditOutbreakUiIntent.ConfirmDelete -> delete()
            EditOutbreakUiIntent.Save -> save()
        }
    }

    private fun loadIfNeeded() {
        val currentState = mutableState.value
        if (currentState.outbreakDescriptor.isNotBlank()) return
        updateState { it.copy(isLoading = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = getOutbreakEventUseCase(recordId)) {
                is AppResult.Success -> {
                    val event = result.value
                    updateState {
                        it.copy(
                            outbreakDescriptor = event.outbreakDescriptor,
                            bodyLocation = event.bodyLocation,
                            notes = event.notes.orEmpty(),
                            occurredAtEpochMs = event.occurredAtEpochMs,
                            occurredAtInput = formatDateTimeInput(event.occurredAtEpochMs),
                            occurredAtLabel = formatTime(event.occurredAtEpochMs),
                            isLoading = false,
                            canSave = canSave(event.outbreakDescriptor, event.bodyLocation)
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
                val result = updateOutbreakEventUseCase(
                    UpdateOutbreakEventCommand(
                        id = recordId,
                        outbreakDescriptor = currentState.outbreakDescriptor,
                        bodyLocation = currentState.bodyLocation,
                        occurredAtEpochMs = parsedOccurredAt,
                        notes = currentState.notes
                    )
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(EditOutbreakUiEffect.NavigateToRecords)
                }
                is AppResult.Error -> {
                    val error = result.error
                    updateState {
                        it.copy(
                            isSaving = false,
                            outbreakDescriptorError = error.fieldError("outbreakDescriptor"),
                            bodyLocationError = error.fieldError("bodyLocation"),
                            occurredAtError = error.fieldError("occurredAtEpochMs"),
                            formMessage = error.message,
                            canSave = canSave(it.outbreakDescriptor, it.bodyLocation)
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
            when (val result = deleteOutbreakEventUseCase(recordId)) {
                is AppResult.Success -> {
                    updateState { it.copy(isDeleting = false) }
                    effectChannel.trySend(EditOutbreakUiEffect.NavigateToRecords)
                }
                is AppResult.Error -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            formMessage = result.error.message,
                            canSave = canSave(it.outbreakDescriptor, it.bodyLocation)
                        )
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmation() {
        val currentState = mutableState.value
        if (currentState.isLoading || currentState.isSaving || currentState.isDeleting || currentState.outbreakDescriptor.isBlank()) return
        updateState { it.copy(isDeleteConfirmationVisible = true, formMessage = "") }
    }

    private fun canSave(outbreakDescriptor: String, bodyLocation: String): Boolean {
        return outbreakDescriptor.isNotBlank() && bodyLocation.isNotBlank()
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

    private fun updateState(transform: (EditOutbreakUiState) -> EditOutbreakUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }
}
