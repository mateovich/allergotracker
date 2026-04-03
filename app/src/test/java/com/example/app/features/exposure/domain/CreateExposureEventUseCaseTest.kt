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

class CreateExposureEventUseCaseTest {

    private val fixedNow = 1_700_000_000_000L

    @Test
    fun returns_field_errors_when_required_values_are_missing() = runBlocking {
        val repository = FakeExposureRepository()
        val useCase = CreateExposureEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateExposureEventCommand(
                exposureType = "   ",
                intensityLevel = 3,
                occurredAtEpochMs = fixedNow,
                notes = ""
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter what you were exposed to.", error.fieldErrors["exposureType"])
        assertTrue(repository.savedCommands.isEmpty())
    }

    @Test
    fun rejects_future_occurrence_time() = runBlocking {
        val useCase = CreateExposureEventUseCase(
            repository = FakeExposureRepository(),
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateExposureEventCommand(
                exposureType = "Pollen",
                intensityLevel = 3,
                occurredAtEpochMs = fixedNow + 60_000L,
                notes = null
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Time cannot be in the future.", error.fieldErrors["occurredAtEpochMs"])
    }

    @Test
    fun rejects_non_positive_occurrence_time() = runBlocking {
        val useCase = CreateExposureEventUseCase(
            repository = FakeExposureRepository(),
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateExposureEventCommand(
                exposureType = "Pollen",
                intensityLevel = 3,
                occurredAtEpochMs = 0L,
                notes = null
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid time.", error.fieldErrors["occurredAtEpochMs"])
    }

    @Test
    fun persists_valid_exposure_through_repository() = runBlocking {
        val repository = FakeExposureRepository()
        val useCase = CreateExposureEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateExposureEventCommand(
                exposureType = "  Pollen  ",
                intensityLevel = 4,
                occurredAtEpochMs = fixedNow,
                notes = "  windy afternoon  "
            )
        )

        val success = result as AppResult.Success
        assertEquals("Pollen", success.value.exposureType)
        assertEquals(4, success.value.intensityLevel)
        assertEquals("Pollen", repository.savedCommands.single().exposureType)
        assertEquals(4, repository.savedCommands.single().intensityLevel)
        assertEquals("windy afternoon", repository.savedCommands.single().notes)
    }

    @Test
    fun rejects_intensity_outside_supported_range() = runBlocking {
        val repository = FakeExposureRepository()
        val useCase = CreateExposureEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateExposureEventCommand(
                exposureType = "Pollen",
                intensityLevel = 6,
                occurredAtEpochMs = fixedNow,
                notes = null
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid intensity.", error.fieldErrors["intensityLevel"])
        assertTrue(repository.savedCommands.isEmpty())
    }

    @Test
    fun rejects_missing_saved_catalog_reference_before_repository_write() = runBlocking {
        val repository = FakeExposureRepository()
        val useCase = CreateExposureEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow),
            exposureCatalogExists = { false }
        )

        val result = useCase(
            CreateExposureEventCommand(
                exposureType = "Pollen",
                intensityLevel = 3,
                occurredAtEpochMs = fixedNow,
                notes = null,
                exposureTypeCatalogId = 99L
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a saved exposure type that still exists.", error.fieldErrors["exposureType"])
        assertTrue(repository.savedCommands.isEmpty())
    }

    @Test
    fun rejects_saved_catalog_reference_when_typed_label_no_longer_matches_selected_value() = runBlocking {
        val repository = FakeExposureRepository()
        val useCase = CreateExposureEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow),
            exposureCatalogExists = { true },
            getExposureCatalogEntry = {
                ReusableEventCatalog(it, ReusableEventCatalogKind.EXPOSURE_TYPE, "Pollen", 1, 1L, 1L, 1L)
            }
        )

        val result = useCase(
            CreateExposureEventCommand(
                exposureType = "Dust",
                intensityLevel = 3,
                occurredAtEpochMs = fixedNow,
                notes = null,
                exposureTypeCatalogId = 99L
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals(
            "Use the saved exposure type exactly as selected, or clear it before editing.",
            error.fieldErrors["exposureType"]
        )
        assertTrue(repository.savedCommands.isEmpty())
    }
}

private class FakeExposureRepository : ExposureRepository {
    val savedCommands = mutableListOf<CreateExposureEventCommand>()

    override suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent> {
        savedCommands += command
        return AppResult.Success(
            ExposureEvent(
                id = 1L,
                exposureType = command.exposureType,
                intensityLevel = command.intensityLevel,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes
            )
        )
    }

    override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
        return AppResult.Error(DomainError.IntegrityError("unused"))
    }

    override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
        return AppResult.Error(DomainError.IntegrityError("unused"))
    }

    override suspend fun deleteExposure(id: Long): AppResult<Unit> {
        return AppResult.Error(DomainError.IntegrityError("unused"))
    }
}

private class FakeCurrentTimeProvider(
    private val nowEpochMs: Long
) : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = nowEpochMs
}
