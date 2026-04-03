package com.example.app.features.outbreak.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.eventcatalog.domain.ReusableEventCatalog
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
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

        val result = useCase(
            UpdateOutbreakEventCommand(
                id = 9L,
                outbreakDescriptor = "Rash flare",
                bodyLocation = "   ",
                occurredAtEpochMs = fixedNow,
                notes = null,
                intensityLevel = 3
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter where on the body it happened.", error.fieldErrors["bodyLocation"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun trims_valid_values_before_updating_repository() = runBlocking {
        val repository = FakeEditableOutbreakRepository()
        val useCase = UpdateOutbreakEventUseCase(repository, FakeOutbreakCurrentTimeProvider(fixedNow))

        val result = useCase(
            UpdateOutbreakEventCommand(
                id = 9L,
                outbreakDescriptor = "  Rash flare  ",
                bodyLocation = "  Left arm  ",
                occurredAtEpochMs = fixedNow,
                notes = "  after workout  ",
                intensityLevel = 4
            )
        )

        val success = result as AppResult.Success
        assertEquals("Rash flare", success.value.outbreakDescriptor)
        assertEquals("Left arm", success.value.bodyLocation)
        assertEquals(4, success.value.intensityLevel)
        assertEquals(4, repository.updatedCommands.single().intensityLevel)
        assertEquals("after workout", repository.updatedCommands.single().notes)
    }

    @Test
    fun rejects_invalid_intensity_without_updating_repository() = runBlocking {
        val repository = FakeEditableOutbreakRepository()
        val useCase = UpdateOutbreakEventUseCase(repository, FakeOutbreakCurrentTimeProvider(fixedNow))

        val result = useCase(
            UpdateOutbreakEventCommand(
                id = 9L,
                outbreakDescriptor = "Rash flare",
                bodyLocation = "Left arm",
                occurredAtEpochMs = fixedNow,
                notes = null,
                intensityLevel = 7
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid intensity.", error.fieldErrors["intensityLevel"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun rejects_missing_saved_catalog_references_without_updating_repository() = runBlocking {
        val repository = FakeEditableOutbreakRepository()
        val useCase = UpdateOutbreakEventUseCase(
            repository,
            FakeOutbreakCurrentTimeProvider(fixedNow),
            outbreakDescriptorCatalogExists = { false },
            bodyLocationCatalogExists = { false }
        )

        val result = useCase(
            UpdateOutbreakEventCommand(
                9L,
                "Rash flare",
                "Left arm",
                fixedNow,
                null,
                intensityLevel = 3,
                outbreakDescriptorCatalogId = 12L,
                bodyLocationCatalogId = 13L
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a saved outbreak descriptor that still exists.", error.fieldErrors["outbreakDescriptor"])
        assertEquals("Choose a saved body location that still exists.", error.fieldErrors["bodyLocation"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun rejects_mismatched_saved_catalog_references_without_updating_repository() = runBlocking {
        val repository = FakeEditableOutbreakRepository()
        val useCase = UpdateOutbreakEventUseCase(
            repository,
            FakeOutbreakCurrentTimeProvider(fixedNow),
            outbreakDescriptorCatalogExists = { true },
            bodyLocationCatalogExists = { true },
            getOutbreakDescriptorCatalogEntry = {
                ReusableEventCatalog(it, ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, "Rash flare", 1, 1L, 1L, 1L)
            },
            getBodyLocationCatalogEntry = {
                ReusableEventCatalog(it, ReusableEventCatalogKind.BODY_LOCATION, "Left arm", 1, 1L, 1L, 1L)
            }
        )

        val result = useCase(
            UpdateOutbreakEventCommand(
                9L,
                "Heat rash",
                "Right arm",
                fixedNow,
                null,
                intensityLevel = 3,
                outbreakDescriptorCatalogId = 12L,
                bodyLocationCatalogId = 13L
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals(
            "Use the saved outbreak descriptor exactly as selected, or clear it before editing.",
            error.fieldErrors["outbreakDescriptor"]
        )
        assertEquals(
            "Use the saved body location exactly as selected, or clear it before editing.",
            error.fieldErrors["bodyLocation"]
        )
        assertTrue(repository.updatedCommands.isEmpty())
    }
}

private class FakeEditableOutbreakRepository : OutbreakRepository {
    val updatedCommands = mutableListOf<UpdateOutbreakEventCommand>()

    override suspend fun createOutbreak(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")

    override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> {
        return AppResult.Success(
            OutbreakEvent(
                id = id,
                outbreakDescriptor = "Rash flare",
                bodyLocation = "Left arm",
                occurredAtEpochMs = 1_700_000_000_000L,
                notes = "notes",
                intensityLevel = 3
            )
        )
    }

    override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
        updatedCommands += command
        return AppResult.Success(
            OutbreakEvent(
                command.id,
                command.outbreakDescriptor,
                command.bodyLocation,
                command.occurredAtEpochMs,
                command.notes
                ,
                command.intensityLevel
            )
        )
    }

    override suspend fun deleteOutbreak(id: Long): AppResult<Unit> = error("unused")
}

private class FakeOutbreakCurrentTimeProvider(private val now: Long) : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = now
}
