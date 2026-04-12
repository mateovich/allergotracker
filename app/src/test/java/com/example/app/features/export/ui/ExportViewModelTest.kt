package com.example.app.features.export.ui

import com.example.app.MainDispatcherRule
import com.example.app.features.export.domain.ExportFormat
import com.example.app.features.export.domain.ExportScope
import com.example.app.features.export.domain.ValidateExportConfigurationUseCase
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordsFilter
import com.example.app.features.records.domain.RecordsRepository
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

    @Test
    fun initial_state_does_not_emit_export_start_effect() {
        val viewModel = ExportViewModel(observeRecordCountUseCase, validateExportConfigurationUseCase)

        assertNull(viewModel.state.value.requestMessage)
        assertEquals(
            "Choose an export format and scope to review what will be included.",
            viewModel.state.value.summaryText
        )
    }

    @Test
    fun selecting_format_and_scope_updates_summary_deterministically() {
        val viewModel = ExportViewModel(observeRecordCountUseCase, validateExportConfigurationUseCase)

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.EventTypeRecords(RecordEventType.EXPOSURE)))

        assertEquals(
            "JSON export will include only exposure records saved on this device.",
            viewModel.state.value.summaryText
        )
    }

    @Test
    fun selecting_configuration_does_not_emit_export_start_before_confirm() = runBlocking {
        val viewModel = ExportViewModel(observeRecordCountUseCase, validateExportConfigurationUseCase)

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))

        val emittedEffect = withTimeoutOrNull(50) { viewModel.effects.first() }

        assertNull(emittedEffect)
        assertNull(viewModel.state.value.requestMessage)
    }

    @Test
    fun confirm_requires_format_selection() {
        val viewModel = ExportViewModel(observeRecordCountUseCase, validateExportConfigurationUseCase)

        viewModel.onIntent(ExportUiIntent.SelectScope(ExportScope.AllRecords))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals("Choose an export format.", viewModel.state.value.formatValidationMessage)
        assertEquals("Choose a format and scope before continuing.", viewModel.state.value.generalValidationMessage)
        assertNull(viewModel.state.value.requestMessage)
    }

    @Test
    fun confirm_requires_scope_selection() {
        val viewModel = ExportViewModel(observeRecordCountUseCase, validateExportConfigurationUseCase)

        viewModel.onIntent(ExportUiIntent.SelectFormat(ExportFormat.JSON))
        viewModel.onIntent(ExportUiIntent.ConfirmExportStart)

        assertEquals("Choose which records to include.", viewModel.state.value.scopeValidationMessage)
        assertEquals("Choose a format and scope before continuing.", viewModel.state.value.generalValidationMessage)
        assertNull(viewModel.state.value.requestMessage)
    }

    @Test
    fun export_start_is_emitted_only_after_explicit_confirm() = runBlocking {
        val viewModel = ExportViewModel(observeRecordCountUseCase, validateExportConfigurationUseCase)

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
            "JSON export will include all 3 saved records on this device.",
            viewModel.state.value.summaryText
        )
        assertTrue(viewModel.state.value.requestMessage?.contains("Export request ready") == true)
    }
}
