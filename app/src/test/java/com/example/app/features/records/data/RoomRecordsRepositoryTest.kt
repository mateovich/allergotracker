package com.example.app.features.records.data

import com.example.app.features.exposure.data.ExposureEventDao
import com.example.app.features.exposure.data.ExposureEventEntity
import com.example.app.features.ingestion.data.IngestionEventDao
import com.example.app.features.ingestion.data.IngestionEventEntity
import com.example.app.features.ingestion.data.RoomIngestionRepository
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.UpdateIngestionEventCommand
import com.example.app.features.outbreak.data.OutbreakEventDao
import com.example.app.features.outbreak.data.OutbreakEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomRecordsRepositoryTest {

    @Test
    fun outbreak_records_keep_body_location_visible_when_notes_exist() = runBlocking {
        val repository = RoomRecordsRepository(
            ingestionEventDao = FakeIngestionEventDao(),
            exposureEventDao = FakeExposureEventDao(),
            outbreakEventDao = FakeOutbreakEventDao(
                events = listOf(
                    OutbreakEventEntity(
                        id = 4L,
                        outbreakDescriptor = "Skin flare",
                        bodyLocation = "Left arm",
                        occurredAtEpochMs = 1_700_000_000_000L,
                        notes = "after workout",
                        createdAtEpochMs = 1_700_000_000_100L
                    )
                )
            )
        )

        val record = repository.observeRecords().first().single()

        assertEquals("Left arm - after workout", record.detailLabel)
        assertEquals(4L, record.sourceId)
    }

    @Test
    fun equal_timestamps_sort_by_numeric_id_descending() = runBlocking {
        val sharedTimestamp = 1_700_000_000_000L
        val repository = RoomRecordsRepository(
            ingestionEventDao = FakeIngestionEventDao(),
            exposureEventDao = FakeExposureEventDao(),
            outbreakEventDao = FakeOutbreakEventDao(
                events = listOf(
                    OutbreakEventEntity(
                        id = 9L,
                        outbreakDescriptor = "Ninth",
                        bodyLocation = "Arm",
                        occurredAtEpochMs = sharedTimestamp,
                        notes = null,
                        createdAtEpochMs = sharedTimestamp
                    ),
                    OutbreakEventEntity(
                        id = 10L,
                        outbreakDescriptor = "Tenth",
                        bodyLocation = "Leg",
                        occurredAtEpochMs = sharedTimestamp,
                        notes = null,
                        createdAtEpochMs = sharedTimestamp
                    )
                )
            )
        )

        val records = repository.observeRecords().first()

        assertEquals(listOf("outbreak-10", "outbreak-9"), records.map { it.id })
    }

    @Test
    fun edited_records_replace_existing_timeline_entry_without_duplicate_row() = runBlocking {
        val ingestionEventDao = FakeIngestionEventDao(
            events = listOf(
                IngestionEventEntity(
                    id = 7L,
                    itemName = "Tea",
                    quantityLevel = 2,
                    occurredAtEpochMs = 1_700_000_000_000L,
                    notes = "with honey",
                    createdAtEpochMs = 1_700_000_000_000L
                )
            )
        )
        val ingestionRepository = RoomIngestionRepository(
            ingestionEventDao = ingestionEventDao,
            currentTimeProvider = CurrentTimeProvider { 1_700_000_500_000L }
        )
        val recordsRepository = RoomRecordsRepository(
            ingestionEventDao = ingestionEventDao,
            exposureEventDao = FakeExposureEventDao(),
            outbreakEventDao = FakeOutbreakEventDao()
        )

        ingestionRepository.updateIngestion(
            UpdateIngestionEventCommand(
                id = 7L,
                itemName = "Tea updated",
                quantityLevel = 2,
                occurredAtEpochMs = 1_700_000_100_000L,
                notes = "with honey"
            )
        )

        val records = recordsRepository.observeRecords().first()

        assertEquals(1, records.size)
        assertEquals("ingestion-7", records.single().id)
        assertEquals("Tea updated", records.single().title)
    }

    @Test
    fun deleting_records_updates_timeline_and_count_without_stale_rows() = runBlocking {
        val ingestionEventDao = FakeIngestionEventDao(
            events = listOf(
                IngestionEventEntity(
                    id = 7L,
                    itemName = "Tea",
                    quantityLevel = 2,
                    occurredAtEpochMs = 1_700_000_200_000L,
                    notes = null,
                    createdAtEpochMs = 1_700_000_200_000L
                )
            )
        )
        val exposureEventDao = FakeExposureEventDao(
            events = listOf(
                ExposureEventEntity(
                    id = 5L,
                    exposureType = "Pollen",
                    occurredAtEpochMs = 1_700_000_100_000L,
                    notes = null,
                    createdAtEpochMs = 1_700_000_100_000L
                )
            )
        )
        val outbreakEventDao = FakeOutbreakEventDao(
            events = listOf(
                OutbreakEventEntity(
                    id = 3L,
                    outbreakDescriptor = "Skin flare",
                    bodyLocation = "Arm",
                    occurredAtEpochMs = 1_700_000_000_000L,
                    notes = null,
                    createdAtEpochMs = 1_700_000_000_000L
                )
            )
        )
        val repository = RoomRecordsRepository(ingestionEventDao, exposureEventDao, outbreakEventDao)

        ingestionEventDao.deleteById(7L)

        val records = repository.observeRecords().first()
        val count = repository.observeRecordCount().first()

        assertEquals(2, count)
        assertEquals(listOf("exposure-5", "outbreak-3"), records.map { it.id })
    }

    @Test
    fun deleting_exposure_and_outbreak_records_updates_timeline_and_count_without_stale_rows() = runBlocking {
        val ingestionEventDao = FakeIngestionEventDao(
            events = listOf(
                IngestionEventEntity(
                    id = 7L,
                    itemName = "Tea",
                    quantityLevel = 2,
                    occurredAtEpochMs = 1_700_000_200_000L,
                    notes = null,
                    createdAtEpochMs = 1_700_000_200_000L
                )
            )
        )
        val exposureEventDao = FakeExposureEventDao(
            events = listOf(
                ExposureEventEntity(
                    id = 5L,
                    exposureType = "Pollen",
                    occurredAtEpochMs = 1_700_000_100_000L,
                    notes = null,
                    createdAtEpochMs = 1_700_000_100_000L
                )
            )
        )
        val outbreakEventDao = FakeOutbreakEventDao(
            events = listOf(
                OutbreakEventEntity(
                    id = 3L,
                    outbreakDescriptor = "Skin flare",
                    bodyLocation = "Arm",
                    occurredAtEpochMs = 1_700_000_000_000L,
                    notes = null,
                    createdAtEpochMs = 1_700_000_000_000L
                )
            )
        )
        val repository = RoomRecordsRepository(ingestionEventDao, exposureEventDao, outbreakEventDao)

        exposureEventDao.deleteById(5L)
        outbreakEventDao.deleteById(3L)

        val records = repository.observeRecords().first()
        val count = repository.observeRecordCount().first()

        assertEquals(1, count)
        assertEquals(listOf("ingestion-7"), records.map { it.id })
    }

    @Test
    fun equal_timestamps_with_equal_ids_use_explicit_event_type_tie_break() = runBlocking {
        val sharedTimestamp = 1_700_000_000_000L
        val repository = RoomRecordsRepository(
            ingestionEventDao = FakeIngestionEventDao(
                events = listOf(
                    IngestionEventEntity(
                        id = 4L,
                        itemName = "Tea",
                        quantityLevel = 2,
                        occurredAtEpochMs = sharedTimestamp,
                        notes = null,
                        createdAtEpochMs = sharedTimestamp
                    )
                )
            ),
            exposureEventDao = FakeExposureEventDao(
                events = listOf(
                    ExposureEventEntity(
                        id = 4L,
                        exposureType = "Pollen",
                        occurredAtEpochMs = sharedTimestamp,
                        notes = null,
                        createdAtEpochMs = sharedTimestamp
                    )
                )
            ),
            outbreakEventDao = FakeOutbreakEventDao(
                events = listOf(
                    OutbreakEventEntity(
                        id = 4L,
                        outbreakDescriptor = "Skin flare",
                        bodyLocation = "Arm",
                        occurredAtEpochMs = sharedTimestamp,
                        notes = null,
                        createdAtEpochMs = sharedTimestamp
                    )
                )
            )
        )

        val records = repository.observeRecords().first()

        assertEquals(listOf("ingestion-4", "exposure-4", "outbreak-4"), records.map { it.id })
    }
}

