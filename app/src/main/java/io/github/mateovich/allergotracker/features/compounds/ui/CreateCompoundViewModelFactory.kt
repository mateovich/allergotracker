package io.github.mateovich.allergotracker.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ObserveReusableCompoundsUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase

class CreateCompoundViewModelFactory(
    private val createReusableCompoundUseCase: CreateReusableCompoundUseCase,
    private val observeReusableIngredientsUseCase: ObserveReusableIngredientsUseCase,
    private val observeReusableCompoundsUseCase: ObserveReusableCompoundsUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateCompoundViewModel::class.java)) {
            return CreateCompoundViewModel(
                createReusableCompoundUseCase = createReusableCompoundUseCase,
                observeReusableIngredients = observeReusableIngredientsUseCase::invoke,
                observeReusableCompounds = observeReusableCompoundsUseCase::invoke
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
