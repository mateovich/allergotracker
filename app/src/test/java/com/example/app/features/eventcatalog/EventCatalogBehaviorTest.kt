package com.example.app.features.eventcatalog

import com.example.app.features.eventcatalog.data.ReusableEventCatalogDao
import com.example.app.features.eventcatalog.data.ReusableEventCatalogEntity
import com.example.app.features.eventcatalog.data.RoomReusableEventCatalogRepository
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.ui.ReusableEventCatalogOptionUiModel
import com.example.app.features.eventcatalog.ui.filterForQuery
import com.example.app.features.eventcatalog.ui.hasExactName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventCatalogBehaviorTest {

    @Test
    fun blank_query_prioritizes_usage_and_recency_then_limits_results() {
        val options = listOf(
            option(id = 1L, name = "Pollen", usageCount = 5, lastUsedAtEpochMs = 500L),
            option(id = 2L, name = "Dust", usageCount = 2, lastUsedAtEpochMs = 300L),
            option(id = 3L, name = "Soap", usageCount = 4, lastUsedAtEpochMs = 400L),
            option(id = 4L, name = "Latex", usageCount = 6, lastUsedAtEpochMs = 600L),
            option(id = 5L, name = "Heat", usageCount = 1, lastUsedAtEpochMs = 100L),
            option(id = 6L, name = "Cold", usageCount = 3, lastUsedAtEpochMs = 200L),
            option(id = 7L, name = "Smoke", usageCount = 0, lastUsedAtEpochMs = 50L)
        )

        val filtered = options.filterForQuery("")

        assertEquals(listOf(4L, 1L, 3L, 6L, 2L, 5L), filtered.map { it.id })
    }

    @Test
    fun query_prioritizes_exact_and_prefix_matches_before_contains() {
        val options = listOf(
            option(id = 1L, name = "Arm"),
            option(id = 2L, name = "Armpit"),
            option(id = 3L, name = "Forearm")
        )

        val filtered = options.filterForQuery("arm")

        assertEquals(listOf(1L, 2L, 3L), filtered.map { it.id })
    }

    @Test
    fun ensure_catalog_entry_reuses_normalized_match_and_updates_usage() = runBlocking {
        val existing = ReusableEventCatalogEntity(
            id = 8L,
            kind = ReusableEventCatalogKind.EXPOSURE_TYPE.storageValue,
            name = "Pollen",
            normalizedName = "pollen",
            usageCount = 2,
            createdAtEpochMs = 10L,
            updatedAtEpochMs = 10L,
            lastUsedAtEpochMs = 10L
        )
        val dao = FakeReusableEventCatalogDao(existingByNormalizedName = existing)
        val repository = RoomReusableEventCatalogRepository(dao) { 99L }

        val result = repository.ensureCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, "  pollen  ")

        assertEquals(8L, result.id)
        assertEquals(3, result.usageCount)
        assertEquals(1, dao.updated.size)
        assertEquals("pollen", dao.updated.single().normalizedName)
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun ensure_catalog_entry_inserts_trimmed_new_value() = runBlocking {
        val dao = FakeReusableEventCatalogDao(insertResult = 15L)
        val repository = RoomReusableEventCatalogRepository(dao) { 99L }

        val result = repository.ensureCatalogEntry(ReusableEventCatalogKind.BODY_LOCATION, "  Left   arm  ")

        assertEquals(15L, result.id)
        assertEquals(1, dao.inserted.size)
        assertEquals("Left arm", dao.inserted.single().name)
        assertEquals("left arm", dao.inserted.single().normalizedName)
    }

    @Test
    fun exact_name_and_filtering_use_same_whitespace_normalization_as_persistence() {
        val options = listOf(
            option(id = 1L, name = "Left arm")
        )

        assertTrue(options.hasExactName(" Left   arm "))
        assertEquals(listOf(1L), options.filterForQuery("left   arm").map { it.id })
    }

    @Test
    fun touch_catalog_entry_updates_usage_and_last_used() = runBlocking {
        val existing = ReusableEventCatalogEntity(
            id = 8L,
            kind = ReusableEventCatalogKind.EXPOSURE_TYPE.storageValue,
            name = "Pollen",
            normalizedName = "pollen",
            usageCount = 2,
            createdAtEpochMs = 10L,
            updatedAtEpochMs = 10L,
            lastUsedAtEpochMs = 10L
        )
        val dao = FakeReusableEventCatalogDao(existingById = existing)
        val repository = RoomReusableEventCatalogRepository(dao) { 99L }

        val result = repository.touchCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, 8L)

        assertEquals(8L, result?.id)
        assertEquals(3, result?.usageCount)
        assertEquals(1, dao.updated.size)
        assertEquals(99L, dao.updated.single().lastUsedAtEpochMs)
    }

    private fun option(
        id: Long,
        name: String,
        usageCount: Int = 0,
        lastUsedAtEpochMs: Long = 0L,
        updatedAtEpochMs: Long = lastUsedAtEpochMs
    ): ReusableEventCatalogOptionUiModel {
        return ReusableEventCatalogOptionUiModel(
            id = id,
            name = name,
            kind = ReusableEventCatalogKind.EXPOSURE_TYPE,
            usageCount = usageCount,
            lastUsedAtEpochMs = lastUsedAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }
}

private class FakeReusableEventCatalogDao(
    private val existingByNormalizedName: ReusableEventCatalogEntity? = null,
    private val existingById: ReusableEventCatalogEntity? = null,
    private val insertResult: Long = 1L
) : ReusableEventCatalogDao {
    val inserted = mutableListOf<ReusableEventCatalogEntity>()
    val updated = mutableListOf<ReusableEventCatalogEntity>()

    override suspend fun insert(entity: ReusableEventCatalogEntity): Long {
        inserted += entity
        return insertResult
    }

    override suspend fun update(entity: ReusableEventCatalogEntity): Int {
        updated += entity
        return 1
    }

    override fun observeByKind(kind: String): Flow<List<ReusableEventCatalogEntity>> = emptyFlow()

    override suspend fun findByNormalizedName(kind: String, normalizedName: String): ReusableEventCatalogEntity? {
        return existingByNormalizedName?.takeIf {
            it.kind == kind && it.normalizedName == normalizedName
        }
    }

    override suspend fun findById(kind: String, id: Long): ReusableEventCatalogEntity? {
        return existingById?.takeIf { it.kind == kind && it.id == id }
    }

    override suspend fun existsById(kind: String, id: Long): Boolean = false
}
