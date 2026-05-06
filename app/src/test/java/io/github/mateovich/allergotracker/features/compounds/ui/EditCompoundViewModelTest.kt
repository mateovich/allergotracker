package io.github.mateovich.allergotracker.features.compounds.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.compounds.domain.CompoundCompositionStatus
import io.github.mateovich.allergotracker.features.compounds.domain.CompoundComponentKind
import io.github.mateovich.allergotracker.features.compounds.domain.GetReusableCompoundMergePreviewUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.GetReusableCompoundRetirementImpactUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.GetReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.MergeReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ObserveReusableCompoundsUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompound
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompoundComponent
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompoundRepository
import io.github.mateovich.allergotracker.features.compounds.domain.RetireReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.UpdateReusableCompoundCommand
import io.github.mateovich.allergotracker.features.compounds.domain.UpdateReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditCompoundViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_populates_existing_value_and_save_returns_updated_selection() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentProportion(index = 0, value = "75"))
        viewModel.onIntent(EditCompoundUiIntent.UpdateName("Breakfast mix plus"))
        viewModel.onIntent(EditCompoundUiIntent.Save)

        val navigation = effect.await() as EditCompoundUiEffect.NavigateBackWithSelection

        assertEquals("Breakfast mix plus", repository.updatedCommand?.name)
        assertEquals(7_500, repository.updatedCommand?.components?.first()?.proportionBasisPoints)
        assertEquals(9L, navigation.result.id)
    }

    @Test
    fun load_maps_existing_components_and_total_label() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)

        assertEquals(2, viewModel.state.value.components.size)
        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun inline_ingredient_creation_adds_component_and_preserves_edit_state() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val ingredientRepository = FakeCreateIngredientRepository()
        val viewModel = buildViewModel(repository, createIngredientRepository = ingredientRepository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateName("Breakfast mix plus"))
        viewModel.onIntent(EditCompoundUiIntent.UpdateNotes("Unsaved note"))
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentSearchQuery("Cocoa"))
        viewModel.onIntent(EditCompoundUiIntent.ShowInlineIngredientDialog("Cocoa"))
        viewModel.onIntent(EditCompoundUiIntent.CreateInlineIngredient("Cocoa"))

        assertFalse(viewModel.state.value.showInlineIngredientDialog)
        assertFalse(viewModel.state.value.isCreatingInlineIngredient)
        assertEquals("", viewModel.state.value.componentSearchQuery)
        assertEquals("Breakfast mix plus", viewModel.state.value.name)
        assertEquals("Unsaved note", viewModel.state.value.notes)
        assertEquals(listOf("Oat milk", "Berry base", "Cocoa"), viewModel.state.value.components.map { it.name })
        assertTrue(viewModel.state.value.filteredComponents.none { it.kind == "ingredient" && it.id == 21L })
        assertEquals(CreateReusableIngredientCommand(name = "Cocoa", notes = null), ingredientRepository.createdCommands.single())
    }

    @Test
    fun successful_existing_component_add_clears_search_query() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentSearchQuery("Safe"))
        viewModel.onIntent(EditCompoundUiIntent.AddCompoundComponent(13L))

        assertEquals("", viewModel.state.value.componentSearchQuery)
        assertEquals(listOf("Oat milk", "Berry base", "Safe crunch"), viewModel.state.value.components.map { it.name })
    }

    @Test
    fun blocked_component_add_keeps_search_query() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentSearchQuery("Oat milk"))
        viewModel.onIntent(EditCompoundUiIntent.AddIngredientComponent(7L))

        assertEquals("Oat milk", viewModel.state.value.componentSearchQuery)
        assertEquals(2, viewModel.state.value.components.size)
    }

    @Test
    fun inline_ingredient_validation_error_stays_in_dialog_and_clears_on_name_change() = runBlocking {
        val viewModel = buildViewModel(
            repository = FakeEditCompoundRepository(),
            createIngredientRepository = FakeCreateIngredientRepository(
                createResult = AppResult.Error(
                    DomainError.ValidationError(
                        message = "Please fix the highlighted fields.",
                        fieldErrors = mapOf("name" to "An ingredient with this name already exists.")
                    )
                )
            )
        )

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentSearchQuery("Cocoa"))
        viewModel.onIntent(EditCompoundUiIntent.ShowInlineIngredientDialog("Cocoa"))
        viewModel.onIntent(EditCompoundUiIntent.CreateInlineIngredient("Cocoa"))

        assertTrue(viewModel.state.value.showInlineIngredientDialog)
        assertFalse(viewModel.state.value.isCreatingInlineIngredient)
        assertEquals("Cocoa", viewModel.state.value.componentSearchQuery)
        assertEquals("An ingredient with this name already exists.", viewModel.state.value.inlineIngredientError)

        viewModel.onIntent(EditCompoundUiIntent.UpdateInlineIngredientName("Cocoa powder"))

        assertEquals("Cocoa powder", viewModel.state.value.inlineIngredientName)
        assertNull(viewModel.state.value.inlineIngredientError)
    }

    @Test
    fun cancel_inline_ingredient_creation_preserves_loaded_edit_state() = runBlocking {
        val viewModel = buildViewModel(FakeEditCompoundRepository())

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateName("Breakfast mix plus"))
        viewModel.onIntent(EditCompoundUiIntent.ShowInlineIngredientDialog("Cocoa"))
        viewModel.onIntent(EditCompoundUiIntent.CancelInlineIngredientCreation)

        assertFalse(viewModel.state.value.showInlineIngredientDialog)
        assertEquals("", viewModel.state.value.inlineIngredientName)
        assertNull(viewModel.state.value.inlineIngredientError)
        assertEquals("Breakfast mix plus", viewModel.state.value.name)
        assertEquals(listOf("Oat milk", "Berry base"), viewModel.state.value.components.map { it.name })
    }

    @Test
    fun duplicate_inline_ingredient_submission_is_ignored_while_busy() = runBlocking {
        val createGate = CompletableDeferred<Unit>()
        val ingredientRepository = FakeCreateIngredientRepository(createGate = createGate)
        val viewModel = buildViewModel(
            repository = FakeEditCompoundRepository(),
            createIngredientRepository = ingredientRepository
        )

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.ShowInlineIngredientDialog("Cocoa"))
        viewModel.onIntent(EditCompoundUiIntent.CreateInlineIngredient("Cocoa"))
        viewModel.onIntent(EditCompoundUiIntent.CreateInlineIngredient("Cocoa"))
        viewModel.onIntent(EditCompoundUiIntent.CancelInlineIngredientCreation)

        assertEquals(1, ingredientRepository.createdCommands.size)
        assertTrue(viewModel.state.value.showInlineIngredientDialog)
        assertTrue(viewModel.state.value.isCreatingInlineIngredient)

        createGate.complete(Unit)
        Unit
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
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.Save)

        assertEquals("A compound with this name already exists.", viewModel.state.value.nameError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun blank_name_shows_inline_error_and_disables_save_immediately() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateName("   "))

        assertEquals("Enter a compound name.", viewModel.state.value.nameError)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun load_filters_duplicate_and_recursive_compound_options() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)

        assertTrue(viewModel.state.value.filteredComponents.none { it.kind == "compound" && it.id == 11L })
        assertTrue(viewModel.state.value.filteredComponents.none { it.kind == "compound" && it.id == 12L })
        assertTrue(viewModel.state.value.filteredComponents.any { it.kind == "compound" && it.id == 13L })
    }

    @Test
    fun stale_recursive_add_keeps_user_in_context_with_actionable_feedback() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.AddCompoundComponent(12L))

        assertEquals(2, viewModel.state.value.components.size)
        assertEquals("This compound would create a recursive composition.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun save_time_cycle_error_maps_component_target_inline() = runBlocking {
        val repository = FakeEditCompoundRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = mapOf(
                        "components[1].target" to "This compound would create a recursive composition."
                    )
                )
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.Save)

        assertEquals(
            "This compound would create a recursive composition.",
            viewModel.state.value.components[1].targetError
        )
    }

    @Test
    fun zero_percent_component_disables_save_until_removed() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentProportion(index = 0, value = "100"))
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentProportion(index = 1, value = "0"))

        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun removed_component_returns_to_picker_after_deletion() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentProportion(index = 0, value = "100"))
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentProportion(index = 1, value = "0"))
        viewModel.onIntent(EditCompoundUiIntent.RemoveComponent(1))

        assertTrue(viewModel.state.value.filteredComponents.any { it.kind == "compound" && it.id == 11L })
    }

    @Test
    fun applying_decreasing_preset_rewrites_loaded_percentages_and_keeps_save_enabled() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.DECREASING))

        assertEquals(listOf("66.67", "33.33"), viewModel.state.value.components.map { it.proportionPercent })
        assertEquals(emptyList<Int>(), viewModel.state.value.touchedComponentOrder)
        assertEquals(CompoundCompositionPreset.DECREASING, viewModel.state.value.selectedPreset)
        assertFalse(viewModel.state.value.isFormMessageError)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun applying_preset_without_components_surfaces_actionable_error() = runBlocking {
        val repository = FakeEditCompoundRepository(withComponents = false)
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))

        assertEquals("Add at least one component before applying a preset.", viewModel.state.value.componentsError)
        assertEquals("Couldn't apply uniform preset to an empty composition.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.isFormMessageError)
        assertEquals(emptyList<CompoundComponentRowUiModel>(), viewModel.state.value.components)
    }

    @Test
    fun applying_preset_clears_stale_validation_errors_after_failed_save() = runBlocking {
        val repository = FakeEditCompoundRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    "Please fix the highlighted fields.",
                    mapOf(
                        "components" to "Add at least one component.",
                        "componentsTotal" to "Keep the total at 100%.",
                        "components[0].proportion" to "Enter a proportion greater than 0%."
                    )
                )
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.Save)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))

        assertEquals(CompoundCompositionPreset.UNIFORM, viewModel.state.value.selectedPreset)
        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertTrue(viewModel.state.value.components.all { it.proportionError == null && it.targetError == null })
        assertEquals(listOf("50", "50"), viewModel.state.value.components.map { it.proportionPercent })
    }

    @Test
    fun updating_notes_clears_stale_form_message_state() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))
        viewModel.onIntent(EditCompoundUiIntent.UpdateNotes("Fresh batch"))

        assertEquals("", viewModel.state.value.formMessage)
        assertFalse(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun updating_notes_preserves_actionable_error_message() = runBlocking {
        val repository = FakeEditCompoundRepository(withComponents = false)
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))
        viewModel.onIntent(EditCompoundUiIntent.UpdateNotes("Fresh batch"))

        assertEquals("Couldn't apply uniform preset to an empty composition.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun updating_search_query_preserves_actionable_error_message() = runBlocking {
        val repository = FakeEditCompoundRepository(withComponents = false)
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentSearchQuery("oat"))

        assertEquals("Couldn't apply uniform preset to an empty composition.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun updating_notes_clears_non_sticky_validation_message() = runBlocking {
        val repository = FakeEditCompoundRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    "Please fix the highlighted fields.",
                    mapOf("name" to "A compound with this name already exists.")
                )
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.Save)
        viewModel.onIntent(EditCompoundUiIntent.UpdateNotes("Fresh batch"))

        assertEquals("", viewModel.state.value.formMessage)
        assertFalse(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun retirement_preview_shows_blocking_nested_reference_count() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(
            repository,
            countIngestionReferences = { 0 },
            countChildCompoundReferences = { 1 }
        )

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestRetirementPreview)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertEquals(1, viewModel.state.value.retirementImpact?.childCompoundReferenceCount)
        assertTrue(viewModel.state.value.retirementImpact?.isBlocked == true)
    }

    @Test
    fun merge_preview_loads_counts_for_selected_destination() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(
            repository = repository,
            countMergeIngestionReferences = { 2 },
            countMergeParentReferences = { 1 }
        )

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestMergeFlow)
        viewModel.onIntent(EditCompoundUiIntent.SelectMergeDestination(13L))
        viewModel.onIntent(EditCompoundUiIntent.LoadMergePreview)

        assertTrue(viewModel.state.value.isMergeDialogVisible)
        assertEquals(2, viewModel.state.value.mergePreview?.ingestionReferenceCount)
        assertEquals(1, viewModel.state.value.mergePreview?.parentCompoundReferenceCount)
    }

    @Test
    fun blocked_merge_preview_surfaces_structural_message() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(
            repository = repository,
            countConflictingParentCompounds = { _, _ -> 1 }
        )

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestMergeFlow)
        viewModel.onIntent(EditCompoundUiIntent.SelectMergeDestination(13L))
        viewModel.onIntent(EditCompoundUiIntent.LoadMergePreview)

        assertTrue(viewModel.state.value.mergePreview?.isBlocked == true)
        assertEquals(1, viewModel.state.value.mergePreview?.conflictingParentCompoundCount)
    }

    @Test
    fun confirm_merge_success_navigates_back_with_canonical_selection() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestMergeFlow)
        viewModel.onIntent(EditCompoundUiIntent.SelectMergeDestination(13L))
        viewModel.onIntent(EditCompoundUiIntent.LoadMergePreview)
        viewModel.onIntent(EditCompoundUiIntent.ConfirmMerge)

        val navigation = effect.await() as EditCompoundUiEffect.NavigateBackWithSelection

        assertEquals(13L, navigation.result.id)
        assertEquals("Safe crunch", navigation.result.name)
        assertFalse(viewModel.state.value.isMergeDialogVisible)
    }

    @Test
    fun confirm_merge_failure_keeps_dialog_open_and_shows_error() = runBlocking {
        val repository = FakeEditCompoundRepository(
            deleteResult = AppResult.Error(
                DomainError.IntegrityError(
                    "Merge couldn't be completed locally because stored data changed unexpectedly. Reload the preview and try again."
                )
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestMergeFlow)
        viewModel.onIntent(EditCompoundUiIntent.SelectMergeDestination(13L))
        viewModel.onIntent(EditCompoundUiIntent.LoadMergePreview)
        viewModel.onIntent(EditCompoundUiIntent.ConfirmMerge)

        assertTrue(viewModel.state.value.isMergeDialogVisible)
        assertEquals(
            "Merge couldn't be completed locally because stored data changed unexpectedly. Reload the preview and try again.",
            viewModel.state.value.mergeError
        )
        assertFalse(viewModel.state.value.isMerging)
    }

    @Test
    fun stale_merge_preview_result_is_discarded_when_selection_changes() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(
            repository = repository,
            countMergeIngestionReferences = {
                repository.replaceObservedCompounds(
                    listOf(
                        repository.currentCompound(),
                        ReusableCompound(14L, "Canonical crunch", null, CompoundCompositionStatus.COMPLETE, emptyList(), 1L, 6L)
                    )
                )
                2
            },
            countMergeParentReferences = { 1 }
        )

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestMergeFlow)
        viewModel.onIntent(EditCompoundUiIntent.SelectMergeDestination(13L))
        viewModel.onIntent(EditCompoundUiIntent.LoadMergePreview)

        assertNull(viewModel.state.value.selectedMergeDestinationId)
        assertNull(viewModel.state.value.mergePreview)
        assertFalse(viewModel.state.value.isLoadingMergePreview)
    }

    @Test
    fun confirm_retirement_navigates_back_after_successful_retire() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditCompoundUiIntent.ConfirmRetirement)

        val navigation = effect.await() as EditCompoundUiEffect.NavigateBackAfterRetirement

        assertEquals(EditCompoundUiEffect.NavigateBackAfterRetirement, navigation)
        assertFalse(viewModel.state.value.isRetirementDialogVisible)
    }

    @Test
    fun blocked_retirement_keeps_dialog_visible_without_navigation() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(
            repository,
            countIngestionReferences = { 0 },
            countChildCompoundReferences = { 1 }
        )

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditCompoundUiIntent.ConfirmRetirement)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertTrue(viewModel.state.value.retirementImpact?.isBlocked == true)
    }

    @Test
    fun confirm_retirement_failure_keeps_dialog_open_and_surfaces_actionable_error() = runBlocking {
        val repository = FakeEditCompoundRepository(
            deleteResult = AppResult.Error(
                DomainError.IntegrityError("Update the nested compounds before retiring this compound.")
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditCompoundUiIntent.ConfirmRetirement)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertEquals(
            "Update the nested compounds before retiring this compound.",
            viewModel.state.value.formMessage
        )
        assertFalse(viewModel.state.value.isRetiring)
    }

    private fun buildViewModel(
        repository: FakeEditCompoundRepository,
        countIngestionReferences: suspend (Long) -> Int = { 0 },
        countChildCompoundReferences: suspend (Long) -> Int = { 0 },
        countMergeIngestionReferences: suspend (Long) -> Int = { 0 },
        countMergeParentReferences: suspend (Long) -> Int = { 0 },
        countConflictingParentCompounds: suspend (Long, Long) -> Int = { _, _ -> 0 },
        countRecursiveParentCompounds: suspend (Long, Long) -> Int = { _, _ -> 0 },
        createIngredientRepository: FakeCreateIngredientRepository = FakeCreateIngredientRepository()
    ): EditCompoundViewModel {
        val impactUseCase = GetReusableCompoundRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = countIngestionReferences,
            countChildCompoundReferences = countChildCompoundReferences
        )
        val mergePreviewUseCase = GetReusableCompoundMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = countMergeIngestionReferences,
            countParentCompoundReferences = countMergeParentReferences,
            countConflictingParentCompounds = countConflictingParentCompounds,
            countRecursiveParentCompounds = countRecursiveParentCompounds
        )
        return EditCompoundViewModel(
            compoundId = 9L,
            getReusableCompoundUseCase = GetReusableCompoundUseCase(repository),
            updateReusableCompoundUseCase = UpdateReusableCompoundUseCase(repository),
            createReusableIngredientUseCase = CreateReusableIngredientUseCase(createIngredientRepository),
            observeReusableIngredients = ObserveReusableIngredientsUseCase(FakeObserveIngredientsRepository()),
            observeReusableCompounds = ObserveReusableCompoundsUseCase(repository),
            getReusableCompoundMergePreviewUseCase = mergePreviewUseCase,
            mergeReusableCompoundUseCase = MergeReusableCompoundUseCase(
                getPreview = mergePreviewUseCase,
                getCompound = repository::getCompound,
                deleteCompound = repository::deleteCompound,
                reassignIngestionReferences = { _, _, _ -> countMergeIngestionReferences(9L) },
                replaceParentCompoundReferences = { _, _ -> countMergeParentReferences(9L) }
            ),
            getReusableCompoundRetirementImpactUseCase = impactUseCase,
            retireReusableCompoundUseCase = RetireReusableCompoundUseCase(repository, impactUseCase)
        )
    }
}

