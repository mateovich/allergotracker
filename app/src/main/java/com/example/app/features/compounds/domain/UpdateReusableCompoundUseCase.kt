package com.example.app.features.compounds.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.CancellationException

class UpdateReusableCompoundUseCase(
    private val repository: ReusableCompoundRepository,
    private val syncReferences: suspend (Long, String) -> Int = { _, _ -> 0 },
    private val countReferences: suspend (Long) -> Int = { 0 },
    private val runAtomically: suspend (suspend () -> AppResult<ReusableCompound>) -> AppResult<ReusableCompound> = { block -> block() }
) {
    suspend operator fun invoke(command: UpdateReusableCompoundCommand): AppResult<ReusableCompound> {
        val sanitizedCommand = command.copy(
            name = command.name.normalizeReusableName(),
            notes = command.notes?.trim()?.takeIf { it.isNotEmpty() }
        )

        if (sanitizedCommand.id <= 0L) {
            return AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
        }

        if (sanitizedCommand.name.isBlank()) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = mapOf("name" to "Enter a compound name.")
                )
            )
        }

        return try {
            runAtomically {
                when (val result = repository.updateCompound(sanitizedCommand)) {
                    is AppResult.Success -> {
                        val expectedReferences = countReferences(result.value.id)
                        val syncedReferences = syncReferences(result.value.id, result.value.name)
                        if (syncedReferences != expectedReferences) {
                            throw ReusableCompoundUpdateRollback(
                                DomainError.IntegrityError("Couldn't keep linked compound records in sync. Try again.")
                            )
                        } else {
                            result
                        }
                    }

                    is AppResult.Error -> result
                }
            }
        } catch (rollback: ReusableCompoundUpdateRollback) {
            AppResult.Error(rollback.error)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't update compound locally. Try again."))
        }
    }
}

private class ReusableCompoundUpdateRollback(
    val error: DomainError
) : RuntimeException()
