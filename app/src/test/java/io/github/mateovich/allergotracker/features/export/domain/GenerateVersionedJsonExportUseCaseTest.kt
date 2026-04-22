package io.github.mateovich.allergotracker.features.export.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.core.export.schema.EXPORT_SCHEMA_VERSION
import io.github.mateovich.allergotracker.core.export.schema.VersionedExportEnvelopeDto
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateVersionedJsonExportUseCaseTest {

    @Test
    fun valid_export_generates_json_with_schema_version_and_utc_timestamp() = runBlocking {
        val exportDirectory = Files.createTempDirectory("json-export").toFile()
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(sampleSnapshot()),
            writer = io.github.mateovich.allergotracker.features.export.data.LocalJsonExportArtifactWriter(exportDirectory),
            serializer = io.github.mateovich.allergotracker.features.export.data.KotlinxVersionedJsonExportSerializer(),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = useCase(ExportConfiguration(ExportFormat.JSON, ExportScope.AllRecords))

        require(result is AppResult.Success)
        val json = Json.parseToJsonElement(File(result.value.artifact.absolutePath).readText()).jsonObject
        assertEquals(EXPORT_SCHEMA_VERSION, json.getValue("schema_version").jsonPrimitive.content)
        assertEquals("2023-11-14T22:13:20Z", json.getValue("exported_at").jsonPrimitive.content)
        assertEquals(2, json.getValue("data").jsonObject.getValue("ingestions").jsonArray.size)
        assertTrue(json.getValue("data").jsonObject.containsKey("reusable_ingredients"))
        assertTrue(json.getValue("data").jsonObject.containsKey("normalized_base_ingredients"))
        assertTrue(json.getValue("data").jsonObject.getValue("ingestions").jsonArray.first().jsonObject.containsKey("item_name"))
        assertTrue(json.getValue("data").jsonObject.getValue("ingestions").jsonArray.first().jsonObject.containsKey("reusable_entity_ref"))
        assertFalse(json.containsKey("schemaVersion"))
        assertTrue(File(result.value.artifact.absolutePath).exists())
    }

    @Test
    fun relationship_export_uses_nested_reference_objects_with_snake_case_fields() = runBlocking {
        val exportDirectory = Files.createTempDirectory("json-export-relationships").toFile()
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(sampleSnapshot()),
            writer = io.github.mateovich.allergotracker.features.export.data.LocalJsonExportArtifactWriter(exportDirectory),
            serializer = io.github.mateovich.allergotracker.features.export.data.KotlinxVersionedJsonExportSerializer(),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = useCase(ExportConfiguration(ExportFormat.JSON, ExportScope.AllRecords))

        require(result is AppResult.Success)
        val data = Json.parseToJsonElement(File(result.value.artifact.absolutePath).readText()).jsonObject.getValue("data").jsonObject
        val ingestion = data.getValue("ingestions").jsonArray.first().jsonObject
        val compound = data.getValue("reusable_compounds").jsonArray.first().jsonObject
        val component = compound.getValue("components").jsonArray.first().jsonObject
        val exposure = data.getValue("exposures").jsonArray.first().jsonObject
        val outbreak = data.getValue("outbreaks").jsonArray.first().jsonObject

        assertEquals("compound", ingestion.getValue("reusable_entity_ref").jsonObject.getValue("kind").jsonPrimitive.content)
        assertEquals("compound", component.getValue("component_ref").jsonObject.getValue("kind").jsonPrimitive.content)
        assertTrue(exposure.containsKey("exposure_type_catalog_ref"))
        assertTrue(outbreak.containsKey("outbreak_descriptor_catalog_ref"))
        assertFalse(ingestion.containsKey("reusable_entity_id"))
        assertFalse(component.containsKey("child_compound_id"))
        assertFalse(exposure.containsKey("exposure_type_catalog_id"))
    }

    @Test
    fun equivalent_exports_preserve_same_serialized_contract_shape() = runBlocking {
        val firstDirectory = Files.createTempDirectory("json-export-first").toFile()
        val secondDirectory = Files.createTempDirectory("json-export-second").toFile()
        val firstUseCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(sampleSnapshot()),
            writer = io.github.mateovich.allergotracker.features.export.data.LocalJsonExportArtifactWriter(firstDirectory),
            serializer = io.github.mateovich.allergotracker.features.export.data.KotlinxVersionedJsonExportSerializer(),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )
        val secondUseCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(sampleSnapshot()),
            writer = io.github.mateovich.allergotracker.features.export.data.LocalJsonExportArtifactWriter(secondDirectory),
            serializer = io.github.mateovich.allergotracker.features.export.data.KotlinxVersionedJsonExportSerializer(),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val firstResult = firstUseCase(ExportConfiguration(ExportFormat.JSON, ExportScope.AllRecords))
        val secondResult = secondUseCase(ExportConfiguration(ExportFormat.JSON, ExportScope.AllRecords))

        require(firstResult is AppResult.Success)
        require(secondResult is AppResult.Success)
        assertEquals(
            File(firstResult.value.artifact.absolutePath).readText(),
            File(secondResult.value.artifact.absolutePath).readText()
        )
    }

    @Test
    fun normalized_export_flattens_nested_compounds_and_aggregates_duplicate_base_ingredients() = runBlocking {
        val exportDirectory = Files.createTempDirectory("json-export-normalized").toFile()
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(sampleSnapshot()),
            writer = io.github.mateovich.allergotracker.features.export.data.LocalJsonExportArtifactWriter(exportDirectory),
            serializer = io.github.mateovich.allergotracker.features.export.data.KotlinxVersionedJsonExportSerializer(),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = useCase(ExportConfiguration(ExportFormat.JSON, ExportScope.AllRecords))

        require(result is AppResult.Success)
        val normalizedEntries = Json.parseToJsonElement(File(result.value.artifact.absolutePath).readText())
            .jsonObject
            .getValue("data")
            .jsonObject
            .getValue("normalized_base_ingredients")
            .jsonArray
        assertEquals(2, normalizedEntries.size)

        val compoundEntry = normalizedEntries.first().jsonObject
        assertEquals("final_base_ingredients", compoundEntry.getValue("derivation_kind").jsonPrimitive.content)
        val sourceEvent = compoundEntry.getValue("source_event").jsonObject
        assertEquals("ingestion", sourceEvent.getValue("kind").jsonPrimitive.content)
        assertEquals("compound", sourceEvent.getValue("reusable_entity_ref").jsonObject.getValue("kind").jsonPrimitive.content)
        val baseIngredients = compoundEntry.getValue("base_ingredients").jsonArray
        assertEquals(listOf("Tea", "Chamomile"), baseIngredients.map { it.jsonObject.getValue("ingredient_name").jsonPrimitive.content })
        assertEquals(listOf(7000, 3000), baseIngredients.map { it.jsonObject.getValue("contribution_basis_points").jsonPrimitive.int })
        assertEquals(listOf(14000, 6000), baseIngredients.map { it.jsonObject.getValue("derived_quantity_level_basis_points").jsonPrimitive.int })

        val directIngredientEntry = normalizedEntries[1].jsonObject
        assertEquals(1, directIngredientEntry.getValue("base_ingredients").jsonArray.size)
        assertEquals(
            30000,
            directIngredientEntry.getValue("base_ingredients").jsonArray.first().jsonObject
                .getValue("derived_quantity_level_basis_points").jsonPrimitive.int
        )
    }

    @Test
    fun malformed_normalized_compound_data_returns_actionable_export_error() = runBlocking {
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(
                sampleSnapshot().copy(
                    reusableCompounds = listOf(
                        ExportReusableCompoundRecord(
                            id = 60L,
                            name = "Broken blend",
                            notes = null,
                            compositionStatus = "complete",
                            components = listOf(
                                ExportReusableCompoundComponentRecord(
                                    id = 61L,
                                    position = 1,
                                    componentRef = ExportReusableEntityReference(kind = "ingredient", id = 50L),
                                    proportionBasisPoints = null
                                )
                            ),
                            createdAtEpochMs = 1_699_998_600_000L,
                            updatedAtEpochMs = 1_699_998_700_000L
                        )
                    )
                )
            ),
            writer = object : ExportArtifactWriter {
                override suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact> = error("should not be called")
            },
            serializer = object : VersionedJsonExportSerializer {
                override fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String> = error("should not be called")
            },
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = useCase(ExportConfiguration(ExportFormat.JSON, ExportScope.AllRecords))

        require(result is AppResult.Error)
        assertTrue(result.error.message.contains("Export blocked"))
        assertTrue(result.error.message.contains("missing proportion_basis_points"))
    }

    @Test
    fun same_timestamp_exports_create_distinct_artifacts_without_overwrite() = runBlocking {
        val exportDirectory = Files.createTempDirectory("json-export-distinct").toFile()
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(sampleSnapshot()),
            writer = io.github.mateovich.allergotracker.features.export.data.LocalJsonExportArtifactWriter(exportDirectory),
            serializer = io.github.mateovich.allergotracker.features.export.data.KotlinxVersionedJsonExportSerializer(),
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val firstResult = useCase(ExportConfiguration(ExportFormat.JSON, ExportScope.AllRecords))
        val secondResult = useCase(ExportConfiguration(ExportFormat.JSON, ExportScope.AllRecords))

        require(firstResult is AppResult.Success)
        require(secondResult is AppResult.Success)
        assertNotEquals(firstResult.value.artifact.absolutePath, secondResult.value.artifact.absolutePath)
        assertTrue(File(firstResult.value.artifact.absolutePath).exists())
        assertTrue(File(secondResult.value.artifact.absolutePath).exists())
    }

    @Test
    fun serializer_failure_returns_export_error() = runBlocking {
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(sampleSnapshot()),
            writer = object : ExportArtifactWriter {
                override suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact> = error("should not be called")
            },
            serializer = object : VersionedJsonExportSerializer {
                override fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String> {
                    return AppResult.Error(DomainError.ExportError("Could not serialize the export to JSON."))
                }
            },
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = useCase(ExportConfiguration(ExportFormat.JSON, ExportScope.AllRecords))

        require(result is AppResult.Error)
        assertEquals("Could not serialize the export to JSON.", result.error.message)
    }

    @Test
    fun review_returns_pass_when_export_scope_has_no_preflight_issues() = runBlocking {
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(sampleSnapshot()),
            writer = object : ExportArtifactWriter {
                override suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact> = error("unused")
            },
            serializer = object : VersionedJsonExportSerializer {
                override fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String> = error("unused")
            },
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = useCase.review(ExportConfiguration(ExportFormat.JSON, ExportScope.AllRecords))

        assertTrue(result.result.issues.isEmpty())
        assertTrue(result.preparedExport != null)
    }

    @Test
    fun review_returns_warning_for_partial_scope_before_generation() = runBlocking {
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(sampleSnapshot()),
            writer = object : ExportArtifactWriter {
                override suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact> = error("unused")
            },
            serializer = object : VersionedJsonExportSerializer {
                override fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String> = error("unused")
            },
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = useCase.review(
            ExportConfiguration(
                ExportFormat.JSON,
                ExportScope.EventTypeRecords(io.github.mateovich.allergotracker.features.records.domain.RecordEventType.EXPOSURE)
            )
        )

        assertEquals(1, result.result.warningIssues.size)
        assertTrue(result.result.requiresExplicitWarningAcceptance())
        assertTrue(result.result.warningIssues.single().message.contains("only exposure records"))
        assertTrue(result.preparedExport != null)
    }

    @Test
    fun review_returns_blocking_issue_for_integrity_failure() = runBlocking {
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(
                sampleSnapshot().copy(
                    reusableCompounds = listOf(
                        ExportReusableCompoundRecord(
                            id = 60L,
                            name = "Broken blend",
                            notes = null,
                            compositionStatus = "complete",
                            components = listOf(
                                ExportReusableCompoundComponentRecord(
                                    id = 61L,
                                    position = 1,
                                    componentRef = ExportReusableEntityReference(kind = "ingredient", id = 50L),
                                    proportionBasisPoints = null
                                )
                            ),
                            createdAtEpochMs = 1_699_998_600_000L,
                            updatedAtEpochMs = 1_699_998_700_000L
                        )
                    )
                )
            ),
            writer = object : ExportArtifactWriter {
                override suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact> = error("unused")
            },
            serializer = object : VersionedJsonExportSerializer {
                override fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String> = error("unused")
            },
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = useCase.review(ExportConfiguration(ExportFormat.JSON, ExportScope.AllRecords))

        assertEquals(1, result.result.blockingIssues.size)
        assertTrue(result.result.hasBlockingIssues())
        assertTrue(result.result.blockingIssues.single().message.contains("missing proportion_basis_points"))
        assertTrue(result.preparedExport == null)
    }

    @Test
    fun warning_scope_does_not_write_artifact_until_warnings_are_explicitly_accepted() = runBlocking {
        var writeInvocationCount = 0
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(sampleSnapshot()),
            writer = object : ExportArtifactWriter {
                override suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact> {
                    writeInvocationCount += 1
                    return AppResult.Success(ExportArtifact(fileName = fileName, absolutePath = "/tmp/$fileName"))
                }
            },
            serializer = object : VersionedJsonExportSerializer {
                override fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String> = AppResult.Success("{}")
            },
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val blockedResult = useCase(
            ExportConfiguration(
                ExportFormat.JSON,
                ExportScope.EventTypeRecords(io.github.mateovich.allergotracker.features.records.domain.RecordEventType.EXPOSURE)
            )
        )

        require(blockedResult is AppResult.Error)
        assertEquals(0, writeInvocationCount)

        val review = useCase.review(
            ExportConfiguration(
                ExportFormat.JSON,
                ExportScope.EventTypeRecords(io.github.mateovich.allergotracker.features.records.domain.RecordEventType.EXPOSURE)
            )
        )

        val acceptedResult = useCase(
            configuration = ExportConfiguration(
                ExportFormat.JSON,
                ExportScope.EventTypeRecords(io.github.mateovich.allergotracker.features.records.domain.RecordEventType.EXPOSURE)
            ),
            warningDecision = ExportWarningDecision.AcceptWarnings,
            preparedExport = review.preparedExport
        )

        require(acceptedResult is AppResult.Success)
        assertEquals(1, writeInvocationCount)
    }

    @Test
    fun accepted_warning_export_succeeds_when_reviewed_snapshot_still_matches_current_data() = runBlocking {
        var snapshotInvocationCount = 0
        var writeInvocationCount = 0
        val firstSnapshot = sampleSnapshot()
        val exportDirectory = Files.createTempDirectory("json-export-reviewed-warning").toFile()
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = object : ExportSnapshotRepository {
                override suspend fun getSnapshot(scope: ExportScope): AppResult<ExportSnapshot> {
                    snapshotInvocationCount += 1
                    return AppResult.Success(firstSnapshot)
                }
            },
            writer = object : ExportArtifactWriter {
                override suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact> {
                    writeInvocationCount += 1
                    val outputFile = File(exportDirectory, fileName)
                    outputFile.writeText(content)
                    return AppResult.Success(ExportArtifact(fileName = fileName, absolutePath = outputFile.absolutePath))
                }
            },
            serializer = object : VersionedJsonExportSerializer {
                override fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String> {
                    return AppResult.Success(Json.encodeToString(VersionedExportEnvelopeDto.serializer(), envelope))
                }
            },
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val review = useCase.review(
            ExportConfiguration(
                ExportFormat.JSON,
                ExportScope.EventTypeRecords(io.github.mateovich.allergotracker.features.records.domain.RecordEventType.EXPOSURE)
            )
        )
        val acceptedResult = useCase(
            configuration = ExportConfiguration(
                ExportFormat.JSON,
                ExportScope.EventTypeRecords(io.github.mateovich.allergotracker.features.records.domain.RecordEventType.EXPOSURE)
            ),
            warningDecision = ExportWarningDecision.AcceptWarnings,
            preparedExport = review.preparedExport
        )

        require(acceptedResult is AppResult.Success)
        assertEquals(2, snapshotInvocationCount)
        assertEquals(1, writeInvocationCount)
        val json = Json.parseToJsonElement(File(acceptedResult.value.artifact.absolutePath).readText()).jsonObject
        assertEquals(1, json.getValue("data").jsonObject.getValue("exposures").jsonArray.size)
    }

    @Test
    fun accepted_warning_export_requires_matching_reviewed_prepared_export() = runBlocking {
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = FakeExportSnapshotRepository(sampleSnapshot()),
            writer = object : ExportArtifactWriter {
                override suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact> = error("unused")
            },
            serializer = object : VersionedJsonExportSerializer {
                override fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String> = error("unused")
            },
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val result = useCase(
            configuration = ExportConfiguration(
                ExportFormat.JSON,
                ExportScope.EventTypeRecords(io.github.mateovich.allergotracker.features.records.domain.RecordEventType.EXPOSURE)
            ),
            warningDecision = ExportWarningDecision.AcceptWarnings,
            preparedExport = null
        )

        require(result is AppResult.Error)
        assertTrue(result.error is DomainError.ValidationError)
        assertEquals("Review export warnings before continuing.", result.error.message)
    }

    @Test
    fun accepted_warning_export_rejects_stale_reviewed_snapshot() = runBlocking {
        var snapshotInvocationCount = 0
        val firstSnapshot = sampleSnapshot()
        val secondSnapshot = sampleSnapshot().copy(exposures = emptyList())
        val useCase = GenerateVersionedJsonExportUseCase(
            snapshotRepository = object : ExportSnapshotRepository {
                override suspend fun getSnapshot(scope: ExportScope): AppResult<ExportSnapshot> {
                    snapshotInvocationCount += 1
                    return AppResult.Success(if (snapshotInvocationCount == 1) firstSnapshot else secondSnapshot)
                }
            },
            writer = object : ExportArtifactWriter {
                override suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact> = error("unused")
            },
            serializer = object : VersionedJsonExportSerializer {
                override fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String> = error("unused")
            },
            currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L }
        )

        val review = useCase.review(
            ExportConfiguration(
                ExportFormat.JSON,
                ExportScope.EventTypeRecords(io.github.mateovich.allergotracker.features.records.domain.RecordEventType.EXPOSURE)
            )
        )
        val result = useCase(
            configuration = ExportConfiguration(
                ExportFormat.JSON,
                ExportScope.EventTypeRecords(io.github.mateovich.allergotracker.features.records.domain.RecordEventType.EXPOSURE)
            ),
            warningDecision = ExportWarningDecision.AcceptWarnings,
            preparedExport = review.preparedExport
        )

        require(result is AppResult.Error)
        assertTrue(result.error is DomainError.ValidationError)
        assertEquals("Export data changed. Review warnings before continuing.", result.error.message)
    }

    private fun sampleSnapshot() = ExportSnapshot(
        ingestions = listOf(
            ExportIngestionRecord(
                id = 10L,
                itemName = "Blend tea",
                quantityLevel = 2,
                occurredAtEpochMs = 1_699_999_000_000L,
                notes = "Night",
                reusableEntityRef = ExportReusableEntityReference(kind = "compound", id = 60L),
                createdAtEpochMs = 1_699_999_100_000L
            ),
            ExportIngestionRecord(
                id = 13L,
                itemName = "Tea",
                quantityLevel = 3,
                occurredAtEpochMs = 1_699_999_600_000L,
                notes = null,
                reusableEntityRef = ExportReusableEntityReference(kind = "ingredient", id = 50L),
                createdAtEpochMs = 1_699_999_700_000L
            )
        ),
        exposures = listOf(
            ExportExposureRecord(
                id = 11L,
                exposureType = "Sun",
                occurredAtEpochMs = 1_699_999_200_000L,
                notes = null,
                intensityLevel = 3,
                exposureTypeCatalogRef = ExportEventCatalogReference(kind = "exposure_type", id = 70L),
                createdAtEpochMs = 1_699_999_300_000L
            )
        ),
        outbreaks = listOf(
            ExportOutbreakRecord(
                id = 12L,
                outbreakDescriptor = "Rash",
                bodyLocation = "Arm",
                occurredAtEpochMs = 1_699_999_400_000L,
                notes = null,
                intensityLevel = 2,
                outbreakDescriptorCatalogRef = ExportEventCatalogReference(kind = "outbreak_descriptor", id = 71L),
                bodyLocationCatalogRef = ExportEventCatalogReference(kind = "body_location", id = 72L),
                createdAtEpochMs = 1_699_999_500_000L
            )
        ),
        reusableIngredients = listOf(
            ExportReusableIngredientRecord(50L, "Tea", null, 1_699_998_000_000L, 1_699_998_500_000L),
            ExportReusableIngredientRecord(51L, "Chamomile", null, 1_699_998_010_000L, 1_699_998_510_000L)
        ),
        reusableCompounds = listOf(
            ExportReusableCompoundRecord(
                id = 60L,
                name = "Blend",
                notes = null,
                compositionStatus = "complete",
                components = listOf(
                    ExportReusableCompoundComponentRecord(
                        id = 61L,
                        position = 1,
                        componentRef = ExportReusableEntityReference(kind = "compound", id = 62L),
                        proportionBasisPoints = 6000
                    ),
                    ExportReusableCompoundComponentRecord(
                        id = 63L,
                        position = 2,
                        componentRef = ExportReusableEntityReference(kind = "ingredient", id = 50L),
                        proportionBasisPoints = 4000
                    )
                ),
                createdAtEpochMs = 1_699_998_600_000L,
                updatedAtEpochMs = 1_699_998_700_000L
            ),
            ExportReusableCompoundRecord(
                id = 62L,
                name = "Base",
                notes = null,
                compositionStatus = "complete",
                components = listOf(
                    ExportReusableCompoundComponentRecord(
                        id = 64L,
                        position = 1,
                        componentRef = ExportReusableEntityReference(kind = "ingredient", id = 50L),
                        proportionBasisPoints = 5000
                    ),
                    ExportReusableCompoundComponentRecord(
                        id = 65L,
                        position = 2,
                        componentRef = ExportReusableEntityReference(kind = "ingredient", id = 51L),
                        proportionBasisPoints = 5000
                    )
                ),
                createdAtEpochMs = 1_699_998_800_000L,
                updatedAtEpochMs = 1_699_998_900_000L
            )
        ),
        reusableEventCatalogEntries = listOf(
            ExportReusableEventCatalogRecord(70L, "exposure_type", "Sun", 1, 1_699_998_100_000L, 1_699_998_200_000L, 1_699_998_300_000L),
            ExportReusableEventCatalogRecord(71L, "outbreak_descriptor", "Rash", 1, 1_699_998_400_000L, 1_699_998_500_000L, 1_699_998_600_000L),
            ExportReusableEventCatalogRecord(72L, "body_location", "Arm", 1, 1_699_998_700_000L, 1_699_998_800_000L, 1_699_998_900_000L)
        )
    )
}

private class FakeExportSnapshotRepository(
    private val snapshot: ExportSnapshot
) : ExportSnapshotRepository {
    override suspend fun getSnapshot(scope: ExportScope): AppResult<ExportSnapshot> = AppResult.Success(snapshot)
}
