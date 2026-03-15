package com.example.app.features.ingestion.data

import android.database.sqlite.SQLiteConstraintException
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomIngestionRepositoryTest {

    @Test
    fun delete_missing_record_returns_integrity_error() = runBlocking {
        val repository = RoomIngestionRepository(
            ingestionEventDao = FakeIngestionEventDaoForRepository(getByIdResult = null),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteIngestion(7L)

        assertEquals("Ingestion record no longer exists.", (result as AppResult.Error).error.message)
    }

    @Test
    fun delete_zero_rows_after_lookup_returns_integrity_error() = runBlocking {
        val repository = RoomIngestionRepository(
            ingestionEventDao = FakeIngestionEventDaoForRepository(
                getByIdResult = IngestionEventEntity(7L, "Tea", 2, 1_700_000_000_000L, null, 1_700_000_000_000L),
                deleteCount = 0
            ),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteIngestion(7L)

        assertEquals("Ingestion record no longer exists.", (result as AppResult.Error).error.message)
    }

    @Test
    fun delete_constraint_failure_returns_integrity_error() = runBlocking {
        val repository = RoomIngestionRepository(
            ingestionEventDao = FakeIngestionEventDaoForRepository(
                getByIdResult = IngestionEventEntity(7L, "Tea", 2, 1_700_000_000_000L, null, 1_700_000_000_000L),
                deleteException = SQLiteConstraintException()
            ),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteIngestion(7L)

        assertEquals(DomainError.IntegrityError("Ingestion record could not be deleted safely."), (result as AppResult.Error).error)
    }

    @Test
    fun delete_unexpected_failure_returns_persistence_error() = runBlocking {
        val repository = RoomIngestionRepository(
            ingestionEventDao = FakeIngestionEventDaoForRepository(
                getByIdResult = IngestionEventEntity(7L, "Tea", 2, 1_700_000_000_000L, null, 1_700_000_000_000L),
                deleteException = IllegalStateException("boom")
            ),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteIngestion(7L)

        assertEquals(DomainError.PersistenceError("Couldn't delete ingestion locally. Try again."), (result as AppResult.Error).error)
    }
}

private class FakeIngestionEventDaoForRepository(
    private val getByIdResult: IngestionEventEntity?,
    private val deleteCount: Int = 1,
    private val deleteException: Exception? = null
) : IngestionEventDao {
    override suspend fun insert(entity: IngestionEventEntity): Long = error("unused")
    override fun observeRecordCount(): Flow<Int> = emptyFlow()
    override suspend fun getById(id: Long): IngestionEventEntity? = getByIdResult
    override suspend fun update(entity: IngestionEventEntity): Int = error("unused")
    override suspend fun deleteById(id: Long): Int {
        deleteException?.let { throw it }
        return deleteCount
    }
    override fun observeIngestionEvents(): Flow<List<IngestionEventEntity>> = emptyFlow()
}
