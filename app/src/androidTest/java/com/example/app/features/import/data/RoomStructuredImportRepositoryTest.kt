package com.example.app.features.import.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.app.core.common.result.AppResult
import com.example.app.core.database.AppDatabase
import com.example.app.core.export.schema.IngestionExportDto
import com.example.app.core.export.schema.ReusableCompoundComponentExportDto
import com.example.app.core.export.schema.ReusableCompoundExportDto
import com.example.app.core.export.schema.ReusableEventCatalogExportDto
import com.example.app.core.export.schema.ReusableEntityReferenceDto
import com.example.app.core.export.schema.ReusableIngredientExportDto
import com.example.app.core.export.schema.VersionedExportDataDto
import com.example.app.core.export.schema.VersionedExportEnvelopeDto
import com.example.app.features.compounds.data.RoomReusableCompoundRepository
import com.example.app.features.compounds.domain.CreateReusableCompoundUseCase
import com.example.app.features.compounds.domain.UpdateReusableCompoundUseCase
import com.example.app.features.eventcatalog.data.RoomReusableEventCatalogRepository
import com.example.app.features.ingredients.data.RoomReusableIngredientRepository
import com.example.app.features.ingredients.domain.CreateReusableIngredientCommand
import com.example.app.features.ingredients.domain.CreateReusableIngredientUseCase
import com.example.app.features.ingestion.data.RoomIngestionRepository
import com.example.app.features.ingestion.domain.CreateIngestionEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.exposure.data.RoomExposureRepository
import com.example.app.features.exposure.domain.CreateExposureEventUseCase
import com.example.app.features.outbreak.data.RoomOutbreakRepository
import com.example.app.features.outbreak.domain.CreateOutbreakEventUseCase
import com.example.app.features.import.domain.ImportDocumentContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStructuredImportRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var databaseName: String
    private lateinit var repository: RoomStructuredImportRepository
    private val currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "structured-import-test-${System.nanoTime()}"
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()

        val reusableIngredientRepository = RoomReusableIngredientRepository(
            ingredientDao = database.reusableIngredientDao(),
            nowEpochMillis = currentTimeProvider::nowEpochMillis
        )
        val reusableEventCatalogRepository = RoomReusableEventCatalogRepository(
            eventCatalogDao = database.reusableEventCatalogDao(),
            nowEpochMillis = currentTimeProvider::nowEpochMillis
        )
        val reusableCompoundRepository = RoomReusableCompoundRepository(
            compoundDao = database.reusableCompoundDao(),
            ingredientExists = database.reusableIngredientDao()::existsById,
            compoundExists = database.reusableCompoundDao()::existsById,
            nowEpochMillis = currentTimeProvider::nowEpochMillis
        )
        val ingestionRepository = RoomIngestionRepository(
            ingestionEventDao = database.ingestionEventDao(),
            currentTimeProvider = currentTimeProvider,
            reusableEntityExists = { kind, id ->
                when (kind) {
                    ReusableEntityKind.INGREDIENT -> database.reusableIngredientDao().existsById(id)
                    ReusableEntityKind.COMPOUND -> database.reusableCompoundDao().existsById(id)
                }
            }
        )
        val exposureRepository = RoomExposureRepository(database.exposureEventDao(), currentTimeProvider, reusableEventCatalogRepository)
        val outbreakRepository = RoomOutbreakRepository(database.outbreakEventDao(), currentTimeProvider, reusableEventCatalogRepository)

        repository = RoomStructuredImportRepository(
            reusableIngredientDao = database.reusableIngredientDao(),
            reusableCompoundDao = database.reusableCompoundDao(),
            reusableEventCatalogDao = database.reusableEventCatalogDao(),
            createReusableIngredientUseCase = CreateReusableIngredientUseCase(reusableIngredientRepository),
            createReusableCompoundUseCase = CreateReusableCompoundUseCase(reusableCompoundRepository),
            updateReusableCompoundUseCase = UpdateReusableCompoundUseCase(
                repository = reusableCompoundRepository,
                ingredientExists = database.reusableIngredientDao()::existsById,
                compoundExists = database.reusableCompoundDao()::existsById,
                runAtomically = { block -> database.withTransaction { block() } }
            ),
            createIngestionEventUseCase = CreateIngestionEventUseCase(
                repository = ingestionRepository,
                currentTimeProvider = currentTimeProvider,
                reusableEntityExists = { kind, id ->
                    when (kind) {
                        ReusableEntityKind.INGREDIENT -> database.reusableIngredientDao().existsById(id)
                        ReusableEntityKind.COMPOUND -> database.reusableCompoundDao().existsById(id)
                    }
                }
            ),
            createExposureEventUseCase = CreateExposureEventUseCase(
                repository = exposureRepository,
                currentTimeProvider = currentTimeProvider,
                exposureCatalogExists = { catalogId ->
                    reusableEventCatalogRepository.existsCatalogEntry(com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind.EXPOSURE_TYPE, catalogId)
                },
                getExposureCatalogEntry = { catalogId ->
                    reusableEventCatalogRepository.getCatalogEntry(com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind.EXPOSURE_TYPE, catalogId)
                }
            ),
            createOutbreakEventUseCase = CreateOutbreakEventUseCase(
                repository = outbreakRepository,
                currentTimeProvider = currentTimeProvider,
                outbreakDescriptorCatalogExists = { catalogId ->
                    reusableEventCatalogRepository.existsCatalogEntry(com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, catalogId)
                },
                bodyLocationCatalogExists = { catalogId ->
                    reusableEventCatalogRepository.existsCatalogEntry(com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind.BODY_LOCATION, catalogId)
                },
                getOutbreakDescriptorCatalogEntry = { catalogId ->
                    reusableEventCatalogRepository.getCatalogEntry(com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, catalogId)
                },
                getBodyLocationCatalogEntry = { catalogId ->
                    reusableEventCatalogRepository.getCatalogEntry(com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind.BODY_LOCATION, catalogId)
                }
            ),
            nowEpochMillis = currentTimeProvider::nowEpochMillis,
            runAtomically = { block -> database.withTransaction { block() } }
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun apply_maps_imported_reusable_reference_to_existing_local_id() = runBlocking {
        val localIngredient = CreateReusableIngredientUseCase(
            RoomReusableIngredientRepository(database.reusableIngredientDao(), currentTimeProvider::nowEpochMillis)
        )(CreateReusableIngredientCommand(name = "Oat milk", notes = null)) as AppResult.Success

        val review = repository.review(document(), reusableIngredientEnvelope()) as AppResult.Success
        val apply = repository.apply(review.value)

        assertTrue(apply is AppResult.Success)
        val ingestions = database.ingestionEventDao().observeIngestionEvents().first()
        assertEquals(1, ingestions.size)
        assertEquals(localIngredient.value.id, ingestions.single().reusableEntityId)
    }

    @Test
    fun apply_rolls_back_all_changes_when_compound_validation_fails() = runBlocking {
        val review = repository.review(document(), invalidCompoundEnvelope()) as AppResult.Success

        val apply = repository.apply(review.value)

        assertTrue(apply is AppResult.Error)
        assertEquals(emptyList<Any>(), database.ingestionEventDao().observeIngestionEvents().first())
        assertEquals(emptyList<Any>(), database.reusableIngredientDao().observeAll().first())
        assertEquals(emptyList<Any>(), database.reusableCompoundDao().observeAll().first())
    }

    @Test
    fun apply_recalculates_catalog_usage_locally_instead_of_reusing_exported_metadata() = runBlocking {
        val review = repository.review(document(), exposureCatalogEnvelope()) as AppResult.Success

        val apply = repository.apply(review.value)

        assertTrue(apply is AppResult.Success)
        val catalogEntries = database.reusableEventCatalogDao().observeByKind("exposure_type").first()
        assertEquals(1, catalogEntries.size)
        assertEquals(1, catalogEntries.single().usageCount)
    }

    @Test
    fun review_rejects_duplicate_reusable_ids_before_mapping() = runBlocking {
        val review = repository.review(document(), duplicateIngredientIdEnvelope())

        assertTrue(review is AppResult.Error)
        review as AppResult.Error
        assertEquals(
            "Import file contains duplicate reusable ingredient IDs. Regenerate or fix the source file before retrying.",
            review.error.message
        )
    }

    @Test
    fun review_rejects_unsupported_catalog_kind_with_actionable_error() = runBlocking {
        val review = repository.review(document(), unsupportedCatalogKindEnvelope())

        assertTrue(review is AppResult.Error)
        review as AppResult.Error
        assertEquals("This import file uses an unsupported saved-value type.", review.error.message)
    }

    @Test
    fun review_rejects_invalid_timestamp_before_apply() = runBlocking {
        val review = repository.review(document(), invalidTimestampEnvelope())

        assertTrue(review is AppResult.Error)
        review as AppResult.Error
        assertEquals(
            "Import file has an invalid ingestion occurred_at timestamp. Use ISO-8601 UTC values from the export contract.",
            review.error.message
        )
    }

    @Test
    fun review_rejects_missing_import_reference_without_echoing_sensitive_payload_details() = runBlocking {
        val review = repository.review(document(), missingIngredientReferenceEnvelope())

        assertTrue(review is AppResult.Error)
        review as AppResult.Error
        assertEquals(
            "An ingestion record references an ingredient that is not present in the import file.",
            review.error.message
        )
    }

    private fun document(): ImportDocumentContent {
        return ImportDocumentContent(documentUri = "file:///tmp/import.json", fileName = "import.json", rawJson = "{}")
    }

    private fun reusableIngredientEnvelope(): VersionedExportEnvelopeDto {
        return VersionedExportEnvelopeDto(
            schemaVersion = "2.0.0",
            exportedAt = "2023-11-14T22:13:20Z",
            data = VersionedExportDataDto(
                ingestions = listOf(
                    IngestionExportDto(
                        id = 10L,
                        itemName = "Oat milk",
                        quantityLevel = 2,
                        occurredAt = "2023-11-14T22:13:20Z",
                        notes = null,
                        reusableEntityRef = ReusableEntityReferenceDto(kind = "ingredient", id = 99L),
                        createdAt = "2023-11-14T22:13:20Z"
                    )
                ),
                reusableIngredients = listOf(
                    ReusableIngredientExportDto(
                        id = 99L,
                        name = "Oat milk",
                        notes = null,
                        createdAt = "2023-11-14T22:13:20Z",
                        updatedAt = "2023-11-14T22:13:20Z"
                    )
                )
            )
        )
    }

    private fun invalidCompoundEnvelope(): VersionedExportEnvelopeDto {
        return VersionedExportEnvelopeDto(
            schemaVersion = "2.0.0",
            exportedAt = "2023-11-14T22:13:20Z",
            data = VersionedExportDataDto(
                reusableIngredients = listOf(
                    ReusableIngredientExportDto(
                        id = 1L,
                        name = "Water",
                        notes = null,
                        createdAt = "2023-11-14T22:13:20Z",
                        updatedAt = "2023-11-14T22:13:20Z"
                    )
                ),
                reusableCompounds = listOf(
                    ReusableCompoundExportDto(
                        id = 20L,
                        name = "Broken mix",
                        notes = null,
                        compositionStatus = "complete",
                        components = listOf(
                            ReusableCompoundComponentExportDto(
                                id = 1L,
                                position = 0,
                                componentRef = ReusableEntityReferenceDto(kind = "ingredient", id = 1L),
                                proportionBasisPoints = 5_000
                            ),
                            ReusableCompoundComponentExportDto(
                                id = 2L,
                                position = 1,
                                componentRef = ReusableEntityReferenceDto(kind = "ingredient", id = 1L),
                                proportionBasisPoints = 5_000
                            )
                        ),
                        createdAt = "2023-11-14T22:13:20Z",
                        updatedAt = "2023-11-14T22:13:20Z"
                    )
                )
            )
        )
    }

    private fun duplicateIngredientIdEnvelope(): VersionedExportEnvelopeDto {
        return VersionedExportEnvelopeDto(
            schemaVersion = "2.0.0",
            exportedAt = "2023-11-14T22:13:20Z",
            data = VersionedExportDataDto(
                reusableIngredients = listOf(
                    ReusableIngredientExportDto(
                        id = 7L,
                        name = "Oat milk",
                        notes = null,
                        createdAt = "2023-11-14T22:13:20Z",
                        updatedAt = "2023-11-14T22:13:20Z"
                    ),
                    ReusableIngredientExportDto(
                        id = 7L,
                        name = "Almond milk",
                        notes = null,
                        createdAt = "2023-11-14T22:13:20Z",
                        updatedAt = "2023-11-14T22:13:20Z"
                    )
                )
            )
        )
    }

    private fun unsupportedCatalogKindEnvelope(): VersionedExportEnvelopeDto {
        return VersionedExportEnvelopeDto(
            schemaVersion = "2.0.0",
            exportedAt = "2023-11-14T22:13:20Z",
            data = VersionedExportDataDto(
                reusableEventCatalog = listOf(
                    ReusableEventCatalogExportDto(
                        id = 1L,
                        kind = "unsupported_kind",
                        name = "Mystery",
                        usageCount = 0,
                        createdAt = "2023-11-14T22:13:20Z",
                        updatedAt = "2023-11-14T22:13:20Z",
                        lastUsedAt = "2023-11-14T22:13:20Z"
                    )
                )
            )
        )
    }

    private fun invalidTimestampEnvelope(): VersionedExportEnvelopeDto {
        return VersionedExportEnvelopeDto(
            schemaVersion = "2.0.0",
            exportedAt = "2023-11-14T22:13:20Z",
            data = VersionedExportDataDto(
                ingestions = listOf(
                    IngestionExportDto(
                        id = 10L,
                        itemName = "Oat milk",
                        quantityLevel = 2,
                        occurredAt = "not-a-timestamp",
                        notes = null,
                        reusableEntityRef = null,
                        createdAt = "2023-11-14T22:13:20Z"
                    )
                )
            )
        )
    }

    private fun exposureCatalogEnvelope(): VersionedExportEnvelopeDto {
        return VersionedExportEnvelopeDto(
            schemaVersion = "2.0.0",
            exportedAt = "2023-11-14T22:13:20Z",
            data = VersionedExportDataDto(
                exposures = listOf(
                    com.example.app.core.export.schema.ExposureExportDto(
                        id = 11L,
                        exposureType = "Pollen",
                        occurredAt = "2023-11-14T22:13:20Z",
                        notes = null,
                        intensityLevel = 3,
                        exposureTypeCatalogRef = com.example.app.core.export.schema.EventCatalogReferenceDto(
                            kind = "exposure_type",
                            id = 5L
                        ),
                        createdAt = "2023-11-14T22:13:20Z"
                    )
                ),
                reusableEventCatalog = listOf(
                    ReusableEventCatalogExportDto(
                        id = 5L,
                        kind = "exposure_type",
                        name = "Pollen",
                        usageCount = 42,
                        createdAt = "2023-11-14T22:13:20Z",
                        updatedAt = "2023-11-14T22:13:20Z",
                        lastUsedAt = "2023-11-14T22:13:20Z"
                    )
                )
            )
        )
    }

    private fun missingIngredientReferenceEnvelope(): VersionedExportEnvelopeDto {
        return VersionedExportEnvelopeDto(
            schemaVersion = "2.0.0",
            exportedAt = "2023-11-14T22:13:20Z",
            data = VersionedExportDataDto(
                ingestions = listOf(
                    IngestionExportDto(
                        id = 10L,
                        itemName = "Private smoothie",
                        quantityLevel = 2,
                        occurredAt = "2023-11-14T22:13:20Z",
                        notes = "contains personal context",
                        reusableEntityRef = ReusableEntityReferenceDto(kind = "ingredient", id = 999L),
                        createdAt = "2023-11-14T22:13:20Z"
                    )
                )
            )
        )
    }
}
