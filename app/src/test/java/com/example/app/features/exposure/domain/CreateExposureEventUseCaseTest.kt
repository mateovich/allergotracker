package com.example.app.features.exposure.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
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
                occurredAtEpochMs = fixedNow,
                notes = "  windy afternoon  "
            )
        )

        val success = result as AppResult.Success
        assertEquals("Pollen", success.value.exposureType)
        assertEquals("Pollen", repository.savedCommands.single().exposureType)
        assertEquals("windy afternoon", repository.savedCommands.single().notes)
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
}

private class FakeCurrentTimeProvider(
    private val nowEpochMs: Long
) : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = nowEpochMs
}
