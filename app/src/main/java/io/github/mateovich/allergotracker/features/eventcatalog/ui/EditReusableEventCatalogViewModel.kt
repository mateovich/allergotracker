package io.github.mateovich.allergotracker.features.eventcatalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.eventcatalog.domain.GetReusableEventCatalogEntryUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.GetReusableEventCatalogRetirementImpactUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.domain.RetireReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.UpdateReusableEventCatalogUseCase
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
    private val updateReusableEventCatalogUseCase: UpdateReusableEventCatalogUseCase,
    private val getReusableEventCatalogRetirementImpactUseCase: GetReusableEventCatalogRetirementImpactUseCase,
    private val retireReusableEventCatalogUseCase: RetireReusableEventCatalogUseCase
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
            EditReusableEventCatalogUiIntent.RequestRetirementPreview -> loadRetirementPreview()
            EditReusableEventCatalogUiIntent.DismissRetirementDialog -> updateState {
                it.copy(isRetirementDialogVisible = false, retirementImpact = null, isRetiring = false)
            }
            EditReusableEventCatalogUiIntent.ConfirmRetirement -> retire()
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

    private fun loadRetirementPreview() {
        val currentState = mutableState.value
        if (currentState.isLoading || currentState.isSaving || currentState.isRetiring) return
        updateState { it.copy(formMessage = "") }
        viewModelScope.launch {
            when (val result = getReusableEventCatalogRetirementImpactUseCase(kind, catalogId)) {
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
            when (val result = retireReusableEventCatalogUseCase(kind, catalogId)) {
                is AppResult.Success -> {
                    updateState {
                        it.copy(
                            isRetiring = false,
                            isRetirementDialogVisible = false,
                            retirementImpact = result.value,
                            formMessage = ""
                        )
                    }
                    effectChannel.trySend(EditReusableEventCatalogUiEffect.NavigateBackAfterRetirement)
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
