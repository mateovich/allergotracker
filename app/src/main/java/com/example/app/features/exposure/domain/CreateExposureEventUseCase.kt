package com.example.app.features.exposure.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider

class CreateExposureEventUseCase(
    private val repository: ExposureRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val exposureCatalogExists: suspend (Long) -> Boolean = { false }
) {
    suspend operator fun invoke(command: CreateExposureEventCommand): AppResult<ExposureEvent> {
        val sanitizedCommand = command.copy(
            exposureType = command.exposureType.trim(),
            notes = command.notes?.trim()?.takeIf { it.isNotEmpty() }
        )
        val fieldErrors = buildMap {
            if (sanitizedCommand.exposureType.isBlank()) {
                put("exposureType", "Enter what you were exposed to.")
            }
            if (sanitizedCommand.occurredAtEpochMs <= 0L) {
                put("occurredAtEpochMs", "Choose a valid time.")
            } else if (sanitizedCommand.occurredAtEpochMs > currentTimeProvider.nowEpochMillis()) {
                put("occurredAtEpochMs", "Time cannot be in the future.")
            }
            val catalogId = sanitizedCommand.exposureTypeCatalogId
            if (catalogId != null && !exposureCatalogExists(catalogId)) {
                put("exposureType", "Choose a saved exposure type that still exists.")
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

        return repository.createExposure(sanitizedCommand)
    }
}
