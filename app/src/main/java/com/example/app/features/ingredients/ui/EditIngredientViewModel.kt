package com.example.app.features.ingredients.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult
import com.example.app.features.ingredients.domain.GetReusableIngredientRetirementImpactUseCase
import com.example.app.features.ingredients.domain.RetireReusableIngredientUseCase
import com.example.app.features.ingredients.domain.GetReusableIngredientUseCase
import com.example.app.features.ingredients.domain.UpdateReusableIngredientCommand
import com.example.app.features.ingredients.domain.UpdateReusableIngredientUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class EditIngredientViewModel(
    private val ingredientId: Long,
    private val getReusableIngredientUseCase: GetReusableIngredientUseCase,
    private val updateReusableIngredientUseCase: UpdateReusableIngredientUseCase,
    private val getReusableIngredientRetirementImpactUseCase: GetReusableIngredientRetirementImpactUseCase,
    private val retireReusableIngredientUseCase: RetireReusableIngredientUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditIngredientUiState())
    val state: StateFlow<EditIngredientUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EditIngredientUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onIntent(intent: EditIngredientUiIntent) {
        when (intent) {
            EditIngredientUiIntent.Load -> loadIfNeeded()
            is EditIngredientUiIntent.UpdateName -> updateState {
                val hasName = intent.value.trim().isNotBlank()
                it.copy(
                    name = intent.value,
                    nameError = if (hasName) null else "Enter an ingredient name.",
                    formMessage = "",
                    canSave = hasName
                )
            }

            is EditIngredientUiIntent.UpdateNotes -> updateState { it.copy(notes = intent.value) }
            EditIngredientUiIntent.Save -> save()
            EditIngredientUiIntent.RequestRetirementPreview -> loadRetirementPreview()
            EditIngredientUiIntent.DismissRetirementDialog -> updateState {
                it.copy(isRetirementDialogVisible = false, retirementImpact = null, isRetiring = false)
            }
            EditIngredientUiIntent.ConfirmRetirement -> retire()
        }
    }

    private fun loadIfNeeded() {
        if (mutableState.value.name.isNotBlank()) return
        updateState { it.copy(isLoading = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = getReusableIngredientUseCase(ingredientId)) {
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
                val result = updateReusableIngredientUseCase(
                    UpdateReusableIngredientCommand(ingredientId, currentState.name, currentState.notes)
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(
                        EditIngredientUiEffect.NavigateBackWithSelection(
                            IngestionReusableSelectionResult(result.value.id, result.value.name, ReusableEntityKind.INGREDIENT)
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
            when (val result = getReusableIngredientRetirementImpactUseCase(ingredientId)) {
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
            when (val result = retireReusableIngredientUseCase(ingredientId)) {
                is AppResult.Success -> {
                    updateState {
                        it.copy(
                            isRetiring = false,
                            isRetirementDialogVisible = false,
                            retirementImpact = result.value,
                            formMessage = ""
                        )
                    }
                    effectChannel.trySend(EditIngredientUiEffect.NavigateBackAfterRetirement)
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

    private fun updateState(transform: (EditIngredientUiState) -> EditIngredientUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }
}
