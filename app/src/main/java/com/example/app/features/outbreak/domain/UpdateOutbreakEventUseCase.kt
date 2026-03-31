package com.example.app.features.outbreak.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider

class UpdateOutbreakEventUseCase(
    private val repository: OutbreakRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val outbreakDescriptorCatalogExists: suspend (Long) -> Boolean = { false },
    private val bodyLocationCatalogExists: suspend (Long) -> Boolean = { false }
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
            val outbreakDescriptorCatalogId = sanitizedCommand.outbreakDescriptorCatalogId
            if (outbreakDescriptorCatalogId != null && !outbreakDescriptorCatalogExists(outbreakDescriptorCatalogId)) {
                put("outbreakDescriptor", "Choose a saved outbreak descriptor that still exists.")
            }
            val bodyLocationCatalogId = sanitizedCommand.bodyLocationCatalogId
            if (bodyLocationCatalogId != null && !bodyLocationCatalogExists(bodyLocationCatalogId)) {
                put("bodyLocation", "Choose a saved body location that still exists.")
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
