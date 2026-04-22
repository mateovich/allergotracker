package io.github.mateovich.allergotracker.features.ingredients.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase

class CreateIngredientViewModelFactory(
    private val createReusableIngredientUseCase: CreateReusableIngredientUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateIngredientViewModel::class.java)) {
            return CreateIngredientViewModel(createReusableIngredientUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
