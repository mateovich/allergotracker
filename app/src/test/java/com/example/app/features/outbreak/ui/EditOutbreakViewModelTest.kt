package com.example.app.features.outbreak.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import com.example.app.features.eventcatalog.domain.ReusableEventCatalog
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogRepository
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.outbreak.domain.DeleteOutbreakEventUseCase
import com.example.app.features.outbreak.domain.GetOutbreakEventUseCase
import com.example.app.features.outbreak.domain.OutbreakEvent
import com.example.app.features.outbreak.domain.OutbreakRepository
import com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand
import com.example.app.features.outbreak.domain.UpdateOutbreakEventUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditOutbreakViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedNow = 1_700_000_000_000L
    private val existingOccurredAt = fixedNow - (2L * 60L * 60L * 1000L)

    @Test
    fun load_prefills_state_from_existing_record() = runBlocking {
        val viewModel = buildViewModel()

        viewModel.onIntent(EditOutbreakUiIntent.Load)

        assertEquals("Skin flare", viewModel.state.value.outbreakDescriptor)
        assertEquals("Left arm", viewModel.state.value.bodyLocation)
        assertEquals("Skin flare at Left arm", viewModel.state.value.deleteRecordLabel)
        assertEquals("after workout", viewModel.state.value.notes)
        assertEquals(3, viewModel.state.value.selectedIntensityLevel)
        assertTrue(viewModel.state.value.canDelete)
    }

    @Test
    fun selecting_intensity_updates_loaded_state_and_save_command() = runBlocking {
        var savedCommand: UpdateOutbreakEventCommand? = null
        val repository = object : OutbreakRepository {
            override suspend fun createOutbreak(command: com.example.app.features.outbreak.domain.CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")
            override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> = AppResult.Success(OutbreakEvent(id, "Skin flare", "Left arm", existingOccurredAt, "after workout"))
            override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
                savedCommand = command
                return AppResult.Success(
                    OutbreakEvent(
                        id = command.id,
                        outbreakDescriptor = command.outbreakDescriptor,
                        bodyLocation = command.bodyLocation,
                        occurredAtEpochMs = command.occurredAtEpochMs,
                        notes = command.notes,
                        intensityLevel = command.intensityLevel
                    )
                )
            }
            override suspend fun deleteOutbreak(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditOutbreakViewModel(
            recordId = 9L,
            getOutbreakEventUseCase = GetOutbreakEventUseCase(repository),
            deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(repository),
            updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditOutbreakUiIntent.Load)
        viewModel.onIntent(EditOutbreakUiIntent.SelectIntensityLevel(4))
        viewModel.onIntent(EditOutbreakUiIntent.Save)

        assertEquals(4, viewModel.state.value.selectedIntensityLevel)
        assertEquals(4, savedCommand?.intensityLevel)
    }

    @Test
    fun invalid_input_disables_save() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditOutbreakUiIntent.Load)
        viewModel.onIntent(EditOutbreakUiIntent.UpdateBodyLocation("   "))
        viewModel.onIntent(EditOutbreakUiIntent.Save)

        assertEquals("   ", viewModel.state.value.bodyLocation)
        assertTrue(!viewModel.state.value.canSave)
        assertTrue(!viewModel.state.value.isSaving)
    }

    @Test
    fun successful_save_navigates_back_to_records() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditOutbreakUiIntent.Load)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditOutbreakUiIntent.UpdateOutbreakDescriptor("Skin flare updated"))
        viewModel.onIntent(EditOutbreakUiIntent.Save)

        assertEquals(EditOutbreakUiEffect.NavigateToRecords, effect.await())
    }

    @Test
    fun delete_confirmation_can_open_and_close_without_losing_form_state() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditOutbreakUiIntent.Load)

        viewModel.onIntent(EditOutbreakUiIntent.ShowDeleteConfirmation)
        assertTrue(viewModel.state.value.isDeleteConfirmationVisible)

        viewModel.onIntent(EditOutbreakUiIntent.DismissDeleteConfirmation)

        assertTrue(!viewModel.state.value.isDeleteConfirmationVisible)
        assertEquals("Skin flare", viewModel.state.value.outbreakDescriptor)
    }

    @Test
    fun successful_delete_navigates_back_to_records() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditOutbreakUiIntent.Load)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditOutbreakUiIntent.ShowDeleteConfirmation)
        viewModel.onIntent(EditOutbreakUiIntent.ConfirmDelete)

        assertEquals(EditOutbreakUiEffect.NavigateToRecords, effect.await())
    }

    @Test
    fun delete_confirmation_uses_persisted_label_not_unsaved_form_edit() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditOutbreakUiIntent.Load)

        viewModel.onIntent(EditOutbreakUiIntent.UpdateOutbreakDescriptor("Skin flare edited"))
        viewModel.onIntent(EditOutbreakUiIntent.UpdateBodyLocation("Right arm"))

        assertEquals("Skin flare at Left arm", viewModel.state.value.deleteRecordLabel)
        assertTrue(viewModel.state.value.canDelete)
    }

    @Test
    fun delete_remains_available_when_persisted_record_lacks_body_location() = runBlocking {
        val repository = object : OutbreakRepository {
            override suspend fun createOutbreak(command: com.example.app.features.outbreak.domain.CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")
            override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> = AppResult.Success(OutbreakEvent(id, "Skin flare", "", existingOccurredAt, "after workout"))
            override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> = AppResult.Success(OutbreakEvent(command.id, command.outbreakDescriptor, command.bodyLocation, command.occurredAtEpochMs, command.notes))
            override suspend fun deleteOutbreak(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditOutbreakViewModel(
            recordId = 9L,
            getOutbreakEventUseCase = GetOutbreakEventUseCase(repository),
            deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(repository),
            updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditOutbreakUiIntent.Load)
        viewModel.onIntent(EditOutbreakUiIntent.ShowDeleteConfirmation)

        assertEquals("Skin flare", viewModel.state.value.deleteRecordLabel)
        assertTrue(viewModel.state.value.canDelete)
        assertTrue(viewModel.state.value.isDeleteConfirmationVisible)
    }

    @Test
    fun confirm_delete_without_visible_confirmation_does_not_delete() = runBlocking {
        var deleteCalls = 0
        val repository = object : OutbreakRepository {
            override suspend fun createOutbreak(command: com.example.app.features.outbreak.domain.CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")
            override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> = AppResult.Success(OutbreakEvent(id, "Skin flare", "Left arm", existingOccurredAt, "after workout"))
            override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> = AppResult.Success(OutbreakEvent(command.id, command.outbreakDescriptor, command.bodyLocation, command.occurredAtEpochMs, command.notes))
            override suspend fun deleteOutbreak(id: Long): AppResult<Unit> {
                deleteCalls += 1
                return AppResult.Success(Unit)
            }
        }
        val viewModel = EditOutbreakViewModel(
            recordId = 9L,
            getOutbreakEventUseCase = GetOutbreakEventUseCase(repository),
            deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(repository),
            updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditOutbreakUiIntent.Load)
        viewModel.onIntent(EditOutbreakUiIntent.ConfirmDelete)

        assertEquals(0, deleteCalls)
        assertTrue(!viewModel.state.value.isDeleting)
    }

    @Test
    fun failed_delete_keeps_screen_recoverable_for_retry() = runBlocking {
        var deleteAttempts = 0
        val repository = object : OutbreakRepository {
            override suspend fun createOutbreak(command: com.example.app.features.outbreak.domain.CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")

            override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> {
                return AppResult.Success(OutbreakEvent(id, "Skin flare", "Left arm", existingOccurredAt, "after workout"))
            }

            override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
                return AppResult.Success(
                    OutbreakEvent(command.id, command.outbreakDescriptor, command.bodyLocation, command.occurredAtEpochMs, command.notes)
                )
            }

            override suspend fun deleteOutbreak(id: Long): AppResult<Unit> {
                deleteAttempts += 1
                return if (deleteAttempts == 1) {
                    AppResult.Error(DomainError.PersistenceError("Couldn't delete outbreak locally. Try again."))
                } else {
                    AppResult.Success(Unit)
                }
            }
        }
        val viewModel = EditOutbreakViewModel(
            recordId = 9L,
            getOutbreakEventUseCase = GetOutbreakEventUseCase(repository),
            deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(repository),
            updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditOutbreakUiIntent.Load)
        viewModel.onIntent(EditOutbreakUiIntent.ShowDeleteConfirmation)
        viewModel.onIntent(EditOutbreakUiIntent.ConfirmDelete)

        assertEquals("Couldn't delete outbreak locally. Try again.", viewModel.state.value.formMessage)
        assertTrue(!viewModel.state.value.isDeleting)
        assertTrue(!viewModel.state.value.isDeleteConfirmationVisible)

        viewModel.onIntent(EditOutbreakUiIntent.ShowDeleteConfirmation)

        assertTrue(viewModel.state.value.isDeleteConfirmationVisible)
    }

    @Test
    fun time_preset_reanchors_edit_time_from_current_time() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditOutbreakUiIntent.Load)

        viewModel.onIntent(EditOutbreakUiIntent.SelectTimePreset(OutbreakTimePreset.ONE_HOUR_AGO))

        assertEquals(fixedNow - (60L * 60L * 1000L), viewModel.state.value.occurredAtEpochMs)
    }

    @Test
    fun invalid_save_does_not_call_update() = runBlocking {
        var updateCalls = 0
        val repository = object : OutbreakRepository {
            override suspend fun createOutbreak(command: com.example.app.features.outbreak.domain.CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")
            override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> = AppResult.Success(OutbreakEvent(id, "Skin flare", "Left arm", existingOccurredAt, "after workout"))
            override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
                updateCalls += 1
                return AppResult.Success(OutbreakEvent(command.id, command.outbreakDescriptor, command.bodyLocation, command.occurredAtEpochMs, command.notes))
            }
            override suspend fun deleteOutbreak(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditOutbreakViewModel(
            recordId = 9L,
            getOutbreakEventUseCase = GetOutbreakEventUseCase(repository),
            deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(repository),
            updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditOutbreakUiIntent.Load)
        viewModel.onIntent(EditOutbreakUiIntent.UpdateBodyLocation("   "))
        viewModel.onIntent(EditOutbreakUiIntent.Save)

        assertEquals(0, updateCalls)
    }

    @Test
    fun second_save_while_first_is_in_flight_is_ignored() = runBlocking {
        var updateCalls = 0
        val updateGate = CompletableDeferred<Unit>()
        val repository = object : OutbreakRepository {
            override suspend fun createOutbreak(command: com.example.app.features.outbreak.domain.CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")
            override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> = AppResult.Success(OutbreakEvent(id, "Skin flare", "Left arm", existingOccurredAt, "after workout"))
            override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
                updateCalls += 1
                updateGate.await()
                return AppResult.Success(OutbreakEvent(command.id, command.outbreakDescriptor, command.bodyLocation, command.occurredAtEpochMs, command.notes))
            }
            override suspend fun deleteOutbreak(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditOutbreakViewModel(
            recordId = 9L,
            getOutbreakEventUseCase = GetOutbreakEventUseCase(repository),
            deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(repository),
            updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditOutbreakUiIntent.Load)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditOutbreakUiIntent.Save)
        viewModel.onIntent(EditOutbreakUiIntent.Save)
        updateGate.complete(Unit)

        assertEquals(EditOutbreakUiEffect.NavigateToRecords, effect.await())
        assertEquals(1, updateCalls)
    }

    @Test
    fun failed_load_can_retry_and_recover() = runBlocking {
        var loadAttempts = 0
        val repository = object : OutbreakRepository {
            override suspend fun createOutbreak(command: com.example.app.features.outbreak.domain.CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")

            override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> {
                loadAttempts += 1
                return if (loadAttempts == 1) {
                    AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Couldn't load outbreak locally. Try again."))
                } else {
                    AppResult.Success(OutbreakEvent(id, "Skin flare", "Left arm", existingOccurredAt, "after workout"))
                }
            }

            override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
                return AppResult.Success(
                    OutbreakEvent(command.id, command.outbreakDescriptor, command.bodyLocation, command.occurredAtEpochMs, command.notes)
                )
            }

            override suspend fun deleteOutbreak(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditOutbreakViewModel(
            recordId = 9L,
            getOutbreakEventUseCase = GetOutbreakEventUseCase(repository),
            deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(repository),
            updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditOutbreakUiIntent.Load)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())

        viewModel.onIntent(EditOutbreakUiIntent.Load)

        assertEquals("Skin flare", viewModel.state.value.outbreakDescriptor)
        assertEquals(2, loadAttempts)
    }

    @Test
    fun saved_catalog_references_are_preserved_while_catalogs_finish_loading() = runBlocking {
        val repository = object : OutbreakRepository {
            override suspend fun createOutbreak(command: com.example.app.features.outbreak.domain.CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")
            override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> = AppResult.Success(
                OutbreakEvent(
                    id = id,
                    outbreakDescriptor = "Skin flare",
                    bodyLocation = "Left arm",
                    occurredAtEpochMs = existingOccurredAt,
                    notes = "after workout",
                    outbreakDescriptorCatalogId = 101L,
                    bodyLocationCatalogId = 102L
                )
            )
            override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> = AppResult.Success(
                OutbreakEvent(
                    command.id,
                    command.outbreakDescriptor,
                    command.bodyLocation,
                    command.occurredAtEpochMs,
                    command.notes,
                    command.intensityLevel,
                    command.outbreakDescriptorCatalogId,
                    command.bodyLocationCatalogId
                )
            )
            override suspend fun deleteOutbreak(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val catalogEntries = MutableStateFlow<List<ReusableEventCatalog>>(emptyList())
        val viewModel = EditOutbreakViewModel(
            recordId = 9L,
            getOutbreakEventUseCase = GetOutbreakEventUseCase(repository),
            deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(repository),
            updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow },
            observeReusableEventCatalogUseCase = ObserveReusableEventCatalogUseCase(
                FlowEditOutbreakReusableEventCatalogRepository(catalogEntries)
            )
        )

        viewModel.onIntent(EditOutbreakUiIntent.Load)

        assertEquals(101L, viewModel.state.value.selectedOutbreakDescriptorCatalogId)
        assertEquals(102L, viewModel.state.value.selectedBodyLocationCatalogId)
        assertEquals(null, viewModel.state.value.selectedOutbreakDescriptorSummary)
        assertEquals(null, viewModel.state.value.selectedBodyLocationSummary)

        catalogEntries.value = listOf(
            ReusableEventCatalog(
                id = 101L,
                kind = ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR,
                name = "Skin flare",
                usageCount = 4,
                createdAtEpochMs = fixedNow - 2_000L,
                lastUsedAtEpochMs = fixedNow,
                updatedAtEpochMs = fixedNow
            ),
            ReusableEventCatalog(
                id = 102L,
                kind = ReusableEventCatalogKind.BODY_LOCATION,
                name = "Left arm",
                usageCount = 4,
                createdAtEpochMs = fixedNow - 1_000L,
                lastUsedAtEpochMs = fixedNow,
                updatedAtEpochMs = fixedNow
            )
        )

        assertEquals("Saved outbreak descriptor selected: Skin flare", viewModel.state.value.selectedOutbreakDescriptorSummary)
        assertEquals("Saved body location selected: Left arm", viewModel.state.value.selectedBodyLocationSummary)
        assertEquals("", viewModel.state.value.formMessage)
    }

    @Test
    fun clearing_retired_catalog_selection_keeps_text_but_drops_stale_ids() = runBlocking {
        val viewModel = buildViewModel()

        viewModel.onIntent(EditOutbreakUiIntent.Load)
        viewModel.onIntent(EditOutbreakUiIntent.SelectOutbreakDescriptorCatalogOption(11L, "Skin flare"))
        viewModel.onIntent(EditOutbreakUiIntent.SelectBodyLocationCatalogOption(12L, "Left arm"))
        viewModel.onIntent(EditOutbreakUiIntent.ClearRetiredOutbreakDescriptorCatalogSelection)
        viewModel.onIntent(EditOutbreakUiIntent.ClearRetiredBodyLocationCatalogSelection)

        assertEquals("Skin flare", viewModel.state.value.outbreakDescriptor)
        assertEquals("Left arm", viewModel.state.value.bodyLocation)
        assertEquals(null, viewModel.state.value.selectedOutbreakDescriptorCatalogId)
        assertEquals(null, viewModel.state.value.selectedBodyLocationCatalogId)
        assertEquals(null, viewModel.state.value.selectedOutbreakDescriptorSummary)
        assertEquals(null, viewModel.state.value.selectedBodyLocationSummary)
        assertEquals("Saved body location was retired. Review the text value before saving.", viewModel.state.value.formMessage)
    }

    private fun buildViewModel(): EditOutbreakViewModel {
        val repository = object : OutbreakRepository {
            override suspend fun createOutbreak(command: com.example.app.features.outbreak.domain.CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")

            override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> {
                return AppResult.Success(OutbreakEvent(id, "Skin flare", "Left arm", existingOccurredAt, "after workout"))
            }

            override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
                return AppResult.Success(
                    OutbreakEvent(command.id, command.outbreakDescriptor, command.bodyLocation, command.occurredAtEpochMs, command.notes)
                )
            }

            override suspend fun deleteOutbreak(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        return EditOutbreakViewModel(
            recordId = 9L,
            getOutbreakEventUseCase = GetOutbreakEventUseCase(repository),
            deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(repository),
            updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )
    }
}

private class FakeEditOutbreakReusableEventCatalogRepository(
    private val entries: List<ReusableEventCatalog>
) : ReusableEventCatalogRepository {
    override fun observeCatalog(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>> {
        return flowOf(entries.filter { it.kind == kind })
    }

    override suspend fun getCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? {
        return entries.firstOrNull { it.kind == kind && it.id == id }
    }

    override suspend fun existsCatalogEntry(kind: ReusableEventCatalogKind, id: Long): Boolean {
        return entries.any { it.kind == kind && it.id == id }
    }

    override suspend fun ensureCatalogEntry(kind: ReusableEventCatalogKind, name: String): ReusableEventCatalog {
        return entries.first { it.kind == kind && it.name == name }
    }

    override suspend fun touchCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? {
        return entries.firstOrNull { it.kind == kind && it.id == id }
    }
}

private class FlowEditOutbreakReusableEventCatalogRepository(
    private val entries: MutableStateFlow<List<ReusableEventCatalog>>
) : ReusableEventCatalogRepository {
    override fun observeCatalog(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>> {
        return entries.asStateFlow().map { current -> current.filter { it.kind == kind } }
    }

    override suspend fun getCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? {
        return entries.value.firstOrNull { it.kind == kind && it.id == id }
    }

    override suspend fun existsCatalogEntry(kind: ReusableEventCatalogKind, id: Long): Boolean {
        return entries.value.any { it.kind == kind && it.id == id }
    }

    override suspend fun ensureCatalogEntry(kind: ReusableEventCatalogKind, name: String): ReusableEventCatalog {
        return entries.value.first { it.kind == kind && it.name == name }
    }

    override suspend fun touchCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? {
        return entries.value.firstOrNull { it.kind == kind && it.id == id }
    }
}
