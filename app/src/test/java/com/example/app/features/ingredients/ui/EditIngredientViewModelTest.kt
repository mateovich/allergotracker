package com.example.app.features.ingredients.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingredients.domain.GetReusableIngredientRetirementImpactUseCase
import com.example.app.features.ingredients.domain.GetReusableIngredientUseCase
import com.example.app.features.ingredients.domain.ReusableIngredient
import com.example.app.features.ingredients.domain.ReusableIngredientRepository
import com.example.app.features.ingredients.domain.RetireReusableIngredientUseCase
import com.example.app.features.ingredients.domain.UpdateReusableIngredientCommand
import com.example.app.features.ingredients.domain.UpdateReusableIngredientUseCase
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

class EditIngredientViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_populates_existing_value_and_successful_save_returns_updated_selection() = runBlocking {
        val repository = FakeEditIngredientRepository()
        val viewModel = buildViewModel(repository)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.UpdateName("Oat milk plus"))
        viewModel.onIntent(EditIngredientUiIntent.Save)

        val navigation = effect.await() as EditIngredientUiEffect.NavigateBackWithSelection

        assertEquals("Oat milk plus", repository.updatedCommand?.name)
        assertEquals(7L, navigation.result.id)
        assertEquals("Oat milk plus", navigation.result.name)
    }

    @Test
    fun validation_error_surfaces_inline_message() = runBlocking {
        val repository = FakeEditIngredientRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    "Please fix the highlighted fields.",
                    mapOf("name" to "An ingredient with this name already exists.")
                )
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.Save)

        assertEquals("An ingredient with this name already exists.", viewModel.state.value.nameError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun blank_name_shows_inline_error_and_disables_save_immediately() = runBlocking {
        val repository = FakeEditIngredientRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.UpdateName("   "))

        assertEquals("Enter an ingredient name.", viewModel.state.value.nameError)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun retirement_preview_shows_detach_counts() = runBlocking {
        val repository = FakeEditIngredientRepository()
        val viewModel = buildViewModel(
            repository,
            countIngestionReferences = { 2 },
            countCompoundReferences = { 0 }
        )

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.RequestRetirementPreview)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertEquals(2, viewModel.state.value.retirementImpact?.ingestionReferenceCount)
    }

    @Test
    fun confirm_retirement_navigates_back_after_successful_retire() = runBlocking {
        val repository = FakeEditIngredientRepository()
        val viewModel = buildViewModel(repository)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditIngredientUiIntent.ConfirmRetirement)

        val navigation = effect.await() as EditIngredientUiEffect.NavigateBackAfterRetirement

        assertEquals(EditIngredientUiEffect.NavigateBackAfterRetirement, navigation)
        assertFalse(viewModel.state.value.isRetirementDialogVisible)
    }

    @Test
    fun blocked_retirement_keeps_dialog_open_without_navigation() = runBlocking {
        val repository = FakeEditIngredientRepository()
        val viewModel = buildViewModel(
            repository,
            countIngestionReferences = { 0 },
            countCompoundReferences = { 1 }
        )

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditIngredientUiIntent.ConfirmRetirement)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertTrue(viewModel.state.value.retirementImpact?.isBlocked == true)
    }

    @Test
    fun confirm_retirement_failure_keeps_dialog_open_and_surfaces_actionable_error() = runBlocking {
        val repository = FakeEditIngredientRepository(
            deleteResult = AppResult.Error(
                DomainError.IntegrityError("Replace those compound references before retiring this ingredient.")
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditIngredientUiIntent.ConfirmRetirement)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertEquals(
            "Replace those compound references before retiring this ingredient.",
            viewModel.state.value.formMessage
        )
        assertFalse(viewModel.state.value.isRetiring)
    }

    private fun buildViewModel(
        repository: FakeEditIngredientRepository,
        countIngestionReferences: suspend (Long) -> Int = { 0 },
        countCompoundReferences: suspend (Long) -> Int = { 0 }
    ): EditIngredientViewModel {
        val impactUseCase = GetReusableIngredientRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = countIngestionReferences,
            countCompoundReferences = countCompoundReferences
        )
        return EditIngredientViewModel(
            ingredientId = 7L,
            getReusableIngredientUseCase = GetReusableIngredientUseCase(repository),
            updateReusableIngredientUseCase = UpdateReusableIngredientUseCase(repository),
            getReusableIngredientRetirementImpactUseCase = impactUseCase,
            retireReusableIngredientUseCase = RetireReusableIngredientUseCase(repository, impactUseCase)
        )
    }
}

private class FakeEditIngredientRepository(
    private val updateResult: AppResult<ReusableIngredient>? = null,
    private val deleteResult: AppResult<Unit> = AppResult.Success(Unit)
) : ReusableIngredientRepository {
    var updatedCommand: UpdateReusableIngredientCommand? = null

    override suspend fun createIngredient(command: com.example.app.features.ingredients.domain.CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
        error("unused")
    }

    override suspend fun getIngredient(id: Long): AppResult<ReusableIngredient> {
        return AppResult.Success(
            ReusableIngredient(
                id = id,
                name = "Oat milk",
                notes = "Staple",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
    }

    override suspend fun updateIngredient(command: UpdateReusableIngredientCommand): AppResult<ReusableIngredient> {
        updatedCommand = command
        return updateResult ?: AppResult.Success(
            ReusableIngredient(
                id = command.id,
                name = command.name,
                notes = command.notes,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L
            )
        )
    }

    override suspend fun deleteIngredient(id: Long): AppResult<Unit> = deleteResult

    override fun observeIngredients(): Flow<List<ReusableIngredient>> = emptyFlow()
}
