package io.github.mateovich.allergotracker.features.ingredients.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingestion.ui.IngestionReusableSelectionResult
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientMergePreviewUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientRetirementImpactUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.MergeReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import io.github.mateovich.allergotracker.features.ingredients.domain.RetireReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.UpdateReusableIngredientCommand
import io.github.mateovich.allergotracker.features.ingredients.domain.UpdateReusableIngredientUseCase
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
    private val observeReusableIngredients: ObserveReusableIngredientsUseCase,
    private val getReusableIngredientMergePreviewUseCase: GetReusableIngredientMergePreviewUseCase,
    private val mergeReusableIngredientUseCase: MergeReusableIngredientUseCase,
    private val getReusableIngredientRetirementImpactUseCase: GetReusableIngredientRetirementImpactUseCase,
    private val retireReusableIngredientUseCase: RetireReusableIngredientUseCase
) : ViewModel() {
    private var observedIngredients: List<ReusableIngredient> = emptyList()

    private val mutableState = MutableStateFlow(EditIngredientUiState())
    val state: StateFlow<EditIngredientUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EditIngredientUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeReusableIngredients().collect { ingredients ->
                observedIngredients = ingredients
                updateState { it.refreshMergeDestinationOptions() }
            }
        }
    }

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
            EditIngredientUiIntent.RequestMergeFlow -> openMergeDialog()
            is EditIngredientUiIntent.UpdateMergeDestinationQuery -> updateState {
                it.copy(
                    mergeDestinationQuery = intent.value,
                    mergeError = null,
                    mergePreview = null
                ).refreshMergeDestinationOptions()
            }
            is EditIngredientUiIntent.SelectMergeDestination -> updateState {
                it.copy(selectedMergeDestinationId = intent.id, mergePreview = null, mergeError = null)
                    .refreshMergeDestinationOptions()
            }
            EditIngredientUiIntent.LoadMergePreview -> loadMergePreview()
            EditIngredientUiIntent.DismissMergeDialog -> updateState {
                it.copy(
                    isMergeDialogVisible = false,
                    mergeDestinationQuery = "",
                    selectedMergeDestinationId = null,
                    mergePreview = null,
                    isLoadingMergePreview = false,
                    isMerging = false,
                    mergeError = null
                ).refreshMergeDestinationOptions()
            }
            EditIngredientUiIntent.ConfirmMerge -> merge()
            EditIngredientUiIntent.RequestRetirementPreview -> loadRetirementPreview()
            EditIngredientUiIntent.DismissRetirementDialog -> updateState {
                it.copy(isRetirementDialogVisible = false, retirementImpact = null, isRetiring = false)
            }
            EditIngredientUiIntent.ConfirmRetirement -> retire()
            EditIngredientUiIntent.MakeIntoCompound -> makeIntoCompound()
        }
    }

    private fun makeIntoCompound() {
        val currentState = mutableState.value
        if (currentState.isLoading || currentState.isSaving || currentState.isRetiring || currentState.isMerging) return
        effectChannel.trySend(
            EditIngredientUiEffect.NavigateToCreateCompound(
                id = ingredientId,
                name = currentState.name,
                kind = ReusableEntityKind.INGREDIENT
            )
        )
    }

    private fun loadIfNeeded() {
        if (!mutableState.value.isLoading && mutableState.value.name.isNotBlank()) return
        updateState { it.copy(isLoading = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = getReusableIngredientUseCase(ingredientId)) {
                is AppResult.Success -> updateState {
                    it.copy(
                        name = result.value.name,
                        notes = result.value.notes.orEmpty(),
                        isLoading = false,
                        canSave = result.value.name.isNotBlank()
                    ).refreshMergeDestinationOptions()
                }

                is AppResult.Error -> updateState { it.copy(isLoading = false, formMessage = result.error.message) }
            }
        }
    }

    private fun openMergeDialog() {
        val currentState = mutableState.value
        if (currentState.isLoading || currentState.isSaving || currentState.isRetiring || currentState.isMerging) return
        updateState {
            it.copy(
                isMergeDialogVisible = true,
                mergeDestinationQuery = "",
                selectedMergeDestinationId = null,
                mergePreview = null,
                isLoadingMergePreview = false,
                isMerging = false,
                mergeError = null
            ).refreshMergeDestinationOptions()
        }
    }

    private fun loadMergePreview() {
        val currentState = mutableState.value
        val destinationId = currentState.selectedMergeDestinationId
        if (!currentState.isMergeDialogVisible || currentState.isLoading || currentState.isSaving || currentState.isRetiring || currentState.isMerging) {
            return
        }
        if (destinationId == null) {
            updateState { it.copy(mergeError = "Choose a canonical ingredient to continue.") }
            return
        }
        updateState { it.copy(isLoadingMergePreview = true, mergePreview = null, mergeError = null) }
        viewModelScope.launch {
            when (val result = getReusableIngredientMergePreviewUseCase(ingredientId, destinationId)) {
                is AppResult.Success -> updateState {
                    if (!it.isMergeDialogVisible || it.selectedMergeDestinationId != destinationId) {
                        it.copy(isLoadingMergePreview = false)
                    } else {
                        it.copy(isLoadingMergePreview = false, mergePreview = result.value, mergeError = null)
                    }
                }

                is AppResult.Error -> updateState {
                    if (!it.isMergeDialogVisible || it.selectedMergeDestinationId != destinationId) {
                        it.copy(isLoadingMergePreview = false)
                    } else {
                        it.copy(isLoadingMergePreview = false, mergePreview = null, mergeError = result.error.message)
                    }
                }
            }
        }
    }

    private fun merge() {
        val currentState = mutableState.value
        val destinationId = currentState.selectedMergeDestinationId ?: return
        val preview = currentState.mergePreview ?: return
        if (
            !currentState.isMergeDialogVisible ||
            currentState.isLoading ||
            currentState.isSaving ||
            currentState.isRetiring ||
            currentState.isLoadingMergePreview ||
            currentState.isMerging ||
            preview.isBlocked
        ) {
            return
        }
        updateState { it.copy(isMerging = true, mergeError = null) }
        viewModelScope.launch {
            when (val result = mergeReusableIngredientUseCase(ingredientId, destinationId)) {
                is AppResult.Success -> {
                    updateState {
                        it.copy(
                            isMerging = false,
                            isMergeDialogVisible = false,
                            mergeDestinationQuery = "",
                            selectedMergeDestinationId = null,
                            mergePreview = null,
                            isLoadingMergePreview = false,
                            mergeError = null,
                            formMessage = ""
                        ).refreshMergeDestinationOptions()
                    }
                    effectChannel.trySend(
                        EditIngredientUiEffect.NavigateBackWithSelection(
                            IngestionReusableSelectionResult(
                                result.value.destination.id,
                                result.value.destination.name,
                                ReusableEntityKind.INGREDIENT
                            )
                        )
                    )
                }

                is AppResult.Error -> updateState {
                    it.copy(isMerging = false, mergeError = result.error.message)
                }
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

    private fun EditIngredientUiState.refreshMergeDestinationOptions(): EditIngredientUiState {
        val normalizedQuery = mergeDestinationQuery.trim().lowercase()
        val filteredOptions = observedIngredients.filter { ingredient ->
            ingredient.id != ingredientId &&
                (normalizedQuery.isBlank() || ingredient.name.lowercase().contains(normalizedQuery))
        }
        val hasSelectedDestination = filteredOptions.any { it.id == selectedMergeDestinationId }
        return copy(
            mergeDestinationOptions = filteredOptions,
            selectedMergeDestinationId = selectedMergeDestinationId?.takeIf { hasSelectedDestination },
            mergePreview = mergePreview?.takeIf { preview ->
                preview.destination.id == selectedMergeDestinationId && hasSelectedDestination
            }
        )
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }
}
