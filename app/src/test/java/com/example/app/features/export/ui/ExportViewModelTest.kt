package com.example.app.features.export.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.core.export.schema.EXPORT_SCHEMA_VERSION
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
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordsFilter
import com.example.app.features.records.domain.RecordsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

    private val repository = object : RecordsRepository {
        override fun observeRecordCount() = flowOf(3)

        override fun observeRecords(filters: Flow<RecordsFilter>) = flowOf(emptyList<RecordSummary>())
    }

    private val observeRecordCountUseCase = ObserveRecordCountUseCase(repository)
    private val validateExportConfigurationUseCase = ValidateExportConfigurationUseCase()
    private var pendingGenerateResult: CompletableDeferred<AppResult<GeneratedJsonExport>>? = null
    private var generateInvocationCount: Int = 0
    private var nextReviewResult: ExportPreflightReview? = null
    private var reviewedPreparedExport: PreparedExport? = null
    private var generatedPreparedExport: PreparedExport? = null
    private var nextGenerateResult: AppResult<GeneratedJsonExport> = AppResult.Success(
        GeneratedJsonExport(
            configuration = com.example.app.features.export.domain.ExportConfiguration(
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
    private fun createViewModel(): ExportViewModel {
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
            override suspend fun review(configuration: com.example.app.features.export.domain.ExportConfiguration): ExportPreflightReview {
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
                configuration: com.example.app.features.export.domain.ExportConfiguration,
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
        return ExportViewModel(observeRecordCountUseCase, validateExportConfigurationUseCase, fakeGenerateUseCase)
    }

    @Test
    fun initial_state_does_not_emit_export_start_effect() {
        val viewModel = createViewModel()

        assertNull(viewModel.state.value.requestMessage)
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
    fun selecting_configuration_does_not_emit_export_start_before_confirm() = runBlocking {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))

        val emittedEffect = withTimeoutOrNull(50) { viewModel.effects.first() }

        assertNull(emittedEffect)
        assertNull(viewModel.state.value.requestMessage)
    }

    @Test
    fun confirm_requires_format_selection() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals("Choose an export format.", viewModel.state.value.formatValidationMessage)
        assertEquals("Choose a format and scope before continuing.", viewModel.state.value.generalValidationMessage)
        assertNull(viewModel.state.value.requestMessage)
    }

    @Test
    fun confirm_requires_scope_selection() {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals("Choose which records to include.", viewModel.state.value.scopeValidationMessage)
        assertEquals("Choose a format and scope before continuing.", viewModel.state.value.generalValidationMessage)
        assertNull(viewModel.state.value.requestMessage)
    }

    @Test
    fun export_start_is_emitted_only_after_explicit_confirm_and_success_updates_message() = runBlocking {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))

        val effect = async { viewModel.effects.first() }
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals(
            ExportUiEffect.ExportStartRequested(
                configuration = com.example.app.features.export.domain.ExportConfiguration(
                    format = ExportFormat.JSON,
                    scope = ExportScope.AllRecords
                )
            ),
            effect.await()
        )
        assertEquals(
            "JSON export generated locally on this device.",
            viewModel.state.value.requestMessage
        )
        assertTrue(viewModel.state.value.generatedExport != null)
    }

    @Test
    fun partial_scope_requires_explicit_warning_continuation_before_generation() = runBlocking {
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.EventTypeRecords(RecordEventType.EXPOSURE)))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals(0, generateInvocationCount)
        assertTrue(viewModel.state.value.isReviewingWarnings)
        assertEquals(1, viewModel.state.value.warningMessages.size)

        val effect = async { viewModel.effects.first() }
        viewModel.onIntent(ExportUiIntent.ContinueWarningReview)

        assertEquals(1, generateInvocationCount)
        assertEquals(reviewedPreparedExport, generatedPreparedExport)
        assertEquals(
            ExportUiEffect.ExportStartRequested(
                configuration = com.example.app.features.export.domain.ExportConfiguration(
                    format = ExportFormat.JSON,
                    scope = ExportScope.EventTypeRecords(RecordEventType.EXPOSURE)
                )
            ),
            effect.await()
        )
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
        assertNull(viewModel.state.value.requestMessage)
        assertNull(viewModel.state.value.generatedExport)
        assertTrue(!viewModel.state.value.isReviewingWarnings)
    }

    @Test
    fun generation_failure_surfaces_actionable_error_without_success_state() = runBlocking {
        nextGenerateResult = AppResult.Error(DomainError.ExportError("Could not generate the local JSON export. Try again."))
        val viewModel = createViewModel()

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals(
            "Could not generate the local JSON export. Try again.",
            viewModel.state.value.generalValidationMessage
        )
        assertNull(viewModel.state.value.requestMessage)
        assertNull(viewModel.state.value.generatedExport)
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
