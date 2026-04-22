package io.github.mateovich.allergotracker.features.ingredients.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientMergePreviewUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientRetirementImpactUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.MergeReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRepository
import io.github.mateovich.allergotracker.features.ingredients.domain.RetireReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.UpdateReusableIngredientCommand
import io.github.mateovich.allergotracker.features.ingredients.domain.UpdateReusableIngredientUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun merge_preview_loads_counts_for_selected_destination() = runBlocking {
        val repository = FakeEditIngredientRepository()
        val viewModel = buildViewModel(
            repository = repository,
            countMergeIngestionReferences = { 2 },
            countMergeCompoundReferences = { 1 }
        )

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.RequestMergeFlow)
        viewModel.onIntent(EditIngredientUiIntent.SelectMergeDestination(8L))
        viewModel.onIntent(EditIngredientUiIntent.LoadMergePreview)

        assertTrue(viewModel.state.value.isMergeDialogVisible)
        assertEquals(2, viewModel.state.value.mergePreview?.ingestionReferenceCount)
        assertEquals(1, viewModel.state.value.mergePreview?.compoundReferenceCount)
    }

    @Test
    fun blocked_merge_preview_surfaces_conflict_message() = runBlocking {
        val repository = FakeEditIngredientRepository()
        val viewModel = buildViewModel(
            repository = repository,
            countConflictingCompounds = { _, _ -> 1 }
        )

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.RequestMergeFlow)
        viewModel.onIntent(EditIngredientUiIntent.SelectMergeDestination(8L))
        viewModel.onIntent(EditIngredientUiIntent.LoadMergePreview)

        assertTrue(viewModel.state.value.mergePreview?.isBlocked == true)
        assertEquals(1, viewModel.state.value.mergePreview?.conflictingCompoundCount)
    }

    @Test
    fun confirm_merge_success_navigates_back_with_canonical_selection() = runBlocking {
        val repository = FakeEditIngredientRepository()
        val viewModel = buildViewModel(repository)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.RequestMergeFlow)
        viewModel.onIntent(EditIngredientUiIntent.SelectMergeDestination(8L))
        viewModel.onIntent(EditIngredientUiIntent.LoadMergePreview)
        viewModel.onIntent(EditIngredientUiIntent.ConfirmMerge)

        val navigation = effect.await() as EditIngredientUiEffect.NavigateBackWithSelection

        assertEquals(8L, navigation.result.id)
        assertEquals("Oat milk plus", navigation.result.name)
        assertFalse(viewModel.state.value.isMergeDialogVisible)
    }

    @Test
    fun confirm_merge_failure_keeps_dialog_open_and_shows_error() = runBlocking {
        val repository = FakeEditIngredientRepository(
            deleteResult = AppResult.Error(
                DomainError.IntegrityError(
                    "Merge couldn't be completed locally because stored data changed unexpectedly. Reload the preview and try again."
                )
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.RequestMergeFlow)
        viewModel.onIntent(EditIngredientUiIntent.SelectMergeDestination(8L))
        viewModel.onIntent(EditIngredientUiIntent.LoadMergePreview)
        viewModel.onIntent(EditIngredientUiIntent.ConfirmMerge)

        assertTrue(viewModel.state.value.isMergeDialogVisible)
        assertEquals(
            "Merge couldn't be completed locally because stored data changed unexpectedly. Reload the preview and try again.",
            viewModel.state.value.mergeError
        )
        assertFalse(viewModel.state.value.isMerging)
    }

    @Test
    fun stale_merge_preview_result_is_discarded_when_selection_changes() = runBlocking {
        val repository = FakeEditIngredientRepository()
        val viewModel = buildViewModel(
            repository = repository,
            countMergeIngestionReferences = {
                repository.replaceObservedIngredients(
                    listOf(
                        ReusableIngredient(7L, "Oat milk", "Staple", 1L, 1L),
                        ReusableIngredient(9L, "Oat milk deluxe", null, 1L, 3L)
                    )
                )
                2
            },
            countMergeCompoundReferences = { 1 }
        )

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.RequestMergeFlow)
        viewModel.onIntent(EditIngredientUiIntent.SelectMergeDestination(8L))
        viewModel.onIntent(EditIngredientUiIntent.LoadMergePreview)

        assertNull(viewModel.state.value.selectedMergeDestinationId)
        assertNull(viewModel.state.value.mergePreview)
        assertFalse(viewModel.state.value.isLoadingMergePreview)
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
        countCompoundReferences: suspend (Long) -> Int = { 0 },
        countMergeIngestionReferences: suspend (Long) -> Int = { 0 },
        countMergeCompoundReferences: suspend (Long) -> Int = { 0 },
        countConflictingCompounds: suspend (Long, Long) -> Int = { _, _ -> 0 }
    ): EditIngredientViewModel {
        val impactUseCase = GetReusableIngredientRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = countIngestionReferences,
            countCompoundReferences = countCompoundReferences
        )
        val mergePreviewUseCase = GetReusableIngredientMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = countMergeIngestionReferences,
            countCompoundReferences = countMergeCompoundReferences,
            countConflictingCompounds = countConflictingCompounds
        )
        return EditIngredientViewModel(
            ingredientId = 7L,
            getReusableIngredientUseCase = GetReusableIngredientUseCase(repository),
            updateReusableIngredientUseCase = UpdateReusableIngredientUseCase(repository),
            observeReusableIngredients = ObserveReusableIngredientsUseCase(repository),
            getReusableIngredientMergePreviewUseCase = mergePreviewUseCase,
            mergeReusableIngredientUseCase = MergeReusableIngredientUseCase(
                getPreview = mergePreviewUseCase,
                getIngredient = repository::getIngredient,
                deleteIngredient = repository::deleteIngredient,
                reassignIngestionReferences = { _, _, _ -> countMergeIngestionReferences(7L) },
                replaceCompoundReferences = { _, _ -> countMergeCompoundReferences(7L) }
            ),
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
    private val observedIngredients = MutableStateFlow(
        listOf(
            ReusableIngredient(7L, "Oat milk", "Staple", 1L, 1L),
            ReusableIngredient(8L, "Oat milk plus", null, 1L, 2L),
            ReusableIngredient(9L, "Oat milk deluxe", null, 1L, 3L)
        )
    )

    override suspend fun createIngredient(command: io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
        error("unused")
    }

    override suspend fun getIngredient(id: Long): AppResult<ReusableIngredient> {
        return AppResult.Success(
            ReusableIngredient(
                id = id,
                name = if (id == 8L) "Oat milk plus" else "Oat milk",
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

    override fun observeIngredients(): Flow<List<ReusableIngredient>> {
        return observedIngredients
    }

    fun replaceObservedIngredients(value: List<ReusableIngredient>) {
        observedIngredients.value = value
    }
}
