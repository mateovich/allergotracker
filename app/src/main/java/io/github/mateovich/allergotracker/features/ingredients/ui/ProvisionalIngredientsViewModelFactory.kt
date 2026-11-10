package io.github.mateovich.allergotracker.features.ingredients.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase

class ProvisionalIngredientsViewModelFactory(
    private val observeReusableIngredientsUseCase: ObserveReusableIngredientsUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProvisionalIngredientsViewModel::class.java)) {
            return ProvisionalIngredientsViewModel(observeReusableIngredientsUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
