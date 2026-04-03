package com.example.app.features.exposure.data

import android.database.sqlite.SQLiteConstraintException
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.eventcatalog.domain.ReusableEventCatalog
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogRepository
import com.example.app.features.exposure.domain.CreateExposureEventCommand
import com.example.app.features.exposure.domain.UpdateExposureEventCommand
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomExposureRepositoryTest {

    @Test
    fun create_persists_exposure_locally_through_room_dao() = runBlocking {
        val dao = FakeExposureEventDaoForRepository(getByIdResult = null)
        val catalogRepository = FakeExposureCatalogRepository()
        val repository = RoomExposureRepository(
            exposureEventDao = dao,
            currentTimeProvider = CurrentTimeProvider { 1_700_000_123_000L },
            eventCatalogRepository = catalogRepository
        )

        val result = repository.createExposure(
            CreateExposureEventCommand(
                exposureType = "Pollen",
                occurredAtEpochMs = 1_700_000_000_000L,
                notes = "outside",
                intensityLevel = 4
            )
        )

        val savedEntity = dao.inserted.single()
        assertTrue(result is AppResult.Success)
        assertEquals("Pollen", savedEntity.exposureType)
        assertEquals(4, savedEntity.intensityLevel)
        assertEquals(1_700_000_123_000L, savedEntity.createdAtEpochMs)
        assertEquals(1L, savedEntity.exposureTypeCatalogId)
        assertEquals(listOf("Pollen"), catalogRepository.ensureRequests)
    }

    @Test
    fun update_rewrites_existing_local_exposure_record_without_network_dependency() = runBlocking {
        val existing = ExposureEventEntity(5L, "Pollen", 1_700_000_000_000L, null, 1_700_000_100_000L, intensityLevel = 2)
        val dao = FakeExposureEventDaoForRepository(getByIdResult = existing)
        val repository = RoomExposureRepository(
            exposureEventDao = dao,
            currentTimeProvider = CurrentTimeProvider { 1_700_000_123_000L }
        )

        repository.updateExposure(
            UpdateExposureEventCommand(
                id = 5L,
                exposureType = "Dust",
                occurredAtEpochMs = 1_700_000_200_000L,
                notes = "indoors",
                intensityLevel = 5
            )
        )

        assertEquals("Dust", dao.updated.single().exposureType)
        assertEquals(1_700_000_200_000L, dao.updated.single().occurredAtEpochMs)
        assertEquals("indoors", dao.updated.single().notes)
        assertEquals(5, dao.updated.single().intensityLevel)
    }

    @Test
    fun update_selected_catalog_reference_refreshes_usage_metrics() = runBlocking {
        val existing = ExposureEventEntity(
            id = 5L,
            exposureType = "Pollen",
            occurredAtEpochMs = 1_700_000_000_000L,
            notes = null,
            createdAtEpochMs = 1_700_000_100_000L,
            exposureTypeCatalogId = 9L
        )
        val catalogRepository = FakeExposureCatalogRepository()
        val repository = RoomExposureRepository(
            exposureEventDao = FakeExposureEventDaoForRepository(getByIdResult = existing),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_123_000L },
            eventCatalogRepository = catalogRepository
        )

        repository.updateExposure(
            UpdateExposureEventCommand(
                id = 5L,
                exposureType = "Pollen",
                occurredAtEpochMs = 1_700_000_200_000L,
                notes = "indoors",
                exposureTypeCatalogId = 9L
            )
        )

        assertEquals(listOf(9L), catalogRepository.touchedIds)
    }

    @Test
    fun update_missing_selected_catalog_reference_returns_integrity_error() = runBlocking {
        val existing = ExposureEventEntity(
            id = 5L,
            exposureType = "Pollen",
            occurredAtEpochMs = 1_700_000_000_000L,
            notes = null,
            createdAtEpochMs = 1_700_000_100_000L,
            exposureTypeCatalogId = 9L
        )
        val repository = RoomExposureRepository(
            exposureEventDao = FakeExposureEventDaoForRepository(getByIdResult = existing),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_123_000L },
            eventCatalogRepository = MissingExposureCatalogRepository()
        )

        val result = repository.updateExposure(
            UpdateExposureEventCommand(
                id = 5L,
                exposureType = "Pollen",
                occurredAtEpochMs = 1_700_000_200_000L,
                notes = "indoors",
                exposureTypeCatalogId = 9L
            )
        )

        assertEquals(
            DomainError.IntegrityError("Saved exposure type no longer exists."),
            (result as AppResult.Error).error
        )
    }

    @Test
    fun delete_missing_record_returns_integrity_error() = runBlocking {
        val repository = RoomExposureRepository(
            exposureEventDao = FakeExposureEventDaoForRepository(getByIdResult = null),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteExposure(5L)

        assertEquals("Exposure record no longer exists.", (result as AppResult.Error).error.message)
    }

    @Test
    fun delete_zero_rows_after_lookup_returns_integrity_error() = runBlocking {
        val repository = RoomExposureRepository(
            exposureEventDao = FakeExposureEventDaoForRepository(
                getByIdResult = ExposureEventEntity(5L, "Pollen", 1_700_000_000_000L, null, 1_700_000_000_000L),
                deleteCount = 0
            ),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteExposure(5L)

        assertEquals("Exposure record no longer exists.", (result as AppResult.Error).error.message)
    }

    @Test
    fun delete_constraint_failure_returns_integrity_error() = runBlocking {
        val repository = RoomExposureRepository(
            exposureEventDao = FakeExposureEventDaoForRepository(
                getByIdResult = ExposureEventEntity(5L, "Pollen", 1_700_000_000_000L, null, 1_700_000_000_000L),
                deleteException = SQLiteConstraintException()
            ),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteExposure(5L)

        assertEquals(DomainError.IntegrityError("Exposure record could not be deleted safely."), (result as AppResult.Error).error)
    }

    @Test
    fun delete_unexpected_failure_returns_persistence_error() = runBlocking {
        val repository = RoomExposureRepository(
            exposureEventDao = FakeExposureEventDaoForRepository(
                getByIdResult = ExposureEventEntity(5L, "Pollen", 1_700_000_000_000L, null, 1_700_000_000_000L),
                deleteException = IllegalStateException("boom")
            ),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteExposure(5L)

        assertEquals(DomainError.PersistenceError("Couldn't delete exposure locally. Try again."), (result as AppResult.Error).error)
    }
}

private class FakeExposureCatalogRepository : ReusableEventCatalogRepository {
    val ensureRequests = mutableListOf<String>()
    val touchedIds = mutableListOf<Long>()

    override fun observeCatalog(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>> = flowOf(emptyList())

    override suspend fun getCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? {
        return ReusableEventCatalog(
            id = id,
            kind = kind,
            name = "Existing",
            usageCount = 1,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            lastUsedAtEpochMs = 1L
        )
    }

    override suspend fun existsCatalogEntry(kind: ReusableEventCatalogKind, id: Long): Boolean = true

    override suspend fun ensureCatalogEntry(kind: ReusableEventCatalogKind, name: String): ReusableEventCatalog {
        ensureRequests += name
        return ReusableEventCatalog(
            id = ensureRequests.size.toLong(),
            kind = kind,
            name = name,
            usageCount = 1,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            lastUsedAtEpochMs = 1L
        )
    }

    override suspend fun touchCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? {
        touchedIds += id
        return getCatalogEntry(kind, id)
    }
}

private class MissingExposureCatalogRepository : ReusableEventCatalogRepository {
    override fun observeCatalog(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>> = flowOf(emptyList())
    override suspend fun getCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? = null
    override suspend fun existsCatalogEntry(kind: ReusableEventCatalogKind, id: Long): Boolean = false
    override suspend fun ensureCatalogEntry(kind: ReusableEventCatalogKind, name: String): ReusableEventCatalog {
        error("unused")
    }

    override suspend fun touchCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? = null
}

private class FakeExposureEventDaoForRepository(
    private val getByIdResult: ExposureEventEntity?,
    private val deleteCount: Int = 1,
    private val deleteException: Exception? = null
) : ExposureEventDao {
    val inserted = mutableListOf<ExposureEventEntity>()
    val updated = mutableListOf<ExposureEventEntity>()

    override suspend fun insert(entity: ExposureEventEntity): Long {
        inserted += entity
        return 5L
    }
    override fun observeRecordCount(): Flow<Int> = emptyFlow()
    override suspend fun getById(id: Long): ExposureEventEntity? = getByIdResult
    override fun observeById(id: Long): Flow<ExposureEventEntity?> = emptyFlow()
    override suspend fun update(entity: ExposureEventEntity): Int {
        updated += entity
        return 1
    }
    override suspend fun deleteById(id: Long): Int {
        deleteException?.let { throw it }
        return deleteCount
    }
    override suspend fun updateCatalogName(catalogId: Long, name: String): Int = 0
    override suspend fun countCatalogReferences(catalogId: Long): Int = 0
    override fun observeExposureEvents(): Flow<List<ExposureEventEntity>> = emptyFlow()
}
