package com.example.app.features.exposure.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
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

        val result = useCase(UpdateExposureEventCommand(5L, "Pollen", fixedNow + 60_000L, null))

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Time cannot be in the future.", error.fieldErrors["occurredAtEpochMs"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun trims_valid_values_before_updating_repository() = runBlocking {
        val repository = FakeEditableExposureRepository()
        val useCase = UpdateExposureEventUseCase(repository, FakeExposureCurrentTimeProvider(fixedNow))

        val result = useCase(UpdateExposureEventCommand(5L, "  Pollen  ", fixedNow, "  windy  "))

        val success = result as AppResult.Success
        assertEquals("Pollen", success.value.exposureType)
        assertEquals("Pollen", repository.updatedCommands.single().exposureType)
        assertEquals("windy", repository.updatedCommands.single().notes)
    }
}

private class FakeEditableExposureRepository : ExposureRepository {
    val updatedCommands = mutableListOf<UpdateExposureEventCommand>()

    override suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")

    override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
        return AppResult.Success(ExposureEvent(id, "Pollen", 1_700_000_000_000L, "notes"))
    }

    override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
        updatedCommands += command
        return AppResult.Success(ExposureEvent(command.id, command.exposureType, command.occurredAtEpochMs, command.notes))
    }

    override suspend fun deleteExposure(id: Long): AppResult<Unit> = error("unused")
}

private class FakeExposureCurrentTimeProvider(private val now: Long) : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = now
}
