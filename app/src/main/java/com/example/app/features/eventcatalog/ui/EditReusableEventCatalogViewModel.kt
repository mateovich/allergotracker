package com.example.app.features.eventcatalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.eventcatalog.domain.GetReusableEventCatalogEntryUseCase
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.UpdateReusableEventCatalogUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class EditReusableEventCatalogViewModel(
    private val catalogId: Long,
    private val kind: ReusableEventCatalogKind,
    private val getReusableEventCatalogEntryUseCase: GetReusableEventCatalogEntryUseCase,
    private val updateReusableEventCatalogUseCase: UpdateReusableEventCatalogUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        EditReusableEventCatalogUiState(
            kind = kind,
            title = titleFor(kind),
            nameLabel = labelFor(kind)
        )
    )
    val state: StateFlow<EditReusableEventCatalogUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EditReusableEventCatalogUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onIntent(intent: EditReusableEventCatalogUiIntent) {
        when (intent) {
            EditReusableEventCatalogUiIntent.Load -> loadIfNeeded()
            is EditReusableEventCatalogUiIntent.UpdateName -> updateState {
                val hasName = intent.value.trim().isNotBlank()
                it.copy(
                    name = intent.value,
                    nameError = if (hasName) null else labelErrorFor(kind),
                    formMessage = "",
                    canSave = hasName
                )
            }

            EditReusableEventCatalogUiIntent.Save -> save()
        }
    }

    private fun loadIfNeeded() {
        if (mutableState.value.name.isNotBlank()) return
        updateState { it.copy(isLoading = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = getReusableEventCatalogEntryUseCase(kind, catalogId)) {
                is AppResult.Success -> updateState {
                    it.copy(
                        name = result.value.name,
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
        if (!currentState.canSave || currentState.isSaving || currentState.isLoading) return
        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = updateReusableEventCatalogUseCase(kind, catalogId, currentState.name)) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(
                        EditReusableEventCatalogUiEffect.NavigateBackWithSelection(
                            EditedEventCatalogSelectionResult(
                                id = result.value.id,
                                kind = result.value.kind,
                                name = result.value.name
                            )
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

    private fun updateState(transform: (EditReusableEventCatalogUiState) -> EditReusableEventCatalogUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }
}

private fun titleFor(kind: ReusableEventCatalogKind): String {
    return when (kind) {
        ReusableEventCatalogKind.EXPOSURE_TYPE -> "Edit Exposure Type"
        ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> "Edit Outbreak Descriptor"
        ReusableEventCatalogKind.BODY_LOCATION -> "Edit Body Location"
    }
}

private fun labelFor(kind: ReusableEventCatalogKind): String {
    return when (kind) {
        ReusableEventCatalogKind.EXPOSURE_TYPE -> "Exposure type"
        ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> "What happened?"
        ReusableEventCatalogKind.BODY_LOCATION -> "Where on the body?"
    }
}

private fun labelErrorFor(kind: ReusableEventCatalogKind): String {
    return when (kind) {
        ReusableEventCatalogKind.EXPOSURE_TYPE -> "Enter an exposure type."
        ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> "Enter what happened."
        ReusableEventCatalogKind.BODY_LOCATION -> "Enter where on the body it happened."
    }
}
