package io.github.mateovich.allergotracker.features.ingredients.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProvisionalIngredientsViewModel(
    private val observeReusableIngredients: ObserveReusableIngredientsUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProvisionalIngredientsUiState())
    val state: StateFlow<ProvisionalIngredientsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            observeReusableIngredients().collect { ingredients ->
                mutableState.value = mutableState.value.copy(
                    items = ingredients
                        .filter { it.isProvisional }
                        .map { ProvisionalIngredientRowUiModel(id = it.id, name = it.name, notes = it.notes) },
                    isLoading = false
                )
            }
        }
    }
}
