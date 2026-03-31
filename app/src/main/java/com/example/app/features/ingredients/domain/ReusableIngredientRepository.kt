package com.example.app.features.ingredients.domain

import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

interface ReusableIngredientRepository {
    suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient>
    fun observeIngredients(): Flow<List<ReusableIngredient>>
}
