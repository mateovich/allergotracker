package com.example.app.features.export.ui

import android.os.Build
import com.example.app.MainDispatcherRule
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.core.distribution.AppDistribution
import com.example.app.core.export.schema.EXPORT_SCHEMA_VERSION
import com.example.app.features.export.domain.DeliverStagedExportUseCase
import com.example.app.features.export.domain.ExportConfiguration
import com.example.app.features.export.domain.ExportDeliveryRepository
import com.example.app.features.export.domain.ExportDeliveryResult
import com.example.app.features.export.domain.ExportDestination
import com.example.app.features.export.domain.ExportFormat
import com.example.app.features.export.domain.ExportScope
import com.example.app.features.export.domain.ExportArtifact
import com.example.app.features.export.domain.ExportPreflightIssue
import com.example.app.features.export.domain.ExportPreflightReview
import com.example.app.features.export.domain.ExportPreflightResult
import com.example.app.features.export.domain.ExportWarningDecision
import com.example.app.features.export.domain.GenerateVersionedJsonExportUseCase
import com.example.app.features.export.domain.GeneratedJsonExport
import com.example.app.features.export.domain.PreparedExport
import com.example.app.features.export.domain.ValidateExportConfigurationUseCase
import com.example.app.features.import.domain.ApplyStructuredImportUseCase
import com.example.app.features.import.domain.ImportDocumentContent
import com.example.app.features.import.domain.ImportDocumentReader
import com.example.app.features.import.domain.PreparedStructuredImport
import com.example.app.features.import.domain.ReviewStructuredImportUseCase
import com.example.app.features.import.domain.StructuredImportApplyResult
import com.example.app.features.import.domain.StructuredImportRepository
import com.example.app.features.import.domain.StructuredImportReview
import com.example.app.features.import.domain.StructuredImportSummary
import com.example.app.features.import.domain.VersionedJsonImportParser
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordsFilter
import com.example.app.features.records.domain.RecordsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val recordCountFlow = MutableStateFlow(3)
    private val repository = object : RecordsRepository {
        override fun observeRecordCount() = recordCountFlow

        override fun observeRecords(filters: Flow<RecordsFilter>) = kotlinx.coroutines.flow.flowOf(emptyList<RecordSummary>())
    }

    private val observeRecordCountUseCase = ObserveRecordCountUseCase(repository)
    private val validateExportConfigurationUseCase = ValidateExportConfigurationUseCase()
    private var pendingGenerateResult: CompletableDeferred<AppResult<GeneratedJsonExport>>? = null
    private var generateInvocationCount: Int = 0
    private var deliverInvocationCount: Int = 0
    private var lastDeliveredExport: GeneratedJsonExport? = null
    private var lastDeliveredDestination: ExportDestination? = null
    private var nextReviewResult: ExportPreflightReview? = null
    private var reviewedPreparedExport: PreparedExport? = null
    private var generatedPreparedExport: PreparedExport? = null
    private var importApplyInvocationCount: Int = 0
    private var pendingImportReviewGate: CompletableDeferred<Unit>? = null
    private var nextGenerateResult: AppResult<GeneratedJsonExport> = AppResult.Success(
        GeneratedJsonExport(
            configuration = ExportConfiguration(
                format = ExportFormat.JSON,
                scope = ExportScope.AllRecords
            ),
            schemaVersion = EXPORT_SCHEMA_VERSION,
            exportedAt = "2023-11-14T22:13:20Z",
            artifact = ExportArtifact(
                fileName = "app-export-v${EXPORT_SCHEMA_VERSION.replace('.', '_')}-20231114T221320Z.json",
                absolutePath = "/tmp/app-export.json"
            )
        )
    )
    private var nextDeliveryResult: AppResult<ExportDeliveryResult> = AppResult.Success(
        ExportDeliveryResult(
            destination = ExportDestination.DeviceDownloads,
            fileName = "app-export-v${EXPORT_SCHEMA_VERSION.replace('.', '_')}-20231114T221320Z.json",
            locationLabel = "Downloads/app",
            locationUri = "content://downloads/app-export.json"
        )
    )

    private fun createViewModel(
        deliveryResultProvider: suspend () -> AppResult<ExportDeliveryResult> = { nextDeliveryResult }
    ): ExportViewModel {
        val fakeGenerateUseCase = object : GenerateVersionedJsonExportUseCase(
            snapshotRepository = object : com.example.app.features.export.domain.ExportSnapshotRepository {
                override suspend fun getSnapshot(scope: ExportScope) = error("unused")
            },
            writer = object : com.example.app.features.export.domain.ExportArtifactWriter {
                override suspend fun writeJson(fileName: String, content: String) = error("unused")
            },
            serializer = object : com.example.app.features.export.domain.VersionedJsonExportSerializer {
                override fun serialize(envelope: com.example.app.core.export.schema.VersionedExportEnvelopeDto) = error("unused")
            },
            currentTimeProvider = com.example.app.features.ingestion.domain.CurrentTimeProvider { 0L }
        ) {
            override suspend fun review(configuration: ExportConfiguration): ExportPreflightReview {
                nextReviewResult?.let { queuedResult ->
                    nextReviewResult = null
                    reviewedPreparedExport = queuedResult.preparedExport
                    return queuedResult
                }
                val preparedExport = PreparedExport(
                    configuration = configuration,
                    snapshot = sampleSnapshot(),
                    normalizedBaseIngredients = emptyList()
                )
                reviewedPreparedExport = preparedExport
                return when (configuration.scope) {
                    ExportScope.AllRecords -> ExportPreflightReview(
                        result = ExportPreflightResult(),
                        preparedExport = preparedExport
                    )

                    is ExportScope.EventTypeRecords -> ExportPreflightReview(
                        result = ExportPreflightResult(
                            issues = listOf(
                                ExportPreflightIssue.Warning(
                                    "This export includes only ${configuration.scope.eventType.name.lowercase()} records. Continue only if you intentionally want a partial dataset."
                                )
                            )
                        ),
                        preparedExport = preparedExport
                    )
                }
            }

            override suspend fun invoke(
                configuration: ExportConfiguration,
                warningDecision: ExportWarningDecision,
                preparedExport: PreparedExport?
            ): AppResult<GeneratedJsonExport> {
                generateInvocationCount += 1
                generatedPreparedExport = preparedExport
                val result = pendingGenerateResult?.await() ?: nextGenerateResult
                return when (result) {
                    is AppResult.Success -> AppResult.Success(result.value.copy(configuration = configuration))
                    is AppResult.Error -> result
                }
            }
        }
        val fakeDeliveryUseCase = DeliverStagedExportUseCase(
            repository = object : ExportDeliveryRepository {
                override suspend fun deliver(
                    stagedExport: GeneratedJsonExport,
                    destination: ExportDestination
                ): AppResult<ExportDeliveryResult> {
                    deliverInvocationCount += 1
                    lastDeliveredExport = stagedExport
                    lastDeliveredDestination = destination
                    return deliveryResultProvider()
                }
            }
        )
        val structuredImportRepository = object : StructuredImportRepository {
            override suspend fun review(
                document: ImportDocumentContent,
                envelope: com.example.app.core.export.schema.VersionedExportEnvelopeDto
            ): AppResult<StructuredImportReview> {
                pendingImportReviewGate?.await()
                return AppResult.Success(
                    StructuredImportReview(
                        fileName = document.fileName,
                        schemaVersion = envelope.schemaVersion,
                        summary = StructuredImportSummary(
                            ingredientsToCreate = 1,
                            ingredientsToReuse = 0,
                            compoundsToCreate = 1,
                            compoundsToUpdate = 0,
                            eventCatalogEntriesToCreate = 0,
                            eventCatalogEntriesToReuse = 0,
                            ingestionsToCreate = 1,
                            exposuresToCreate = 0,
                            outbreaksToCreate = 0
                        ),
                        preparedImport = PreparedStructuredImport(document, envelope)
                    )
                )
            }

            override suspend fun apply(review: StructuredImportReview): AppResult<StructuredImportApplyResult> {
                importApplyInvocationCount += 1
                return AppResult.Success(StructuredImportApplyResult(review.fileName, review.summary))
            }
        }
        val reviewStructuredImportUseCase = ReviewStructuredImportUseCase(
            documentReader = object : ImportDocumentReader {
                override suspend fun readDocument(documentUri: String): AppResult<ImportDocumentContent> {
                    return AppResult.Success(
                        ImportDocumentContent(
                            documentUri = documentUri,
                            fileName = "import.json",
                            rawJson = """
                                {"schema_version":"2.0.0","exported_at":"2023-11-14T22:13:20Z","data":{"ingestions":[],"exposures":[],"outbreaks":[],"reusable_ingredients":[],"reusable_compounds":[],"reusable_event_catalog":[],"normalized_base_ingredients":[]}}
                            """.trimIndent()
                        )
                    )
                }
            },
            parser = object : VersionedJsonImportParser {
                override fun parse(rawJson: String): AppResult<com.example.app.core.export.schema.VersionedExportEnvelopeDto> {
                    return AppResult.Success(
                        com.example.app.core.export.schema.VersionedExportEnvelopeDto(
                            schemaVersion = "2.0.0",
                            exportedAt = "2023-11-14T22:13:20Z",
                            data = com.example.app.core.export.schema.VersionedExportDataDto()
                        )
                    )
                }
            },
            repository = structuredImportRepository
        )
        val applyStructuredImportUseCase = ApplyStructuredImportUseCase(structuredImportRepository)
        return ExportViewModel(
            appDistribution = AppDistribution(
                channel = "store-agnostic",
                supportsStoreServices = false,
                unavailableCapabilityMessage = null
            ),
            observeRecordCountUseCase = observeRecordCountUseCase,
            validateExportConfigurationUseCase = validateExportConfigurationUseCase,
            generateVersionedJsonExportUseCase = fakeGenerateUseCase,
            deliverStagedExportUseCase = fakeDeliveryUseCase,
            reviewStructuredImportUseCase = reviewStructuredImportUseCase,
            applyStructuredImportUseCase = applyStructuredImportUseCase
        )
    }

    private fun createViewModelWithPendingDelivery(
        pendingDelivery: CompletableDeferred<AppResult<ExportDeliveryResult>>
    ): ExportViewModel {
        return createViewModel(deliveryResultProvider = { pendingDelivery.await() })
    }

    @Test
    fun initial_state_does_not_emit_delivery_effect() {
        val viewModel = createViewModel()

        assertNull(viewModel.state.value.statusMessage)
        assertEquals(
            "This build keeps capture, review, export, and import available without Play or store services.",
            viewModel.state.value.distributionNotice
        )
        assertEquals(
            "Choose an export format and scope to review what will be included.",
            viewModel.state.value.summaryText
        )
    }

    @Test
    fun selecting_format_and_scope_updates_summary_deterministically() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.EventTypeRecords(RecordEventType.EXPOSURE)))

        assertEquals(
            "JSON export will include only exposure records saved on this device.",
            viewModel.state.value.summaryText
        )
    }

    @Test
    fun selecting_configuration_does_not_emit_delivery_effect_before_final_confirmation() = runBlocking {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))

        val emittedEffect = withTimeoutOrNull(50) { viewModel.effects.first() }

        assertNull(emittedEffect)
        assertNull(viewModel.state.value.statusMessage)
    }

    @Test
    fun confirm_requires_format_selection() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals("Choose an export format.", viewModel.state.value.formatValidationMessage)
        assertEquals("Choose a format and scope before continuing.", viewModel.state.value.generalValidationMessage)
        assertNull(viewModel.state.value.statusMessage)
    }

    @Test
    fun confirm_requires_scope_selection() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals("Choose which records to include.", viewModel.state.value.scopeValidationMessage)
        assertEquals("Choose a format and scope before continuing.", viewModel.state.value.generalValidationMessage)
        assertNull(viewModel.state.value.statusMessage)
    }

    @Test
    fun export_start_stages_export_and_requires_destination_before_delivery() = runBlocking {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        val emittedEffect = withTimeoutOrNull(50) { viewModel.effects.first() }

        assertNull(emittedEffect)
        assertEquals(1, generateInvocationCount)
        assertTrue(viewModel.state.value.stagedExport != null)
        assertEquals(
            "Staged export ready. Choose a destination and review the final confirmation before saving.",
            viewModel.state.value.statusMessage
        )
        assertNull(viewModel.state.value.deliveryResult)
    }

    @Test
    fun partial_scope_requires_explicit_warning_continuation_before_staging() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.EventTypeRecords(RecordEventType.EXPOSURE)))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals(0, generateInvocationCount)
        assertTrue(viewModel.state.value.isReviewingWarnings)
        assertEquals(1, viewModel.state.value.warningMessages.size)

        viewModel.onIntent(ExportUiIntent.ContinueWarningReview)

        assertEquals(1, generateInvocationCount)
        assertEquals(reviewedPreparedExport, generatedPreparedExport)
        assertTrue(viewModel.state.value.stagedExport != null)
    }

    @Test
    fun canceling_warning_review_restores_editable_state_without_generation() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.EventTypeRecords(RecordEventType.EXPOSURE)))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)
        viewModel.onIntent(ExportUiIntent.CancelWarningReview)

        assertEquals(0, generateInvocationCount)
        assertEquals(ExportFormat.JSON, viewModel.state.value.selectedFormat)
        assertEquals(ExportScope.EventTypeRecords(RecordEventType.EXPOSURE), viewModel.state.value.selectedScope)
        assertTrue(viewModel.state.value.warningMessages.isEmpty())
        assertTrue(!viewModel.state.value.isReviewingWarnings)
    }

    @Test
    fun blocking_preflight_failure_surfaces_actionable_error_without_generation() {
        nextReviewResult = ExportPreflightReview(
            result = ExportPreflightResult(
                issues = listOf(
                    ExportPreflightIssue.Blocking(DomainError.IntegrityError("Export blocked: fix missing references before retrying."))
                )
            ),
            preparedExport = null
        )
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals(0, generateInvocationCount)
        assertEquals("Export blocked: fix missing references before retrying.", viewModel.state.value.generalValidationMessage)
        assertNull(viewModel.state.value.statusMessage)
        assertNull(viewModel.state.value.stagedExport)
        assertTrue(!viewModel.state.value.isReviewingWarnings)
    }

    @Test
    fun generation_failure_surfaces_actionable_error_without_staged_export() {
        nextGenerateResult = AppResult.Error(DomainError.ExportError("Could not generate the local JSON export. Try again."))
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals(
            "Could not generate the local JSON export. Try again.",
            viewModel.state.value.generalValidationMessage
        )
        assertNull(viewModel.state.value.statusMessage)
        assertNull(viewModel.state.value.stagedExport)
    }

    @Test
    fun canceling_final_confirmation_preserves_staged_export_without_delivery() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)
        viewModel.onIntent(ExportUiIntent.SelectDestination(ExportDestination.DeviceDownloads))
        viewModel.onIntent(ExportUiIntent.CancelFinalDelivery)

        assertEquals(0, deliverInvocationCount)
        assertTrue(viewModel.state.value.stagedExport != null)
        assertNull(viewModel.state.value.selectedDestination)
        assertEquals(
            "Final confirmation canceled. The staged export remains local. Choose a destination again when ready.",
            viewModel.state.value.statusMessage
        )
    }

    @Test
    fun confirming_final_delivery_emits_effect_and_surfaces_result_metadata() = runBlocking {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)
        viewModel.onIntent(ExportUiIntent.SelectDestination(ExportDestination.DeviceDownloads))

        val effect = async { viewModel.effects.first() }
        viewModel.onIntent(ExportUiIntent.ConfirmFinalDelivery)

        assertEquals(1, deliverInvocationCount)
        assertEquals(ExportDestination.DeviceDownloads, lastDeliveredDestination)
        assertTrue(lastDeliveredExport != null)
        assertEquals(
            ExportUiEffect.ExportDelivered(
                configuration = ExportConfiguration(
                    format = ExportFormat.JSON,
                    scope = ExportScope.AllRecords
                ),
                result = (nextDeliveryResult as AppResult.Success).value
            ),
            effect.await()
        )
        assertEquals("Downloads/app", viewModel.state.value.deliveryResult?.locationLabel)
        assertTrue(viewModel.state.value.statusMessage?.contains("Downloads/app") == true)
    }

    @Test
    fun repeated_final_confirm_taps_do_not_start_duplicate_delivery() {
        runBlocking {
            val pendingDelivery = CompletableDeferred<AppResult<ExportDeliveryResult>>()
            nextDeliveryResult = AppResult.Success(
                ExportDeliveryResult(
                    destination = ExportDestination.DeviceDownloads,
                    fileName = "app-export.json",
                    locationLabel = "Downloads/app",
                    locationUri = "content://downloads/app-export.json"
                )
            )
            val viewModel = createViewModelWithPendingDelivery(pendingDelivery)

            viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
            viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
            viewModel.onIntent(ExportUiIntent.ConfirmExportStart)
            viewModel.onIntent(ExportUiIntent.SelectDestination(ExportDestination.DeviceDownloads))
            viewModel.onIntent(ExportUiIntent.ConfirmFinalDelivery)
            viewModel.onIntent(ExportUiIntent.ConfirmFinalDelivery)

            assertEquals(1, deliverInvocationCount)

            pendingDelivery.complete(nextDeliveryResult)
        }
    }

    @Test
    fun cancel_is_ignored_while_delivery_is_in_flight() {
        runBlocking {
            val pendingDelivery = CompletableDeferred<AppResult<ExportDeliveryResult>>()
            nextDeliveryResult = AppResult.Success(
                ExportDeliveryResult(
                    destination = ExportDestination.DeviceDownloads,
                    fileName = "app-export.json",
                    locationLabel = "Downloads/app",
                    locationUri = "content://downloads/app-export.json"
                )
            )
            val viewModel = createViewModelWithPendingDelivery(pendingDelivery)

            viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
            viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
            viewModel.onIntent(ExportUiIntent.ConfirmExportStart)
            viewModel.onIntent(ExportUiIntent.SelectDestination(ExportDestination.DeviceDownloads))
            viewModel.onIntent(ExportUiIntent.ConfirmFinalDelivery)
            viewModel.onIntent(ExportUiIntent.CancelFinalDelivery)

            assertEquals(ExportDestination.DeviceDownloads, viewModel.state.value.selectedDestination)
            assertTrue(viewModel.state.value.isBusy)

            pendingDelivery.complete(nextDeliveryResult)
        }
    }

    @Test
    fun delivery_failure_surfaces_actionable_error_without_success_state() {
        nextDeliveryResult = AppResult.Error(DomainError.ExportError("Could not save the export to Downloads. Try again."))
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)
        viewModel.onIntent(ExportUiIntent.SelectDestination(ExportDestination.DeviceDownloads))
        viewModel.onIntent(ExportUiIntent.ConfirmFinalDelivery)

        assertEquals(
            "Could not save the export to Downloads. Try again.",
            viewModel.state.value.generalValidationMessage
        )
        assertNull(viewModel.state.value.deliveryResult)
        assertEquals(ExportDestination.DeviceDownloads, viewModel.state.value.selectedDestination)
    }

    @Test
    fun canceling_replacement_import_selection_clears_review_and_blocks_stale_apply() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.ImportDocumentSelected("file:///tmp/import.json"))

        assertTrue(viewModel.state.value.importReview != null)

        viewModel.onIntent(ExportUiIntent.ImportDocumentSelected(null))
        viewModel.onIntent(ExportUiIntent.ConfirmImportApply)

        assertNull(viewModel.state.value.importReview)
        assertNull(viewModel.state.value.importResult)
        assertEquals(0, importApplyInvocationCount)
        assertEquals(
            "Import selection canceled. Choose a file to review when ready.",
            viewModel.state.value.importStatusMessage
        )
    }

    @Test
    fun canceling_in_flight_import_review_prevents_stale_review_from_resurfacing() = runBlocking {
        pendingImportReviewGate = CompletableDeferred()
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.ImportDocumentSelected("file:///tmp/import.json"))

        assertTrue(viewModel.state.value.isBusy)

        viewModel.onIntent(ExportUiIntent.CancelImportReview)
        pendingImportReviewGate?.complete(Unit)

        assertNull(viewModel.state.value.importReview)
        assertEquals("Import review canceled. Choose another file when ready.", viewModel.state.value.importStatusMessage)
        assertTrue(!viewModel.state.value.isBusy)
    }

    @Test
    fun canceling_import_selection_does_not_block_unrelated_export_flow() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.ImportDocumentSelected("file:///tmp/import.json"))
        assertTrue(viewModel.state.value.importReview != null)

        viewModel.onIntent(ExportUiIntent.ImportDocumentSelected(null))
        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertTrue(viewModel.state.value.stagedExport != null)
        assertEquals(1, generateInvocationCount)
        assertNull(viewModel.state.value.importReview)
        assertNull(viewModel.state.value.importResult)
    }

    @Test
    fun restarting_after_cancel_regenerates_export_for_the_same_configuration() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)
        viewModel.onIntent(ExportUiIntent.SelectDestination(ExportDestination.DeviceDownloads))
        viewModel.onIntent(ExportUiIntent.CancelFinalDelivery)
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals(2, generateInvocationCount)
        assertTrue(viewModel.state.value.stagedExport != null)
    }

    @Test
    fun record_count_changes_after_staging_do_not_change_confirmation_summary() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals(
            "JSON export will include all 3 saved records on this device.",
            viewModel.state.value.summaryText
        )

        recordCountFlow.value = 5

        assertEquals(
            "JSON export will include all 3 saved records on this device.",
            viewModel.state.value.summaryText
        )
    }

    @Test
    fun record_count_changes_after_delivery_do_not_change_success_summary() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)
        viewModel.onIntent(ExportUiIntent.SelectDestination(ExportDestination.DeviceDownloads))
        viewModel.onIntent(ExportUiIntent.ConfirmFinalDelivery)

        assertEquals(
            "JSON export will include all 3 saved records on this device.",
            viewModel.state.value.summaryText
        )

        recordCountFlow.value = 1

        assertEquals(
            "JSON export will include all 3 saved records on this device.",
            viewModel.state.value.summaryText
        )
    }

    @Test
    fun destination_option_label_matches_supported_runtime_destination() {
        val viewModel = createViewModel()

        val expectedLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Device Downloads"
        } else {
            "App-specific Downloads"
        }

        assertEquals(expectedLabel, viewModel.state.value.destinationOptions.single().label)
    }

    @Test
    fun repeated_confirm_taps_do_not_start_duplicate_exports() {
        runBlocking {
            pendingGenerateResult = CompletableDeferred()
            generateInvocationCount = 0
            val viewModel = createViewModel()

            viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
            viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
            viewModel.onIntent(ExportUiIntent.ConfirmExportStart)
            viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

            assertEquals(1, generateInvocationCount)

            pendingGenerateResult?.complete(nextGenerateResult)
        }
    }

    @Test
    fun repeated_warning_continue_taps_do_not_start_duplicate_exports() {
        runBlocking {
            pendingGenerateResult = CompletableDeferred()
            generateInvocationCount = 0
            val viewModel = createViewModel()

            viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
            viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.EventTypeRecords(RecordEventType.EXPOSURE)))
            viewModel.onIntent(ExportUiIntent.ConfirmExportStart)
            viewModel.onIntent(ExportUiIntent.ContinueWarningReview)
            viewModel.onIntent(ExportUiIntent.ContinueWarningReview)

            assertEquals(1, generateInvocationCount)
            assertEquals(reviewedPreparedExport, generatedPreparedExport)

            pendingGenerateResult?.complete(nextGenerateResult)
        }
    }

    @Test
    fun selection_changes_are_ignored_while_export_generation_is_in_flight() {
        runBlocking {
            pendingGenerateResult = CompletableDeferred()
            generateInvocationCount = 0
            val viewModel = createViewModel()

            viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
            viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
            viewModel.onIntent(ExportUiIntent.ConfirmExportStart)
            viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.EventTypeRecords(RecordEventType.EXPOSURE)))

            assertEquals(ExportScope.AllRecords, viewModel.state.value.selectedScope)

            pendingGenerateResult?.complete(nextGenerateResult)
        }
    }

    private fun sampleSnapshot() = com.example.app.features.export.domain.ExportSnapshot(
        ingestions = emptyList(),
        exposures = emptyList(),
        outbreaks = emptyList(),
        reusableIngredients = emptyList(),
        reusableCompounds = emptyList(),
        reusableEventCatalogEntries = emptyList()
    )
}
