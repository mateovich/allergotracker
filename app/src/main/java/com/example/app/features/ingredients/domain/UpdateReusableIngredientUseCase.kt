package com.example.app.features.ingredients.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.CancellationException

class UpdateReusableIngredientUseCase(
    private val repository: ReusableIngredientRepository,
    private val syncReferences: suspend (Long, String) -> Int = { _, _ -> 0 },
    private val countReferences: suspend (Long) -> Int = { 0 },
    private val runAtomically: suspend (suspend () -> AppResult<ReusableIngredient>) -> AppResult<ReusableIngredient> = { block -> block() }
) {
    suspend operator fun invoke(command: UpdateReusableIngredientCommand): AppResult<ReusableIngredient> {
        val sanitizedCommand = command.copy(
            name = command.name.normalizeReusableName(),
            notes = command.notes?.trim()?.takeIf { it.isNotEmpty() }
        )

        if (sanitizedCommand.id <= 0L) {
            return AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
        }

        if (sanitizedCommand.name.isBlank()) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = mapOf("name" to "Enter an ingredient name.")
                )
            )
        }

        return try {
            runAtomically {
                when (val result = repository.updateIngredient(sanitizedCommand)) {
                    is AppResult.Success -> {
                        val expectedReferences = countReferences(result.value.id)
                        val syncedReferences = syncReferences(result.value.id, result.value.name)
                        if (syncedReferences != expectedReferences) {
                            throw ReusableIngredientUpdateRollback(
                                DomainError.IntegrityError("Couldn't keep linked ingredient records in sync. Try again.")
                            )
                        } else {
                            result
                        }
                    }

                    is AppResult.Error -> result
                }
            }
        } catch (rollback: ReusableIngredientUpdateRollback) {
            AppResult.Error(rollback.error)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't update ingredient locally. Try again."))
        }
    }
}

private class ReusableIngredientUpdateRollback(
    val error: DomainError
) : RuntimeException()
