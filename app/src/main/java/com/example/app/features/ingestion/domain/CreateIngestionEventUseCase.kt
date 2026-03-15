package com.example.app.features.ingestion.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.IngestionQuantityOption.Companion.fromLevel

class CreateIngestionEventUseCase(
    private val repository: IngestionRepository,
    private val currentTimeProvider: CurrentTimeProvider
) {
    suspend operator fun invoke(command: CreateIngestionEventCommand): AppResult<IngestionEvent> {
        val sanitizedCommand = command.copy(
            itemName = command.itemName.trim(),
            notes = command.notes?.trim()?.takeIf { it.isNotEmpty() }
        )
        val fieldErrors = buildMap {
            if (sanitizedCommand.itemName.isBlank()) {
                put("itemName", "Enter what you ingested.")
            }
            if (sanitizedCommand.quantityLevel == null) {
                put("quantityLevel", "Choose a quantity.")
            } else if (fromLevel(sanitizedCommand.quantityLevel) == null) {
                put("quantityLevel", "Choose a valid quantity.")
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

        return repository.createIngestion(sanitizedCommand)
    }
}
