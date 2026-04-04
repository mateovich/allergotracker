package com.example.app.features.exposure.ui

import com.example.app.MainDispatcherRule
import com.example.app.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import com.example.app.features.eventcatalog.domain.ReusableEventCatalog
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogRepository
import com.example.app.core.common.result.AppResult
import com.example.app.features.exposure.domain.CreateExposureDefaultsUseCase
import com.example.app.features.exposure.domain.CreateExposureEventCommand
import com.example.app.features.exposure.domain.CreateExposureEventUseCase
import com.example.app.features.exposure.domain.ExposureEvent
import com.example.app.features.exposure.domain.ExposureRepository
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateExposureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedNow = 1_700_000_000_000L

    @Test
    fun initializes_with_contextual_defaults_when_form_opens() {
        val viewModel = buildViewModel()
        val expectedTimeLabel = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(fixedNow))

        assertEquals(expectedTimeLabel, viewModel.state.value.occurredAtLabel)
        assertEquals(ExposureTimePreset.NOW, viewModel.state.value.selectedTimePreset)
        assertEquals(3, viewModel.state.value.selectedIntensityLevel)
        assertTrue(viewModel.state.value.occurredAtInput.isNotBlank())
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun invalid_save_keeps_user_input_and_shows_inline_errors() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateExposureUiIntent.UpdateExposureType("Pollen"))
        viewModel.onIntent(CreateExposureUiIntent.SelectTimePreset(ExposureTimePreset.ONE_HOUR_AGO))
        viewModel.onIntent(CreateExposureUiIntent.UpdateExposureType("   "))
        viewModel.onIntent(CreateExposureUiIntent.Save)

        assertEquals("   ", viewModel.state.value.exposureType)
        assertEquals("Enter what you were exposed to.", viewModel.state.value.exposureTypeError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun invalid_intensity_save_surfaces_inline_error() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateExposureUiIntent.UpdateExposureType("Pollen"))
        viewModel.onIntent(CreateExposureUiIntent.SelectIntensityLevel(0))
        viewModel.onIntent(CreateExposureUiIntent.Save)

        assertEquals("Choose a valid intensity.", viewModel.state.value.intensityLevelError)
        assertEquals("Please fix the highlighted fields.", viewModel.state.value.formMessage)
    }

    @Test
    fun successful_save_emits_navigation_back_to_records() = runBlocking {
        val viewModel = buildViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateExposureUiIntent.UpdateExposureType("Pollen"))
        viewModel.onIntent(CreateExposureUiIntent.Save)

        assertEquals(CreateExposureUiEffect.NavigateToRecords, effect.await())
    }

    @Test
    fun invalid_datetime_input_shows_actionable_inline_error() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateExposureUiIntent.UpdateExposureType("Pollen"))
        viewModel.onIntent(CreateExposureUiIntent.UpdateOccurredAtInput("bad-input"))
        viewModel.onIntent(CreateExposureUiIntent.Save)

        assertEquals("Enter date and time as yyyy-MM-dd HH:mm.", viewModel.state.value.occurredAtError)
        assertEquals("Please fix the highlighted fields.", viewModel.state.value.formMessage)
    }

    @Test
    fun valid_manual_datetime_input_normalizes_hidden_subminute_precision() {
        val viewModel = buildViewModel()
        val zoneId = ZoneId.systemDefault()
        val updatedEpochMs = Instant.ofEpochMilli(fixedNow)
            .atZone(zoneId)
            .minusHours(2)
            .withMinute(5)
            .withSecond(0)
            .withNano(0)
            .toInstant()
            .toEpochMilli()
        val updatedInput = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(zoneId)
            .format(Instant.ofEpochMilli(updatedEpochMs))
        val expectedTimeLabel = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(zoneId)
            .format(Instant.ofEpochMilli(updatedEpochMs))

        viewModel.onIntent(CreateExposureUiIntent.UpdateOccurredAtInput(updatedInput))

        assertEquals(updatedEpochMs, viewModel.state.value.occurredAtEpochMs)
        assertEquals(expectedTimeLabel, viewModel.state.value.occurredAtLabel)
    }

    @Test
    fun selecting_saved_exposure_type_sets_catalog_reference_and_summary() {
        val viewModel = buildViewModel(
            availableCatalog = listOf(
                ReusableEventCatalog(
                    id = 5L,
                    kind = ReusableEventCatalogKind.EXPOSURE_TYPE,
                    name = "Pollen",
                    usageCount = 3,
                    createdAtEpochMs = fixedNow - 1000L,
                    updatedAtEpochMs = fixedNow - 500L,
                    lastUsedAtEpochMs = fixedNow - 500L
                )
            )
        )

        viewModel.onIntent(CreateExposureUiIntent.UpdateExposureType("Pol"))
        viewModel.onIntent(CreateExposureUiIntent.SelectExposureCatalogOption(5L, "Pollen"))

        assertEquals(5L, viewModel.state.value.selectedExposureTypeCatalogId)
        assertEquals("Saved exposure type selected: Pollen", viewModel.state.value.selectedExposureTypeSummary)
        assertEquals("Pollen", viewModel.state.value.exposureType)
    }

    @Test
    fun clearing_retired_catalog_selection_keeps_text_but_drops_stale_id() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateExposureUiIntent.SelectExposureCatalogOption(5L, "Pollen"))
        viewModel.onIntent(CreateExposureUiIntent.ClearRetiredExposureCatalogSelection)

        assertEquals("Pollen", viewModel.state.value.exposureType)
        assertEquals(null, viewModel.state.value.selectedExposureTypeCatalogId)
        assertEquals(null, viewModel.state.value.selectedExposureTypeSummary)
        assertEquals("Saved exposure type was retired. Review the text value before saving.", viewModel.state.value.formMessage)
    }

    @Test
    fun save_after_clearing_retired_catalog_selection_still_navigates_with_plain_text_value() = runBlocking {
        var savedCommand: CreateExposureEventCommand? = null
        val viewModel = buildViewModel(onCreateExposure = { savedCommand = it })
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateExposureUiIntent.SelectExposureCatalogOption(5L, "Pollen"))
        viewModel.onIntent(CreateExposureUiIntent.ClearRetiredExposureCatalogSelection)
        viewModel.onIntent(CreateExposureUiIntent.Save)

        assertEquals(CreateExposureUiEffect.NavigateToRecords, effect.await())
        assertEquals("Pollen", savedCommand?.exposureType)
        assertEquals(null, savedCommand?.exposureTypeCatalogId)
    }

    @Test
    fun second_save_while_first_is_in_flight_is_ignored() = runBlocking {
        var createCalls = 0
        val saveGate = CompletableDeferred<Unit>()
        val timeProvider = object : CurrentTimeProvider {
            override fun nowEpochMillis(): Long = fixedNow
        }
        val viewModel = CreateExposureViewModel(
            createExposureDefaultsUseCase = CreateExposureDefaultsUseCase(timeProvider),
            createExposureEventUseCase = CreateExposureEventUseCase(
                repository = object : ExposureRepository {
                    override suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent> {
                        createCalls += 1
                        saveGate.await()
                        return AppResult.Success(
                            ExposureEvent(
                                id = 11L,
                                exposureType = command.exposureType,
                                occurredAtEpochMs = command.occurredAtEpochMs,
                                notes = command.notes,
                                intensityLevel = command.intensityLevel
                            )
                        )
                    }

                    override suspend fun getExposure(id: Long): AppResult<ExposureEvent> = error("unused")
                    override suspend fun updateExposure(command: com.example.app.features.exposure.domain.UpdateExposureEventCommand): AppResult<ExposureEvent> = error("unused")
                    override suspend fun deleteExposure(id: Long): AppResult<Unit> = error("unused")
                },
                currentTimeProvider = timeProvider
            )
        )
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateExposureUiIntent.UpdateExposureType("Pollen"))
        viewModel.onIntent(CreateExposureUiIntent.Save)
        viewModel.onIntent(CreateExposureUiIntent.Save)
        saveGate.complete(Unit)

        assertEquals(CreateExposureUiEffect.NavigateToRecords, effect.await())
        assertEquals(1, createCalls)
    }

    @Test
    fun save_uses_selected_intensity_level() = runBlocking {
        var savedCommand: CreateExposureEventCommand? = null
        val timeProvider = object : CurrentTimeProvider {
            override fun nowEpochMillis(): Long = fixedNow
        }
        val viewModel = CreateExposureViewModel(
            createExposureDefaultsUseCase = CreateExposureDefaultsUseCase(timeProvider),
            createExposureEventUseCase = CreateExposureEventUseCase(
                repository = object : ExposureRepository {
                    override suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent> {
                        savedCommand = command
                        return AppResult.Success(
                            ExposureEvent(
                                id = 11L,
                                exposureType = command.exposureType,
                                occurredAtEpochMs = command.occurredAtEpochMs,
                                notes = command.notes,
                                intensityLevel = command.intensityLevel
                            )
                        )
                    }

                    override suspend fun getExposure(id: Long): AppResult<ExposureEvent> = error("unused")
                    override suspend fun updateExposure(command: com.example.app.features.exposure.domain.UpdateExposureEventCommand): AppResult<ExposureEvent> = error("unused")
                    override suspend fun deleteExposure(id: Long): AppResult<Unit> = error("unused")
                },
                currentTimeProvider = timeProvider
            )
        )

        viewModel.onIntent(CreateExposureUiIntent.UpdateExposureType("Pollen"))
        viewModel.onIntent(CreateExposureUiIntent.SelectIntensityLevel(5))
        viewModel.onIntent(CreateExposureUiIntent.Save)

        assertEquals(5, savedCommand?.intensityLevel)
    }

    private fun buildViewModel(
        availableCatalog: List<ReusableEventCatalog> = emptyList(),
        onCreateExposure: (CreateExposureEventCommand) -> Unit = {}
    ): CreateExposureViewModel {
        val timeProvider = object : CurrentTimeProvider {
            override fun nowEpochMillis(): Long = fixedNow
        }
        return CreateExposureViewModel(
            createExposureDefaultsUseCase = CreateExposureDefaultsUseCase(timeProvider),
            createExposureEventUseCase = CreateExposureEventUseCase(
                repository = object : ExposureRepository {
                    override suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent> {
                        onCreateExposure(command)
                        return AppResult.Success(
                            ExposureEvent(
                                id = 11L,
                                exposureType = command.exposureType,
                                occurredAtEpochMs = command.occurredAtEpochMs,
                                notes = command.notes,
                                intensityLevel = command.intensityLevel
                            )
                        )
                    }

                    override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
                        error("Not used in create tests")
                    }

                    override suspend fun updateExposure(command: com.example.app.features.exposure.domain.UpdateExposureEventCommand): AppResult<ExposureEvent> {
                        error("Not used in create tests")
                    }

                    override suspend fun deleteExposure(id: Long): AppResult<Unit> {
                        error("Not used in create tests")
                    }
                },
                currentTimeProvider = timeProvider
            ),
            observeReusableEventCatalogUseCase = ObserveReusableEventCatalogUseCase(
                FakeReusableEventCatalogRepository(availableCatalog)
            )
        )
    }
}

private class FakeReusableEventCatalogRepository(
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
