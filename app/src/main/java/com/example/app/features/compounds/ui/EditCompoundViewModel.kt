package com.example.app.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.GetReusableCompoundRetirementImpactUseCase
import com.example.app.features.compounds.domain.GetReusableCompoundUseCase
import com.example.app.features.compounds.domain.RetireReusableCompoundUseCase
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
    private val updateReusableCompoundUseCase: UpdateReusableCompoundUseCase,
    private val getReusableCompoundRetirementImpactUseCase: GetReusableCompoundRetirementImpactUseCase,
    private val retireReusableCompoundUseCase: RetireReusableCompoundUseCase
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
            EditCompoundUiIntent.RequestRetirementPreview -> loadRetirementPreview()
            EditCompoundUiIntent.DismissRetirementDialog -> updateState {
                it.copy(isRetirementDialogVisible = false, retirementImpact = null, isRetiring = false)
            }
            EditCompoundUiIntent.ConfirmRetirement -> retire()
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

    private fun loadRetirementPreview() {
        val currentState = mutableState.value
        if (currentState.isLoading || currentState.isSaving || currentState.isRetiring) return
        updateState { it.copy(formMessage = "") }
        viewModelScope.launch {
            when (val result = getReusableCompoundRetirementImpactUseCase(compoundId)) {
                is AppResult.Success -> updateState {
                    it.copy(retirementImpact = result.value, isRetirementDialogVisible = true, formMessage = "")
                }

                is AppResult.Error -> updateState { it.copy(formMessage = result.error.message) }
            }
        }
    }

    private fun retire() {
        val currentState = mutableState.value
        val impact = currentState.retirementImpact ?: return
        if (currentState.isLoading || currentState.isSaving || currentState.isRetiring || impact.isBlocked) return
        updateState { it.copy(isRetiring = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = retireReusableCompoundUseCase(compoundId)) {
                is AppResult.Success -> {
                    updateState {
                        it.copy(
                            isRetiring = false,
                            isRetirementDialogVisible = false,
                            retirementImpact = result.value,
                            formMessage = ""
                        )
                    }
                    effectChannel.trySend(EditCompoundUiEffect.NavigateBackAfterRetirement)
                }

                is AppResult.Error -> updateState {
                    it.copy(
                        isRetiring = false,
                        isRetirementDialogVisible = true,
                        formMessage = result.error.message
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
