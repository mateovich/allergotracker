package com.example.app.features.export.data

import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.data.ReusableCompoundComponentEntity
import com.example.app.features.compounds.data.ReusableCompoundEntity
import com.example.app.features.compounds.data.ReusableCompoundWithComponents
import com.example.app.features.eventcatalog.data.ReusableEventCatalogEntity
import com.example.app.features.export.domain.ExportScope
import com.example.app.features.ingredients.data.ReusableIngredientEntity
import com.example.app.features.ingestion.data.IngestionEventEntity
import com.example.app.features.records.domain.RecordEventType
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
                )
            )
        )

        val result = repository.getSnapshot(ExportScope.AllRecords)

        require(result is AppResult.Success)
        assertEquals(20L, result.value.ingestions.single().reusableEntityId)
        assertEquals(listOf(1, 2), result.value.reusableCompounds.single().components.map { it.position })
        assertEquals(listOf(20L, 21L), result.value.reusableCompounds.single().components.map { it.ingredientId })
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
}

private class FakeExportSnapshotDataAccess(
    private val ingestions: List<com.example.app.features.ingestion.data.IngestionEventEntity> = emptyList(),
    private val exposures: List<com.example.app.features.exposure.data.ExposureEventEntity> = emptyList(),
    private val outbreaks: List<com.example.app.features.outbreak.data.OutbreakEventEntity> = emptyList(),
    private val reusableIngredients: List<com.example.app.features.ingredients.data.ReusableIngredientEntity> = emptyList(),
    private val reusableCompounds: List<ReusableCompoundWithComponents> = emptyList(),
    private val reusableEventCatalogEntries: List<com.example.app.features.eventcatalog.data.ReusableEventCatalogEntity> = emptyList()
) : ExportSnapshotDataAccess {
    override suspend fun <T> readSnapshot(block: suspend () -> T): T = block()
    override suspend fun getIngestionEvents() = ingestions
    override suspend fun getExposureEvents() = exposures
    override suspend fun getOutbreakEvents() = outbreaks
    override suspend fun getReusableIngredients() = reusableIngredients
    override suspend fun getReusableCompounds() = reusableCompounds
    override suspend fun getReusableEventCatalogEntries() = reusableEventCatalogEntries
}
