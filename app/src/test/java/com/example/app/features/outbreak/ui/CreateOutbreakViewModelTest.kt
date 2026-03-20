package com.example.app.features.outbreak.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.outbreak.domain.CreateOutbreakDefaultsUseCase
import com.example.app.features.outbreak.domain.CreateOutbreakEventCommand
import com.example.app.features.outbreak.domain.CreateOutbreakEventUseCase
import com.example.app.features.outbreak.domain.OutbreakEvent
import com.example.app.features.outbreak.domain.OutbreakRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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

    private fun buildViewModel(): CreateOutbreakViewModel {
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
            )
        )
    }
}