private class FakeEditCompoundRepository(
    private val updateResult: AppResult<ReusableCompound>? = null,
    private val deleteResult: AppResult<Unit> = AppResult.Success(Unit),
    private val withComponents: Boolean = true
) : ReusableCompoundRepository {
    var updatedCommand: UpdateReusableCompoundCommand? = null
    private val observedCompounds = MutableStateFlow(
        listOf(
            currentCompoundStatic(),
            ReusableCompound(
                id = 11L,
                name = "Berry base",
                notes = null,
                compositionStatus = CompoundCompositionStatus.COMPLETE,
                components = emptyList(),
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 3L
            ),
            ReusableCompound(
                id = 12L,
                name = "Recursive base",
                notes = null,
                compositionStatus = CompoundCompositionStatus.COMPLETE,
                components = listOf(
                    ReusableCompoundComponent(
                        id = 2L,
                        position = 0,
                        kind = CompoundComponentKind.COMPOUND,
                        ingredientId = null,
                        childCompoundId = 9L,
                        proportionBasisPoints = 10_000
                    )
                ),
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 4L
            ),
            ReusableCompound(
                id = 13L,
                name = "Safe crunch",
                notes = null,
                compositionStatus = CompoundCompositionStatus.COMPLETE,
                components = emptyList(),
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 5L
            )
        )
    )

    fun replaceObservedCompounds(compounds: List<ReusableCompound>) {
        observedCompounds.value = compounds
    }

    fun currentCompound(): ReusableCompound = currentCompoundStatic()

    override suspend fun createCompound(command: io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundCommand): AppResult<ReusableCompound> {
        error("unused")
    }

    override suspend fun getCompound(id: Long): AppResult<ReusableCompound> {
        val observed = observedCompounds.value.firstOrNull { it.id == id }
        if (observed != null) {
            return AppResult.Success(
                if (id == 9L && !withComponents) {
                    observed.copy(components = emptyList())
                } else {
                    observed
                }
            )
        }
        return AppResult.Success(
            ReusableCompound(id, "Compound $id", null, CompoundCompositionStatus.COMPLETE, emptyList(), 1L, 1L)
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
                components = command.components.mapIndexed { index, component ->
                    ReusableCompoundComponent(
                        id = index + 1L,
                        position = index,
                        kind = component.kind,
                        ingredientId = component.reusableEntityId.takeIf { component.kind == CompoundComponentKind.INGREDIENT },
                        childCompoundId = component.reusableEntityId.takeIf { component.kind == CompoundComponentKind.COMPOUND },
                        proportionBasisPoints = component.proportionBasisPoints
                    )
                },
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L
            )
        )
    }

    override suspend fun deleteCompound(id: Long): AppResult<Unit> = deleteResult

    override fun observeCompounds(): Flow<List<ReusableCompound>> {
        return observedCompounds
    }

    companion object {
        private fun currentCompoundStatic(): ReusableCompound {
            return ReusableCompound(
                id = 9L,
                name = "Breakfast mix",
                notes = "Draft",
                compositionStatus = CompoundCompositionStatus.COMPLETE,
                components = listOf(
                    ReusableCompoundComponent(
                        id = 1L,
                        position = 0,
                        kind = CompoundComponentKind.INGREDIENT,
                        ingredientId = 7L,
                        childCompoundId = null,
                        proportionBasisPoints = 6_000
                    ),
                    ReusableCompoundComponent(
                        id = 2L,
                        position = 1,
                        kind = CompoundComponentKind.COMPOUND,
                        ingredientId = null,
                        childCompoundId = 11L,
                        proportionBasisPoints = 4_000
                    )
                ),
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        }
    }
}

private class FakeObserveIngredientsRepository : io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRepository {
    override suspend fun createIngredient(
        command: io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand
    ): AppResult<io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient> = error("unused")

    override fun observeIngredients(): Flow<List<io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient>> {
        return MutableStateFlow(
            listOf(
                io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient(
                    id = 7L,
                    name = "Oat milk",
                    notes = null,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 2L
                )
            )
        )
    }
}

private class FakeCreateIngredientRepository(
    private val createResult: AppResult<ReusableIngredient> = AppResult.Success(
        ReusableIngredient(
            id = 21L,
            name = "Cocoa",
            notes = null,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L
        )
    ),
    private val createGate: CompletableDeferred<Unit>? = null
) : io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRepository {
    val createdCommands = mutableListOf<CreateReusableIngredientCommand>()

    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
        createdCommands += command
        createGate?.await()
        return createResult
    }

    override fun observeIngredients(): Flow<List<ReusableIngredient>> = emptyFlow()
}
