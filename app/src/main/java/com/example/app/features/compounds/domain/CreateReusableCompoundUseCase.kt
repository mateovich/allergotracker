package com.example.app.features.compounds.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult

class CreateReusableCompoundUseCase(
    private val repository: ReusableCompoundRepository
) {
    suspend operator fun invoke(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> {
        val sanitizedCommand = command.copy(
            name = command.name.normalizeReusableName(),
            notes = command.notes?.trim()?.takeIf { it.isNotEmpty() },
            components = command.components
        )

        val fieldErrors = linkedMapOf<String, String>()
        val duplicateIndexes = CompoundCompositionIntegrity.findDuplicateComponentIndexes(
            sanitizedCommand.components.map { it.kind to it.reusableEntityId }
        )

        if (sanitizedCommand.name.isBlank()) {
            fieldErrors["name"] = "Enter a compound name."
        }

        if (sanitizedCommand.components.isEmpty()) {
            fieldErrors["components"] = "Add at least one component."
        }

        sanitizedCommand.components.forEachIndexed { index, component ->
            val targetKey = "components[$index].target"
            val proportionKey = "components[$index].proportion"
            if (component.reusableEntityId == null || component.reusableEntityId <= 0L) {
                fieldErrors[targetKey] = "Choose a saved ingredient or compound."
            } else if (index in duplicateIndexes) {
                fieldErrors[targetKey] = when (component.kind) {
                    CompoundComponentKind.INGREDIENT -> "This ingredient is already in the composition. Edit the existing row instead."
                    CompoundComponentKind.COMPOUND -> "This compound is already in the composition. Edit the existing row instead."
                }
            }
            val proportionBasisPoints = component.proportionBasisPoints
            if (proportionBasisPoints == null || proportionBasisPoints !in 1..10_000) {
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

        return repository.createCompound(sanitizedCommand)
    }
}

internal fun String.normalizeReusableName(): String {
    return trim().replace(Regex("\\s+"), " ")
}
