package io.github.mateovich.allergotracker.features.ingredients.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

class SetIngredientProvisionalUseCase(
    private val repository: ReusableIngredientRepository
) {
    suspend operator fun invoke(id: Long, isProvisional: Boolean): AppResult<ReusableIngredient> =
        repository.setIngredientProvisional(id, isProvisional)
}
