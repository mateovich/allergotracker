package com.example.app.features.outbreak.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider

class UpdateOutbreakEventUseCase(
    private val repository: OutbreakRepository,
    private val currentTimeProvider: CurrentTimeProvider
) {
    suspend operator fun invoke(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
        val sanitizedCommand = command.copy(
            outbreakDescriptor = command.outbreakDescriptor.trim(),
            bodyLocation = command.bodyLocation.trim(),
            notes = command.notes?.trim()?.takeIf { it.isNotEmpty() }
        )
        val fieldErrors = buildMap {
            if (sanitizedCommand.outbreakDescriptor.isBlank()) {
                put("outbreakDescriptor", "Enter what happened.")
            }
            if (sanitizedCommand.bodyLocation.isBlank()) {
                put("bodyLocation", "Enter where on the body it happened.")
            }
            if (sanitizedCommand.occurredAtEpochMs <= 0L) {
                put("occurredAtEpochMs", "Choose a valid time.")
            } else if (sanitizedCommand.occurredAtEpochMs > currentTimeProvider.nowEpochMillis()) {
                put("occurredAtEpochMs", "Time cannot be in the future.")
            }
        }

        if (fieldErrors.isNotEmpty()) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = fieldErrors
                )
            )
        }

        return repository.updateOutbreak(sanitizedCommand)
    }
}
