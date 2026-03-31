package com.example.app.features.compounds.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult

class CreateReusableCompoundUseCase(
    private val repository: ReusableCompoundRepository
) {
    suspend operator fun invoke(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> {
        val sanitizedCommand = command.copy(
            name = command.name.normalizeReusableName(),
            notes = command.notes?.trim()?.takeIf { it.isNotEmpty() }
        )

        if (sanitizedCommand.name.isBlank()) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = mapOf("name" to "Enter a compound name.")
                )
            )
        }

        return repository.createCompound(sanitizedCommand)
    }
}

private fun String.normalizeReusableName(): String {
    return trim().replace(Regex("\\s+"), " ")
}
