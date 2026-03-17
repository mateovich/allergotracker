package com.example.app.features.outbreak.data

import android.database.sqlite.SQLiteConstraintException
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.outbreak.domain.CreateOutbreakEventCommand
import com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomOutbreakRepositoryTest {

    @Test
    fun create_persists_outbreak_locally_through_room_dao() = runBlocking {
        val dao = FakeOutbreakEventDaoForRepository(getByIdResult = null)
        val repository = RoomOutbreakRepository(
            outbreakEventDao = dao,
            currentTimeProvider = CurrentTimeProvider { 1_700_000_123_000L }
        )

        val result = repository.createOutbreak(
            CreateOutbreakEventCommand(
                outbreakDescriptor = "Skin flare",
                bodyLocation = "Arm",
                occurredAtEpochMs = 1_700_000_000_000L,
                notes = "after workout"
            )
        )

        val savedEntity = dao.inserted.single()
        assertTrue(result is AppResult.Success)
        assertEquals("Skin flare", savedEntity.outbreakDescriptor)
        assertEquals("Arm", savedEntity.bodyLocation)
        assertEquals(1_700_000_123_000L, savedEntity.createdAtEpochMs)
    }

    @Test
    fun update_rewrites_existing_local_outbreak_record_without_network_dependency() = runBlocking {
        val existing = OutbreakEventEntity(9L, "Skin flare", "Arm", 1_700_000_000_000L, null, 1_700_000_100_000L)
        val dao = FakeOutbreakEventDaoForRepository(getByIdResult = existing)
        val repository = RoomOutbreakRepository(
            outbreakEventDao = dao,
            currentTimeProvider = CurrentTimeProvider { 1_700_000_123_000L }
        )

        repository.updateOutbreak(
            UpdateOutbreakEventCommand(
                id = 9L,
                outbreakDescriptor = "Skin flare updated",
                bodyLocation = "Left arm",
                occurredAtEpochMs = 1_700_000_200_000L,
                notes = "settling"
            )
        )

        assertEquals("Skin flare updated", dao.updated.single().outbreakDescriptor)
        assertEquals("Left arm", dao.updated.single().bodyLocation)
        assertEquals("settling", dao.updated.single().notes)
    }

    @Test
    fun delete_missing_record_returns_integrity_error() = runBlocking {
        val repository = RoomOutbreakRepository(
            outbreakEventDao = FakeOutbreakEventDaoForRepository(getByIdResult = null),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteOutbreak(9L)

        assertEquals("Outbreak record no longer exists.", (result as AppResult.Error).error.message)
    }

    @Test
    fun delete_zero_rows_after_lookup_returns_integrity_error() = runBlocking {
        val repository = RoomOutbreakRepository(
            outbreakEventDao = FakeOutbreakEventDaoForRepository(
                getByIdResult = OutbreakEventEntity(9L, "Skin flare", "Arm", 1_700_000_000_000L, null, 1_700_000_000_000L),
                deleteCount = 0
            ),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteOutbreak(9L)

        assertEquals("Outbreak record no longer exists.", (result as AppResult.Error).error.message)
    }

    @Test
    fun delete_constraint_failure_returns_integrity_error() = runBlocking {
        val repository = RoomOutbreakRepository(
            outbreakEventDao = FakeOutbreakEventDaoForRepository(
                getByIdResult = OutbreakEventEntity(9L, "Skin flare", "Arm", 1_700_000_000_000L, null, 1_700_000_000_000L),
                deleteException = SQLiteConstraintException()
            ),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteOutbreak(9L)

        assertEquals(DomainError.IntegrityError("Outbreak record could not be deleted safely."), (result as AppResult.Error).error)
    }

    @Test
    fun delete_unexpected_failure_returns_persistence_error() = runBlocking {
        val repository = RoomOutbreakRepository(
            outbreakEventDao = FakeOutbreakEventDaoForRepository(
                getByIdResult = OutbreakEventEntity(9L, "Skin flare", "Arm", 1_700_000_000_000L, null, 1_700_000_000_000L),
                deleteException = IllegalStateException("boom")
            ),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteOutbreak(9L)

        assertEquals(DomainError.PersistenceError("Couldn't delete outbreak locally. Try again."), (result as AppResult.Error).error)
    }
}

private class FakeOutbreakEventDaoForRepository(
    private val getByIdResult: OutbreakEventEntity?,
    private val deleteCount: Int = 1,
    private val deleteException: Exception? = null
) : OutbreakEventDao {
    val inserted = mutableListOf<OutbreakEventEntity>()
    val updated = mutableListOf<OutbreakEventEntity>()

    override suspend fun insert(entity: OutbreakEventEntity): Long {
        inserted += entity
        return 9L
    }
    override fun observeRecordCount(): Flow<Int> = emptyFlow()
    override suspend fun getById(id: Long): OutbreakEventEntity? = getByIdResult
    override suspend fun update(entity: OutbreakEventEntity): Int {
        updated += entity
        return 1
    }
    override suspend fun deleteById(id: Long): Int {
        deleteException?.let { throw it }
        return deleteCount
    }
    override fun observeOutbreakEvents(): Flow<List<OutbreakEventEntity>> = emptyFlow()
}
