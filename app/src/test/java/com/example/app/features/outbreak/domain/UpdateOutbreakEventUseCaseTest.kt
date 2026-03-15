package com.example.app.features.outbreak.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateOutbreakEventUseCaseTest {

    private val fixedNow = 1_700_000_000_000L

    @Test
    fun rejects_missing_body_location_without_updating_repository() = runBlocking {
        val repository = FakeEditableOutbreakRepository()
        val useCase = UpdateOutbreakEventUseCase(repository, FakeOutbreakCurrentTimeProvider(fixedNow))

        val result = useCase(UpdateOutbreakEventCommand(9L, "Rash flare", "   ", fixedNow, null))

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter where on the body it happened.", error.fieldErrors["bodyLocation"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun trims_valid_values_before_updating_repository() = runBlocking {
        val repository = FakeEditableOutbreakRepository()
        val useCase = UpdateOutbreakEventUseCase(repository, FakeOutbreakCurrentTimeProvider(fixedNow))

        val result = useCase(UpdateOutbreakEventCommand(9L, "  Rash flare  ", "  Left arm  ", fixedNow, "  after workout  "))

        val success = result as AppResult.Success
        assertEquals("Rash flare", success.value.outbreakDescriptor)
        assertEquals("Left arm", success.value.bodyLocation)
        assertEquals("after workout", repository.updatedCommands.single().notes)
    }
}

private class FakeEditableOutbreakRepository : OutbreakRepository {
    val updatedCommands = mutableListOf<UpdateOutbreakEventCommand>()

    override suspend fun createOutbreak(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")

    override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> {
        return AppResult.Success(OutbreakEvent(id, "Rash flare", "Left arm", 1_700_000_000_000L, "notes"))
    }

    override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
        updatedCommands += command
        return AppResult.Success(
            OutbreakEvent(command.id, command.outbreakDescriptor, command.bodyLocation, command.occurredAtEpochMs, command.notes)
        )
    }
}

private class FakeOutbreakCurrentTimeProvider(private val now: Long) : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = now
}
