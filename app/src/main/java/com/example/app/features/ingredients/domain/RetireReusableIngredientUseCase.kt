package com.example.app.features.ingredients.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.CancellationException

data class ReusableIngredientRetirementImpact(
    val ingredient: ReusableIngredient,
    val ingestionReferenceCount: Int,
    val compoundReferenceCount: Int
) {
    val isBlocked: Boolean = compoundReferenceCount > 0
    val requiresDetachment: Boolean = !isBlocked && ingestionReferenceCount > 0
}

class GetReusableIngredientRetirementImpactUseCase(
    private val repository: ReusableIngredientRepository,
    private val countIngestionReferences: suspend (Long) -> Int = { 0 },
    private val countCompoundReferences: suspend (Long) -> Int = { 0 }
) {
    suspend operator fun invoke(id: Long): AppResult<ReusableIngredientRetirementImpact> {
        if (id <= 0L) {
            return AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
        }

        return try {
            when (val ingredient = repository.getIngredient(id)) {
                is AppResult.Success -> AppResult.Success(
                    ReusableIngredientRetirementImpact(
                        ingredient = ingredient.value,
                        ingestionReferenceCount = countIngestionReferences(id),
                        compoundReferenceCount = countCompoundReferences(id)
                    )
                )

                is AppResult.Error -> ingredient
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't inspect ingredient references locally. Try again."))
        }
    }
}

class RetireReusableIngredientUseCase(
    private val repository: ReusableIngredientRepository,
    private val getImpact: GetReusableIngredientRetirementImpactUseCase,
    private val detachIngestionReferences: suspend (Long) -> Int = { 0 },
    private val runAtomically: suspend (suspend () -> AppResult<ReusableIngredientRetirementImpact>) -> AppResult<ReusableIngredientRetirementImpact> = { block -> block() }
) {
    suspend operator fun invoke(id: Long): AppResult<ReusableIngredientRetirementImpact> {
        val impact = when (val result = getImpact(id)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        if (impact.isBlocked) {
            return AppResult.Error(
                DomainError.IntegrityError(
                    "This ingredient is still used by ${impact.compoundReferenceCount} compound components. Replace those references before retiring it."
                )
            )
        }

        return try {
            runAtomically {
                if (impact.ingestionReferenceCount > 0) {
                    val detached = detachIngestionReferences(id)
                    if (detached != impact.ingestionReferenceCount) {
                        throw ReusableIngredientRetirementRollback(
                            DomainError.IntegrityError("Couldn't safely detach linked ingestion records. Try again.")
                        )
                    }
                }
                when (val deleteResult = repository.deleteIngredient(id)) {
                    is AppResult.Success -> AppResult.Success(impact)
                    is AppResult.Error -> throw ReusableIngredientRetirementRollback(deleteResult.error)
                }
            }
        } catch (rollback: ReusableIngredientRetirementRollback) {
            AppResult.Error(rollback.error)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't retire ingredient locally. Try again."))
        }
    }
}

private class ReusableIngredientRetirementRollback(
    val error: DomainError
) : RuntimeException()
