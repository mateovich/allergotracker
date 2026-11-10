package io.github.mateovich.allergotracker.features.ingestion.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.compounds.domain.CompoundCompositionStatus
import io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundCommand
import io.github.mateovich.allergotracker.features.compounds.domain.ObserveReusableCompoundsUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompound
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompoundRepository
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.ingestion.domain.DeleteIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.GetIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionEvent
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionRepository
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventCommand
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditIngestionReusableSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedNow = 1_700_000_000_000L

    @Test
    fun suggestions_rank_exact_then_prefix_then_contains_and_include_compounds() = runTest {
        val viewModel = buildViewModel(
            ingredients = listOf(
                ingredient(1L, "Dark chocolate", updatedAt = fixedNow),
                ingredient(2L, "Chocolate", updatedAt = fixedNow - 10),
                ingredient(3L, "Chocolate chips", updatedAt = fixedNow - 20)
            ),
            compounds = listOf(compound(4L, "Chocolate mix", updatedAt = fixedNow - 30))
        )
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.UpdateReusableSearchQuery("chocolate"))
        advanceUntilIdle()

        assertEquals(
            listOf("Chocolate", "Chocolate chips", "Chocolate mix", "Dark chocolate"),
            viewModel.state.value.filteredReusableEntities.map { it.name }
        )
        assertTrue(viewModel.state.value.filteredReusableEntities.any { it.kind == ReusableEntityKind.COMPOUND })
    }

    @Test
    fun blank_query_limits_suggestions_to_six_recent_items() = runTest {
        val viewModel = buildViewModel(
            ingredients = (1L..8L).map { ingredient(it, "Item $it", updatedAt = fixedNow + it) }
        )
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.UpdateReusableSearchQuery(""))
        advanceUntilIdle()

        assertEquals(6, viewModel.state.value.filteredReusableEntities.size)
        assertEquals("Item 8", viewModel.state.value.filteredReusableEntities.first().name)
    }

    @Test
    fun loaded_link_keeps_item_name_in_search_query_after_idle() = runTest {
        val viewModel = buildViewModel(ingredients = listOf(ingredient(5L, "Tea")))
        viewModel.onIntent(EditIngestionUiIntent.Load)

        advanceUntilIdle()

        assertEquals("Tea", viewModel.state.value.reusableSearchQuery)
        assertEquals(5L, viewModel.state.value.selectedReusableEntityId)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun selection_after_typing_keeps_selected_name_in_search_query() = runTest {
        val viewModel = buildViewModel(
            ingredients = listOf(ingredient(5L, "Tea"), ingredient(6L, "Banana"))
        )
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.UpdateReusableSearchQuery("Ban"))
        viewModel.onIntent(
            EditIngestionUiIntent.SelectReusableEntity(
                IngestionReusableSelectionResult(id = 6L, name = "Banana", kind = ReusableEntityKind.INGREDIENT)
            )
        )
        advanceUntilIdle()

        assertEquals("Banana", viewModel.state.value.reusableSearchQuery)
        assertEquals(6L, viewModel.state.value.selectedReusableEntityId)
        assertEquals("Banana", viewModel.state.value.itemName)
    }

    @Test
    fun unlinked_legacy_record_explains_that_a_saved_item_is_required() = runTest {
        val viewModel = buildViewModel(linked = false)

        viewModel.onIntent(EditIngestionUiIntent.Load)

        assertEquals("Select a saved ingredient or compound to save changes.", viewModel.state.value.formMessage)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun inline_create_success_links_new_ingredient_and_closes_dialog() = runTest {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.onIntent(EditIngestionUiIntent.UpdateReusableSearchQuery("Cocoa"))
        viewModel.onIntent(EditIngestionUiIntent.ShowInlineIngredientDialog("Cocoa"))

        viewModel.onIntent(EditIngestionUiIntent.CreateInlineIngredient("Cocoa"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.showInlineIngredientDialog)
        assertFalse(state.isCreatingInlineIngredient)
        assertEquals(99L, state.selectedReusableEntityId)
        assertEquals(ReusableEntityKind.INGREDIENT, state.selectedReusableEntityKind)
        assertEquals("Cocoa", state.itemName)
        assertTrue(state.canSave)
    }

    @Test
    fun inline_create_error_keeps_dialog_open_with_message_and_no_link() = runTest {
        val viewModel = buildViewModel(
            createIngredientResult = AppResult.Error(DomainError.PersistenceError("Couldn't create ingredient."))
        )
        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.onIntent(EditIngestionUiIntent.UpdateReusableSearchQuery("Cocoa"))
        viewModel.onIntent(EditIngestionUiIntent.ShowInlineIngredientDialog("Cocoa"))

        viewModel.onIntent(EditIngestionUiIntent.CreateInlineIngredient("Cocoa"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.showInlineIngredientDialog)
        assertFalse(state.isCreatingInlineIngredient)
        assertEquals("Couldn't create ingredient.", state.inlineIngredientError)
        assertNull(state.selectedReusableEntityId)
        assertFalse(state.canSave)
    }

    @Test
    fun second_inline_create_while_first_is_in_flight_is_ignored() = runTest {
        val gate = CompletableDeferred<Unit>()
        var createCalls = 0
        val viewModel = buildViewModel(
            onCreateIngredient = {
                createCalls += 1
                gate.await()
            }
        )
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.CreateInlineIngredient("Cocoa"))
        viewModel.onIntent(EditIngestionUiIntent.CreateInlineIngredient("Cocoa"))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, createCalls)
    }

    private fun ingredient(id: Long, name: String, updatedAt: Long = fixedNow) =
        ReusableIngredient(id, name, null, fixedNow, updatedAt)

    private fun compound(id: Long, name: String, updatedAt: Long = fixedNow) = ReusableCompound(
        id = id,
        name = name,
        notes = null,
        compositionStatus = CompoundCompositionStatus.COMPLETE,
        components = emptyList(),
        createdAtEpochMs = fixedNow,
        updatedAtEpochMs = updatedAt
    )

    private fun buildViewModel(
        ingredients: List<ReusableIngredient> = emptyList(),
        compounds: List<ReusableCompound> = emptyList(),
        linked: Boolean = true,
        createIngredientResult: AppResult<ReusableIngredient>? = null,
        onCreateIngredient: suspend () -> Unit = {}
    ): EditIngestionViewModel {
        val timeProvider = CurrentTimeProvider { fixedNow }
        val ingestionRepository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> = AppResult.Success(
                IngestionEvent(
                    id = id,
                    itemName = "Tea",
                    quantityLevel = 2,
                    occurredAtEpochMs = fixedNow - 60_000L,
                    notes = null,
                    reusableEntityKind = ReusableEntityKind.INGREDIENT.takeIf { linked },
                    reusableEntityId = 5L.takeIf { linked }
                )
            )

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

            override suspend fun deleteIngestion(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        return EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(ingestionRepository),
            deleteIngestionEventUseCase = DeleteIngestionEventUseCase(ingestionRepository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(ingestionRepository, timeProvider),
            currentTimeProvider = timeProvider,
            observeReusableIngredientsUseCase = ObserveReusableIngredientsUseCase(
                repository = object : ReusableIngredientRepository {
                    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> =
                        error("Not used by observe use case")

                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(ingredients)
                }
            ),
            observeReusableCompoundsUseCase = ObserveReusableCompoundsUseCase(
                repository = object : ReusableCompoundRepository {
                    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> =
                        error("Not used by observe use case")

                    override fun observeCompounds(): Flow<List<ReusableCompound>> = flowOf(compounds)
                }
            ),
            createReusableIngredientUseCase = CreateReusableIngredientUseCase(
                repository = object : ReusableIngredientRepository {
                    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
                        onCreateIngredient()
                        return createIngredientResult ?: AppResult.Success(
                            ReusableIngredient(99L, command.name, command.notes, fixedNow, fixedNow)
                        )
                    }

                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(ingredients)
                }
            )
        )
    }
}
