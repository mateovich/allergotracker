package com.example.app.features.ingestion.data

import android.database.sqlite.SQLiteConstraintException
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.domain.UpdateIngestionEventCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomIngestionRepositoryTest {

    @Test
    fun create_persists_ingestion_locally_through_room_dao() = runBlocking {
        val dao = FakeIngestionEventDaoForRepository(getByIdResult = null)
        val repository = RoomIngestionRepository(
            ingestionEventDao = dao,
            currentTimeProvider = CurrentTimeProvider { 1_700_000_123_000L }
        )

        val result = repository.createIngestion(
            CreateIngestionEventCommand(
                itemName = "Tea",
                quantityLevel = 2,
                occurredAtEpochMs = 1_700_000_000_000L,
                notes = "with honey"
            )
        )

        val savedEntity = dao.inserted.single()
        assertTrue(result is AppResult.Success)
        assertEquals("Tea", savedEntity.itemName)
        assertEquals(2, savedEntity.quantityLevel)
        assertEquals(1_700_000_123_000L, savedEntity.createdAtEpochMs)
    }

    @Test
    fun update_rewrites_existing_local_record_without_network_dependency() = runBlocking {
        val existing = IngestionEventEntity(7L, "Tea", 2, 1_700_000_000_000L, null, null, null, 1_700_000_100_000L)
        val dao = FakeIngestionEventDaoForRepository(getByIdResult = existing)
        val repository = RoomIngestionRepository(
            ingestionEventDao = dao,
            currentTimeProvider = CurrentTimeProvider { 1_700_000_123_000L }
        )

        repository.updateIngestion(
            UpdateIngestionEventCommand(
                id = 7L,
                itemName = "Tea updated",
                quantityLevel = 3,
                occurredAtEpochMs = 1_700_000_200_000L,
                notes = "after lunch"
            )
        )

        assertEquals("Tea updated", dao.updated.single().itemName)
        assertEquals(3, dao.updated.single().quantityLevel)
        assertEquals(1_700_000_200_000L, dao.updated.single().occurredAtEpochMs)
    }

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
                getByIdResult = IngestionEventEntity(7L, "Tea", 2, 1_700_000_000_000L, null, null, null, 1_700_000_000_000L),
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
                getByIdResult = IngestionEventEntity(7L, "Tea", 2, 1_700_000_000_000L, null, null, null, 1_700_000_000_000L),
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
                getByIdResult = IngestionEventEntity(7L, "Tea", 2, 1_700_000_000_000L, null, null, null, 1_700_000_000_000L),
                deleteException = IllegalStateException("boom")
            ),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.deleteIngestion(7L)

        assertEquals(DomainError.PersistenceError("Couldn't delete ingestion locally. Try again."), (result as AppResult.Error).error)
    }

    @Test
    fun create_rejects_missing_reusable_reference() = runBlocking {
        val repository = RoomIngestionRepository(
            ingestionEventDao = FakeIngestionEventDaoForRepository(getByIdResult = null),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L },
            reusableEntityExists = { _, _ -> false }
        )

        val result = repository.createIngestion(
            CreateIngestionEventCommand(
                itemName = "Tea",
                quantityLevel = 2,
                occurredAtEpochMs = 1_700_000_000_000L,
                notes = null,
                reusableEntityKind = ReusableEntityKind.INGREDIENT,
                reusableEntityId = 99L
            )
        )

        assertEquals(
            DomainError.IntegrityError("Selected reusable item is no longer available."),
            (result as AppResult.Error).error
        )
    }

    @Test
    fun create_rejects_inconsistent_reusable_identity() = runBlocking {
        val repository = RoomIngestionRepository(
            ingestionEventDao = FakeIngestionEventDaoForRepository(getByIdResult = null),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = repository.createIngestion(
            CreateIngestionEventCommand(
                itemName = "Tea",
                quantityLevel = 2,
                occurredAtEpochMs = 1_700_000_000_000L,
                notes = null,
                reusableEntityKind = ReusableEntityKind.INGREDIENT,
                reusableEntityId = null
            )
        )

        assertTrue(result is AppResult.Error)
        assertEquals(
            DomainError.ValidationError(
                "Please fix the highlighted fields.",
                mapOf("itemName" to "Choose a valid reusable item or clear the selection.")
            ),
            (result as AppResult.Error).error
        )
    }

    @Test
    fun create_persists_valid_reusable_identity_without_recreating_entity() = runBlocking {
        val dao = FakeIngestionEventDaoForRepository(getByIdResult = null)
        val existenceChecks = mutableListOf<Pair<ReusableEntityKind, Long>>()
        val touched = mutableListOf<Pair<ReusableEntityKind, Long>>()
        val repository = RoomIngestionRepository(
            ingestionEventDao = dao,
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L },
            reusableEntityExists = { kind, id ->
                existenceChecks += kind to id
                true
            },
            touchReusableEntity = { kind, id ->
                touched += kind to id
            }
        )

        val result = repository.createIngestion(
            CreateIngestionEventCommand(
                itemName = "Oat milk",
                quantityLevel = 2,
                occurredAtEpochMs = 1_700_000_000_000L,
                notes = null,
                reusableEntityKind = ReusableEntityKind.INGREDIENT,
                reusableEntityId = 11L
            )
        )

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(ReusableEntityKind.INGREDIENT to 11L), existenceChecks)
        assertEquals(listOf(ReusableEntityKind.INGREDIENT to 11L), touched)
        assertEquals(ReusableEntityKind.INGREDIENT.storageValue, dao.inserted.single().reusableEntityKind)
        assertEquals(11L, dao.inserted.single().reusableEntityId)
    }

    @Test
    fun update_touches_compound_reference_after_successful_reuse() = runBlocking {
        val existing = IngestionEventEntity(7L, "Breakfast mix", 2, 1_700_000_000_000L, null, ReusableEntityKind.COMPOUND.storageValue, 22L, 1_700_000_100_000L)
        val dao = FakeIngestionEventDaoForRepository(getByIdResult = existing)
        val touched = mutableListOf<Pair<ReusableEntityKind, Long>>()
        val repository = RoomIngestionRepository(
            ingestionEventDao = dao,
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L },
            reusableEntityExists = { _, _ -> true },
            touchReusableEntity = { kind, id -> touched += kind to id }
        )

        val result = repository.updateIngestion(
            UpdateIngestionEventCommand(
                id = 7L,
                itemName = "Breakfast mix",
                quantityLevel = 2,
                occurredAtEpochMs = 1_700_000_200_000L,
                notes = null,
                reusableEntityKind = ReusableEntityKind.COMPOUND,
                reusableEntityId = 22L
            )
        )

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(ReusableEntityKind.COMPOUND to 22L), touched)
    }
}

