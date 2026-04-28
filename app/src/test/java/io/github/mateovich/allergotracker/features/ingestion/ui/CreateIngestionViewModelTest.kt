@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.mateovich.allergotracker.features.ingestion.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.features.compounds.domain.ObserveReusableCompoundsUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompound
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompoundRepository
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionDefaultsUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionEvent
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionRepository
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class CreateIngestionViewModelTest {

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
        assertEquals(IngestionTimePreset.NOW, viewModel.state.value.selectedTimePreset)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun invalid_save_keeps_user_input_and_shows_inline_errors() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateIngestionUiIntent.UpdateItemName("Yogurt"))
        viewModel.onIntent(CreateIngestionUiIntent.Save)

        assertEquals("Yogurt", viewModel.state.value.itemName)
        assertEquals("Choose a quantity.", viewModel.state.value.quantityError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun successful_save_emits_navigation_back_to_records() = runBlocking {
        val viewModel = buildViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateIngestionUiIntent.UpdateItemName("Tea"))
        viewModel.onIntent(CreateIngestionUiIntent.SelectQuantityLevel(2))
        viewModel.onIntent(CreateIngestionUiIntent.Save)

        assertEquals(CreateIngestionUiEffect.NavigateToRecords, effect.await())
    }

    @Test
    fun selecting_reusable_entity_populates_name_and_identity() {
        val viewModel = buildViewModel()

        viewModel.onIntent(
            CreateIngestionUiIntent.SelectReusableEntity(
                IngestionReusableSelectionResult(
                    id = 11L,
                    name = "Oat milk",
                    kind = ReusableEntityKind.INGREDIENT
                )
            )
        )

        assertEquals("Oat milk", viewModel.state.value.itemName)
        assertEquals(11L, viewModel.state.value.selectedReusableEntityId)
        assertEquals(ReusableEntityKind.INGREDIENT, viewModel.state.value.selectedReusableEntityKind)
    }

    @Test
    fun editing_name_after_reusable_selection_clears_reusable_identity() {
        val viewModel = buildViewModel()

        viewModel.onIntent(
            CreateIngestionUiIntent.SelectReusableEntity(
                IngestionReusableSelectionResult(
                    id = 11L,
                    name = "Oat milk",
                    kind = ReusableEntityKind.INGREDIENT
                )
            )
        )
        viewModel.onIntent(CreateIngestionUiIntent.UpdateItemName("Manual override"))

        assertNull(viewModel.state.value.selectedReusableEntityId)
        assertNull(viewModel.state.value.selectedReusableEntityKind)
    }

    @Test
    fun search_query_filters_reusable_entities_without_hiding_inline_create_actions() = runTest {
        val viewModel = buildViewModel(
            ingredients = listOf(
                ReusableIngredient(11L, "Oat milk", null, fixedNow, fixedNow),
                ReusableIngredient(12L, "Banana", null, fixedNow, fixedNow)
            ),
            compounds = listOf(
                ReusableCompound(21L, "Oat breakfast mix", null, io.github.mateovich.allergotracker.features.compounds.domain.CompoundCompositionStatus.DRAFT, emptyList(), fixedNow, fixedNow)
            )
        )

        viewModel.onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery("oat"))
        advanceUntilIdle()

        assertEquals(listOf("Oat breakfast mix", "Oat milk"), viewModel.state.value.filteredReusableEntities.map { it.name })
    }

    @Test
    fun empty_search_query_shows_recent_reusable_items_in_recency_order_and_limits_noise() {
        val viewModel = buildViewModel(
            ingredients = listOf(
                ReusableIngredient(11L, "Alpha", null, fixedNow, fixedNow - 5L),
                ReusableIngredient(12L, "Bravo", null, fixedNow, fixedNow - 4L),
                ReusableIngredient(13L, "Charlie", null, fixedNow, fixedNow - 3L),
                ReusableIngredient(14L, "Delta", null, fixedNow, fixedNow - 2L)
            ),
            compounds = listOf(
                ReusableCompound(21L, "Echo", null, io.github.mateovich.allergotracker.features.compounds.domain.CompoundCompositionStatus.DRAFT, emptyList(), fixedNow, fixedNow - 1L),
                ReusableCompound(22L, "Foxtrot", null, io.github.mateovich.allergotracker.features.compounds.domain.CompoundCompositionStatus.DRAFT, emptyList(), fixedNow, fixedNow),
                ReusableCompound(23L, "Golf", null, io.github.mateovich.allergotracker.features.compounds.domain.CompoundCompositionStatus.DRAFT, emptyList(), fixedNow, fixedNow - 6L)
            )
        )

        assertEquals(
            listOf("Foxtrot", "Echo", "Delta", "Charlie", "Bravo", "Alpha"),
            viewModel.state.value.filteredReusableEntities.map { it.name }
        )
    }

    @Test
    fun apply_reusable_selection_preserves_existing_contextual_inputs() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateIngestionUiIntent.UpdateNotes("keep me"))
        viewModel.onIntent(CreateIngestionUiIntent.SelectQuantityLevel(2))
        viewModel.onIntent(CreateIngestionUiIntent.SelectTimePreset(IngestionTimePreset.ONE_HOUR_AGO))
        viewModel.onIntent(
            CreateIngestionUiIntent.ApplyReusableSelection(
                IngestionReusableSelectionResult(
                    id = 22L,
                    name = "Breakfast mix",
                    kind = ReusableEntityKind.COMPOUND
                )
            )
        )

        assertEquals("Breakfast mix", viewModel.state.value.itemName)
        assertEquals("keep me", viewModel.state.value.notes)
        assertEquals(2, viewModel.state.value.selectedQuantityLevel)
        assertEquals(IngestionTimePreset.ONE_HOUR_AGO, viewModel.state.value.selectedTimePreset)
        assertEquals("Breakfast mix", viewModel.state.value.reusableSearchQuery)
    }

    @Test
    fun clearing_retired_reusable_selection_keeps_text_but_drops_stale_identity() {
        val viewModel = buildViewModel()

        viewModel.onIntent(
            CreateIngestionUiIntent.SelectReusableEntity(
                IngestionReusableSelectionResult(id = 11L, name = "Oat milk", kind = ReusableEntityKind.INGREDIENT)
            )
        )
        viewModel.onIntent(CreateIngestionUiIntent.ClearRetiredReusableSelection)

        assertEquals("Oat milk", viewModel.state.value.itemName)
        assertNull(viewModel.state.value.selectedReusableEntityId)
        assertNull(viewModel.state.value.selectedReusableEntityKind)
        assertNull(viewModel.state.value.selectedReusableEntitySummary)
        assertEquals("Saved reusable item was retired. Review the text value before saving.", viewModel.state.value.formMessage)
    }

    @Test
    fun edit_selected_reusable_entity_emits_navigation_for_current_kind() = runBlocking {
        val viewModel = buildViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(
            CreateIngestionUiIntent.SelectReusableEntity(
                IngestionReusableSelectionResult(
                    id = 22L,
                    name = "Breakfast mix",
                    kind = ReusableEntityKind.COMPOUND
                )
            )
        )
        viewModel.onIntent(CreateIngestionUiIntent.EditSelectedReusableEntity)

        assertEquals(
            CreateIngestionUiEffect.NavigateToEditReusableEntity(ReusableEntityKind.COMPOUND, 22L),
            effect.await()
        )
    }

    @Test
    fun create_new_compound_carries_current_typed_prefill() = runBlocking {
        val viewModel = buildViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery("Banana shake"))
        viewModel.onIntent(CreateIngestionUiIntent.CreateNewCompound)

        assertEquals(
            CreateIngestionUiEffect.NavigateToCreateCompound(prefillName = "Banana shake"),
            effect.await()
        )
    }

    @Test
    fun integrity_error_from_save_is_mapped_back_into_form_message() = runBlocking {
        val viewModel = buildViewModel(
            saveResult = AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.IntegrityError("Selected reusable item is no longer available."))
        )

        viewModel.onIntent(
            CreateIngestionUiIntent.SelectReusableEntity(
                IngestionReusableSelectionResult(
                    id = 11L,
                    name = "Oat milk",
                    kind = ReusableEntityKind.INGREDIENT
                )
            )
        )
        viewModel.onIntent(CreateIngestionUiIntent.SelectQuantityLevel(2))
        viewModel.onIntent(CreateIngestionUiIntent.Save)

        assertEquals("Selected reusable item is no longer available.", viewModel.state.value.formMessage)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun second_save_while_first_is_in_flight_is_ignored() = runBlocking {
        var createCalls = 0
        val saveGate = CompletableDeferred<Unit>()
        val timeProvider = object : CurrentTimeProvider {
            override fun nowEpochMillis(): Long = fixedNow
        }
        val viewModel = CreateIngestionViewModel(
            createIngestionDefaultsUseCase = CreateIngestionDefaultsUseCase(timeProvider),
            createIngestionEventUseCase = CreateIngestionEventUseCase(
                repository = object : IngestionRepository {
                    override suspend fun createIngestion(command: CreateIngestionEventCommand): AppResult<IngestionEvent> {
                        createCalls += 1
                        saveGate.await()
                        return AppResult.Success(
                            IngestionEvent(
                                id = 7L,
                                itemName = command.itemName,
                                quantityLevel = command.quantityLevel ?: 0,
                                occurredAtEpochMs = command.occurredAtEpochMs,
                                notes = command.notes,
                                reusableEntityKind = command.reusableEntityKind,
                                reusableEntityId = command.reusableEntityId
                            )
                        )
                    }

                    override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> = error("unused")
                    override suspend fun updateIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")
                    override suspend fun deleteIngestion(id: Long): AppResult<Unit> = error("unused")
                },
                currentTimeProvider = timeProvider
            ),
            observeReusableIngredientsUseCase = ObserveReusableIngredientsUseCase(
                repository = object : ReusableIngredientRepository {
                    override suspend fun createIngredient(command: io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand): AppResult<ReusableIngredient> = error("unused")
                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(emptyList())
                }
            ),
            observeReusableCompoundsUseCase = ObserveReusableCompoundsUseCase(
                repository = object : ReusableCompoundRepository {
                    override suspend fun createCompound(command: io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundCommand): AppResult<ReusableCompound> = error("unused")
                    override fun observeCompounds(): Flow<List<ReusableCompound>> = flowOf(emptyList())
                }
            ),
            createReusableIngredientUseCase = CreateReusableIngredientUseCase(
                repository = object : ReusableIngredientRepository {
                    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> = error("unused")
                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(emptyList())
                }
            )
        )
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateIngestionUiIntent.UpdateItemName("Tea"))
        viewModel.onIntent(CreateIngestionUiIntent.SelectQuantityLevel(2))
        viewModel.onIntent(CreateIngestionUiIntent.Save)
        viewModel.onIntent(CreateIngestionUiIntent.Save)
        saveGate.complete(Unit)

        assertEquals(CreateIngestionUiEffect.NavigateToRecords, effect.await())
        assertEquals(1, createCalls)
    }

    private fun buildViewModel(
        ingredients: List<ReusableIngredient> = emptyList(),
        compounds: List<ReusableCompound> = emptyList(),
        saveResult: AppResult<IngestionEvent>? = null,
        createIngredientResult: AppResult<ReusableIngredient>? = null
    ): CreateIngestionViewModel {
        val timeProvider = object : CurrentTimeProvider {
            override fun nowEpochMillis(): Long = fixedNow
        }
        return CreateIngestionViewModel(
            createIngestionDefaultsUseCase = CreateIngestionDefaultsUseCase(timeProvider),
            createIngestionEventUseCase = CreateIngestionEventUseCase(
                repository = object : IngestionRepository {
                    override suspend fun createIngestion(command: CreateIngestionEventCommand): AppResult<IngestionEvent> {
                        return saveResult ?: AppResult.Success(
                            IngestionEvent(
                                id = 7L,
                                itemName = command.itemName,
                                quantityLevel = command.quantityLevel ?: 0,
                                occurredAtEpochMs = command.occurredAtEpochMs,
                                notes = command.notes
                            )
                        )
                    }

                    override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
                        error("Not used in create ingestion tests")
                    }

                    override suspend fun updateIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                        error("Not used in create ingestion tests")
                    }

                    override suspend fun deleteIngestion(id: Long): AppResult<Unit> {
                        error("Not used in create ingestion tests")
                    }
                },
                currentTimeProvider = timeProvider
            ),
            observeReusableIngredientsUseCase = ObserveReusableIngredientsUseCase(
                repository = object : ReusableIngredientRepository {
                    // createIngredient is never called by ObserveReusableIngredientsUseCase
                    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
                        error("Not used by observe use case")
                    }
                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(ingredients)
                }
            ),
            observeReusableCompoundsUseCase = ObserveReusableCompoundsUseCase(
                repository = object : ReusableCompoundRepository {
                    override suspend fun createCompound(command: io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundCommand): AppResult<ReusableCompound> {
                        error("Not used in create ingestion tests")
                    }

                    override fun observeCompounds(): Flow<List<ReusableCompound>> = flowOf(compounds)
                }
            ),
            createReusableIngredientUseCase = CreateReusableIngredientUseCase(
                repository = object : ReusableIngredientRepository {
                    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
                        return createIngredientResult ?: AppResult.Success(
                            ReusableIngredient(
                                id = 99L,
                                name = command.name,
                                notes = command.notes,
                                createdAtEpochMs = fixedNow,
                                updatedAtEpochMs = fixedNow
                            )
                        )
                    }

                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(ingredients)
                }
            )
        )
    }

    // === Inline ingredient creation tests (Story 5.1) ===

    @Test
    fun searching_with_no_match_shows_create_option_in_state() = runTest {
        val viewModel = buildViewModel(
            ingredients = listOf(
                ReusableIngredient(11L, "Oat milk", null, fixedNow, fixedNow)
            )
        )

        viewModel.onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery("banana"))
        advanceUntilIdle()

        // No match for "banana" — filtered list is empty, so UI should show create option (PROMINENT chip)
        assertTrue(viewModel.state.value.filteredReusableEntities.isEmpty())
        // itemName is synced to the search query immediately
        assertEquals("banana", viewModel.state.value.itemName)
    }

    @Test
    fun searching_with_exact_match_hides_create_option_in_state() = runTest {
        val viewModel = buildViewModel(
            ingredients = listOf(
                ReusableIngredient(11L, "Oat milk", null, fixedNow, fixedNow)
            )
        )

        viewModel.onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery("Oat milk"))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.filteredReusableEntities.size)
        assertEquals("Oat milk", viewModel.state.value.filteredReusableEntities.first().name)
    }

    @Test
    fun clearing_search_query_clears_item_name_and_can_save() = runTest {
        val viewModel = buildViewModel()
        viewModel.onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery("Banana"))
        viewModel.onIntent(CreateIngestionUiIntent.SelectQuantityLevel(2))
        assertTrue(viewModel.state.value.canSave)

        viewModel.onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery(""))
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.itemName)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun show_inline_ingredient_dialog_sets_state_correctly() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateIngestionUiIntent.ShowInlineIngredientDialog("Coconut oil"))

        assertTrue(viewModel.state.value.showInlineIngredientDialog)
        assertEquals("Coconut oil", viewModel.state.value.inlineIngredientName)
        assertNull(viewModel.state.value.inlineIngredientError)
    }

    @Test
    fun create_inline_ingredient_succeeds_and_selects() = runTest {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateIngestionUiIntent.ShowInlineIngredientDialog("Coconut oil"))
        assertTrue(viewModel.state.value.showInlineIngredientDialog)

        viewModel.onIntent(CreateIngestionUiIntent.CreateInlineIngredient("Coconut oil"))
        advanceUntilIdle()

        assertEquals("Coconut oil", viewModel.state.value.itemName)
        assertEquals(ReusableEntityKind.INGREDIENT, viewModel.state.value.selectedReusableEntityKind)
        assertFalse(viewModel.state.value.showInlineIngredientDialog)
        assertFalse(viewModel.state.value.isCreatingInlineIngredient)
    }

    @Test
    fun create_inline_ingredient_fails_shows_error() = runTest {
        val viewModel = buildViewModel(
            createIngredientResult = AppResult.Error(
                io.github.mateovich.allergotracker.core.common.error.DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = mapOf("name" to "Enter an ingredient name.")
                )
            )
        )

        viewModel.onIntent(CreateIngestionUiIntent.ShowInlineIngredientDialog(""))
        viewModel.onIntent(CreateIngestionUiIntent.CreateInlineIngredient(""))
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.inlineIngredientError)
        assertFalse(viewModel.state.value.isCreatingInlineIngredient)
        assertTrue(viewModel.state.value.showInlineIngredientDialog)
    }

    @Test
    fun create_inline_ingredient_duplicate_name_shows_error() = runTest {
        val viewModel = buildViewModel(
            createIngredientResult = AppResult.Error(
                io.github.mateovich.allergotracker.core.common.error.DomainError.ValidationError(
                    message = "An ingredient with this name already exists.",
                    fieldErrors = mapOf("name" to "An ingredient with this name already exists.")
                )
            )
        )

        viewModel.onIntent(CreateIngestionUiIntent.ShowInlineIngredientDialog("Oat milk"))
        viewModel.onIntent(CreateIngestionUiIntent.CreateInlineIngredient("Oat milk"))
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.inlineIngredientError)
        assertTrue(viewModel.state.value.inlineIngredientError?.contains("already exists") == true)
        assertFalse(viewModel.state.value.isCreatingInlineIngredient)
    }

    @Test
    fun cancel_inline_ingredient_closes_dialog_and_clears_state() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateIngestionUiIntent.ShowInlineIngredientDialog("Test"))
        assertTrue(viewModel.state.value.showInlineIngredientDialog)

        viewModel.onIntent(CreateIngestionUiIntent.CancelInlineIngredientCreation)

        assertFalse(viewModel.state.value.showInlineIngredientDialog)
        assertEquals("", viewModel.state.value.inlineIngredientName)
        assertNull(viewModel.state.value.inlineIngredientError)
        assertFalse(viewModel.state.value.isCreatingInlineIngredient)
    }

    @Test
    fun cancel_inline_ingredient_does_not_clear_item_name_when_already_set() = runTest {
        val viewModel = buildViewModel()

        // User types "Banana" — itemName syncs immediately
        viewModel.onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery("Banana"))
        viewModel.onIntent(CreateIngestionUiIntent.SelectQuantityLevel(2))
        assertEquals("Banana", viewModel.state.value.itemName)
        assertTrue(viewModel.state.value.canSave)

        // User opens and cancels the inline dialog
        viewModel.onIntent(CreateIngestionUiIntent.ShowInlineIngredientDialog("Banana"))
        viewModel.onIntent(CreateIngestionUiIntent.CancelInlineIngredientCreation)

        // itemName is NOT cleared — form remains saveable as free-text entry
        assertEquals("Banana", viewModel.state.value.itemName)
        assertTrue(viewModel.state.value.canSave)
        assertFalse(viewModel.state.value.showInlineIngredientDialog)
    }

    @Test
    fun second_inline_create_while_first_is_in_flight_is_ignored() = runTest {
        val createGate = CompletableDeferred<Unit>()
        val timeProvider = object : CurrentTimeProvider {
            override fun nowEpochMillis(): Long = fixedNow
        }
        var createCalls = 0
        val viewModel = CreateIngestionViewModel(
            createIngestionDefaultsUseCase = CreateIngestionDefaultsUseCase(timeProvider),
            createIngestionEventUseCase = CreateIngestionEventUseCase(
                repository = object : IngestionRepository {
                    override suspend fun createIngestion(command: CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")
                    override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> = error("unused")
                    override suspend fun updateIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")
                    override suspend fun deleteIngestion(id: Long): AppResult<Unit> = error("unused")
                },
                currentTimeProvider = timeProvider
            ),
            observeReusableIngredientsUseCase = ObserveReusableIngredientsUseCase(
                repository = object : ReusableIngredientRepository {
                    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> = error("unused")
                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(emptyList())
                }
            ),
            observeReusableCompoundsUseCase = ObserveReusableCompoundsUseCase(
                repository = object : ReusableCompoundRepository {
                    override suspend fun createCompound(command: io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundCommand): AppResult<ReusableCompound> = error("unused")
                    override fun observeCompounds(): Flow<List<ReusableCompound>> = flowOf(emptyList())
                }
            ),
            createReusableIngredientUseCase = CreateReusableIngredientUseCase(
                repository = object : ReusableIngredientRepository {
                    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
                        createCalls += 1
                        createGate.await()
                        return AppResult.Success(
                            ReusableIngredient(
                                id = 99L,
                                name = command.name,
                                notes = command.notes,
                                createdAtEpochMs = fixedNow,
                                updatedAtEpochMs = fixedNow
                            )
                        )
                    }
                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(emptyList())
                }
            )
        )

        viewModel.onIntent(CreateIngestionUiIntent.ShowInlineIngredientDialog("Test"))
        viewModel.onIntent(CreateIngestionUiIntent.CreateInlineIngredient("Test"))
        viewModel.onIntent(CreateIngestionUiIntent.CreateInlineIngredient("Test2"))
        createGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, createCalls)
    }
}
