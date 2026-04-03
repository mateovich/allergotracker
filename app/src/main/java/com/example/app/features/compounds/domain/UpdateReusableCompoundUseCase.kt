package com.example.app.features.compounds.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.CancellationException

class UpdateReusableCompoundUseCase(
    private val repository: ReusableCompoundRepository,
    private val ingredientExists: suspend (Long) -> Boolean = { true },
    private val compoundExists: suspend (Long) -> Boolean = { true },
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

        val fieldErrors = linkedMapOf<String, String>()
        if (sanitizedCommand.components.isEmpty()) {
            fieldErrors["components"] = "Add at least one component."
        }
        sanitizedCommand.components.forEachIndexed { index, component ->
            val reusableId = component.reusableEntityId
            val targetKey = "components[$index].target"
            val proportionKey = "components[$index].proportion"
            when {
                reusableId == null || reusableId <= 0L -> fieldErrors[targetKey] = "Choose a saved ingredient or compound."
                component.kind == CompoundComponentKind.INGREDIENT && !ingredientExists(reusableId) -> {
                    fieldErrors[targetKey] = "Selected ingredient is no longer available."
                }
                component.kind == CompoundComponentKind.COMPOUND && reusableId == sanitizedCommand.id -> {
                    fieldErrors[targetKey] = "A compound cannot include itself."
                }
                component.kind == CompoundComponentKind.COMPOUND && !compoundExists(reusableId) -> {
                    fieldErrors[targetKey] = "Selected compound is no longer available."
                }
            }
            if (component.proportionBasisPoints == null || component.proportionBasisPoints !in 1..10_000) {
                fieldErrors[proportionKey] = "Enter a proportion greater than 0%."
            }
        }
        val totalProportion = sanitizedCommand.components.sumOf { it.proportionBasisPoints ?: 0 }
        if (sanitizedCommand.components.isNotEmpty() && totalProportion != 10_000) {
            fieldErrors["componentsTotal"] = "Total proportion must equal 100%."
        }
        if (fieldErrors.isNotEmpty()) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = fieldErrors
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
