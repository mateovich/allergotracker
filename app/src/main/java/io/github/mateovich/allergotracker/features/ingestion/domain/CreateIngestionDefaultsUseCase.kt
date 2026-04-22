package io.github.mateovich.allergotracker.features.ingestion.domain

class CreateIngestionDefaultsUseCase(
    private val currentTimeProvider: CurrentTimeProvider
) {
    operator fun invoke(): CreateIngestionDefaults {
        return CreateIngestionDefaults(
            occurredAtEpochMs = currentTimeProvider.nowEpochMillis()
        )
    }
}
