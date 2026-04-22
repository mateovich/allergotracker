package io.github.mateovich.allergotracker.features.outbreak.domain

import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider

class CreateOutbreakDefaultsUseCase(
    private val currentTimeProvider: CurrentTimeProvider
) {
    operator fun invoke(): CreateOutbreakDefaults {
        return CreateOutbreakDefaults(
            occurredAtEpochMs = currentTimeProvider.nowEpochMillis(),
            intensityLevel = DEFAULT_INTENSITY_LEVEL
        )
    }
}
