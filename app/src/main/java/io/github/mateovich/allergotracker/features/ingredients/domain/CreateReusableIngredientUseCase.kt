package io.github.mateovich.allergotracker.features.ingredients.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult

class CreateReusableIngredientUseCase(
    private val repository: ReusableIngredientRepository
) {
    suspend operator fun invoke(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
        val sanitizedCommand = command.copy(
            name = command.name.normalizeReusableName(),
            notes = command.notes?.trim()?.takeIf { it.isNotEmpty() }
        )

        if (sanitizedCommand.name.isBlank()) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = mapOf("name" to "Enter an ingredient name.")
                )
            )
        }

        return repository.createIngredient(sanitizedCommand)
    }
}

internal fun String.normalizeReusableName(): String {
    return trim().replace(Regex("\\s+"), " ")
}
