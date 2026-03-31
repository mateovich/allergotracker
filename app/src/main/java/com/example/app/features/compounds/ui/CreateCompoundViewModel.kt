package com.example.app.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
import com.example.app.features.compounds.domain.CreateReusableCompoundUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CreateCompoundViewModel(
    private val createReusableCompoundUseCase: CreateReusableCompoundUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreateCompoundUiState())
    val state: StateFlow<CreateCompoundUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CreateCompoundUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onIntent(intent: CreateCompoundUiIntent) {
        when (intent) {
            is CreateCompoundUiIntent.UpdateName -> updateState {
                it.copy(name = intent.value, nameError = null, formMessage = "", canSave = intent.value.isNotBlank())
            }

            is CreateCompoundUiIntent.UpdateNotes -> updateState { it.copy(notes = intent.value) }
            CreateCompoundUiIntent.Save -> save()
        }
    }

    private fun save() {
        val currentState = mutableState.value
        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = createReusableCompoundUseCase(
                    CreateReusableCompoundCommand(name = currentState.name, notes = currentState.notes)
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(
                        CreateCompoundUiEffect.NavigateBackWithSelection(
                            IngestionReusableSelectionResult(
                                id = result.value.id,
                                name = result.value.name,
                                kind = ReusableEntityKind.COMPOUND
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

    private fun updateState(transform: (CreateCompoundUiState) -> CreateCompoundUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }
}
