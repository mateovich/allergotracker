package com.example.app.features.export.domain

import com.example.app.core.common.result.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedIngredientBaseExportBuilderTest {

    private val builder = NormalizedIngredientBaseExportBuilder()

    @Test
    fun builder_flattens_nested_compounds_with_deterministic_rounding_and_ordering() {
        val result = builder.build(
            ExportSnapshot(
                ingestions = listOf(
                    ExportIngestionRecord(
                        id = 1L,
                        itemName = "Blend",
                        quantityLevel = 5,
                        occurredAtEpochMs = 100L,
                        notes = null,
                        reusableEntityRef = ExportReusableEntityReference(kind = "compound", id = 10L),
                        createdAtEpochMs = 101L
                    )
                ),
                exposures = emptyList(),
                outbreaks = emptyList(),
                reusableIngredients = listOf(
                    ExportReusableIngredientRecord(2L, "A", null, 1L, 2L),
                    ExportReusableIngredientRecord(3L, "B", null, 1L, 2L),
                    ExportReusableIngredientRecord(4L, "C", null, 1L, 2L)
                ),
                reusableCompounds = listOf(
                    ExportReusableCompoundRecord(
                        id = 10L,
                        name = "Parent",
                        notes = null,
                        compositionStatus = "complete",
                        components = listOf(
                            ExportReusableCompoundComponentRecord(1L, 1, "compound".toRef(11L), 3333),
                            ExportReusableCompoundComponentRecord(2L, 2, "ingredient".toRef(4L), 6667)
                        ),
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 2L
                    ),
                    ExportReusableCompoundRecord(
                        id = 11L,
                        name = "Child",
                        notes = null,
                        compositionStatus = "complete",
                        components = listOf(
                            ExportReusableCompoundComponentRecord(3L, 1, "ingredient".toRef(2L), 5000),
                            ExportReusableCompoundComponentRecord(4L, 2, "ingredient".toRef(3L), 5000)
                        ),
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 2L
                    )
                ),
                reusableEventCatalogEntries = emptyList()
            )
        )

        require(result is AppResult.Success)
        val ingredients = result.value.single().baseIngredients
        assertEquals(listOf(2L, 3L, 4L), ingredients.map { it.reusableIngredientRef.id })
        assertEquals(listOf(1667, 1666, 6667), ingredients.map { it.contributionBasisPoints })
        assertEquals(50_000, ingredients.sumOf { it.derivedQuantityLevelBasisPoints })
    }

    @Test
    fun builder_aggregates_duplicate_base_ingredient_paths_by_id() {
        val result = builder.build(
            ExportSnapshot(
                ingestions = listOf(
                    ExportIngestionRecord(1L, "Blend", 2, 100L, null, ExportReusableEntityReference("compound", 10L), 101L)
                ),
                exposures = emptyList(),
                outbreaks = emptyList(),
                reusableIngredients = listOf(
                    ExportReusableIngredientRecord(2L, "Tea", null, 1L, 2L)
                ),
                reusableCompounds = listOf(
                    ExportReusableCompoundRecord(
                        id = 10L,
                        name = "Parent",
                        notes = null,
                        compositionStatus = "complete",
                        components = listOf(
                            ExportReusableCompoundComponentRecord(1L, 1, "ingredient".toRef(2L), 4000),
                            ExportReusableCompoundComponentRecord(2L, 2, "compound".toRef(11L), 6000)
                        ),
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 2L
                    ),
                    ExportReusableCompoundRecord(
                        id = 11L,
                        name = "Child",
                        notes = null,
                        compositionStatus = "complete",
                        components = listOf(
                            ExportReusableCompoundComponentRecord(3L, 1, "ingredient".toRef(2L), 10000)
                        ),
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 2L
                    )
                ),
                reusableEventCatalogEntries = emptyList()
            )
        )

        require(result is AppResult.Success)
        val ingredient = result.value.single().baseIngredients.single()
        assertEquals(10_000, ingredient.contributionBasisPoints)
        assertEquals(20_000, ingredient.derivedQuantityLevelBasisPoints)
    }

    @Test
    fun builder_preserves_direct_ingredient_ingestion_as_trivial_normalized_output() {
        val result = builder.build(
            ExportSnapshot(
                ingestions = listOf(
                    ExportIngestionRecord(1L, "Tea", 3, 100L, null, ExportReusableEntityReference("ingredient", 2L), 101L)
                ),
                exposures = emptyList(),
                outbreaks = emptyList(),
                reusableIngredients = listOf(
                    ExportReusableIngredientRecord(2L, "Tea", null, 1L, 2L)
                ),
                reusableCompounds = emptyList(),
                reusableEventCatalogEntries = emptyList()
            )
        )

        require(result is AppResult.Success)
        val normalized = result.value.single()
        assertEquals(NORMALIZED_DERIVATION_KIND, normalized.derivationKind)
        assertEquals(1, normalized.baseIngredients.size)
        assertEquals(10_000, normalized.baseIngredients.single().contributionBasisPoints)
        assertEquals(30_000, normalized.baseIngredients.single().derivedQuantityLevelBasisPoints)
    }

    @Test
    fun builder_blocks_recursive_loops_in_normalized_expansion() {
        val result = builder.build(
            ExportSnapshot(
                ingestions = listOf(
                    ExportIngestionRecord(1L, "Loop", 2, 100L, null, ExportReusableEntityReference("compound", 10L), 101L)
                ),
                exposures = emptyList(),
                outbreaks = emptyList(),
                reusableIngredients = emptyList(),
                reusableCompounds = listOf(
                    ExportReusableCompoundRecord(
                        id = 10L,
                        name = "A",
                        notes = null,
                        compositionStatus = "complete",
                        components = listOf(
                            ExportReusableCompoundComponentRecord(1L, 1, "compound".toRef(11L), 10000)
                        ),
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 2L
                    ),
                    ExportReusableCompoundRecord(
                        id = 11L,
                        name = "B",
                        notes = null,
                        compositionStatus = "complete",
                        components = listOf(
                            ExportReusableCompoundComponentRecord(2L, 1, "compound".toRef(10L), 10000)
                        ),
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 2L
                    )
                ),
                reusableEventCatalogEntries = emptyList()
            )
        )

        require(result is AppResult.Error)
        assertTrue(result.error.message.contains("recursive compound loop"))
    }

    @Test
    fun builder_blocks_ingestions_missing_reusable_entity_reference() {
        val result = builder.build(
            ExportSnapshot(
                ingestions = listOf(
                    ExportIngestionRecord(1L, "Tea", 3, 100L, null, null, 101L)
                ),
                exposures = emptyList(),
                outbreaks = emptyList(),
                reusableIngredients = emptyList(),
                reusableCompounds = emptyList(),
                reusableEventCatalogEntries = emptyList()
            )
        )

        require(result is AppResult.Error)
        assertTrue(result.error.message.contains("missing reusable_entity_ref"))
    }

    @Test
    fun builder_blocks_compounds_with_invalid_total_proportions() {
        val result = builder.build(
            ExportSnapshot(
                ingestions = listOf(
                    ExportIngestionRecord(1L, "Blend", 3, 100L, null, ExportReusableEntityReference("compound", 10L), 101L)
                ),
                exposures = emptyList(),
                outbreaks = emptyList(),
                reusableIngredients = listOf(
                    ExportReusableIngredientRecord(2L, "Tea", null, 1L, 2L),
                    ExportReusableIngredientRecord(3L, "Mint", null, 1L, 2L)
                ),
                reusableCompounds = listOf(
                    ExportReusableCompoundRecord(
                        id = 10L,
                        name = "Broken",
                        notes = null,
                        compositionStatus = "complete",
                        components = listOf(
                            ExportReusableCompoundComponentRecord(1L, 1, "ingredient".toRef(2L), 5000),
                            ExportReusableCompoundComponentRecord(2L, 2, "ingredient".toRef(3L), 4999)
                        ),
                        createdAtEpochMs = 1L,
                        updatedAtEpochMs = 2L
                    )
                ),
                reusableEventCatalogEntries = emptyList()
            )
        )

        require(result is AppResult.Error)
        assertTrue(result.error.message.contains("invalid total proportion_basis_points 9999"))
    }

    @Test
    fun builder_blocks_ingestions_with_invalid_quantity_level() {
        val result = builder.build(
            ExportSnapshot(
                ingestions = listOf(
                    ExportIngestionRecord(1L, "Tea", 6, 100L, null, ExportReusableEntityReference("ingredient", 2L), 101L)
                ),
                exposures = emptyList(),
                outbreaks = emptyList(),
                reusableIngredients = listOf(
                    ExportReusableIngredientRecord(2L, "Tea", null, 1L, 2L)
                ),
                reusableCompounds = emptyList(),
                reusableEventCatalogEntries = emptyList()
            )
        )

        require(result is AppResult.Error)
        assertTrue(result.error.message.contains("invalid quantity_level 6"))
    }

    private fun String.toRef(id: Long): ExportReusableEntityReference = ExportReusableEntityReference(kind = this, id = id)
}
