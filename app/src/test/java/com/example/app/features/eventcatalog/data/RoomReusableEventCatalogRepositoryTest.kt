package com.example.app.features.eventcatalog.data

import android.database.sqlite.SQLiteConstraintException
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomReusableEventCatalogRepositoryTest {

    @Test
    fun delete_maps_fk_constraint_failures_to_integrity_error() = runBlocking {
        val dao = FakeReusableEventCatalogDao(
            existing = ReusableEventCatalogEntity(
                id = 5L,
                kind = ReusableEventCatalogKind.EXPOSURE_TYPE.storageValue,
                name = "Pollen",
                normalizedName = "pollen",
                usageCount = 1,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
                lastUsedAtEpochMs = 1L
            ),
            deleteFailure = SQLiteConstraintException("fk")
        )
        val repository = RoomReusableEventCatalogRepository(dao) { 2_000L }

        val result = repository.deleteCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L)

        val error = result as AppResult.Error
        assertEquals(
            DomainError.IntegrityError(
                "This saved value is still linked to other records. Replace or clear those references before retiring it."
            ),
            error.error
        )
    }
}

private class FakeReusableEventCatalogDao(
    private val existing: ReusableEventCatalogEntity? = null,
    private val deleteFailure: Exception? = null
) : ReusableEventCatalogDao {
    override suspend fun insert(entity: ReusableEventCatalogEntity): Long = 1L

    override suspend fun update(entity: ReusableEventCatalogEntity): Int = 1

    override suspend fun deleteById(kind: String, id: Long): Int {
        deleteFailure?.let { throw it }
        return if (existing?.kind == kind && existing.id == id) 1 else 0
    }

    override fun observeByKind(kind: String): Flow<List<ReusableEventCatalogEntity>> = emptyFlow()

    override suspend fun findByNormalizedName(kind: String, normalizedName: String): ReusableEventCatalogEntity? = existing

    override suspend fun findById(kind: String, id: Long): ReusableEventCatalogEntity? {
        return existing?.takeIf { it.kind == kind && it.id == id }
    }

    override suspend fun existsById(kind: String, id: Long): Boolean = existing?.kind == kind && existing.id == id

    override suspend fun updateExposureReferences(catalogId: Long, name: String): Int = 0

    override suspend fun updateOutbreakDescriptorReferences(catalogId: Long, name: String): Int = 0

    override suspend fun updateBodyLocationReferences(catalogId: Long, name: String): Int = 0
}
