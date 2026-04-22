package io.github.mateovich.allergotracker.features.ingredients.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

interface ReusableIngredientRepository {
    suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient>
    suspend fun getIngredient(id: Long): AppResult<ReusableIngredient> =
        AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    suspend fun updateIngredient(command: UpdateReusableIngredientCommand): AppResult<ReusableIngredient> =
        AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    suspend fun deleteIngredient(id: Long): AppResult<Unit> =
        AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    fun observeIngredients(): Flow<List<ReusableIngredient>>
}
