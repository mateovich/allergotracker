package com.example.app.features.outbreak.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateOutbreakEventUseCaseTest {

    private val fixedNow = 1_700_000_000_000L

    @Test
    fun returns_field_errors_when_required_values_are_missing() = runBlocking {
        val repository = FakeOutbreakRepository()
        val useCase = CreateOutbreakEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateOutbreakEventCommand(
                outbreakDescriptor = "   ",
                bodyLocation = " ",
                occurredAtEpochMs = fixedNow,
                notes = ""
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter what happened.", error.fieldErrors["outbreakDescriptor"])
        assertEquals("Enter where on the body it happened.", error.fieldErrors["bodyLocation"])
        assertTrue(repository.savedCommands.isEmpty())
    }

    @Test
    fun rejects_future_occurrence_time() = runBlocking {
        val useCase = CreateOutbreakEventUseCase(
            repository = FakeOutbreakRepository(),
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateOutbreakEventCommand(
                outbreakDescriptor = "Rash flare",
                bodyLocation = "Left arm",
                occurredAtEpochMs = fixedNow + 60_000L,
                notes = null
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Time cannot be in the future.", error.fieldErrors["occurredAtEpochMs"])
    }

    @Test
    fun rejects_non_positive_occurrence_time() = runBlocking {
        val useCase = CreateOutbreakEventUseCase(
            repository = FakeOutbreakRepository(),
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateOutbreakEventCommand(
                outbreakDescriptor = "Rash flare",
                bodyLocation = "Left arm",
                occurredAtEpochMs = 0L,
                notes = null
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid time.", error.fieldErrors["occurredAtEpochMs"])
    }

    @Test
    fun persists_trimmed_valid_outbreak_through_repository() = runBlocking {
        val repository = FakeOutbreakRepository()
        val useCase = CreateOutbreakEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateOutbreakEventCommand(
                outbreakDescriptor = "  Rash flare  ",
                bodyLocation = "  Left arm  ",
                occurredAtEpochMs = fixedNow,
                notes = "  after workout  "
            )
        )

        val success = result as AppResult.Success
        assertEquals("Rash flare", success.value.outbreakDescriptor)
        assertEquals("Left arm", success.value.bodyLocation)
        assertEquals("Rash flare", repository.savedCommands.single().outbreakDescriptor)
        assertEquals("Left arm", repository.savedCommands.single().bodyLocation)
        assertEquals("after workout", repository.savedCommands.single().notes)
    }
}

private class FakeOutbreakRepository : OutbreakRepository {
    val savedCommands = mutableListOf<CreateOutbreakEventCommand>()

    override suspend fun createOutbreak(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent> {
        savedCommands += command
        return AppResult.Success(
            OutbreakEvent(
                id = 1L,
                outbreakDescriptor = command.outbreakDescriptor,
                bodyLocation = command.bodyLocation,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes
            )
        )
    }

    override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> {
        return AppResult.Error(DomainError.IntegrityError("unused"))
    }

    override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
        return AppResult.Error(DomainError.IntegrityError("unused"))
    }

    override suspend fun deleteOutbreak(id: Long): AppResult<Unit> {
        return AppResult.Error(DomainError.IntegrityError("unused"))
    }
}

private class FakeCurrentTimeProvider(
    private val nowEpochMs: Long
) : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = nowEpochMs
}