private class FakeIngestionEventDaoForRepository(
    private val getByIdResult: IngestionEventEntity?,
    private val deleteCount: Int = 1,
    private val deleteException: Exception? = null
) : IngestionEventDao {
    val inserted = mutableListOf<IngestionEventEntity>()
    val updated = mutableListOf<IngestionEventEntity>()

    override suspend fun insert(entity: IngestionEventEntity): Long {
        inserted += entity
        return 7L
    }
    override fun observeRecordCount(): Flow<Int> = emptyFlow()
    override suspend fun getById(id: Long): IngestionEventEntity? = getByIdResult
    override fun observeById(id: Long): Flow<IngestionEventEntity?> = emptyFlow()
    override suspend fun update(entity: IngestionEventEntity): Int {
        updated += entity
        return 1
    }
    override suspend fun deleteById(id: Long): Int {
        deleteException?.let { throw it }
        return deleteCount
    }
    override suspend fun updateReusableEntityName(kind: String, reusableId: Long, name: String): Int = 0
    override suspend fun countReusableEntityReferences(kind: String, reusableId: Long): Int = 0
    override suspend fun clearReusableEntityReferences(kind: String, reusableId: Long): Int = 0
    override fun observeIngestionEvents(): Flow<List<IngestionEventEntity>> = emptyFlow()
}
