package io.github.mateovich.allergotracker.features.outbreak.domain

import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class CreateOutbreakDefaultsUseCaseTest {

    @Test
    fun returns_current_time_and_default_intensity() {
        val useCase = CreateOutbreakDefaultsUseCase(CurrentTimeProvider { 1_700_000_000_000L })

        val defaults = useCase()

        assertEquals(1_700_000_000_000L, defaults.occurredAtEpochMs)
        assertEquals(3, defaults.intensityLevel)
    }
}
