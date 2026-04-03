package com.example.app.features.exposure.domain

import com.example.app.features.ingestion.domain.CurrentTimeProvider

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
