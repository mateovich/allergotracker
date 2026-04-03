package com.example.app.features.records.data

import com.example.app.features.exposure.data.ExposureEventDao
import com.example.app.features.exposure.data.ExposureEventEntity
import com.example.app.features.exposure.data.RoomExposureRepository
import com.example.app.features.ingestion.data.IngestionEventDao
import com.example.app.features.ingestion.data.IngestionEventEntity
import com.example.app.features.ingestion.data.RoomIngestionRepository
import com.example.app.features.exposure.domain.CreateExposureEventCommand
import com.example.app.features.exposure.domain.UpdateExposureEventCommand
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.IngestionQuantityOption
import com.example.app.features.ingestion.domain.UpdateIngestionEventCommand
import com.example.app.features.outbreak.data.OutbreakEventDao
import com.example.app.features.outbreak.data.OutbreakEventEntity
import com.example.app.features.outbreak.data.RoomOutbreakRepository
import com.example.app.features.outbreak.domain.CreateOutbreakEventCommand
import com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordsFilter
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomRecordsRepositoryTest {

    @Test
    fun filters_records_by_date_type_and_intensity_with_and_semantics() = runBlocking {
        val zoneId = ZoneId.systemDefault()
        val matchingDay = LocalDate.of(2026, 3, 18)
        val matchingEpoch = matchingDay.atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli()
        val repository = RoomRecordsRepository(
            ingestionEventDao = FakeIngestionEventDao(
                events = listOf(
                    IngestionEventEntity(
                        id = 7L,
                        itemName = "Tea",
                        quantityLevel = 3,
                        occurredAtEpochMs = matchingEpoch,
                        notes = null,
                        reusableEntityKind = null,
                        reusableEntityId = null,
                        createdAtEpochMs = matchingEpoch
                    ),
                    IngestionEventEntity(
                        id = 8L,
                        itemName = "Coffee",
                        quantityLevel = 2,
                        occurredAtEpochMs = matchingEpoch,
                        notes = null,
                        reusableEntityKind = null,
                        reusableEntityId = null,
                        createdAtEpochMs = matchingEpoch
                    )
                )
            ),
            exposureEventDao = FakeExposureEventDao(
                events = listOf(
                    ExposureEventEntity(
                        id = 5L,
                        exposureType = "Pollen",
                        occurredAtEpochMs = matchingEpoch,
                        notes = null,
                        createdAtEpochMs = matchingEpoch
                    )
                )
            ),
            outbreakEventDao = FakeOutbreakEventDao()
        )

        val records = repository.observeRecords(
            flowOf(
                RecordsFilter(
                    startDate = matchingDay,
                    endDate = matchingDay,
                    eventTypes = setOf(RecordEventType.INGESTION),
                    intensityLevels = setOf(IngestionQuantityOption.STANDARD)
                )
            )
        ).first()

        assertEquals(listOf("ingestion-7"), records.map { it.id })
    }

    @Test
    fun intensity_filter_applies_to_matching_records_and_reset_restores_full_timeline() = runBlocking {
        val timestamp = 1_700_000_000_000L
        val repository = RoomRecordsRepository(
            ingestionEventDao = FakeIngestionEventDao(
                events = listOf(
                    IngestionEventEntity(
                        id = 1L,
                        itemName = "Tea",
                        quantityLevel = 2,
                        occurredAtEpochMs = timestamp,
                        notes = null,
                        reusableEntityKind = null,
                        reusableEntityId = null,
                        createdAtEpochMs = timestamp
                    )
                )
            ),
            exposureEventDao = FakeExposureEventDao(
                events = listOf(
                    ExposureEventEntity(
                        id = 2L,
                        exposureType = "Pollen",
                        occurredAtEpochMs = timestamp - 1_000L,
                        notes = null,
                        createdAtEpochMs = timestamp - 1_000L,
                        intensityLevel = 4
                    )
                )
            ),
            outbreakEventDao = FakeOutbreakEventDao()
        )

        val filtered = repository.observeRecords(
            flowOf(RecordsFilter(intensityLevels = setOf(IngestionQuantityOption.LIGHT_SNACK)))
        ).first()
        val reset = repository.observeRecords(flowOf(RecordsFilter())).first()

        assertEquals(listOf("ingestion-1"), filtered.map { it.id })
        assertEquals(listOf("ingestion-1", "exposure-2"), reset.map { it.id })
    }

    @Test
    fun outbreak_records_include_intensity_body_location_and_notes() = runBlocking {
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
                        createdAtEpochMs = 1_700_000_000_100L,
                        intensityLevel = 4
                    )
                )
            )
        )

        val record = repository.observeRecords().first().single()

        assertEquals("Intensity 4 - Left arm - after workout", record.detailLabel)
        assertEquals(4L, record.sourceId)
    }

    @Test
    fun exposure_records_include_intensity_in_projection_and_filtering() = runBlocking {
        val timestamp = 1_700_000_000_000L
        val repository = RoomRecordsRepository(
            ingestionEventDao = FakeIngestionEventDao(),
            exposureEventDao = FakeExposureEventDao(
                events = listOf(
                    ExposureEventEntity(
                        id = 2L,
                        exposureType = "Pollen",
                        occurredAtEpochMs = timestamp,
                        notes = "outside",
                        createdAtEpochMs = timestamp,
                        intensityLevel = 2
                    ),
                    ExposureEventEntity(
                        id = 3L,
                        exposureType = "Dust",
                        occurredAtEpochMs = timestamp - 1L,
                        notes = null,
                        createdAtEpochMs = timestamp - 1L,
                        intensityLevel = 5
                    )
                )
            ),
            outbreakEventDao = FakeOutbreakEventDao()
        )

        val filtered = repository.observeRecords(
            flowOf(RecordsFilter(intensityLevels = setOf(IngestionQuantityOption.LIGHT_SNACK)))
        ).first()

        assertEquals(listOf("exposure-2"), filtered.map { it.id })
        assertEquals("Intensity 2 - outside", filtered.single().detailLabel)
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
                    reusableEntityKind = null,
                    reusableEntityId = null,
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
                    reusableEntityKind = null,
                    reusableEntityId = null,
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
                    reusableEntityKind = null,
                    reusableEntityId = null,
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
                        reusableEntityKind = null,
                        reusableEntityId = null,
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

    @Test
    fun cross_type_ties_prefer_last_saved_record_before_source_id_or_type() = runBlocking {
        val sharedOccurredAt = 1_700_000_000_000L
        val repository = RoomRecordsRepository(
            ingestionEventDao = FakeIngestionEventDao(
                events = listOf(
                    IngestionEventEntity(
                        id = 10L,
                        itemName = "Tea",
                        quantityLevel = 2,
                        occurredAtEpochMs = sharedOccurredAt,
                        notes = null,
                        reusableEntityKind = null,
                        reusableEntityId = null,
                        createdAtEpochMs = 100L
                    )
                )
            ),
            exposureEventDao = FakeExposureEventDao(
                events = listOf(
                    ExposureEventEntity(
                        id = 1L,
                        exposureType = "Pollen",
                        occurredAtEpochMs = sharedOccurredAt,
                        notes = null,
                        createdAtEpochMs = 200L
                    )
                )
            ),
            outbreakEventDao = FakeOutbreakEventDao()
        )

        val records = repository.observeRecords().first()

        assertEquals(listOf("exposure-1", "ingestion-10"), records.map { it.id })
    }

    @Test
    fun consecutive_local_create_edit_delete_operations_keep_records_reactive_and_consistent() = runBlocking {
        val now = 1_700_000_500_000L
        val ingestionEventDao = FakeIngestionEventDao()
        val exposureEventDao = FakeExposureEventDao()
        val outbreakEventDao = FakeOutbreakEventDao()
        val recordsRepository = RoomRecordsRepository(ingestionEventDao, exposureEventDao, outbreakEventDao)
        val ingestionRepository = RoomIngestionRepository(ingestionEventDao, CurrentTimeProvider { now })
        val exposureRepository = RoomExposureRepository(exposureEventDao, CurrentTimeProvider { now })
        val outbreakRepository = RoomOutbreakRepository(outbreakEventDao, CurrentTimeProvider { now })

        ingestionRepository.createIngestion(
            CreateIngestionEventCommand(
                itemName = "Tea",
                quantityLevel = 2,
                occurredAtEpochMs = now - 300_000L,
                notes = null
            )
        )
        exposureRepository.createExposure(
            CreateExposureEventCommand(
                exposureType = "Pollen",
                occurredAtEpochMs = now - 200_000L,
                notes = "outside"
            )
        )
        outbreakRepository.createOutbreak(
            CreateOutbreakEventCommand(
                outbreakDescriptor = "Skin flare",
                bodyLocation = "Arm",
                occurredAtEpochMs = now - 100_000L,
                notes = "after workout"
            )
        )
        ingestionRepository.updateIngestion(
            UpdateIngestionEventCommand(
                id = 1L,
                itemName = "Green tea",
                quantityLevel = 3,
                occurredAtEpochMs = now - 50_000L,
                notes = "after lunch"
            )
        )
        exposureRepository.updateExposure(
            UpdateExposureEventCommand(
                id = 1L,
                exposureType = "Dust",
                occurredAtEpochMs = now - 25_000L,
                notes = "inside"
            )
        )
        outbreakRepository.deleteOutbreak(1L)

        val records = recordsRepository.observeRecords().first()
        val count = recordsRepository.observeRecordCount().first()

        assertEquals(2, count)
        assertEquals(listOf("exposure-1", "ingestion-1"), records.map { it.id })
        assertEquals("Dust", records.first().title)
        assertEquals("Green tea", records.last().title)
    }

    @Test
    fun observe_record_detail_maps_ingestion_exposure_and_outbreak_fields_by_type() = runBlocking {
        val repository = RoomRecordsRepository(
            ingestionEventDao = FakeIngestionEventDao(
                events = listOf(
                    IngestionEventEntity(
                        id = 7L,
                        itemName = "Tea",
                        quantityLevel = 2,
                        occurredAtEpochMs = 1_700_000_000_000L,
                        notes = "with honey",
                        reusableEntityKind = null,
                        reusableEntityId = null,
                        createdAtEpochMs = 1_700_000_000_000L
                    )
                )
            ),
            exposureEventDao = FakeExposureEventDao(
                events = listOf(
                    ExposureEventEntity(
                        id = 5L,
                        exposureType = "Pollen",
                        occurredAtEpochMs = 1_700_000_100_000L,
                        notes = "outside",
                        createdAtEpochMs = 1_700_000_100_000L,
                        intensityLevel = 2
                    )
                )
            ),
            outbreakEventDao = FakeOutbreakEventDao(
                events = listOf(
                    OutbreakEventEntity(
                        id = 3L,
                        outbreakDescriptor = "Skin flare",
                        bodyLocation = "Arm",
                        occurredAtEpochMs = 1_700_000_200_000L,
                        notes = "after workout",
                        createdAtEpochMs = 1_700_000_200_000L,
                        intensityLevel = 4
                    )
                )
            )
        )

        val ingestion = repository.observeRecordDetail(7L, RecordEventType.INGESTION).first()
        val exposure = repository.observeRecordDetail(5L, RecordEventType.EXPOSURE).first()
        val outbreak = repository.observeRecordDetail(3L, RecordEventType.OUTBREAK).first()

        val ingestionValue = (ingestion as AppResult.Success<*>).value as com.example.app.features.records.domain.RecordDetail.Ingestion
        val exposureValue = (exposure as AppResult.Success<*>).value as com.example.app.features.records.domain.RecordDetail.Exposure
        val outbreakValue = (outbreak as AppResult.Success<*>).value as com.example.app.features.records.domain.RecordDetail.Outbreak

        assertEquals("Tea", ingestionValue.itemName)
        assertEquals("2 Light snack", ingestionValue.quantityLabel)
        assertEquals("with honey", ingestionValue.notes)
        assertEquals("Pollen", exposureValue.exposureType)
        assertEquals("Intensity 2", exposureValue.intensityLabel)
        assertEquals("outside", exposureValue.notes)
        assertEquals("Skin flare", outbreakValue.outbreakDescriptor)
        assertEquals("Arm", outbreakValue.bodyLocation)
        assertEquals("Intensity 4", outbreakValue.intensityLabel)
        assertEquals("after workout", outbreakValue.notes)
    }

    @Test
    fun observe_record_detail_emits_unavailable_when_record_is_deleted() = runBlocking {
        val ingestionEventDao = FakeIngestionEventDao(
            events = listOf(
                IngestionEventEntity(
                    id = 7L,
                    itemName = "Tea",
                    quantityLevel = 2,
                    occurredAtEpochMs = 1_700_000_000_000L,
                    notes = null,
                    reusableEntityKind = null,
                    reusableEntityId = null,
                    createdAtEpochMs = 1_700_000_000_000L
                )
            )
        )
        val repository = RoomRecordsRepository(
            ingestionEventDao = ingestionEventDao,
            exposureEventDao = FakeExposureEventDao(),
            outbreakEventDao = FakeOutbreakEventDao()
        )

        ingestionEventDao.deleteById(7L)

        val result = repository.observeRecordDetail(7L, RecordEventType.INGESTION).first()

        assertEquals(
            "This record is no longer available.",
            (result as AppResult.Error).error.message
        )
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

    override fun observeById(id: Long): Flow<IngestionEventEntity?> = state.map { values -> values.firstOrNull { it.id == id } }

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

    override suspend fun updateReusableEntityName(kind: String, reusableId: Long, name: String): Int = 0

    override suspend fun countReusableEntityReferences(kind: String, reusableId: Long): Int = 0

    override suspend fun clearReusableEntityReferences(kind: String, reusableId: Long): Int = 0

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

    override fun observeById(id: Long): Flow<ExposureEventEntity?> = state.map { values -> values.firstOrNull { it.id == id } }

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

    override suspend fun updateCatalogName(catalogId: Long, name: String): Int = 0

    override suspend fun countCatalogReferences(catalogId: Long): Int = 0

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

    override fun observeById(id: Long): Flow<OutbreakEventEntity?> = state.map { values -> values.firstOrNull { it.id == id } }

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

    override suspend fun updateOutbreakDescriptorCatalogName(catalogId: Long, name: String): Int = 0

    override suspend fun countOutbreakDescriptorCatalogReferences(catalogId: Long): Int = 0

    override suspend fun updateBodyLocationCatalogName(catalogId: Long, name: String): Int = 0

    override suspend fun countBodyLocationCatalogReferences(catalogId: Long): Int = 0

    override fun observeRecordCount(): Flow<Int> = state.map { it.size }

    override fun observeOutbreakEvents(): Flow<List<OutbreakEventEntity>> = state
}
