package com.example.app.features.records.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.app.core.common.result.AppResult
import com.example.app.core.database.AppDatabase
import com.example.app.features.exposure.data.RoomExposureRepository
import com.example.app.features.exposure.domain.CreateExposureEventCommand
import com.example.app.features.exposure.domain.UpdateExposureEventCommand
import com.example.app.features.ingredients.data.RoomReusableIngredientRepository
import com.example.app.features.ingredients.domain.CreateReusableIngredientCommand
import com.example.app.features.ingredients.domain.GetReusableIngredientRetirementImpactUseCase
import com.example.app.features.ingredients.domain.RetireReusableIngredientUseCase
import com.example.app.features.ingredients.domain.UpdateReusableIngredientCommand
import com.example.app.features.ingredients.domain.UpdateReusableIngredientUseCase
import com.example.app.features.ingestion.data.RoomIngestionRepository
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.domain.UpdateIngestionEventCommand
import com.example.app.features.compounds.data.RoomReusableCompoundRepository
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
import com.example.app.features.compounds.domain.GetReusableCompoundRetirementImpactUseCase
import com.example.app.features.compounds.domain.RetireReusableCompoundUseCase
import com.example.app.features.compounds.domain.UpdateReusableCompoundCommand
import com.example.app.features.compounds.domain.UpdateReusableCompoundUseCase
import com.example.app.features.eventcatalog.data.RoomReusableEventCatalogRepository
import com.example.app.features.eventcatalog.domain.GetReusableEventCatalogRetirementImpactUseCase
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.RetireReusableEventCatalogUseCase
import com.example.app.features.eventcatalog.domain.UpdateReusableEventCatalogUseCase
import com.example.app.features.outbreak.data.RoomOutbreakRepository
import com.example.app.features.outbreak.domain.CreateOutbreakEventCommand
import com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand
import com.example.app.features.records.domain.RecordDetail
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordsFilter
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun editing_reusable_entities_updates_linked_records_after_database_reopen() = runBlocking {
        val now = 1_700_000_123_000L
        val ingredientRepository = RoomReusableIngredientRepository(database.reusableIngredientDao()) { now }
        val compoundRepository = RoomReusableCompoundRepository(database.reusableCompoundDao()) { now }
        val eventCatalogRepository = RoomReusableEventCatalogRepository(database.reusableEventCatalogDao()) { now }

        val reusableIngredient = ingredientRepository.createIngredient(
            CreateReusableIngredientCommand(name = "Oat milk", notes = "Staple")
        ).requireSuccess()
        val reusableCompound = compoundRepository.createCompound(
            CreateReusableCompoundCommand(name = "Breakfast mix", notes = "Draft")
        ).requireSuccess()
        val exposureCatalog = eventCatalogRepository.ensureCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, "Pollen")
        val descriptorCatalog = eventCatalogRepository.ensureCatalogEntry(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, "Skin flare")
        val bodyLocationCatalog = eventCatalogRepository.ensureCatalogEntry(ReusableEventCatalogKind.BODY_LOCATION, "Left arm")

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
        val exposureRepository = RoomExposureRepository(database.exposureEventDao(), CurrentTimeProvider { now }, eventCatalogRepository)
        val outbreakRepository = RoomOutbreakRepository(database.outbreakEventDao(), CurrentTimeProvider { now }, eventCatalogRepository)

        ingestionRepository.createIngestion(
            CreateIngestionEventCommand(
                itemName = reusableIngredient.name,
                quantityLevel = 2,
                occurredAtEpochMs = now - 3_000L,
                notes = null,
                reusableEntityKind = ReusableEntityKind.INGREDIENT,
                reusableEntityId = reusableIngredient.id
            )
        ).requireSuccess()
        ingestionRepository.createIngestion(
            CreateIngestionEventCommand(
                itemName = reusableCompound.name,
                quantityLevel = 3,
                occurredAtEpochMs = now - 2_500L,
                notes = null,
                reusableEntityKind = ReusableEntityKind.COMPOUND,
                reusableEntityId = reusableCompound.id
            )
        ).requireSuccess()
        exposureRepository.createExposure(
            CreateExposureEventCommand(
                exposureType = exposureCatalog.name,
                occurredAtEpochMs = now - 2_000L,
                notes = null,
                exposureTypeCatalogId = exposureCatalog.id
            )
        ).requireSuccess()
        outbreakRepository.createOutbreak(
            CreateOutbreakEventCommand(
                outbreakDescriptor = descriptorCatalog.name,
                bodyLocation = bodyLocationCatalog.name,
                occurredAtEpochMs = now - 1_000L,
                notes = null,
                outbreakDescriptorCatalogId = descriptorCatalog.id,
                bodyLocationCatalogId = bodyLocationCatalog.id
            )
        ).requireSuccess()

        UpdateReusableIngredientUseCase(
            repository = ingredientRepository,
            syncReferences = { ingredientId, name ->
                database.ingestionEventDao().updateReusableEntityName(ReusableEntityKind.INGREDIENT.storageValue, ingredientId, name)
            }
        ).invoke(UpdateReusableIngredientCommand(reusableIngredient.id, "Oat milk plus", "Staple"))
        UpdateReusableCompoundUseCase(
            repository = compoundRepository,
            syncReferences = { compoundId, name ->
                database.ingestionEventDao().updateReusableEntityName(ReusableEntityKind.COMPOUND.storageValue, compoundId, name)
            }
        ).invoke(UpdateReusableCompoundCommand(reusableCompound.id, "Breakfast mix plus", "Draft"))
        UpdateReusableEventCatalogUseCase(
            repository = eventCatalogRepository,
            syncReferences = { kind, catalogId, name ->
                when (kind) {
                    ReusableEventCatalogKind.EXPOSURE_TYPE -> database.exposureEventDao().updateCatalogName(catalogId, name)
                    ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> database.outbreakEventDao().updateOutbreakDescriptorCatalogName(catalogId, name)
                    ReusableEventCatalogKind.BODY_LOCATION -> database.outbreakEventDao().updateBodyLocationCatalogName(catalogId, name)
                }
            }
        ).invoke(ReusableEventCatalogKind.EXPOSURE_TYPE, exposureCatalog.id, "Dust")
        UpdateReusableEventCatalogUseCase(
            repository = eventCatalogRepository,
            syncReferences = { kind, catalogId, name ->
                when (kind) {
                    ReusableEventCatalogKind.EXPOSURE_TYPE -> database.exposureEventDao().updateCatalogName(catalogId, name)
                    ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> database.outbreakEventDao().updateOutbreakDescriptorCatalogName(catalogId, name)
                    ReusableEventCatalogKind.BODY_LOCATION -> database.outbreakEventDao().updateBodyLocationCatalogName(catalogId, name)
                }
            }
        ).invoke(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, descriptorCatalog.id, "Skin flare plus")
        UpdateReusableEventCatalogUseCase(
            repository = eventCatalogRepository,
            syncReferences = { kind, catalogId, name ->
                when (kind) {
                    ReusableEventCatalogKind.EXPOSURE_TYPE -> database.exposureEventDao().updateCatalogName(catalogId, name)
                    ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> database.outbreakEventDao().updateOutbreakDescriptorCatalogName(catalogId, name)
                    ReusableEventCatalogKind.BODY_LOCATION -> database.outbreakEventDao().updateBodyLocationCatalogName(catalogId, name)
                }
            }
        ).invoke(ReusableEventCatalogKind.BODY_LOCATION, bodyLocationCatalog.id, "Right arm")

        database.close()
        database = buildDatabase()

        val reopenedRecordsRepository = RoomRecordsRepository(
            ingestionEventDao = database.ingestionEventDao(),
            exposureEventDao = database.exposureEventDao(),
            outbreakEventDao = database.outbreakEventDao()
        )

        val records = reopenedRecordsRepository.observeRecords().first()
        val exposureRecords = reopenedRecordsRepository.observeRecords(
            flowOf(RecordsFilter(eventTypes = setOf(RecordEventType.EXPOSURE)))
        ).first()
        val exposureDetail = reopenedRecordsRepository.observeRecordDetail(1L, RecordEventType.EXPOSURE).first()
        val outbreakDetail = reopenedRecordsRepository.observeRecordDetail(1L, RecordEventType.OUTBREAK).first()

        assertTrue(records.any { it.title == "Oat milk plus" })
        assertTrue(records.any { it.title == "Breakfast mix plus" })
        assertTrue(records.any { it.title == "Dust" })
        assertTrue(records.any { it.title == "Skin flare plus" && it.detailLabel.contains("Right arm") })
        assertEquals(listOf("Dust"), exposureRecords.map { it.title })
        assertEquals(
            "Dust",
            ((exposureDetail as AppResult.Success<RecordDetail.Exposure>).value).exposureType
        )
        val reopenedOutbreak = (outbreakDetail as AppResult.Success<RecordDetail.Outbreak>).value
        assertEquals("Skin flare plus", reopenedOutbreak.outbreakDescriptor)
        assertEquals("Right arm", reopenedOutbreak.bodyLocation)
    }

    @Test
    fun failed_reusable_edit_sync_rolls_back_canonical_and_linked_labels() = runBlocking {
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

        val result = UpdateReusableIngredientUseCase(
            repository = ingredientRepository,
            syncReferences = { ingredientId, name ->
                database.ingestionEventDao().updateReusableEntityName(ReusableEntityKind.INGREDIENT.storageValue, ingredientId, name)
                throw IllegalStateException("boom")
            },
            countReferences = { ingredientId ->
                database.ingestionEventDao().countReusableEntityReferences(ReusableEntityKind.INGREDIENT.storageValue, ingredientId)
            },
            runAtomically = { block ->
                database.withTransaction { block() }
            }
        ).invoke(UpdateReusableIngredientCommand(reusableIngredient.id, "Oat milk plus", "Staple"))

        assertTrue(result is AppResult.Error)
        assertEquals("Oat milk", database.reusableIngredientDao().findById(reusableIngredient.id)?.name)
        assertEquals("Oat milk", database.ingestionEventDao().getById(ingestionId)?.itemName)
    }

    @Test
    fun failed_compound_edit_sync_rolls_back_canonical_and_linked_labels() = runBlocking {
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

        val result = UpdateReusableCompoundUseCase(
            repository = compoundRepository,
            syncReferences = { compoundId, name ->
                database.ingestionEventDao().updateReusableEntityName(ReusableEntityKind.COMPOUND.storageValue, compoundId, name)
                throw IllegalStateException("boom")
            },
            countReferences = { compoundId ->
                database.ingestionEventDao().countReusableEntityReferences(ReusableEntityKind.COMPOUND.storageValue, compoundId)
            },
            runAtomically = { block ->
                database.withTransaction { block() }
            }
        ).invoke(UpdateReusableCompoundCommand(reusableCompound.id, "Breakfast mix plus", "Draft"))

        assertTrue(result is AppResult.Error)
        assertEquals("Breakfast mix", database.reusableCompoundDao().findById(reusableCompound.id)?.name)
        assertEquals("Breakfast mix", database.ingestionEventDao().getById(ingestionId)?.itemName)
    }

    @Test
    fun event_catalog_edit_count_mismatch_rolls_back_canonical_and_linked_labels() = runBlocking {
        val now = 1_700_000_123_000L
        val eventCatalogRepository = RoomReusableEventCatalogRepository(database.reusableEventCatalogDao()) { now }
        val exposureCatalog = eventCatalogRepository.ensureCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, "Pollen")
        val exposureRepository = RoomExposureRepository(database.exposureEventDao(), CurrentTimeProvider { now }, eventCatalogRepository)

        val exposureId = exposureRepository.createExposure(
            CreateExposureEventCommand(
                exposureType = exposureCatalog.name,
                occurredAtEpochMs = now - 1_000L,
                notes = null,
                exposureTypeCatalogId = exposureCatalog.id
            )
        ).requireSuccess().id

        val result = UpdateReusableEventCatalogUseCase(
            repository = eventCatalogRepository,
            syncReferences = { kind, catalogId, name ->
                when (kind) {
                    ReusableEventCatalogKind.EXPOSURE_TYPE -> database.exposureEventDao().updateCatalogName(catalogId, name)
                    ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> database.outbreakEventDao().updateOutbreakDescriptorCatalogName(catalogId, name)
                    ReusableEventCatalogKind.BODY_LOCATION -> database.outbreakEventDao().updateBodyLocationCatalogName(catalogId, name)
                }
                0
            },
            countReferences = { kind, catalogId ->
                when (kind) {
                    ReusableEventCatalogKind.EXPOSURE_TYPE -> database.exposureEventDao().countCatalogReferences(catalogId)
                    ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> database.outbreakEventDao().countOutbreakDescriptorCatalogReferences(catalogId)
                    ReusableEventCatalogKind.BODY_LOCATION -> database.outbreakEventDao().countBodyLocationCatalogReferences(catalogId)
                }
            },
            runAtomically = { block ->
                database.withTransaction { block() }
            }
        ).invoke(ReusableEventCatalogKind.EXPOSURE_TYPE, exposureCatalog.id, "Dust")

        assertTrue(result is AppResult.Error)
        assertEquals("Pollen", database.reusableEventCatalogDao().findById(ReusableEventCatalogKind.EXPOSURE_TYPE.storageValue, exposureCatalog.id)?.name)
        assertEquals("Pollen", database.exposureEventDao().getById(exposureId)?.exposureType)
    }

    @Test
    fun outbreak_descriptor_edit_count_mismatch_rolls_back_canonical_and_linked_labels() = runBlocking {
        val now = 1_700_000_123_000L
        val eventCatalogRepository = RoomReusableEventCatalogRepository(database.reusableEventCatalogDao()) { now }
        val descriptorCatalog = eventCatalogRepository.ensureCatalogEntry(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, "Skin flare")
        val bodyLocationCatalog = eventCatalogRepository.ensureCatalogEntry(ReusableEventCatalogKind.BODY_LOCATION, "Arm")
        val outbreakRepository = RoomOutbreakRepository(database.outbreakEventDao(), CurrentTimeProvider { now }, eventCatalogRepository)

        val outbreakId = outbreakRepository.createOutbreak(
            CreateOutbreakEventCommand(
                outbreakDescriptor = descriptorCatalog.name,
                bodyLocation = bodyLocationCatalog.name,
                occurredAtEpochMs = now - 1_000L,
                notes = null,
                outbreakDescriptorCatalogId = descriptorCatalog.id,
                bodyLocationCatalogId = bodyLocationCatalog.id
            )
        ).requireSuccess().id

        val result = UpdateReusableEventCatalogUseCase(
            repository = eventCatalogRepository,
            syncReferences = { kind, catalogId, name ->
                when (kind) {
                    ReusableEventCatalogKind.EXPOSURE_TYPE -> database.exposureEventDao().updateCatalogName(catalogId, name)
                    ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> database.outbreakEventDao().updateOutbreakDescriptorCatalogName(catalogId, name)
                    ReusableEventCatalogKind.BODY_LOCATION -> database.outbreakEventDao().updateBodyLocationCatalogName(catalogId, name)
                }
                0
            },
            countReferences = { kind, catalogId ->
                when (kind) {
                    ReusableEventCatalogKind.EXPOSURE_TYPE -> database.exposureEventDao().countCatalogReferences(catalogId)
                    ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> database.outbreakEventDao().countOutbreakDescriptorCatalogReferences(catalogId)
                    ReusableEventCatalogKind.BODY_LOCATION -> database.outbreakEventDao().countBodyLocationCatalogReferences(catalogId)
                }
            },
            runAtomically = { block ->
                database.withTransaction { block() }
            }
        ).invoke(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, descriptorCatalog.id, "Skin flare plus")

        assertTrue(result is AppResult.Error)
        assertEquals(
            "Skin flare",
            database.reusableEventCatalogDao().findById(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR.storageValue, descriptorCatalog.id)?.name
        )
        assertEquals("Skin flare", database.outbreakEventDao().getById(outbreakId)?.outbreakDescriptor)
    }

    @Test
    fun body_location_edit_count_mismatch_rolls_back_canonical_and_linked_labels() = runBlocking {
        val now = 1_700_000_123_000L
        val eventCatalogRepository = RoomReusableEventCatalogRepository(database.reusableEventCatalogDao()) { now }
        val descriptorCatalog = eventCatalogRepository.ensureCatalogEntry(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, "Skin flare")
        val bodyLocationCatalog = eventCatalogRepository.ensureCatalogEntry(ReusableEventCatalogKind.BODY_LOCATION, "Left arm")
        val outbreakRepository = RoomOutbreakRepository(database.outbreakEventDao(), CurrentTimeProvider { now }, eventCatalogRepository)

        val outbreakId = outbreakRepository.createOutbreak(
            CreateOutbreakEventCommand(
                outbreakDescriptor = descriptorCatalog.name,
                bodyLocation = bodyLocationCatalog.name,
                occurredAtEpochMs = now - 1_000L,
                notes = null,
                outbreakDescriptorCatalogId = descriptorCatalog.id,
                bodyLocationCatalogId = bodyLocationCatalog.id
            )
        ).requireSuccess().id

        val result = UpdateReusableEventCatalogUseCase(
            repository = eventCatalogRepository,
            syncReferences = { kind, catalogId, name ->
                when (kind) {
                    ReusableEventCatalogKind.EXPOSURE_TYPE -> database.exposureEventDao().updateCatalogName(catalogId, name)
                    ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> database.outbreakEventDao().updateOutbreakDescriptorCatalogName(catalogId, name)
                    ReusableEventCatalogKind.BODY_LOCATION -> database.outbreakEventDao().updateBodyLocationCatalogName(catalogId, name)
                }
                0
            },
            countReferences = { kind, catalogId ->
                when (kind) {
                    ReusableEventCatalogKind.EXPOSURE_TYPE -> database.exposureEventDao().countCatalogReferences(catalogId)
                    ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> database.outbreakEventDao().countOutbreakDescriptorCatalogReferences(catalogId)
                    ReusableEventCatalogKind.BODY_LOCATION -> database.outbreakEventDao().countBodyLocationCatalogReferences(catalogId)
                }
            },
            runAtomically = { block ->
                database.withTransaction { block() }
            }
        ).invoke(ReusableEventCatalogKind.BODY_LOCATION, bodyLocationCatalog.id, "Right arm")

        assertTrue(result is AppResult.Error)
        assertEquals(
            "Left arm",
            database.reusableEventCatalogDao().findById(ReusableEventCatalogKind.BODY_LOCATION.storageValue, bodyLocationCatalog.id)?.name
        )
        assertEquals("Left arm", database.outbreakEventDao().getById(outbreakId)?.bodyLocation)
    }

    @Test
    fun retiring_reusable_ingredient_detaches_ingestion_links_and_keeps_records_interpretable_after_reopen() = runBlocking {
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
        ingestionRepository.createIngestion(
            CreateIngestionEventCommand(
                itemName = reusableIngredient.name,
                quantityLevel = 2,
                occurredAtEpochMs = now - 1_000L,
                notes = null,
                reusableEntityKind = ReusableEntityKind.INGREDIENT,
                reusableEntityId = reusableIngredient.id
            )
        ).requireSuccess()

        val result = RetireReusableIngredientUseCase(
            repository = ingredientRepository,
            getImpact = GetReusableIngredientRetirementImpactUseCase(
                repository = ingredientRepository,
                countIngestionReferences = { ingredientId ->
                    database.ingestionEventDao().countReusableEntityReferences(ReusableEntityKind.INGREDIENT.storageValue, ingredientId)
                },
                countCompoundReferences = { ingredientId ->
                    database.reusableCompoundDao().countIngredientComponentReferences(ingredientId)
                }
            ),
            detachIngestionReferences = { ingredientId ->
                database.ingestionEventDao().clearReusableEntityReferences(ReusableEntityKind.INGREDIENT.storageValue, ingredientId)
            },
            runAtomically = { block ->
                database.withTransaction { block() }
            }
        ).invoke(reusableIngredient.id)

        assertTrue(result is AppResult.Success)
        assertEquals(null, database.ingestionEventDao().getById(1L)?.reusableEntityId)

        database.close()
        database = buildDatabase()

        val reopenedRecordsRepository = RoomRecordsRepository(
            ingestionEventDao = database.ingestionEventDao(),
            exposureEventDao = database.exposureEventDao(),
            outbreakEventDao = database.outbreakEventDao()
        )
        val records = reopenedRecordsRepository.observeRecords().first()

        assertEquals(listOf("Oat milk"), records.map { it.title })
        assertTrue(records.single().detailLabel.contains("Light snack"))
    }

    @Test
    fun retiring_nested_reusable_compound_is_blocked() = runBlocking {
        val now = 1_700_000_123_000L
        val compoundRepository = RoomReusableCompoundRepository(database.reusableCompoundDao()) { now }
        val parent = compoundRepository.createCompound(CreateReusableCompoundCommand(name = "Parent", notes = null)).requireSuccess()
        val child = compoundRepository.createCompound(CreateReusableCompoundCommand(name = "Child", notes = null)).requireSuccess()
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO reusable_compound_components (compound_id, position, component_kind, ingredient_id, child_compound_id, proportion_basis_points) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(parent.id, 0, "compound", null, child.id, 10_000)
        )

        val result = RetireReusableCompoundUseCase(
            repository = compoundRepository,
            getImpact = GetReusableCompoundRetirementImpactUseCase(
                repository = compoundRepository,
                countIngestionReferences = { compoundId ->
                    database.ingestionEventDao().countReusableEntityReferences(ReusableEntityKind.COMPOUND.storageValue, compoundId)
                },
                countChildCompoundReferences = { compoundId ->
                    database.reusableCompoundDao().countChildCompoundReferences(compoundId)
                }
            ),
            detachIngestionReferences = { compoundId ->
                database.ingestionEventDao().clearReusableEntityReferences(ReusableEntityKind.COMPOUND.storageValue, compoundId)
            },
            runAtomically = { block ->
                database.withTransaction { block() }
            }
        ).invoke(child.id)

        assertTrue(result is AppResult.Error)
        assertTrue(database.reusableCompoundDao().existsById(child.id))
    }

    @Test
    fun retiring_referenced_event_catalog_is_blocked_and_preserves_detail_views() = runBlocking {
        val now = 1_700_000_123_000L
        val eventCatalogRepository = RoomReusableEventCatalogRepository(database.reusableEventCatalogDao()) { now }
        val exposureCatalog = eventCatalogRepository.ensureCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, "Pollen")
        val exposureRepository = RoomExposureRepository(database.exposureEventDao(), CurrentTimeProvider { now }, eventCatalogRepository)
        exposureRepository.createExposure(
            CreateExposureEventCommand(
                exposureType = exposureCatalog.name,
                occurredAtEpochMs = now - 1_000L,
                notes = null,
                exposureTypeCatalogId = exposureCatalog.id
            )
        ).requireSuccess()

        val result = RetireReusableEventCatalogUseCase(
            repository = eventCatalogRepository,
            getImpact = GetReusableEventCatalogRetirementImpactUseCase(
                repository = eventCatalogRepository,
                countReferences = { kind, catalogId ->
                    when (kind) {
                        ReusableEventCatalogKind.EXPOSURE_TYPE -> database.exposureEventDao().countCatalogReferences(catalogId)
                        ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> database.outbreakEventDao().countOutbreakDescriptorCatalogReferences(catalogId)
                        ReusableEventCatalogKind.BODY_LOCATION -> database.outbreakEventDao().countBodyLocationCatalogReferences(catalogId)
                    }
                }
            ),
            runAtomically = { block ->
                database.withTransaction { block() }
            }
        ).invoke(ReusableEventCatalogKind.EXPOSURE_TYPE, exposureCatalog.id)

        assertTrue(result is AppResult.Error)
        assertEquals("Pollen", database.exposureEventDao().getById(1L)?.exposureType)
        assertTrue(database.reusableEventCatalogDao().existsById(ReusableEventCatalogKind.EXPOSURE_TYPE.storageValue, exposureCatalog.id))
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
