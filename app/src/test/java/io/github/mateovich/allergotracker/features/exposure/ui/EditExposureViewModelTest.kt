package io.github.mateovich.allergotracker.features.exposure.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalog
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogRepository
import io.github.mateovich.allergotracker.features.exposure.domain.DeleteExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.ExposureEvent
import io.github.mateovich.allergotracker.features.exposure.domain.ExposureRepository
import io.github.mateovich.allergotracker.features.exposure.domain.GetExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.UpdateExposureEventCommand
import io.github.mateovich.allergotracker.features.exposure.domain.UpdateExposureEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
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

class EditExposureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedNow = 1_700_000_000_000L
    private val existingOccurredAt = fixedNow - (2L * 60L * 60L * 1000L)

    @Test
    fun load_prefills_state_from_existing_record() = runBlocking {
        val viewModel = buildViewModel()

        viewModel.onIntent(EditExposureUiIntent.Load)

        assertEquals("Pollen", viewModel.state.value.exposureType)
        assertEquals("Pollen", viewModel.state.value.deleteRecordLabel)
        assertEquals("outside", viewModel.state.value.notes)
        assertEquals(4, viewModel.state.value.selectedIntensityLevel)
        assertTrue(viewModel.state.value.occurredAtInput.isNotBlank())
        assertTrue(viewModel.state.value.canDelete)
    }

    @Test
    fun invalid_input_disables_save() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)
        viewModel.onIntent(EditExposureUiIntent.UpdateExposureType("   "))
        viewModel.onIntent(EditExposureUiIntent.Save)

        assertEquals("   ", viewModel.state.value.exposureType)
        assertTrue(!viewModel.state.value.canSave)
        assertTrue(!viewModel.state.value.isSaving)
    }

    @Test
    fun successful_save_navigates_back_to_records() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditExposureUiIntent.UpdateExposureType("Dust"))
        viewModel.onIntent(EditExposureUiIntent.Save)

        assertEquals(EditExposureUiEffect.NavigateToRecords, effect.await())
    }

    @Test
    fun invalid_intensity_save_surfaces_inline_error() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)

        viewModel.onIntent(EditExposureUiIntent.SelectIntensityLevel(6))
        viewModel.onIntent(EditExposureUiIntent.Save)

        assertEquals("Choose a valid intensity.", viewModel.state.value.intensityLevelError)
        assertEquals("Please fix the highlighted fields.", viewModel.state.value.formMessage)
    }

    @Test
    fun delete_confirmation_can_open_and_close_without_losing_form_state() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)

        viewModel.onIntent(EditExposureUiIntent.ShowDeleteConfirmation)
        assertTrue(viewModel.state.value.isDeleteConfirmationVisible)

        viewModel.onIntent(EditExposureUiIntent.DismissDeleteConfirmation)

        assertTrue(!viewModel.state.value.isDeleteConfirmationVisible)
        assertEquals("Pollen", viewModel.state.value.exposureType)
    }

    @Test
    fun successful_delete_navigates_back_to_records() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditExposureUiIntent.ShowDeleteConfirmation)
        viewModel.onIntent(EditExposureUiIntent.ConfirmDelete)

        assertEquals(EditExposureUiEffect.NavigateToRecords, effect.await())
    }

    @Test
    fun delete_confirmation_uses_persisted_label_not_unsaved_form_edit() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)

        viewModel.onIntent(EditExposureUiIntent.UpdateExposureType("Dust"))

        assertEquals("Pollen", viewModel.state.value.deleteRecordLabel)
        assertTrue(viewModel.state.value.canDelete)
    }

    @Test
    fun confirm_delete_without_visible_confirmation_does_not_delete() = runBlocking {
        var deleteCalls = 0
        val repository = object : ExposureRepository {
            override suspend fun createExposure(command: io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")
            override suspend fun getExposure(id: Long): AppResult<ExposureEvent> = AppResult.Success(ExposureEvent(id, "Pollen", existingOccurredAt, "outside"))
            override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> = AppResult.Success(ExposureEvent(command.id, command.exposureType, command.occurredAtEpochMs, command.notes))
            override suspend fun deleteExposure(id: Long): AppResult<Unit> {
                deleteCalls += 1
                return AppResult.Success(Unit)
            }
        }
        val viewModel = EditExposureViewModel(
            recordId = 5L,
            getExposureEventUseCase = GetExposureEventUseCase(repository),
            deleteExposureEventUseCase = DeleteExposureEventUseCase(repository),
            updateExposureEventUseCase = UpdateExposureEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditExposureUiIntent.Load)
        viewModel.onIntent(EditExposureUiIntent.ConfirmDelete)

        assertEquals(0, deleteCalls)
        assertTrue(!viewModel.state.value.isDeleting)
    }

    @Test
    fun failed_delete_keeps_screen_recoverable_for_retry() = runBlocking {
        var deleteAttempts = 0
        val repository = object : ExposureRepository {
            override suspend fun createExposure(command: io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")

            override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
                return AppResult.Success(ExposureEvent(id, "Pollen", existingOccurredAt, "outside"))
            }

            override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
                return AppResult.Success(ExposureEvent(command.id, command.exposureType, command.occurredAtEpochMs, command.notes))
            }

            override suspend fun deleteExposure(id: Long): AppResult<Unit> {
                deleteAttempts += 1
                return if (deleteAttempts == 1) {
                    AppResult.Error(DomainError.PersistenceError("Couldn't delete exposure locally. Try again."))
                } else {
                    AppResult.Success(Unit)
                }
            }
        }
        val viewModel = EditExposureViewModel(
            recordId = 5L,
            getExposureEventUseCase = GetExposureEventUseCase(repository),
            deleteExposureEventUseCase = DeleteExposureEventUseCase(repository),
            updateExposureEventUseCase = UpdateExposureEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditExposureUiIntent.Load)
        viewModel.onIntent(EditExposureUiIntent.ShowDeleteConfirmation)
        viewModel.onIntent(EditExposureUiIntent.ConfirmDelete)

        assertEquals("Couldn't delete exposure locally. Try again.", viewModel.state.value.formMessage)
        assertTrue(!viewModel.state.value.isDeleting)
        assertTrue(!viewModel.state.value.isDeleteConfirmationVisible)

        viewModel.onIntent(EditExposureUiIntent.ShowDeleteConfirmation)

        assertTrue(viewModel.state.value.isDeleteConfirmationVisible)
    }

    @Test
    fun valid_manual_datetime_input_updates_visible_time_label() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)

        viewModel.onIntent(EditExposureUiIntent.UpdateOccurredAtInput("2023-11-14 21:13"))

        assertEquals("21:13", viewModel.state.value.occurredAtLabel)
    }

    @Test
    fun time_preset_reanchors_edit_time_from_current_time() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)

        viewModel.onIntent(EditExposureUiIntent.SelectTimePreset(ExposureTimePreset.ONE_HOUR_AGO))

        assertEquals(fixedNow - (60L * 60L * 1000L), viewModel.state.value.occurredAtEpochMs)
    }

    @Test
    fun invalid_save_does_not_call_update() = runBlocking {
        var updateCalls = 0
        val repository = object : ExposureRepository {
            override suspend fun createExposure(command: io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")
            override suspend fun getExposure(id: Long): AppResult<ExposureEvent> = AppResult.Success(ExposureEvent(id, "Pollen", existingOccurredAt, "outside"))
            override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
                updateCalls += 1
                return AppResult.Success(ExposureEvent(command.id, command.exposureType, command.occurredAtEpochMs, command.notes))
            }
            override suspend fun deleteExposure(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditExposureViewModel(
            recordId = 5L,
            getExposureEventUseCase = GetExposureEventUseCase(repository),
            deleteExposureEventUseCase = DeleteExposureEventUseCase(repository),
            updateExposureEventUseCase = UpdateExposureEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditExposureUiIntent.Load)
        viewModel.onIntent(EditExposureUiIntent.UpdateExposureType("   "))
        viewModel.onIntent(EditExposureUiIntent.Save)

        assertEquals(0, updateCalls)
    }

    @Test
    fun second_save_while_first_is_in_flight_is_ignored() = runBlocking {
        var updateCalls = 0
        val updateGate = CompletableDeferred<Unit>()
        val repository = object : ExposureRepository {
            override suspend fun createExposure(command: io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")
            override suspend fun getExposure(id: Long): AppResult<ExposureEvent> = AppResult.Success(ExposureEvent(id, "Pollen", existingOccurredAt, "outside"))
            override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
                updateCalls += 1
                updateGate.await()
                return AppResult.Success(ExposureEvent(command.id, command.exposureType, command.occurredAtEpochMs, command.notes))
            }
            override suspend fun deleteExposure(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditExposureViewModel(
            recordId = 5L,
            getExposureEventUseCase = GetExposureEventUseCase(repository),
            deleteExposureEventUseCase = DeleteExposureEventUseCase(repository),
            updateExposureEventUseCase = UpdateExposureEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditExposureUiIntent.Load)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditExposureUiIntent.Save)
        viewModel.onIntent(EditExposureUiIntent.Save)
        updateGate.complete(Unit)

        assertEquals(EditExposureUiEffect.NavigateToRecords, effect.await())
        assertEquals(1, updateCalls)
    }

    @Test
    fun save_preserves_existing_intensity_when_user_does_not_change_it() = runBlocking {
        var savedCommand: UpdateExposureEventCommand? = null
        val repository = object : ExposureRepository {
            override suspend fun createExposure(command: io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")
            override suspend fun getExposure(id: Long): AppResult<ExposureEvent> = AppResult.Success(
                ExposureEvent(id, "Pollen", existingOccurredAt, "outside", intensityLevel = 5)
            )
            override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
                savedCommand = command
                return AppResult.Success(
                    ExposureEvent(
                        command.id,
                        command.exposureType,
                        command.occurredAtEpochMs,
                        command.notes,
                        intensityLevel = command.intensityLevel
                    )
                )
            }
            override suspend fun deleteExposure(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditExposureViewModel(
            recordId = 5L,
            getExposureEventUseCase = GetExposureEventUseCase(repository),
            deleteExposureEventUseCase = DeleteExposureEventUseCase(repository),
            updateExposureEventUseCase = UpdateExposureEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditExposureUiIntent.Load)
        viewModel.onIntent(EditExposureUiIntent.Save)

        assertEquals(5, savedCommand?.intensityLevel)
    }

    @Test
    fun failed_load_can_retry_and_recover() = runBlocking {
        var loadAttempts = 0
        val repository = object : ExposureRepository {
            override suspend fun createExposure(command: io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")

            override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
                loadAttempts += 1
                return if (loadAttempts == 1) {
                    AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Couldn't load exposure locally. Try again."))
                } else {
                    AppResult.Success(ExposureEvent(id, "Pollen", existingOccurredAt, "outside"))
                }
            }

            override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
                return AppResult.Success(ExposureEvent(command.id, command.exposureType, command.occurredAtEpochMs, command.notes))
            }

            override suspend fun deleteExposure(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditExposureViewModel(
            recordId = 5L,
            getExposureEventUseCase = GetExposureEventUseCase(repository),
            deleteExposureEventUseCase = DeleteExposureEventUseCase(repository),
            updateExposureEventUseCase = UpdateExposureEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditExposureUiIntent.Load)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())

        viewModel.onIntent(EditExposureUiIntent.Load)

        assertEquals("Pollen", viewModel.state.value.exposureType)
        assertEquals(2, loadAttempts)
    }

    @Test
    fun saved_catalog_reference_is_preserved_while_catalogs_finish_loading() = runBlocking {
        val repository = object : ExposureRepository {
            override suspend fun createExposure(command: io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")
            override suspend fun getExposure(id: Long): AppResult<ExposureEvent> = AppResult.Success(
                ExposureEvent(id, "Pollen", existingOccurredAt, "outside", exposureTypeCatalogId = 99L)
            )
            override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> = AppResult.Success(
                ExposureEvent(
                    id = command.id,
                    exposureType = command.exposureType,
                    occurredAtEpochMs = command.occurredAtEpochMs,
                    notes = command.notes,
                    intensityLevel = command.intensityLevel,
                    exposureTypeCatalogId = command.exposureTypeCatalogId
                )
            )
            override suspend fun deleteExposure(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val catalogEntries = MutableStateFlow<List<ReusableEventCatalog>>(emptyList())
        val viewModel = EditExposureViewModel(
            recordId = 5L,
            getExposureEventUseCase = GetExposureEventUseCase(repository),
            deleteExposureEventUseCase = DeleteExposureEventUseCase(repository),
            updateExposureEventUseCase = UpdateExposureEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow },
            observeReusableEventCatalogUseCase = ObserveReusableEventCatalogUseCase(
                FlowEditExposureReusableEventCatalogRepository(catalogEntries)
            )
        )

        viewModel.onIntent(EditExposureUiIntent.Load)

        assertEquals(99L, viewModel.state.value.selectedExposureTypeCatalogId)
        assertEquals(null, viewModel.state.value.selectedExposureTypeSummary)

        catalogEntries.value = listOf(
            ReusableEventCatalog(
                id = 99L,
                kind = ReusableEventCatalogKind.EXPOSURE_TYPE,
                name = "Pollen",
                usageCount = 3,
                createdAtEpochMs = fixedNow - 1_000L,
                lastUsedAtEpochMs = fixedNow,
                updatedAtEpochMs = fixedNow
            )
        )

        assertEquals(99L, viewModel.state.value.selectedExposureTypeCatalogId)
        assertEquals("Saved exposure type selected: Pollen", viewModel.state.value.selectedExposureTypeSummary)
        assertEquals("", viewModel.state.value.formMessage)
    }

    @Test
    fun clearing_retired_catalog_selection_keeps_text_but_drops_stale_id() = runBlocking {
        val viewModel = buildViewModel()

        viewModel.onIntent(EditExposureUiIntent.Load)
        viewModel.onIntent(EditExposureUiIntent.SelectExposureCatalogOption(5L, "Pollen"))
        viewModel.onIntent(EditExposureUiIntent.ClearRetiredExposureCatalogSelection)

        assertEquals("Pollen", viewModel.state.value.exposureType)
        assertEquals(null, viewModel.state.value.selectedExposureTypeCatalogId)
        assertEquals(null, viewModel.state.value.selectedExposureTypeSummary)
        assertEquals("Saved exposure type was retired. Review the text value before saving.", viewModel.state.value.formMessage)
    }

    private fun buildViewModel(): EditExposureViewModel {
        val repository = object : ExposureRepository {
            override suspend fun createExposure(command: io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")

            override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
                return AppResult.Success(ExposureEvent(id, "Pollen", existingOccurredAt, "outside", intensityLevel = 4))
            }

            override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
                return AppResult.Success(
                    ExposureEvent(
                        command.id,
                        command.exposureType,
                        command.occurredAtEpochMs,
                        command.notes,
                        intensityLevel = command.intensityLevel
                    )
                )
            }

            override suspend fun deleteExposure(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        return EditExposureViewModel(
            recordId = 5L,
            getExposureEventUseCase = GetExposureEventUseCase(repository),
            deleteExposureEventUseCase = DeleteExposureEventUseCase(repository),
            updateExposureEventUseCase = UpdateExposureEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )
    }
}

private class FakeEditExposureReusableEventCatalogRepository(
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

private class FlowEditExposureReusableEventCatalogRepository(
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