private class FakeIngestionEventDao(
    private val events: List<IngestionEventEntity> = emptyList()
) : IngestionEventDao {
    private val state = MutableStateFlow(events)

    override suspend fun insert(entity: IngestionEventEntity): Long {
        val nextId = (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
        state.value = state.value + entity.copy(id = nextId)
        return nextId
    }

    override suspend fun getById(id: Long): IngestionEventEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun update(entity: IngestionEventEntity): Int {
        val index = state.value.indexOfFirst { it.id == entity.id }
        if (index == -1) return 0
        state.value = state.value.toMutableList().also { it[index] = entity }
        return 1
    }

    override suspend fun deleteById(id: Long): Int {
        val updated = state.value.filterNot { it.id == id }
        if (updated.size == state.value.size) return 0
        state.value = updated
        return 1
    }

    override fun observeRecordCount(): Flow<Int> = state.map { it.size }

    override fun observeIngestionEvents(): Flow<List<IngestionEventEntity>> = state
}

private class FakeExposureEventDao(
    private val events: List<ExposureEventEntity> = emptyList()
) : ExposureEventDao {
    private val state = MutableStateFlow(events)

    override suspend fun insert(entity: ExposureEventEntity): Long {
        val nextId = (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
        state.value = state.value + entity.copy(id = nextId)
        return nextId
    }

    override suspend fun getById(id: Long): ExposureEventEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun update(entity: ExposureEventEntity): Int {
        val index = state.value.indexOfFirst { it.id == entity.id }
        if (index == -1) return 0
        state.value = state.value.toMutableList().also { it[index] = entity }
        return 1
    }

    override suspend fun deleteById(id: Long): Int {
        val updated = state.value.filterNot { it.id == id }
        if (updated.size == state.value.size) return 0
        state.value = updated
        return 1
    }

    override fun observeRecordCount(): Flow<Int> = state.map { it.size }

    override fun observeExposureEvents(): Flow<List<ExposureEventEntity>> = state
}

private class FakeOutbreakEventDao(
    private val events: List<OutbreakEventEntity> = emptyList()
) : OutbreakEventDao {
    private val state = MutableStateFlow(events)

    override suspend fun insert(entity: OutbreakEventEntity): Long {
        val nextId = (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
        state.value = state.value + entity.copy(id = nextId)
        return nextId
    }

    override suspend fun getById(id: Long): OutbreakEventEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun update(entity: OutbreakEventEntity): Int {
        val index = state.value.indexOfFirst { it.id == entity.id }
        if (index == -1) return 0
        state.value = state.value.toMutableList().also { it[index] = entity }
        return 1
    }

    override suspend fun deleteById(id: Long): Int {
        val updated = state.value.filterNot { it.id == id }
        if (updated.size == state.value.size) return 0
        state.value = updated
        return 1
    }

    override fun observeRecordCount(): Flow<Int> = state.map { it.size }

    override fun observeOutbreakEvents(): Flow<List<OutbreakEventEntity>> = state
}
