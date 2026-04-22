package io.github.mateovich.allergotracker.features.exposure.domain

import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider

class CreateExposureDefaultsUseCase(
    private val currentTimeProvider: CurrentTimeProvider
) {
    operator fun invoke(): CreateExposureDefaults {
        return CreateExposureDefaults(
            occurredAtEpochMs = currentTimeProvider.nowEpochMillis(),
            intensityLevel = DEFAULT_INTENSITY_LEVEL
        )
    }
}
