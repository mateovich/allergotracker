package com.example.app.features.ingredients.domain

import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

interface ReusableIngredientRepository {
    suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient>
    suspend fun getIngredient(id: Long): AppResult<ReusableIngredient> =
        AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    suspend fun updateIngredient(command: UpdateReusableIngredientCommand): AppResult<ReusableIngredient> =
        AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    fun observeIngredients(): Flow<List<ReusableIngredient>>
}
