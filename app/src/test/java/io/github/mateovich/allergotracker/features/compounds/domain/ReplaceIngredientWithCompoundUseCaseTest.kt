package io.github.mateovich.allergotracker.features.compounds.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaceIngredientWithCompoundUseCaseTest {

    private val oatMilkId = 1L
    private val oatsId = 2L

    @Test
    fun replace_creates_compound_relinks_records_swaps_components_and_deletes_ingredient() = runBlocking {
        val world = World(ingestionRefs = 3, componentRefs = 2, parents = listOf(50L, 51L))

        val result = world.useCase()(oatMilkId, command(oatsId))

        val success = result as AppResult.Success
        assertEquals("Oat milk", success.value.compound.name)
        assertEquals(3, success.value.reassignedIngestionCount)
        assertEquals(2, success.value.replacedComponentCount)
        assertEquals(listOf(oatMilkId), world.deletedIngredientIds)
        assertEquals(listOf(Triple(oatMilkId, 100L, "Oat milk")), world.reassignCalls)
        assertEquals(listOf(oatMilkId to 100L), world.replaceCalls)
    }

    @Test
    fun replace_without_references_only_creates_compound_and_deletes_ingredient() = runBlocking {
        val world = World()

        val result = world.useCase()(oatMilkId, command(oatsId))

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(oatMilkId), world.deletedIngredientIds)
    }

    @Test
    fun replacement_that_contains_the_replaced_ingredient_is_rejected_without_side_effects() = runBlocking {
        val world = World(ingestionRefs = 1)

        val result = world.useCase()(oatMilkId, command(oatMilkId))

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertTrue(error.fieldErrors.containsKey("components"))
        assertEquals(0, world.createCalls)
        assertTrue(world.deletedIngredientIds.isEmpty())
        assertTrue(world.reassignCalls.isEmpty())
    }

    @Test
    fun parent_that_would_contain_itself_blocks_replacement_before_any_relink_or_delete() = runBlocking {
        // New compound 100 contains compound 50; compound 50 uses the ingredient, so 50 -> 100 -> 50 would loop.
        val world = World(
            ingestionRefs = 1,
            componentRefs = 1,
            parents = listOf(50L),
            edgesAfterCreate = listOf(CompoundChildEdge(parentCompoundId = 100L, childCompoundId = 50L))
        )

        val result = world.useCase()(oatMilkId, command(oatsId))

        val error = (result as AppResult.Error).error
        assertTrue(error is DomainError.IntegrityError)
        assertTrue(error.message.contains("contain itself"))
        assertTrue(world.deletedIngredientIds.isEmpty())
        assertTrue(world.reassignCalls.isEmpty())
        assertTrue(world.replaceCalls.isEmpty())
    }

    @Test
    fun indirect_loop_through_a_descendant_also_blocks_replacement() = runBlocking {
        // 100 -> 60 -> 50, and 50 uses the ingredient: replacing it in 50 would create 50 -> 100 -> 60 -> 50.
        val world = World(
            parents = listOf(50L),
            componentRefs = 1,
            edgesAfterCreate = listOf(
                CompoundChildEdge(100L, 60L),
                CompoundChildEdge(60L, 50L)
            )
        )

        val result = world.useCase()(oatMilkId, command(oatsId))

        assertTrue((result as AppResult.Error).error is DomainError.IntegrityError)
        assertTrue(world.deletedIngredientIds.isEmpty())
    }

    @Test
    fun unrelated_compound_edges_do_not_block_replacement() = runBlocking {
        val world = World(
            parents = listOf(50L),
            componentRefs = 1,
            edgesAfterCreate = listOf(CompoundChildEdge(70L, 71L))
        )

        val result = world.useCase()(oatMilkId, command(oatsId))

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun invalid_compound_returns_validation_error_and_leaves_ingredient_untouched() = runBlocking {
        val world = World(ingestionRefs = 2, createError = DomainError.ValidationError("Please fix the highlighted fields.", mapOf("name" to "Enter a compound name.")))

        val result = world.useCase()(oatMilkId, command(oatsId))

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter a compound name.", error.fieldErrors["name"])
        assertTrue(world.deletedIngredientIds.isEmpty())
        assertTrue(world.reassignCalls.isEmpty())
    }

    @Test
    fun mismatched_relink_count_aborts_before_deleting_the_ingredient() = runBlocking {
        val world = World(ingestionRefs = 3, reassignedOverride = 2)

        val result = world.useCase()(oatMilkId, command(oatsId))

        assertTrue((result as AppResult.Error).error is DomainError.IntegrityError)
        assertTrue(world.deletedIngredientIds.isEmpty())
    }

    @Test
    fun mismatched_component_swap_count_aborts_before_deleting_the_ingredient() = runBlocking {
        val world = World(componentRefs = 2, parents = listOf(50L), replacedOverride = 1)

        val result = world.useCase()(oatMilkId, command(oatsId))

        assertTrue((result as AppResult.Error).error is DomainError.IntegrityError)
        assertTrue(world.deletedIngredientIds.isEmpty())
    }

    @Test
    fun missing_ingredient_returns_error_without_creating_a_compound() = runBlocking {
        val world = World(ingredientMissing = true)

        val result = world.useCase()(oatMilkId, command(oatsId))

        assertTrue(result is AppResult.Error)
        assertEquals(0, world.createCalls)
    }

    @Test
    fun impact_reports_name_and_reference_counts() = runBlocking {
        val world = World(ingestionRefs = 4, componentRefs = 1)

        val impact = (world.useCase().impact(oatMilkId) as AppResult.Success).value

        assertEquals("Oat milk", impact.ingredientName)
        assertEquals(4, impact.ingestionReferenceCount)
        assertEquals(1, impact.compoundReferenceCount)
    }

    private fun command(componentIngredientId: Long) = CreateReusableCompoundCommand(
        name = "Oat milk",
        notes = null,
        components = listOf(
            CreateReusableCompoundComponentCommand(
                kind = CompoundComponentKind.INGREDIENT,
                reusableEntityId = componentIngredientId,
                proportionBasisPoints = 10_000
            )
        )
    )

    private inner class World(
        private val ingestionRefs: Int = 0,
        private val componentRefs: Int = 0,
        private val parents: List<Long> = emptyList(),
        private val edgesAfterCreate: List<CompoundChildEdge> = emptyList(),
        private val createError: DomainError? = null,
        private val reassignedOverride: Int? = null,
        private val replacedOverride: Int? = null,
        private val ingredientMissing: Boolean = false
    ) {
        var createCalls = 0
        val deletedIngredientIds = mutableListOf<Long>()
        val reassignCalls = mutableListOf<Triple<Long, Long, String>>()
        val replaceCalls = mutableListOf<Pair<Long, Long>>()

        fun useCase() = ReplaceIngredientWithCompoundUseCase(
            createCompound = { command ->
                createCalls += 1
                createError?.let { AppResult.Error(it) } ?: AppResult.Success(
                    ReusableCompound(
                        id = 100L,
                        name = command.name,
                        notes = command.notes,
                        compositionStatus = CompoundCompositionStatus.COMPLETE,
                        components = emptyList(),
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 1L
                    )
                )
            },
            getIngredientName = {
                if (ingredientMissing) {
                    AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
                } else {
                    AppResult.Success("Oat milk")
                }
            },
            deleteIngredient = { id ->
                deletedIngredientIds += id
                AppResult.Success(Unit)
            },
            countIngestionReferences = { ingestionRefs },
            countComponentReferences = { componentRefs },
            findParentCompoundIds = { parents },
            findChildCompoundEdges = { edgesAfterCreate },
            reassignIngestionReferences = { ingredientId, compoundId, name ->
                reassignCalls += Triple(ingredientId, compoundId, name)
                reassignedOverride ?: ingestionRefs
            },
            replaceComponentReferences = { ingredientId, compoundId ->
                replaceCalls += ingredientId to compoundId
                replacedOverride ?: componentRefs
            }
        )
    }
}
