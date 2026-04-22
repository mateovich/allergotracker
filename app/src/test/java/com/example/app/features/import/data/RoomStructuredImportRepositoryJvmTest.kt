package com.example.app.features.import.data

import com.example.app.core.common.result.AppResult
import com.example.app.core.export.schema.IngestionExportDto
import com.example.app.core.export.schema.ReusableEntityReferenceDto
import com.example.app.core.export.schema.ReusableEventCatalogExportDto
import com.example.app.core.export.schema.VersionedExportDataDto
import com.example.app.core.export.schema.VersionedExportEnvelopeDto
import com.example.app.features.compounds.data.ReusableCompoundChildEdge
import com.example.app.features.compounds.data.ReusableCompoundDao
import com.example.app.features.compounds.data.ReusableCompoundComponentEntity
import com.example.app.features.compounds.data.ReusableCompoundEntity
import com.example.app.features.compounds.data.ReusableCompoundWithComponents
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
import com.example.app.features.compounds.domain.CreateReusableCompoundUseCase
import com.example.app.features.compounds.domain.ReusableCompound
import com.example.app.features.compounds.domain.ReusableCompoundRepository
import com.example.app.features.compounds.domain.UpdateReusableCompoundCommand
import com.example.app.features.compounds.domain.UpdateReusableCompoundUseCase
import com.example.app.features.eventcatalog.data.ReusableEventCatalogDao
import com.example.app.features.eventcatalog.data.ReusableEventCatalogEntity
import com.example.app.features.exposure.domain.CreateExposureEventCommand
import com.example.app.features.exposure.domain.CreateExposureEventUseCase
import com.example.app.features.exposure.domain.ExposureEvent
import com.example.app.features.exposure.domain.ExposureRepository
import com.example.app.features.import.domain.ImportDocumentContent
import com.example.app.features.ingredients.data.ReusableIngredientDao
import com.example.app.features.ingredients.data.ReusableIngredientEntity
import com.example.app.features.ingredients.domain.CreateReusableIngredientCommand
import com.example.app.features.ingredients.domain.CreateReusableIngredientUseCase
import com.example.app.features.ingredients.domain.ReusableIngredient
import com.example.app.features.ingredients.domain.ReusableIngredientRepository
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CreateIngestionEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.IngestionEvent
import com.example.app.features.ingestion.domain.IngestionRepository
import com.example.app.features.outbreak.domain.CreateOutbreakEventCommand
import com.example.app.features.outbreak.domain.CreateOutbreakEventUseCase
import com.example.app.features.outbreak.domain.OutbreakEvent
import com.example.app.features.outbreak.domain.OutbreakRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStructuredImportRepositoryJvmTest {
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
            repository = FakeExposureRepository(),
            currentTimeProvider = CurrentTimeProvider { FIXED_NOW }
        ),
        createOutbreakEventUseCase = CreateOutbreakEventUseCase(
            repository = FakeOutbreakRepository(),
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
        override suspend fun updateIngestion(command: com.example.app.features.ingestion.domain.UpdateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")
        override suspend fun deleteIngestion(id: Long): AppResult<Unit> = error("unused")
    }

    private class FakeExposureRepository : ExposureRepository {
        override suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")
        override suspend fun getExposure(id: Long): AppResult<ExposureEvent> = error("unused")
        override suspend fun updateExposure(command: com.example.app.features.exposure.domain.UpdateExposureEventCommand): AppResult<ExposureEvent> = error("unused")
        override suspend fun deleteExposure(id: Long): AppResult<Unit> = error("unused")
    }

    private class FakeOutbreakRepository : OutbreakRepository {
        override suspend fun createOutbreak(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")
        override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> = error("unused")
        override suspend fun updateOutbreak(command: com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")
        override suspend fun deleteOutbreak(id: Long): AppResult<Unit> = error("unused")
    }

    private companion object {
        const val FIXED_NOW = 1_700_000_000_000L
    }
}
