package com.example.app.features.ingredients.data

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingredients.domain.CreateReusableIngredientCommand
import com.example.app.features.ingredients.domain.ReusableIngredient
import com.example.app.features.ingredients.domain.ReusableIngredientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReusableIngredientRepository(
    private val ingredientDao: ReusableIngredientDao,
    private val nowEpochMillis: () -> Long
) : ReusableIngredientRepository {
    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
        return try {
            val normalizedName = command.name.normalizedKey()
            ingredientDao.findByNormalizedName(normalizedName)?.let { existing ->
                return AppResult.Success(existing.toDomain())
            }
            val now = nowEpochMillis()
            val entity = ReusableIngredientEntity(
                name = command.name,
                normalizedName = normalizedName,
                notes = command.notes,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            val id = ingredientDao.insert(entity)
            AppResult.Success(entity.copy(id = id).toDomain())
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't save ingredient locally. Try again."))
        }
    }

    override fun observeIngredients(): Flow<List<ReusableIngredient>> {
        return ingredientDao.observeAll().map { entities -> entities.map(ReusableIngredientEntity::toDomain) }
    }
}

private fun ReusableIngredientEntity.toDomain(): ReusableIngredient {
    return ReusableIngredient(
        id = id,
        name = name,
        notes = notes,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

private fun String.normalizedKey(): String = trim().lowercase()
