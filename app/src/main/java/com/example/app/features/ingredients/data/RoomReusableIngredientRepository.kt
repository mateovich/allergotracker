package com.example.app.features.ingredients.data

import android.database.sqlite.SQLiteConstraintException
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingredients.domain.CreateReusableIngredientCommand
import com.example.app.features.ingredients.domain.ReusableIngredient
import com.example.app.features.ingredients.domain.ReusableIngredientRepository
import com.example.app.features.ingredients.domain.UpdateReusableIngredientCommand
import com.example.app.features.ingredients.domain.normalizeReusableName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReusableIngredientRepository(
    private val ingredientDao: ReusableIngredientDao,
    private val nowEpochMillis: () -> Long
) : ReusableIngredientRepository {
    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
        return try {
            val canonicalName = command.name.normalizeReusableName()
            val normalizedName = canonicalName.normalizedKey()
            ingredientDao.findByNormalizedName(normalizedName)?.let { existing ->
                return AppResult.Success(existing.toDomain())
            }
            val now = nowEpochMillis()
            val entity = ReusableIngredientEntity(
                name = canonicalName,
                normalizedName = normalizedName,
                notes = command.notes,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            val id = ingredientDao.insert(entity)
            AppResult.Success(entity.copy(id = id).toDomain())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't save ingredient locally. Try again."))
        }
    }

    override suspend fun getIngredient(id: Long): AppResult<ReusableIngredient> {
        return try {
            val entity = ingredientDao.findById(id)
                ?: return AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
            AppResult.Success(entity.toDomain())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't load ingredient locally. Try again."))
        }
    }

    override suspend fun updateIngredient(command: UpdateReusableIngredientCommand): AppResult<ReusableIngredient> {
        return try {
            val current = ingredientDao.findById(command.id)
                ?: return AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
            val canonicalName = command.name.normalizeReusableName()
            val normalizedName = canonicalName.normalizedKey()
            ingredientDao.findByNormalizedName(normalizedName)?.let { existing ->
                if (existing.id != command.id) {
                    return AppResult.Error(
                        DomainError.ValidationError(
                            message = "Please fix the highlighted fields.",
                            fieldErrors = mapOf("name" to "An ingredient with this name already exists.")
                        )
                    )
                }
            }
            val updated = current.copy(
                name = canonicalName,
                normalizedName = normalizedName,
                notes = command.notes,
                updatedAtEpochMs = nowEpochMillis()
            )
            if (ingredientDao.update(updated) == 0) {
                return AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
            }
            AppResult.Success(updated.toDomain())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't update ingredient locally. Try again."))
        }
    }

    override suspend fun deleteIngredient(id: Long): AppResult<Unit> {
        return try {
            if (ingredientDao.deleteById(id) == 0) {
                AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
            } else {
                AppResult.Success(Unit)
            }
        } catch (_: SQLiteConstraintException) {
            AppResult.Error(
                DomainError.IntegrityError(
                    "This ingredient is still linked from compound components. Replace those references before retiring it."
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't retire ingredient locally. Try again."))
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

private fun String.normalizedKey(): String = normalizeReusableName().lowercase()
