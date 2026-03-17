package com.example.app.features.exposure.data

import android.database.sqlite.SQLiteConstraintException
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.exposure.domain.CreateExposureEventCommand
import com.example.app.features.exposure.domain.UpdateExposureEventCommand
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomExposureRepositoryTest {

    @Test
    fun create_persists_exposure_locally_through_room_dao() = runBlocking {
        val dao = FakeExposureEventDaoForRepository(getByIdResult = null)
        val repository = RoomExposureRepository(
            exposureEventDao = dao,
            currentTimeProvider = CurrentTimeProvider { 1_700_000_123_000L }
        )

        val result = repository.createExposure(
            CreateExposureEventCommand(
                exposureType = "Pollen",
                occurredAtEpochMs = 1_700_000_000_000L,
                notes = "outside"
            )
        )

        val savedEntity = dao.inserted.single()
        assertTrue(result is AppResult.Success)
        assertEquals("Pollen", savedEntity.exposureType)
        assertEquals(1_700_000_123_000L, savedEntity.createdAtEpochMs)
    }

    @Test
    fun update_rewrites_existing_local_exposure_record_without_network_dependency() = runBlocking {
        val existing = ExposureEventEntity(5L, "Pollen", 1_700_000_000_000L, null, 1_700_000_100_000L)
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
                notes = "indoors"
            )
        )

        assertEquals("Dust", dao.updated.single().exposureType)
        assertEquals(1_700_000_200_000L, dao.updated.single().occurredAtEpochMs)
        assertEquals("indoors", dao.updated.single().notes)
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
    override suspend fun update(entity: ExposureEventEntity): Int {
        updated += entity
        return 1
    }
    override suspend fun deleteById(id: Long): Int {
        deleteException?.let { throw it }
        return deleteCount
    }
    override fun observeExposureEvents(): Flow<List<ExposureEventEntity>> = emptyFlow()
}
