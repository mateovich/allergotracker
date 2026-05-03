package io.github.mateovich.allergotracker.features.export.data

import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundComponentEntity
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundEntity
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundWithComponents
import io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogEntity
import io.github.mateovich.allergotracker.features.exposure.data.ExposureEventEntity
import io.github.mateovich.allergotracker.features.export.domain.ExportScope
import io.github.mateovich.allergotracker.features.ingredients.data.ReusableIngredientEntity
import io.github.mateovich.allergotracker.features.ingestion.data.IngestionEventEntity
import io.github.mateovich.allergotracker.features.outbreak.data.OutbreakEventEntity
import io.github.mateovich.allergotracker.features.records.domain.RecordEventType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomExportSnapshotRepositoryTest {

    @Test
    fun snapshot_preserves_authoritative_ids_and_component_ordering() = runBlocking {
        val repository = RoomExportSnapshotRepository(
            FakeExportSnapshotDataAccess(
                ingestions = listOf(
                    IngestionEventEntity(
                        id = 2L,
                        itemName = "Tea",
                        quantityLevel = 2,
                        occurredAtEpochMs = 200L,
                        notes = "later",
                        reusableEntityKind = "ingredient",
                        reusableEntityId = 20L,
                        createdAtEpochMs = 201L
                    )
                ),
                reusableCompounds = listOf(
                    ReusableCompoundWithComponents(
                        compound = ReusableCompoundEntity(
                            id = 9L,
                            name = "Blend",
                            normalizedName = "blend",
                            notes = null,
                            compositionStatus = "complete",
                            createdAtEpochMs = 100L,
                            updatedAtEpochMs = 101L
                        ),
                        components = listOf(
                            ReusableCompoundComponentEntity(6L, 9L, 2, "ingredient", 21L, null, 4000),
                            ReusableCompoundComponentEntity(5L, 9L, 1, "ingredient", 20L, null, 6000)
                        )
                    )
                ),
                reusableIngredients = listOf(
                    ReusableIngredientEntity(20L, "Tea", "tea", null, 100L, 101L),
                    ReusableIngredientEntity(21L, "Coffee", "coffee", null, 102L, 103L)
                )
            )
        )

        val result = repository.getSnapshot(ExportScope.AllRecords)

        require(result is AppResult.Success)
        assertEquals(20L, result.value.ingestions.single().reusableEntityRef?.id)
        assertEquals(listOf(1, 2), result.value.reusableCompounds.single().components.map { it.position })
        assertEquals(listOf(20L, 21L), result.value.reusableCompounds.single().components.map { it.componentRef.id })
    }

    @Test
    fun event_type_scope_filters_unselected_record_groups() = runBlocking {
        val repository = RoomExportSnapshotRepository(
            FakeExportSnapshotDataAccess(
                ingestions = listOf(
                    IngestionEventEntity(2L, "Tea", 2, 200L, null, "ingredient", 20L, 201L)
                ),
                reusableIngredients = listOf(
                    ReusableIngredientEntity(20L, "Tea", "tea", null, 100L, 101L),
                    ReusableIngredientEntity(21L, "Coffee", "coffee", null, 102L, 103L)
                ),
                reusableEventCatalogEntries = listOf(
                    ReusableEventCatalogEntity(1L, "exposure_type", "Sun", "sun", 2, 100L, 101L, 102L)
                )
            )
        )

        val result = repository.getSnapshot(ExportScope.EventTypeRecords(RecordEventType.INGESTION))

        require(result is AppResult.Success)
        assertEquals(listOf(20L), result.value.reusableIngredients.map { it.id })
        assertTrue(result.value.reusableCompounds.isEmpty())
        assertTrue(result.value.reusableEventCatalogEntries.isEmpty())
    }

    @Test
    fun ingestion_scope_includes_recursive_child_compounds_and_nested_ingredient_dependencies() = runBlocking {
        val repository = RoomExportSnapshotRepository(
            FakeExportSnapshotDataAccess(
                ingestions = listOf(
                    IngestionEventEntity(2L, "Blend drink", 2, 200L, null, "compound", 90L, 201L)
                ),
                reusableIngredients = listOf(
                    ReusableIngredientEntity(20L, "Tea", "tea", null, 100L, 101L),
                    ReusableIngredientEntity(21L, "Mint", "mint", null, 102L, 103L)
                ),
                reusableCompounds = listOf(
                    ReusableCompoundWithComponents(
                        compound = ReusableCompoundEntity(90L, "Blend", "blend", null, "complete", 100L, 101L),
                        components = listOf(
                            ReusableCompoundComponentEntity(1L, 90L, 1, "compound", null, 91L, 7000),
                            ReusableCompoundComponentEntity(2L, 90L, 2, "ingredient", 20L, null, 3000)
                        )
                    ),
                    ReusableCompoundWithComponents(
                        compound = ReusableCompoundEntity(91L, "Base", "base", null, "complete", 102L, 103L),
                        components = listOf(
                            ReusableCompoundComponentEntity(3L, 91L, 1, "ingredient", 21L, null, 10000)
                        )
                    )
                )
            )
        )

        val result = repository.getSnapshot(ExportScope.EventTypeRecords(RecordEventType.INGESTION))

        require(result is AppResult.Success)
        assertEquals(listOf(20L, 21L), result.value.reusableIngredients.map { it.id })
        assertEquals(listOf(90L, 91L), result.value.reusableCompounds.map { it.id })
        assertEquals("compound", result.value.ingestions.single().reusableEntityRef?.kind)
        assertEquals(91L, result.value.reusableCompounds.first().components.first().componentRef.id)
    }

    @Test
    fun missing_referenced_catalog_entry_returns_actionable_export_error() = runBlocking {
        val repository = RoomExportSnapshotRepository(
            FakeExportSnapshotDataAccess(
                outbreaks = listOf(
                    OutbreakEventEntity(
                        id = 7L,
                        outbreakDescriptor = "Rash",
                        bodyLocation = "Arm",
                        occurredAtEpochMs = 300L,
                        notes = null,
                        createdAtEpochMs = 301L,
                        intensityLevel = 2,
                        outbreakDescriptorCatalogId = 99L,
                        bodyLocationCatalogId = null
                    )
                ),
                reusableEventCatalogEntries = listOf(
                    ReusableEventCatalogEntity(12L, "outbreak_descriptor", "Rash", "rash", 1, 100L, 101L, 102L)
                )
            )
        )

        val result = repository.getSnapshot(ExportScope.EventTypeRecords(RecordEventType.OUTBREAK))

        require(result is AppResult.Error)
        assertMessageContainsAll(
            result.error.message,
            "Export blocked",
            "outbreak record 7",
            "outbreak_descriptor_catalog_id",
            "99",
            "expected 'outbreak_descriptor'",
            "Edit the outbreak saved value before exporting."
        )
    }

    @Test
    fun missing_reusable_ingredient_in_ingestion_names_record_field_kind_id_and_correction() = runBlocking {
        val repository = RoomExportSnapshotRepository(
            FakeExportSnapshotDataAccess(
                ingestions = listOf(
                    IngestionEventEntity(12L, "Tea", 2, 200L, null, "ingredient", 44L, 201L)
                ),
                reusableIngredients = emptyList()
            )
        )

        val result = repository.getSnapshot(ExportScope.AllRecords)

        require(result is AppResult.Error)
        assertMessageContainsAll(
            result.error.message,
            "Export blocked",
            "ingestion record 12",
            "missing reusable ingredient 44",
            "reusable_entity_id",
            "Edit that ingestion or restore the reusable ingredient before exporting."
        )
    }

    @Test
    fun missing_reusable_compound_in_ingestion_names_record_field_kind_id_and_correction() = runBlocking {
        val repository = RoomExportSnapshotRepository(
            FakeExportSnapshotDataAccess(
                ingestions = listOf(
                    IngestionEventEntity(13L, "Blend", 2, 200L, null, "compound", 45L, 201L)
                ),
                reusableCompounds = emptyList()
            )
        )

        val result = repository.getSnapshot(ExportScope.AllRecords)

        require(result is AppResult.Error)
        assertMessageContainsAll(
            result.error.message,
            "Export blocked",
            "ingestion record 13",
            "missing reusable compound 45",
            "reusable_entity_id",
            "Edit that ingestion or restore the reusable compound before exporting."
        )
    }

    @Test
    fun incomplete_ingestion_reusable_reference_names_both_fields_and_correction() = runBlocking {
        val repository = RoomExportSnapshotRepository(
            FakeExportSnapshotDataAccess(
                ingestions = listOf(
                    IngestionEventEntity(14L, "Tea", 2, 200L, null, "ingredient", null, 201L)
                )
            )
        )

        val result = repository.getSnapshot(ExportScope.AllRecords)

        require(result is AppResult.Error)
        assertMessageContainsAll(
            result.error.message,
            "Export blocked",
            "ingestion record 14",
            "reusable_entity_kind",
            "reusable_entity_id",
            "Edit that ingestion and set both reusable reference fields before exporting."
        )
    }

    @Test
    fun exposure_catalog_wrong_kind_names_expected_actual_field_and_correction_in_all_and_partial_scopes() = runBlocking {
        val repository = RoomExportSnapshotRepository(
            FakeExportSnapshotDataAccess(
                exposures = listOf(
                    ExposureEventEntity(15L, "Pollen", 200L, null, 201L, exposureTypeCatalogId = 99L)
                ),
                reusableEventCatalogEntries = listOf(
                    ReusableEventCatalogEntity(99L, "body_location", "Arm", "arm", 1, 100L, 101L, 102L)
                )
            )
        )

        listOf(ExportScope.AllRecords, ExportScope.EventTypeRecords(RecordEventType.EXPOSURE)).forEach { scope ->
            val result = repository.getSnapshot(scope)

            require(result is AppResult.Error)
            assertMessageContainsAll(
                result.error.message,
                "Export blocked",
                "exposure record 15",
                "exposure_type_catalog_id",
                "99",
                "expected 'exposure_type'",
                "actual 'body_location'",
                "Edit the exposure saved value before exporting."
            )
        }
    }

    @Test
    fun compound_component_missing_ids_and_unsupported_kind_name_field_and_correction() = runBlocking {
        val cases = listOf(
            ReusableCompoundComponentEntity(61L, 60L, 1, "ingredient", null, null, 10_000) to listOf(
                "compound 60",
                "component 61",
                "ingredient_id",
                "Edit that compound component before exporting."
            ),
            ReusableCompoundComponentEntity(62L, 60L, 1, "compound", null, null, 10_000) to listOf(
                "compound 60",
                "component 62",
                "child_compound_id",
                "Edit that compound component before exporting."
            ),
            ReusableCompoundComponentEntity(63L, 60L, 1, "unknown", null, null, 10_000) to listOf(
                "compound 60",
                "component 63",
                "componentKind 'unknown'",
                "Edit that compound component before exporting."
            )
        )

        cases.forEach { (component, expectedTokens) ->
            val repository = RoomExportSnapshotRepository(
                FakeExportSnapshotDataAccess(
                    reusableCompounds = listOf(
                        ReusableCompoundWithComponents(
                            compound = ReusableCompoundEntity(60L, "Broken", "broken", null, "complete", 100L, 101L),
                            components = listOf(component)
                        )
                    )
                )
            )

            val result = repository.getSnapshot(ExportScope.AllRecords)

            require(result is AppResult.Error)
            assertMessageContainsAll(result.error.message, "Export blocked", *expectedTokens.toTypedArray())
        }
    }

    @Test
    fun compound_component_missing_referenced_entities_name_field_id_and_correction() = runBlocking {
        val cases = listOf(
            ReusableCompoundComponentEntity(64L, 60L, 1, "ingredient", 404L, null, 10_000) to listOf(
                "compound 60",
                "component 64",
                "missing reusable ingredient 404",
                "ingredient_id",
                "Edit that compound component or restore the reusable ingredient before exporting."
            ),
            ReusableCompoundComponentEntity(65L, 60L, 1, "compound", null, 405L, 10_000) to listOf(
                "compound 60",
                "component 65",
                "missing child compound 405",
                "child_compound_id",
                "Edit that compound component or restore the child compound before exporting."
            )
        )

        cases.forEach { (component, expectedTokens) ->
            val repository = RoomExportSnapshotRepository(
                FakeExportSnapshotDataAccess(
                    reusableCompounds = listOf(
                        ReusableCompoundWithComponents(
                            compound = ReusableCompoundEntity(60L, "Broken", "broken", null, "complete", 100L, 101L),
                            components = listOf(component)
                        )
                    )
                )
            )

            val result = repository.getSnapshot(ExportScope.AllRecords)

            require(result is AppResult.Error)
            assertMessageContainsAll(result.error.message, "Export blocked", *expectedTokens.toTypedArray())
        }
    }
}

