package com.example.app.features.ingredients.domain

import com.example.app.core.common.result.AppResult

class GetReusableIngredientUseCase(
    private val repository: ReusableIngredientRepository
) {
    suspend operator fun invoke(id: Long): AppResult<ReusableIngredient> {
        return repository.getIngredient(id)
    }
}
