package com.example.app.features.outbreak.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.outbreak.domain.GetOutbreakEventUseCase
import com.example.app.features.outbreak.domain.OutbreakEvent
import com.example.app.features.outbreak.domain.OutbreakRepository
import com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand
import com.example.app.features.outbreak.domain.UpdateOutbreakEventUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
        assertEquals("after workout", viewModel.state.value.notes)
    }

    @Test
    fun invalid_save_keeps_user_input_and_shows_inline_errors() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditOutbreakUiIntent.Load)
        viewModel.onIntent(EditOutbreakUiIntent.UpdateBodyLocation("   "))
        viewModel.onIntent(EditOutbreakUiIntent.Save)

        assertEquals("   ", viewModel.state.value.bodyLocation)
        assertEquals("Enter where on the body it happened.", viewModel.state.value.bodyLocationError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
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
    fun time_preset_reanchors_edit_time_from_current_time() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditOutbreakUiIntent.Load)

        viewModel.onIntent(EditOutbreakUiIntent.SelectTimePreset(OutbreakTimePreset.ONE_HOUR_AGO))

        assertEquals(fixedNow - (60L * 60L * 1000L), viewModel.state.value.occurredAtEpochMs)
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
        }
        val viewModel = EditOutbreakViewModel(
            recordId = 9L,
            getOutbreakEventUseCase = GetOutbreakEventUseCase(repository),
            updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditOutbreakUiIntent.Load)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())

        viewModel.onIntent(EditOutbreakUiIntent.Load)

        assertEquals("Skin flare", viewModel.state.value.outbreakDescriptor)
        assertEquals(2, loadAttempts)
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
        }
        return EditOutbreakViewModel(
            recordId = 9L,
            getOutbreakEventUseCase = GetOutbreakEventUseCase(repository),
            updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )
    }
}
