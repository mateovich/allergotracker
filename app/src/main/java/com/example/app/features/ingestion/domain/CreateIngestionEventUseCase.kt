package com.example.app.features.ingestion.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.IngestionQuantityOption.Companion.fromLevel

class CreateIngestionEventUseCase(
    private val repository: IngestionRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val reusableEntityExists: suspend (ReusableEntityKind, Long) -> Boolean = { _, _ -> true }
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
            putAll(sanitizedCommand.reusableIdentityErrors())
        }

        if (fieldErrors.isNotEmpty()) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = fieldErrors
                )
            )
        }

        val reusableEntityKind = sanitizedCommand.reusableEntityKind
        val reusableEntityId = sanitizedCommand.reusableEntityId
        if (
            reusableEntityKind != null &&
            reusableEntityId != null &&
            !reusableEntityExists(reusableEntityKind, reusableEntityId)
        ) {
            return AppResult.Error(DomainError.IntegrityError("Selected reusable item is no longer available."))
        }

        return repository.createIngestion(sanitizedCommand)
    }
}

private fun CreateIngestionEventCommand.reusableIdentityErrors(): Map<String, String> {
    val kind = reusableEntityKind
    val id = reusableEntityId
    if (kind == null && id == null) {
        return emptyMap()
    }
    if (kind == null || id == null || id <= 0L) {
        return mapOf("itemName" to "Choose a valid reusable item or clear the selection.")
    }
    return emptyMap()
}
