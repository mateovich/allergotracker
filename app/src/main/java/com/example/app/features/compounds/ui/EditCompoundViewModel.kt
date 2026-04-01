package com.example.app.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.GetReusableCompoundUseCase
import com.example.app.features.compounds.domain.UpdateReusableCompoundCommand
import com.example.app.features.compounds.domain.UpdateReusableCompoundUseCase
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class EditCompoundViewModel(
    private val compoundId: Long,
    private val getReusableCompoundUseCase: GetReusableCompoundUseCase,
    private val updateReusableCompoundUseCase: UpdateReusableCompoundUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditCompoundUiState())
    val state: StateFlow<EditCompoundUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EditCompoundUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onIntent(intent: EditCompoundUiIntent) {
        when (intent) {
            EditCompoundUiIntent.Load -> loadIfNeeded()
            is EditCompoundUiIntent.UpdateName -> updateState {
                val hasName = intent.value.trim().isNotBlank()
                it.copy(
                    name = intent.value,
                    nameError = if (hasName) null else "Enter a compound name.",
                    formMessage = "",
                    canSave = hasName
                )
            }

            is EditCompoundUiIntent.UpdateNotes -> updateState { it.copy(notes = intent.value) }
            EditCompoundUiIntent.Save -> save()
        }
    }

    private fun loadIfNeeded() {
        if (mutableState.value.name.isNotBlank()) return
        updateState { it.copy(isLoading = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = getReusableCompoundUseCase(compoundId)) {
                is AppResult.Success -> updateState {
                    it.copy(
                        name = result.value.name,
                        notes = result.value.notes.orEmpty(),
                        isLoading = false,
                        canSave = result.value.name.isNotBlank()
                    )
                }

                is AppResult.Error -> updateState { it.copy(isLoading = false, formMessage = result.error.message) }
            }
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (!currentState.canSave || currentState.isLoading || currentState.isSaving) return
        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = updateReusableCompoundUseCase(
                    UpdateReusableCompoundCommand(compoundId, currentState.name, currentState.notes)
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(
                        EditCompoundUiEffect.NavigateBackWithSelection(
                            IngestionReusableSelectionResult(result.value.id, result.value.name, ReusableEntityKind.COMPOUND)
                        )
                    )
                }

                is AppResult.Error -> updateState {
                    it.copy(
                        isSaving = false,
                        nameError = result.error.fieldError("name"),
                        formMessage = result.error.message,
                        canSave = it.name.trim().isNotBlank()
                    )
                }
            }
        }
    }

    private fun updateState(transform: (EditCompoundUiState) -> EditCompoundUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }
}
