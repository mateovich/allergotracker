package com.example.app.features.ingestion.ui

import com.example.app.MainDispatcherRule
import com.example.app.features.compounds.domain.ObserveReusableCompoundsUseCase
import com.example.app.features.compounds.domain.ReusableCompound
import com.example.app.features.compounds.domain.ReusableCompoundRepository
import com.example.app.features.ingestion.domain.CreateIngestionDefaultsUseCase
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CreateIngestionEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.IngestionEvent
import com.example.app.features.ingestion.domain.IngestionRepository
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingredients.domain.ObserveReusableIngredientsUseCase
import com.example.app.features.ingredients.domain.ReusableIngredient
import com.example.app.features.ingredients.domain.ReusableIngredientRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun search_query_filters_reusable_entities_without_hiding_inline_create_actions() {
        val viewModel = buildViewModel(
            ingredients = listOf(
                ReusableIngredient(11L, "Oat milk", null, fixedNow, fixedNow),
                ReusableIngredient(12L, "Banana", null, fixedNow, fixedNow)
            ),
            compounds = listOf(
                ReusableCompound(21L, "Oat breakfast mix", null, com.example.app.features.compounds.domain.CompoundCompositionStatus.DRAFT, emptyList(), fixedNow, fixedNow)
            )
        )

        viewModel.onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery("oat"))

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
                ReusableCompound(21L, "Echo", null, com.example.app.features.compounds.domain.CompoundCompositionStatus.DRAFT, emptyList(), fixedNow, fixedNow - 1L),
                ReusableCompound(22L, "Foxtrot", null, com.example.app.features.compounds.domain.CompoundCompositionStatus.DRAFT, emptyList(), fixedNow, fixedNow),
                ReusableCompound(23L, "Golf", null, com.example.app.features.compounds.domain.CompoundCompositionStatus.DRAFT, emptyList(), fixedNow, fixedNow - 6L)
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
    fun integrity_error_from_save_is_mapped_back_into_form_message() = runBlocking {
        val viewModel = buildViewModel(
            saveResult = AppResult.Error(com.example.app.core.common.error.DomainError.IntegrityError("Selected reusable item is no longer available."))
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
                    override suspend fun updateIngestion(command: com.example.app.features.ingestion.domain.UpdateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")
                    override suspend fun deleteIngestion(id: Long): AppResult<Unit> = error("unused")
                },
                currentTimeProvider = timeProvider
            ),
            observeReusableIngredientsUseCase = ObserveReusableIngredientsUseCase(
                repository = object : ReusableIngredientRepository {
                    override suspend fun createIngredient(command: com.example.app.features.ingredients.domain.CreateReusableIngredientCommand): AppResult<ReusableIngredient> = error("unused")
                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(emptyList())
                }
            ),
            observeReusableCompoundsUseCase = ObserveReusableCompoundsUseCase(
                repository = object : ReusableCompoundRepository {
                    override suspend fun createCompound(command: com.example.app.features.compounds.domain.CreateReusableCompoundCommand): AppResult<ReusableCompound> = error("unused")
                    override fun observeCompounds(): Flow<List<ReusableCompound>> = flowOf(emptyList())
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
        saveResult: AppResult<IngestionEvent>? = null
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
                        error("Not used in create tests")
                    }

                    override suspend fun updateIngestion(command: com.example.app.features.ingestion.domain.UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                        error("Not used in create tests")
                    }

                    override suspend fun deleteIngestion(id: Long): AppResult<Unit> {
                        error("Not used in create tests")
                    }
                },
                currentTimeProvider = timeProvider
            ),
            observeReusableIngredientsUseCase = ObserveReusableIngredientsUseCase(
                repository = object : ReusableIngredientRepository {
                    override suspend fun createIngredient(command: com.example.app.features.ingredients.domain.CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
                        error("Not used in create ingestion tests")
                    }

                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(ingredients)
                }
            ),
            observeReusableCompoundsUseCase = ObserveReusableCompoundsUseCase(
                repository = object : ReusableCompoundRepository {
                    override suspend fun createCompound(command: com.example.app.features.compounds.domain.CreateReusableCompoundCommand): AppResult<ReusableCompound> {
                        error("Not used in create ingestion tests")
                    }

                    override fun observeCompounds(): Flow<List<ReusableCompound>> = flowOf(compounds)
                }
            )
        )
    }
}
