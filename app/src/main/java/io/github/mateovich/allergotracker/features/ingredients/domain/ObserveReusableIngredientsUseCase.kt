package io.github.mateovich.allergotracker.features.ingredients.domain

import kotlinx.coroutines.flow.Flow

class ObserveReusableIngredientsUseCase(
    private val repository: ReusableIngredientRepository
) {
    operator fun invoke(): Flow<List<ReusableIngredient>> = repository.observeIngredients()
}
