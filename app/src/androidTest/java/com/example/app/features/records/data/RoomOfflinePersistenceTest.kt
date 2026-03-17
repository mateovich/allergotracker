package com.example.app.features.records.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.app.core.common.result.AppResult
import com.example.app.core.database.AppDatabase
import com.example.app.features.exposure.data.RoomExposureRepository
import com.example.app.features.exposure.domain.CreateExposureEventCommand
import com.example.app.features.exposure.domain.UpdateExposureEventCommand
import com.example.app.features.ingestion.data.RoomIngestionRepository
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.UpdateIngestionEventCommand
import com.example.app.features.outbreak.data.RoomOutbreakRepository
import com.example.app.features.outbreak.domain.CreateOutbreakEventCommand
import com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomOfflinePersistenceTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun room_repositories_persist_and_reload_all_event_types_locally() = runBlocking {
        val now = 1_700_000_123_000L
        val ingestionRepository = RoomIngestionRepository(database.ingestionEventDao(), CurrentTimeProvider { now })
        val exposureRepository = RoomExposureRepository(database.exposureEventDao(), CurrentTimeProvider { now })
        val outbreakRepository = RoomOutbreakRepository(database.outbreakEventDao(), CurrentTimeProvider { now })

        val savedIngestion = ingestionRepository.createIngestion(
            CreateIngestionEventCommand(
                itemName = "Tea",
                quantityLevel = 2,
                occurredAtEpochMs = now - 3_000L,
                notes = "with honey"
            )
        ).requireSuccess().id
        val savedExposure = exposureRepository.createExposure(
            CreateExposureEventCommand(
                exposureType = "Pollen",
                occurredAtEpochMs = now - 2_000L,
                notes = "outside"
            )
        ).requireSuccess().id
        val savedOutbreak = outbreakRepository.createOutbreak(
            CreateOutbreakEventCommand(
                outbreakDescriptor = "Skin flare",
                bodyLocation = "Arm",
                occurredAtEpochMs = now - 1_000L,
                notes = "after workout"
            )
        ).requireSuccess().id

        val ingestion = ingestionRepository.getIngestion(savedIngestion).requireSuccess()
        val exposure = exposureRepository.getExposure(savedExposure).requireSuccess()
        val outbreak = outbreakRepository.getOutbreak(savedOutbreak).requireSuccess()

        assertEquals("Tea", ingestion.itemName)
        assertEquals("Pollen", exposure.exposureType)
        assertEquals("Skin flare", outbreak.outbreakDescriptor)
    }

    @Test
    fun room_records_timeline_reacts_to_create_edit_delete_sequence() = runBlocking {
        val now = 1_700_000_500_000L
        val ingestionRepository = RoomIngestionRepository(database.ingestionEventDao(), CurrentTimeProvider { now })
        val exposureRepository = RoomExposureRepository(database.exposureEventDao(), CurrentTimeProvider { now })
        val outbreakRepository = RoomOutbreakRepository(database.outbreakEventDao(), CurrentTimeProvider { now })
        val recordsRepository = RoomRecordsRepository(
            ingestionEventDao = database.ingestionEventDao(),
            exposureEventDao = database.exposureEventDao(),
            outbreakEventDao = database.outbreakEventDao()
        )

        val ingestionId = ingestionRepository.createIngestion(
            CreateIngestionEventCommand(
                itemName = "Tea",
                quantityLevel = 2,
                occurredAtEpochMs = now - 300_000L,
                notes = null
            )
        ).requireSuccess().id
        val exposureId = exposureRepository.createExposure(
            CreateExposureEventCommand(
                exposureType = "Pollen",
                occurredAtEpochMs = now - 200_000L,
                notes = "outside"
            )
        ).requireSuccess().id
        val outbreakId = outbreakRepository.createOutbreak(
            CreateOutbreakEventCommand(
                outbreakDescriptor = "Skin flare",
                bodyLocation = "Arm",
                occurredAtEpochMs = now - 100_000L,
                notes = "after workout"
            )
        ).requireSuccess().id

        ingestionRepository.updateIngestion(
            UpdateIngestionEventCommand(
                id = ingestionId,
                itemName = "Green tea",
                quantityLevel = 3,
                occurredAtEpochMs = now - 50_000L,
                notes = "after lunch"
            )
        )
        exposureRepository.updateExposure(
            UpdateExposureEventCommand(
                id = exposureId,
                exposureType = "Dust",
                occurredAtEpochMs = now - 25_000L,
                notes = "inside"
            )
        )
        outbreakRepository.deleteOutbreak(outbreakId)

        val records = recordsRepository.observeRecords().first()
        val count = recordsRepository.observeRecordCount().first()

        assertEquals(2, count)
        assertEquals(listOf("exposure-1", "ingestion-1"), records.map { it.id })
        assertEquals("Dust", records.first().title)
        assertEquals("Green tea", records.last().title)
    }

    private fun <T> AppResult<T>.requireSuccess(): T {
        assertTrue(this is AppResult.Success)
        return (this as AppResult.Success).value
    }
}
