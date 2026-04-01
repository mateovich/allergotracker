package com.example.app.features.compounds.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundCompositionStatus
import com.example.app.features.compounds.domain.GetReusableCompoundUseCase
import com.example.app.features.compounds.domain.ReusableCompound
import com.example.app.features.compounds.domain.ReusableCompoundRepository
import com.example.app.features.compounds.domain.UpdateReusableCompoundCommand
import com.example.app.features.compounds.domain.UpdateReusableCompoundUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditCompoundViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_populates_existing_value_and_save_returns_updated_selection() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = EditCompoundViewModel(
            compoundId = 9L,
            getReusableCompoundUseCase = GetReusableCompoundUseCase(repository),
            updateReusableCompoundUseCase = UpdateReusableCompoundUseCase(repository)
        )
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateName("Breakfast mix plus"))
        viewModel.onIntent(EditCompoundUiIntent.Save)

        val navigation = effect.await() as EditCompoundUiEffect.NavigateBackWithSelection

        assertEquals("Breakfast mix plus", repository.updatedCommand?.name)
        assertEquals(9L, navigation.result.id)
    }

    @Test
    fun validation_error_surfaces_inline_message() = runBlocking {
        val repository = FakeEditCompoundRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    "Please fix the highlighted fields.",
                    mapOf("name" to "A compound with this name already exists.")
                )
            )
        )
        val viewModel = EditCompoundViewModel(
            compoundId = 9L,
            getReusableCompoundUseCase = GetReusableCompoundUseCase(repository),
            updateReusableCompoundUseCase = UpdateReusableCompoundUseCase(repository)
        )

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.Save)

        assertEquals("A compound with this name already exists.", viewModel.state.value.nameError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun blank_name_shows_inline_error_and_disables_save_immediately() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = EditCompoundViewModel(
            compoundId = 9L,
            getReusableCompoundUseCase = GetReusableCompoundUseCase(repository),
            updateReusableCompoundUseCase = UpdateReusableCompoundUseCase(repository)
        )

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateName("   "))

        assertEquals("Enter a compound name.", viewModel.state.value.nameError)
        assertFalse(viewModel.state.value.canSave)
    }
}

private class FakeEditCompoundRepository(
    private val updateResult: AppResult<ReusableCompound>? = null
) : ReusableCompoundRepository {
    var updatedCommand: UpdateReusableCompoundCommand? = null

    override suspend fun createCompound(command: com.example.app.features.compounds.domain.CreateReusableCompoundCommand): AppResult<ReusableCompound> {
        error("unused")
    }

    override suspend fun getCompound(id: Long): AppResult<ReusableCompound> {
        return AppResult.Success(
            ReusableCompound(
                id = id,
                name = "Breakfast mix",
                notes = "Draft",
                compositionStatus = CompoundCompositionStatus.DRAFT,
                components = emptyList(),
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
    }

    override suspend fun updateCompound(command: UpdateReusableCompoundCommand): AppResult<ReusableCompound> {
        updatedCommand = command
        return updateResult ?: AppResult.Success(
            ReusableCompound(
                id = command.id,
                name = command.name,
                notes = command.notes,
                compositionStatus = CompoundCompositionStatus.DRAFT,
                components = emptyList(),
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L
            )
        )
    }

    override fun observeCompounds(): Flow<List<ReusableCompound>> = emptyFlow()
}
