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
import com.example.app.features.ingredients.data.RoomReusableIngredientRepository
import com.example.app.features.ingredients.domain.CreateReusableIngredientCommand
import com.example.app.features.ingestion.data.RoomIngestionRepository
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.domain.UpdateIngestionEventCommand
import com.example.app.features.compounds.data.RoomReusableCompoundRepository
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
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

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "records-test-${System.nanoTime()}"
        context.deleteDatabase(databaseName)
        database = buildDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
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

        database.close()
        database = buildDatabase()

        val reopenedIngestionRepository = RoomIngestionRepository(database.ingestionEventDao(), CurrentTimeProvider { now })
        val reopenedExposureRepository = RoomExposureRepository(database.exposureEventDao(), CurrentTimeProvider { now })
        val reopenedOutbreakRepository = RoomOutbreakRepository(database.outbreakEventDao(), CurrentTimeProvider { now })

        val ingestion = reopenedIngestionRepository.getIngestion(savedIngestion).requireSuccess()
        val exposure = reopenedExposureRepository.getExposure(savedExposure).requireSuccess()
        val outbreak = reopenedOutbreakRepository.getOutbreak(savedOutbreak).requireSuccess()

        assertEquals("Tea", ingestion.itemName)
        assertEquals("Pollen", exposure.exposureType)
        assertEquals("Skin flare", outbreak.outbreakDescriptor)
    }

    @Test
    fun reusable_catalogs_persist_and_reload_locally() = runBlocking {
        val now = 1_700_000_123_000L
        val ingredientRepository = RoomReusableIngredientRepository(database.reusableIngredientDao()) { now }
        val compoundRepository = RoomReusableCompoundRepository(database.reusableCompoundDao()) { now }

        ingredientRepository.createIngredient(
            CreateReusableIngredientCommand(name = "Oat milk", notes = "Staple")
        ).requireSuccess()
        compoundRepository.createCompound(
            CreateReusableCompoundCommand(name = "Breakfast mix", notes = "Draft")
        ).requireSuccess()

        database.close()
        database = buildDatabase()

        val reopenedIngredientRepository = RoomReusableIngredientRepository(database.reusableIngredientDao()) { now }
        val reopenedCompoundRepository = RoomReusableCompoundRepository(database.reusableCompoundDao()) { now }

        assertEquals("Oat milk", reopenedIngredientRepository.observeIngredients().first().single().name)
        val compound = reopenedCompoundRepository.observeCompounds().first().single()
        assertEquals("Breakfast mix", compound.name)
        assertTrue(compound.components.isEmpty())
    }

    @Test
    fun ingestion_reuse_reference_survives_database_reopen_and_records_mapping() = runBlocking {
        val now = 1_700_000_123_000L
        val ingredientRepository = RoomReusableIngredientRepository(database.reusableIngredientDao()) { now }
        val reusableIngredient = ingredientRepository.createIngredient(
            CreateReusableIngredientCommand(name = "Oat milk", notes = "Staple")
        ).requireSuccess()
        val ingestionRepository = RoomIngestionRepository(
            database.ingestionEventDao(),
            CurrentTimeProvider { now },
            reusableEntityExists = { kind, id ->
                when (kind) {
                    ReusableEntityKind.INGREDIENT -> database.reusableIngredientDao().existsById(id)
                    ReusableEntityKind.COMPOUND -> database.reusableCompoundDao().existsById(id)
                }
            }
        )

        val ingestionId = ingestionRepository.createIngestion(
            CreateIngestionEventCommand(
                itemName = reusableIngredient.name,
                quantityLevel = 2,
                occurredAtEpochMs = now - 1_000L,
                notes = null,
                reusableEntityKind = ReusableEntityKind.INGREDIENT,
                reusableEntityId = reusableIngredient.id
            )
        ).requireSuccess().id

        database.close()
        database = buildDatabase()

        val reopenedIngestionRepository = RoomIngestionRepository(database.ingestionEventDao(), CurrentTimeProvider { now })
        val reopenedRecordsRepository = RoomRecordsRepository(
            ingestionEventDao = database.ingestionEventDao(),
            exposureEventDao = database.exposureEventDao(),
            outbreakEventDao = database.outbreakEventDao()
        )

        val ingestion = reopenedIngestionRepository.getIngestion(ingestionId).requireSuccess()
        val record = reopenedRecordsRepository.observeRecords().first().single()

        assertEquals("Oat milk", ingestion.itemName)
        assertEquals(ReusableEntityKind.INGREDIENT, ingestion.reusableEntityKind)
        assertEquals(reusableIngredient.id, ingestion.reusableEntityId)
        assertEquals("Oat milk", record.title)
        assertEquals("2 Light snack - Reusable ingredient", record.detailLabel)
    }

    @Test
    fun compound_reuse_reference_survives_database_reopen_and_records_mapping() = runBlocking {
        val now = 1_700_000_123_000L
        val compoundRepository = RoomReusableCompoundRepository(database.reusableCompoundDao()) { now }
        val reusableCompound = compoundRepository.createCompound(
            CreateReusableCompoundCommand(name = "Breakfast mix", notes = "Draft")
        ).requireSuccess()
        val ingestionRepository = RoomIngestionRepository(
            database.ingestionEventDao(),
            CurrentTimeProvider { now },
            reusableEntityExists = { kind, id ->
                when (kind) {
                    ReusableEntityKind.INGREDIENT -> database.reusableIngredientDao().existsById(id)
                    ReusableEntityKind.COMPOUND -> database.reusableCompoundDao().existsById(id)
                }
            }
        )

        val ingestionId = ingestionRepository.createIngestion(
            CreateIngestionEventCommand(
                itemName = reusableCompound.name,
                quantityLevel = 2,
                occurredAtEpochMs = now - 1_000L,
                notes = null,
                reusableEntityKind = ReusableEntityKind.COMPOUND,
                reusableEntityId = reusableCompound.id
            )
        ).requireSuccess().id

        database.close()
        database = buildDatabase()

        val reopenedIngestionRepository = RoomIngestionRepository(database.ingestionEventDao(), CurrentTimeProvider { now })
        val reopenedRecordsRepository = RoomRecordsRepository(
            ingestionEventDao = database.ingestionEventDao(),
            exposureEventDao = database.exposureEventDao(),
            outbreakEventDao = database.outbreakEventDao()
        )

        val ingestion = reopenedIngestionRepository.getIngestion(ingestionId).requireSuccess()
        val record = reopenedRecordsRepository.observeRecords().first().single()

        assertEquals("Breakfast mix", ingestion.itemName)
        assertEquals(ReusableEntityKind.COMPOUND, ingestion.reusableEntityKind)
        assertEquals(reusableCompound.id, ingestion.reusableEntityId)
        assertEquals("Breakfast mix", record.title)
        assertEquals("2 Light snack - Reusable compound", record.detailLabel)
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

    @Test
    fun room_records_timeline_uses_created_at_tie_break_across_types() = runBlocking {
        val baseNow = 1_700_000_500_900L
        val sharedOccurredAt = baseNow - 60_000L

        val firstIngestionRepository = RoomIngestionRepository(database.ingestionEventDao(), CurrentTimeProvider { baseNow - 400L })
        val laterExposureRepository = RoomExposureRepository(database.exposureEventDao(), CurrentTimeProvider { baseNow })
        val recordsRepository = RoomRecordsRepository(
            ingestionEventDao = database.ingestionEventDao(),
            exposureEventDao = database.exposureEventDao(),
            outbreakEventDao = database.outbreakEventDao()
        )

        firstIngestionRepository.createIngestion(
            CreateIngestionEventCommand(
                itemName = "Tea",
                quantityLevel = 2,
                occurredAtEpochMs = sharedOccurredAt,
                notes = null
            )
        ).requireSuccess()
        laterExposureRepository.createExposure(
            CreateExposureEventCommand(
                exposureType = "Pollen",
                occurredAtEpochMs = sharedOccurredAt,
                notes = null
            )
        ).requireSuccess()

        val records = recordsRepository.observeRecords().first()

        assertEquals(listOf("exposure-1", "ingestion-1"), records.map { it.id })
    }

    private fun <T> AppResult<T>.requireSuccess(): T {
        assertTrue(this is AppResult.Success)
        return (this as AppResult.Success).value
    }

    private fun buildDatabase(): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
    }
}
