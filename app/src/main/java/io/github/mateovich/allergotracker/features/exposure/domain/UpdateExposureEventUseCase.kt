package io.github.mateovich.allergotracker.features.exposure.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalog
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption.Companion.fromLevel

class UpdateExposureEventUseCase(
    private val repository: ExposureRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val exposureCatalogExists: suspend (Long) -> Boolean = { false },
    private val getExposureCatalogEntry: suspend (Long) -> ReusableEventCatalog? = { null }
) {
    suspend operator fun invoke(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
        val sanitizedCommand = command.copy(
            exposureType = command.exposureType.trim(),
            place = command.place?.trim()?.takeIf { it.isNotEmpty() },
            notes = command.notes?.trim()?.takeIf { it.isNotEmpty() }
        )
        val fieldErrors = buildMap {
            if (sanitizedCommand.exposureType.isBlank()) {
                put("exposureType", "Enter what you were exposed to.")
            }
            if (fromLevel(sanitizedCommand.intensityLevel) == null) {
                put("intensityLevel", "Choose a valid intensity.")
            }
            if (sanitizedCommand.occurredAtEpochMs <= 0L) {
                put("occurredAtEpochMs", "Choose a valid time.")
            } else if (sanitizedCommand.occurredAtEpochMs > currentTimeProvider.nowEpochMillis()) {
                put("occurredAtEpochMs", "Time cannot be in the future.")
            }
            val catalogId = sanitizedCommand.exposureTypeCatalogId
            if (catalogId != null && !exposureCatalogExists(catalogId)) {
                put("exposureType", "Choose a saved exposure type that still exists.")
            } else if (catalogId != null) {
                val catalogName = getExposureCatalogEntry(catalogId)?.name
                if (catalogName != null && catalogName != sanitizedCommand.exposureType) {
                    put("exposureType", "Use the saved exposure type exactly as selected, or clear it before editing.")
                }
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

        return repository.updateExposure(sanitizedCommand)
    }
}
