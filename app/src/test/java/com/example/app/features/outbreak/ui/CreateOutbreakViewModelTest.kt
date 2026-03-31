package com.example.app.features.outbreak.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.result.AppResult
import com.example.app.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import com.example.app.features.eventcatalog.domain.ReusableEventCatalog
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogRepository
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.outbreak.domain.CreateOutbreakDefaultsUseCase
import com.example.app.features.outbreak.domain.CreateOutbreakEventCommand
import com.example.app.features.outbreak.domain.CreateOutbreakEventUseCase
import com.example.app.features.outbreak.domain.OutbreakEvent
import com.example.app.features.outbreak.domain.OutbreakRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateOutbreakViewModelTest {

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
        assertEquals(OutbreakTimePreset.NOW, viewModel.state.value.selectedTimePreset)
        assertTrue(viewModel.state.value.occurredAtInput.isNotBlank())
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun invalid_save_keeps_user_input_and_shows_inline_errors() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateOutbreakUiIntent.UpdateOutbreakDescriptor("Skin flare"))
        viewModel.onIntent(CreateOutbreakUiIntent.UpdateBodyLocation("Left arm"))
        viewModel.onIntent(CreateOutbreakUiIntent.UpdateBodyLocation("   "))
        viewModel.onIntent(CreateOutbreakUiIntent.Save)

        assertEquals("Skin flare", viewModel.state.value.outbreakDescriptor)
        assertEquals("   ", viewModel.state.value.bodyLocation)
        assertEquals("Enter where on the body it happened.", viewModel.state.value.bodyLocationError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun successful_save_emits_navigation_back_to_records() = runBlocking {
        val viewModel = buildViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateOutbreakUiIntent.UpdateOutbreakDescriptor("Skin flare"))
        viewModel.onIntent(CreateOutbreakUiIntent.UpdateBodyLocation("Left arm"))
        viewModel.onIntent(CreateOutbreakUiIntent.Save)

        assertEquals(CreateOutbreakUiEffect.NavigateToRecords, effect.await())
    }

    @Test
    fun invalid_datetime_input_shows_actionable_inline_error() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateOutbreakUiIntent.UpdateOutbreakDescriptor("Skin flare"))
        viewModel.onIntent(CreateOutbreakUiIntent.UpdateBodyLocation("Left arm"))
        viewModel.onIntent(CreateOutbreakUiIntent.UpdateOccurredAtInput("bad-input"))
        viewModel.onIntent(CreateOutbreakUiIntent.Save)

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

        viewModel.onIntent(CreateOutbreakUiIntent.UpdateOccurredAtInput(updatedInput))

        assertEquals(updatedEpochMs, viewModel.state.value.occurredAtEpochMs)
        assertEquals(expectedTimeLabel, viewModel.state.value.occurredAtLabel)
    }

    @Test
    fun selecting_saved_catalog_values_keeps_both_references_ready_for_save() {
        val viewModel = buildViewModel(
            availableCatalog = listOf(
                ReusableEventCatalog(
                    id = 11L,
                    kind = ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR,
                    name = "Skin flare",
                    usageCount = 4,
                    createdAtEpochMs = fixedNow - 1000L,
                    updatedAtEpochMs = fixedNow - 500L,
                    lastUsedAtEpochMs = fixedNow - 500L
                ),
                ReusableEventCatalog(
                    id = 12L,
                    kind = ReusableEventCatalogKind.BODY_LOCATION,
                    name = "Left arm",
                    usageCount = 4,
                    createdAtEpochMs = fixedNow - 1000L,
                    updatedAtEpochMs = fixedNow - 500L,
                    lastUsedAtEpochMs = fixedNow - 500L
                )
            )
        )

        viewModel.onIntent(CreateOutbreakUiIntent.SelectOutbreakDescriptorCatalogOption(11L, "Skin flare"))
        viewModel.onIntent(CreateOutbreakUiIntent.SelectBodyLocationCatalogOption(12L, "Left arm"))

        assertEquals(11L, viewModel.state.value.selectedOutbreakDescriptorCatalogId)
        assertEquals(12L, viewModel.state.value.selectedBodyLocationCatalogId)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun second_save_while_first_is_in_flight_is_ignored() = runBlocking {
        var createCalls = 0
        val saveGate = CompletableDeferred<Unit>()
        val timeProvider = object : CurrentTimeProvider {
            override fun nowEpochMillis(): Long = fixedNow
        }
        val viewModel = CreateOutbreakViewModel(
            createOutbreakDefaultsUseCase = CreateOutbreakDefaultsUseCase(timeProvider),
            createOutbreakEventUseCase = CreateOutbreakEventUseCase(
                repository = object : OutbreakRepository {
                    override suspend fun createOutbreak(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent> {
                        createCalls += 1
                        saveGate.await()
                        return AppResult.Success(
                            OutbreakEvent(
                                id = 17L,
                                outbreakDescriptor = command.outbreakDescriptor,
                                bodyLocation = command.bodyLocation,
                                occurredAtEpochMs = command.occurredAtEpochMs,
                                notes = command.notes
                            )
                        )
                    }

                    override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> = error("unused")
                    override suspend fun updateOutbreak(command: com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")
                    override suspend fun deleteOutbreak(id: Long): AppResult<Unit> = error("unused")
                },
                currentTimeProvider = timeProvider
            )
        )
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateOutbreakUiIntent.UpdateOutbreakDescriptor("Skin flare"))
        viewModel.onIntent(CreateOutbreakUiIntent.UpdateBodyLocation("Left arm"))
        viewModel.onIntent(CreateOutbreakUiIntent.Save)
        viewModel.onIntent(CreateOutbreakUiIntent.Save)
        saveGate.complete(Unit)

        assertEquals(CreateOutbreakUiEffect.NavigateToRecords, effect.await())
        assertEquals(1, createCalls)
    }

    private fun buildViewModel(
        availableCatalog: List<ReusableEventCatalog> = emptyList()
    ): CreateOutbreakViewModel {
        val timeProvider = object : CurrentTimeProvider {
            override fun nowEpochMillis(): Long = fixedNow
        }
        return CreateOutbreakViewModel(
            createOutbreakDefaultsUseCase = CreateOutbreakDefaultsUseCase(timeProvider),
            createOutbreakEventUseCase = CreateOutbreakEventUseCase(
                repository = object : OutbreakRepository {
                    override suspend fun createOutbreak(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent> {
                        return AppResult.Success(
                            OutbreakEvent(
                                id = 17L,
                                outbreakDescriptor = command.outbreakDescriptor,
                                bodyLocation = command.bodyLocation,
                                occurredAtEpochMs = command.occurredAtEpochMs,
                                notes = command.notes
                            )
                        )
                    }

                    override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> {
                        error("Not used in create tests")
                    }

                    override suspend fun updateOutbreak(command: com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
                        error("Not used in create tests")
                    }

                    override suspend fun deleteOutbreak(id: Long): AppResult<Unit> {
                        error("Not used in create tests")
                    }
                },
                currentTimeProvider = timeProvider
            ),
            observeReusableEventCatalogUseCase = ObserveReusableEventCatalogUseCase(
                FakeOutbreakReusableEventCatalogRepository(availableCatalog)
            )
        )
    }
}

private class FakeOutbreakReusableEventCatalogRepository(
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
}
