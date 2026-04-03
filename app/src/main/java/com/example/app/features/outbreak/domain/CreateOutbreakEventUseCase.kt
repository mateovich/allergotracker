package com.example.app.features.outbreak.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.eventcatalog.domain.ReusableEventCatalog
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.IngestionQuantityOption.Companion.fromLevel

class CreateOutbreakEventUseCase(
    private val repository: OutbreakRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val outbreakDescriptorCatalogExists: suspend (Long) -> Boolean = { false },
    private val bodyLocationCatalogExists: suspend (Long) -> Boolean = { false },
    private val getOutbreakDescriptorCatalogEntry: suspend (Long) -> ReusableEventCatalog? = { null },
    private val getBodyLocationCatalogEntry: suspend (Long) -> ReusableEventCatalog? = { null }
) {
    suspend operator fun invoke(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent> {
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
            if (fromLevel(sanitizedCommand.intensityLevel) == null) {
                put("intensityLevel", "Choose a valid intensity.")
            }
            if (sanitizedCommand.occurredAtEpochMs <= 0L) {
                put("occurredAtEpochMs", "Choose a valid time.")
            } else if (sanitizedCommand.occurredAtEpochMs > currentTimeProvider.nowEpochMillis()) {
                put("occurredAtEpochMs", "Time cannot be in the future.")
            }
            val outbreakDescriptorCatalogId = sanitizedCommand.outbreakDescriptorCatalogId
            if (outbreakDescriptorCatalogId != null && !outbreakDescriptorCatalogExists(outbreakDescriptorCatalogId)) {
                put("outbreakDescriptor", "Choose a saved outbreak descriptor that still exists.")
            } else if (outbreakDescriptorCatalogId != null) {
                val catalogName = getOutbreakDescriptorCatalogEntry(outbreakDescriptorCatalogId)?.name
                if (catalogName != null && catalogName != sanitizedCommand.outbreakDescriptor) {
                    put("outbreakDescriptor", "Use the saved outbreak descriptor exactly as selected, or clear it before editing.")
                }
            }
            val bodyLocationCatalogId = sanitizedCommand.bodyLocationCatalogId
            if (bodyLocationCatalogId != null && !bodyLocationCatalogExists(bodyLocationCatalogId)) {
                put("bodyLocation", "Choose a saved body location that still exists.")
            } else if (bodyLocationCatalogId != null) {
                val catalogName = getBodyLocationCatalogEntry(bodyLocationCatalogId)?.name
                if (catalogName != null && catalogName != sanitizedCommand.bodyLocation) {
                    put("bodyLocation", "Use the saved body location exactly as selected, or clear it before editing.")
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

        return repository.createOutbreak(sanitizedCommand)
    }
}
