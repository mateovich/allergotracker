package com.example.app.features.exposure.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.eventcatalog.domain.ReusableEventCatalog
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateExposureEventUseCaseTest {

    private val fixedNow = 1_700_000_000_000L

    @Test
    fun rejects_future_occurrence_time_without_updating_repository() = runBlocking {
        val repository = FakeEditableExposureRepository()
        val useCase = UpdateExposureEventUseCase(repository, FakeExposureCurrentTimeProvider(fixedNow))

        val result = useCase(
            UpdateExposureEventCommand(
                id = 5L,
                exposureType = "Pollen",
                occurredAtEpochMs = fixedNow + 60_000L,
                notes = null,
                intensityLevel = 3
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Time cannot be in the future.", error.fieldErrors["occurredAtEpochMs"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun trims_valid_values_before_updating_repository() = runBlocking {
        val repository = FakeEditableExposureRepository()
        val useCase = UpdateExposureEventUseCase(repository, FakeExposureCurrentTimeProvider(fixedNow))

        val result = useCase(
            UpdateExposureEventCommand(
                id = 5L,
                exposureType = "  Pollen  ",
                occurredAtEpochMs = fixedNow,
                notes = "  windy  ",
                intensityLevel = 4
            )
        )

        val success = result as AppResult.Success
        assertEquals("Pollen", success.value.exposureType)
        assertEquals(4, success.value.intensityLevel)
        assertEquals("Pollen", repository.updatedCommands.single().exposureType)
        assertEquals(4, repository.updatedCommands.single().intensityLevel)
        assertEquals("windy", repository.updatedCommands.single().notes)
    }

    @Test
    fun rejects_invalid_intensity_without_updating_repository() = runBlocking {
        val repository = FakeEditableExposureRepository()
        val useCase = UpdateExposureEventUseCase(repository, FakeExposureCurrentTimeProvider(fixedNow))

        val result = useCase(
            UpdateExposureEventCommand(
                id = 5L,
                exposureType = "Pollen",
                occurredAtEpochMs = fixedNow,
                notes = null,
                intensityLevel = 0
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid intensity.", error.fieldErrors["intensityLevel"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun rejects_missing_saved_catalog_reference_without_updating_repository() = runBlocking {
        val repository = FakeEditableExposureRepository()
        val useCase = UpdateExposureEventUseCase(
            repository,
            FakeExposureCurrentTimeProvider(fixedNow),
            exposureCatalogExists = { false }
        )

        val result = useCase(
            UpdateExposureEventCommand(
                id = 5L,
                exposureType = "Pollen",
                occurredAtEpochMs = fixedNow,
                notes = null,
                intensityLevel = 3,
                exposureTypeCatalogId = 77L
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a saved exposure type that still exists.", error.fieldErrors["exposureType"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun rejects_mismatched_saved_catalog_reference_without_updating_repository() = runBlocking {
        val repository = FakeEditableExposureRepository()
        val useCase = UpdateExposureEventUseCase(
            repository,
            FakeExposureCurrentTimeProvider(fixedNow),
            exposureCatalogExists = { true },
            getExposureCatalogEntry = {
                ReusableEventCatalog(it, ReusableEventCatalogKind.EXPOSURE_TYPE, "Pollen", 1, 1L, 1L, 1L)
            }
        )

        val result = useCase(
            UpdateExposureEventCommand(
                id = 5L,
                exposureType = "Dust",
                occurredAtEpochMs = fixedNow,
                notes = null,
                intensityLevel = 3,
                exposureTypeCatalogId = 77L
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals(
            "Use the saved exposure type exactly as selected, or clear it before editing.",
            error.fieldErrors["exposureType"]
        )
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun accepts_valid_historical_edit_timestamp() = runBlocking {
        val repository = FakeEditableExposureRepository()
        val useCase = UpdateExposureEventUseCase(repository, FakeExposureCurrentTimeProvider(fixedNow))

        val historicalOccurredAt = fixedNow - 86_400_000L
        val result = useCase(
            UpdateExposureEventCommand(
                id = 5L,
                exposureType = "Pollen",
                occurredAtEpochMs = historicalOccurredAt,
                notes = "older record",
                intensityLevel = 3
            )
        )

        assertTrue(result is AppResult.Success)
        assertEquals(historicalOccurredAt, repository.updatedCommands.single().occurredAtEpochMs)
    }
}

private class FakeEditableExposureRepository : ExposureRepository {
    val updatedCommands = mutableListOf<UpdateExposureEventCommand>()

    override suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")

    override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
        return AppResult.Success(
            ExposureEvent(
                id = id,
                exposureType = "Pollen",
                occurredAtEpochMs = 1_700_000_000_000L,
                notes = "notes",
                intensityLevel = 3
            )
        )
    }

    override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
        updatedCommands += command
        return AppResult.Success(
            ExposureEvent(
                id = command.id,
                exposureType = command.exposureType,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes,
                intensityLevel = command.intensityLevel
            )
        )
    }

    override suspend fun deleteExposure(id: Long): AppResult<Unit> = error("unused")
}

private class FakeExposureCurrentTimeProvider(private val now: Long) : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = now
}
