package io.github.mateovich.allergotracker.features.ingredients.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingestion.ui.IngestionReusableSelectionResult
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CreateIngredientViewModel(
    private val createReusableIngredientUseCase: CreateReusableIngredientUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreateIngredientUiState())
    val state: StateFlow<CreateIngredientUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CreateIngredientUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onIntent(intent: CreateIngredientUiIntent) {
        when (intent) {
            is CreateIngredientUiIntent.UpdateName -> updateState {
                it.copy(name = intent.value, nameError = null, formMessage = "", canSave = intent.value.isNotBlank())
            }

            is CreateIngredientUiIntent.UpdateNotes -> updateState { it.copy(notes = intent.value) }
            CreateIngredientUiIntent.Save -> save()
        }
    }

    private fun save() {
        val currentState = mutableState.value
        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = createReusableIngredientUseCase(
                    CreateReusableIngredientCommand(name = currentState.name, notes = currentState.notes)
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(
                        CreateIngredientUiEffect.NavigateBackWithSelection(
                            IngestionReusableSelectionResult(
                                id = result.value.id,
                                name = result.value.name,
                                kind = ReusableEntityKind.INGREDIENT
                            )
                        )
                    )
                }

                is AppResult.Error -> {
                    updateState {
                        it.copy(
                            isSaving = false,
                            nameError = result.error.fieldError("name"),
                            formMessage = result.error.message,
                            canSave = it.name.isNotBlank()
                        )
                    }
                }
            }
        }
    }

    private fun updateState(transform: (CreateIngredientUiState) -> CreateIngredientUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }
}
