package com.example.app.features.outbreak.domain

import com.example.app.features.ingestion.domain.CurrentTimeProvider

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