private fun assertMessageContainsAll(message: String, vararg expectedTokens: String) {
    expectedTokens.forEach { token ->
        assertTrue("Expected message to contain '$token' but was: $message", message.contains(token))
    }
}

private class FakeExportSnapshotDataAccess(
    private val ingestions: List<io.github.mateovich.allergotracker.features.ingestion.data.IngestionEventEntity> = emptyList(),
    private val exposures: List<io.github.mateovich.allergotracker.features.exposure.data.ExposureEventEntity> = emptyList(),
    private val outbreaks: List<io.github.mateovich.allergotracker.features.outbreak.data.OutbreakEventEntity> = emptyList(),
    private val reusableIngredients: List<io.github.mateovich.allergotracker.features.ingredients.data.ReusableIngredientEntity> = emptyList(),
    private val reusableCompounds: List<ReusableCompoundWithComponents> = emptyList(),
    private val reusableEventCatalogEntries: List<io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogEntity> = emptyList()
) : ExportSnapshotDataAccess {
    override suspend fun <T> readSnapshot(block: suspend () -> T): T = block()
    override suspend fun getIngestionEvents() = ingestions
    override suspend fun getExposureEvents() = exposures
    override suspend fun getOutbreakEvents() = outbreaks
    override suspend fun getReusableIngredients() = reusableIngredients
    override suspend fun getReusableCompounds() = reusableCompounds
    override suspend fun getReusableEventCatalogEntries() = reusableEventCatalogEntries
}
