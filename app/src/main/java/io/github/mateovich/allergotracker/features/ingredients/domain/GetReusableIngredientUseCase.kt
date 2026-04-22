package io.github.mateovich.allergotracker.features.ingredients.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

class GetReusableIngredientUseCase(
    private val repository: ReusableIngredientRepository
) {
    suspend operator fun invoke(id: Long): AppResult<ReusableIngredient> {
        return repository.getIngredient(id)
    }
}
