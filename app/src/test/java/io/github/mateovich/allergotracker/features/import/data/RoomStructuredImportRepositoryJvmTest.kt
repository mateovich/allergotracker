package io.github.mateovich.allergotracker.features.import.data

import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.core.export.schema.ExposureExportDto
import io.github.mateovich.allergotracker.core.export.schema.IngestionExportDto
import io.github.mateovich.allergotracker.core.export.schema.OutbreakExportDto
import io.github.mateovich.allergotracker.core.export.schema.ReusableEntityReferenceDto
import io.github.mateovich.allergotracker.core.export.schema.ReusableEventCatalogExportDto
import io.github.mateovich.allergotracker.core.export.schema.VersionedExportDataDto
import io.github.mateovich.allergotracker.core.export.schema.VersionedExportEnvelopeDto
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundChildEdge
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundDao
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundComponentEntity
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundEntity
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundWithComponents
import io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundCommand
import io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompound
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompoundRepository
import io.github.mateovich.allergotracker.features.compounds.domain.UpdateReusableCompoundCommand
import io.github.mateovich.allergotracker.features.compounds.domain.UpdateReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogDao
import io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogEntity
import io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand
import io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.ExposureEvent
import io.github.mateovich.allergotracker.features.exposure.domain.ExposureRepository
import io.github.mateovich.allergotracker.features.import.domain.ImportDocumentContent
import io.github.mateovich.allergotracker.features.ingredients.data.ReusableIngredientDao
import io.github.mateovich.allergotracker.features.ingredients.data.ReusableIngredientEntity
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRepository
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionEvent
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionRepository
import io.github.mateovich.allergotracker.features.outbreak.domain.CreateOutbreakEventCommand
import io.github.mateovich.allergotracker.features.outbreak.domain.CreateOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.OutbreakEvent
import io.github.mateovich.allergotracker.features.outbreak.domain.OutbreakRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStructuredImportRepositoryJvmTest {
    private val exposureCommands = mutableListOf<CreateExposureEventCommand>()
    private val outbreakCommands = mutableListOf<CreateOutbreakEventCommand>()
    private val repository = RoomStructuredImportRepository(
        reusableIngredientDao = FakeReusableIngredientDao(),
        reusableCompoundDao = FakeReusableCompoundDao(),
        reusableEventCatalogDao = FakeReusableEventCatalogDao(),
        createReusableIngredientUseCase = CreateReusableIngredientUseCase(FakeReusableIngredientRepository()),
        createReusableCompoundUseCase = CreateReusableCompoundUseCase(FakeReusableCompoundRepository()),
        updateReusableCompoundUseCase = UpdateReusableCompoundUseCase(FakeReusableCompoundRepository()),
        createIngestionEventUseCase = CreateIngestionEventUseCase(
            repository = FakeIngestionRepository(),
            currentTimeProvider = CurrentTimeProvider { FIXED_NOW }
        ),
        createExposureEventUseCase = CreateExposureEventUseCase(
            repository = FakeExposureRepository(exposureCommands),
            currentTimeProvider = CurrentTimeProvider { FIXED_NOW }
        ),
        createOutbreakEventUseCase = CreateOutbreakEventUseCase(
            repository = FakeOutbreakRepository(outbreakCommands),
            currentTimeProvider = CurrentTimeProvider { FIXED_NOW }
        ),
        nowEpochMillis = { FIXED_NOW },
        runAtomically = { block -> block() }
    )

    @Test
    fun review_rejects_unsupported_catalog_kind_without_echoing_payload_details() = runBlocking {
        val review = repository.review(document(), unsupportedCatalogKindEnvelope())

        assertTrue(review is AppResult.Error)
        review as AppResult.Error
        assertEquals("This import file uses an unsupported saved-value type.", review.error.message)
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

    @Test
    fun apply_preserves_exposure_place_and_accepts_missing_place_from_older_exports() = runBlocking {
        val reviewResult = repository.review(document(), exposurePlaceEnvelope())
        require(reviewResult is AppResult.Success)

        val result = repository.apply(reviewResult.value)

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("Garage", null), exposureCommands.map { it.place })
    }

    @Test
    fun apply_preserves_duration_and_accepts_missing_duration_from_older_exports() = runBlocking {
        val reviewResult = repository.review(document(), durationEnvelope())
        require(reviewResult is AppResult.Success)

        val result = repository.apply(reviewResult.value)

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(45, null), exposureCommands.map { it.durationMinutes })
        assertEquals(listOf(90, null), outbreakCommands.map { it.durationMinutes })
    }

    @Test
    fun review_rejects_invalid_duration_values() = runBlocking {
        val review = repository.review(document(), invalidDurationEnvelope())

        assertTrue(review is AppResult.Error)
        review as AppResult.Error
        assertEquals("Enter a positive duration in minutes.", review.error.message)
    }

    private fun document(): ImportDocumentContent {
        return ImportDocumentContent(documentUri = "file:///tmp/import.json", fileName = "import.json", rawJson = "{}")
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
                        name = "Secret catalog",
                        usageCount = 0,
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

    private fun exposurePlaceEnvelope(): VersionedExportEnvelopeDto {
        return VersionedExportEnvelopeDto(
            schemaVersion = "2.0.0",
            exportedAt = "2023-11-14T22:13:20Z",
            data = VersionedExportDataDto(
                exposures = listOf(
                    ExposureExportDto(
                        id = 20L,
                        exposureType = "Dust",
                        occurredAt = "2023-11-14T22:13:20Z",
                        place = "Garage",
                        notes = null,
                        intensityLevel = 2,
                        createdAt = "2023-11-14T22:13:20Z"
                    ),
                    ExposureExportDto(
                        id = 21L,
                        exposureType = "Pollen",
                        occurredAt = "2023-11-14T22:13:20Z",
                        notes = null,
                        intensityLevel = 3,
                        createdAt = "2023-11-14T22:13:20Z"
                    )
                )
            )
        )
    }

    private fun durationEnvelope(): VersionedExportEnvelopeDto {
        return VersionedExportEnvelopeDto(
            schemaVersion = "2.0.0",
            exportedAt = "2023-11-14T22:13:20Z",
            data = VersionedExportDataDto(
                exposures = listOf(
                    ExposureExportDto(
                        id = 30L,
                        exposureType = "Dust",
                        occurredAt = "2023-11-14T22:13:20Z",
                        notes = null,
                        durationMinutes = 45,
                        intensityLevel = 2,
                        createdAt = "2023-11-14T22:13:20Z"
                    ),
                    ExposureExportDto(
                        id = 31L,
                        exposureType = "Pollen",
                        occurredAt = "2023-11-14T22:13:20Z",
                        notes = null,
                        intensityLevel = 3,
                        createdAt = "2023-11-14T22:13:20Z"
                    )
                ),
                outbreaks = listOf(
                    OutbreakExportDto(
                        id = 40L,
                        outbreakDescriptor = "Skin flare",
                        bodyLocation = "Left arm",
                        occurredAt = "2023-11-14T22:13:20Z",
                        notes = null,
                        durationMinutes = 90,
                        intensityLevel = 4,
                        createdAt = "2023-11-14T22:13:20Z"
                    ),
                    OutbreakExportDto(
                        id = 41L,
                        outbreakDescriptor = "Skin flare",
                        bodyLocation = "Right arm",
                        occurredAt = "2023-11-14T22:13:20Z",
                        notes = null,
                        intensityLevel = 3,
                        createdAt = "2023-11-14T22:13:20Z"
                    )
                )
            )
        )
    }

    private fun invalidDurationEnvelope(): VersionedExportEnvelopeDto {
        return VersionedExportEnvelopeDto(
            schemaVersion = "2.0.0",
            exportedAt = "2023-11-14T22:13:20Z",
            data = VersionedExportDataDto(
                exposures = listOf(
                    ExposureExportDto(
                        id = 50L,
                        exposureType = "Dust",
                        occurredAt = "2023-11-14T22:13:20Z",
                        notes = null,
                        durationMinutes = 0,
                        intensityLevel = 2,
                        createdAt = "2023-11-14T22:13:20Z"
                    )
                )
            )
        )
    }

    private class FakeReusableIngredientDao : ReusableIngredientDao {
        override suspend fun insert(entity: ReusableIngredientEntity): Long = error("unused")
        override suspend fun findByNormalizedName(normalizedName: String): ReusableIngredientEntity? = null
        override suspend fun findById(id: Long): ReusableIngredientEntity? = null
        override suspend fun update(entity: ReusableIngredientEntity): Int = error("unused")
        override suspend fun deleteById(id: Long): Int = error("unused")
        override suspend fun existsById(id: Long): Boolean = false
        override suspend fun touchById(id: Long, updatedAtEpochMs: Long): Int = error("unused")
        override fun observeAll(): Flow<List<ReusableIngredientEntity>> = flowOf(emptyList())
    }

    private class FakeReusableCompoundDao : ReusableCompoundDao {
        override suspend fun insertCompound(entity: ReusableCompoundEntity): Long = error("unused")
        override suspend fun insertComponents(entities: List<ReusableCompoundComponentEntity>) = error("unused")
        override suspend fun findByNormalizedName(normalizedName: String): ReusableCompoundEntity? = null
        override suspend fun findById(id: Long): ReusableCompoundEntity? = null
        override suspend fun findCompoundWithComponentsById(id: Long): ReusableCompoundWithComponents? = null
        override suspend fun findCompoundsWithComponentsByIds(ids: List<Long>): List<ReusableCompoundWithComponents> = emptyList()
        override suspend fun updateCompound(entity: ReusableCompoundEntity): Int = error("unused")
        override suspend fun deleteComponentsByCompoundId(compoundId: Long): Int = error("unused")
        override suspend fun deleteCompoundById(id: Long): Int = error("unused")
        override suspend fun existsById(id: Long): Boolean = false
        override suspend fun countIngredientComponentReferences(ingredientId: Long): Int = 0
        override suspend fun findCompoundIdsReferencingIngredient(ingredientId: Long): List<Long> = emptyList()
        override suspend fun countIngredientMergeConflictingCompounds(sourceIngredientId: Long, destinationIngredientId: Long): Int = 0
        override suspend fun replaceIngredientComponentReferences(sourceIngredientId: Long, destinationIngredientId: Long): Int = 0
        override suspend fun countChildCompoundReferences(compoundId: Long): Int = 0
        override suspend fun countCompoundMergeConflictingParents(sourceCompoundId: Long, destinationCompoundId: Long): Int = 0
        override suspend fun findParentCompoundIdsReferencingChild(sourceCompoundId: Long): List<Long> = emptyList()
        override suspend fun replaceChildCompoundReferences(sourceCompoundId: Long, destinationCompoundId: Long): Int = 0
        override suspend fun findAllChildCompoundEdges(): List<ReusableCompoundChildEdge> = emptyList()
        override suspend fun touchById(id: Long, updatedAtEpochMs: Long): Int = 0
        override fun observeAll(): Flow<List<ReusableCompoundWithComponents>> = flowOf(emptyList())
    }

    private class FakeReusableEventCatalogDao : ReusableEventCatalogDao {
        override suspend fun insert(entity: ReusableEventCatalogEntity): Long = error("unused")
        override suspend fun update(entity: ReusableEventCatalogEntity): Int = error("unused")
        override suspend fun deleteById(kind: String, id: Long): Int = error("unused")
        override fun observeByKind(kind: String): Flow<List<ReusableEventCatalogEntity>> = flowOf(emptyList())
        override suspend fun findByNormalizedName(kind: String, normalizedName: String): ReusableEventCatalogEntity? = null
        override suspend fun findById(kind: String, id: Long): ReusableEventCatalogEntity? = null
        override suspend fun existsById(kind: String, id: Long): Boolean = false
        override suspend fun updateExposureReferences(catalogId: Long, name: String): Int = 0
        override suspend fun updateOutbreakDescriptorReferences(catalogId: Long, name: String): Int = 0
        override suspend fun updateBodyLocationReferences(catalogId: Long, name: String): Int = 0
    }

    private class FakeReusableIngredientRepository : ReusableIngredientRepository {
        override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> = error("unused")
        override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(emptyList())
    }

    private class FakeReusableCompoundRepository : ReusableCompoundRepository {
        override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> = error("unused")
        override fun observeCompounds(): Flow<List<ReusableCompound>> = flowOf(emptyList())
        override suspend fun updateCompound(command: UpdateReusableCompoundCommand): AppResult<ReusableCompound> = error("unused")
    }

    private class FakeIngestionRepository : IngestionRepository {
        override suspend fun createIngestion(command: CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")
        override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> = error("unused")
        override suspend fun updateIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")
        override suspend fun deleteIngestion(id: Long): AppResult<Unit> = error("unused")
    }

    private class FakeExposureRepository(
        private val createdCommands: MutableList<CreateExposureEventCommand>
    ) : ExposureRepository {
        override suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent> {
            createdCommands += command
            return AppResult.Success(
                ExposureEvent(
                    100L + createdCommands.size,
                    command.exposureType,
                    command.occurredAtEpochMs,
                    command.notes,
                    place = command.place,
                    durationMinutes = command.durationMinutes
                )
            )
        }
        override suspend fun getExposure(id: Long): AppResult<ExposureEvent> = error("unused")
        override suspend fun updateExposure(command: io.github.mateovich.allergotracker.features.exposure.domain.UpdateExposureEventCommand): AppResult<ExposureEvent> = error("unused")
        override suspend fun deleteExposure(id: Long): AppResult<Unit> = error("unused")
    }

    private class FakeOutbreakRepository(
        private val createdCommands: MutableList<CreateOutbreakEventCommand>
    ) : OutbreakRepository {
        override suspend fun createOutbreak(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent> {
            createdCommands += command
            return AppResult.Success(
                OutbreakEvent(
                    200L + createdCommands.size,
                    command.outbreakDescriptor,
                    command.bodyLocation,
                    command.occurredAtEpochMs,
                    command.notes,
                    durationMinutes = command.durationMinutes
                )
            )
        }
        override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> = error("unused")
        override suspend fun updateOutbreak(command: io.github.mateovich.allergotracker.features.outbreak.domain.UpdateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")
        override suspend fun deleteOutbreak(id: Long): AppResult<Unit> = error("unused")
    }

    private companion object {
        const val FIXED_NOW = 1_700_000_000_000L
    }
}
