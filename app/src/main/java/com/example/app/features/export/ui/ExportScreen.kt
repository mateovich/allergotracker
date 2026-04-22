package com.example.app.features.export.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.MainActivity
import com.example.app.core.designsystem.AppScreenScaffold
import com.example.app.core.designsystem.LocalOnlyNotice
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ExportScreen(
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: ExportViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val importPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onIntent(ExportUiIntent.ImportDocumentSelected(uri?.toString()))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ExportUiEffect.ExportDelivered -> Unit
            }
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        Column(
            modifier = contentModifier
                .testTag("export-screen")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LocalOnlyNotice()
                Text(
                    text = state.privacyNotice,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("export-privacy-copy")
                )
            }

            ExportSection(
                title = "Format",
                testTag = "export-format-selector"
            ) {
                state.formatOptions.forEach { option ->
                    ExportChoiceRow(
                        title = option.label,
                        description = option.description,
                        selected = option.isSelected,
                        testTag = "export-format-${option.format.name.lowercase()}",
                        contentDescription = "Export format ${option.label}",
                        onClick = { viewModel.onIntent(ExportUiIntent.SelectFormat(option.format)) }
                    )
                }
                state.formatValidationMessage?.let { message ->
                    ValidationMessage(message = message, testTag = "export-format-validation")
                }
            }

            ExportSection(
                title = "Scope",
                testTag = "export-scope-selector"
            ) {
                state.scopeOptions.forEach { option ->
                    ExportChoiceRow(
                        title = option.label,
                        description = option.description,
                        selected = option.isSelected,
                        testTag = option.testTag,
                        contentDescription = "Export scope ${option.label}",
                        onClick = { viewModel.onIntent(ExportUiIntent.SelectScope(option.scope)) }
                    )
                }
                state.scopeValidationMessage?.let { message ->
                    ValidationMessage(message = message, testTag = "export-scope-validation")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export-summary")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Summary", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = state.summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("export-summary-text")
                    )
                }
            }

            if (state.stagedExport != null && state.deliveryResult == null) {
                ExportSection(
                    title = "Destination",
                    testTag = "export-destination-selector"
                ) {
                    state.destinationOptions.forEach { option ->
                        ExportChoiceRow(
                            title = option.label,
                            description = option.description,
                            selected = option.isSelected,
                            testTag = option.testTag,
                            contentDescription = "Export destination ${option.label}",
                            onClick = { viewModel.onIntent(ExportUiIntent.SelectDestination(option.destination)) }
                        )
                    }
                    Text(
                        text = "Save access stays scoped to the destination you confirm for this staged file.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("export-destination-scope-note")
                    )
                }
            }

            if (state.stagedExport != null && state.selectedDestination != null && state.deliveryResult == null) {
                FinalConfirmationSection(
                    state = state,
                    onConfirm = { viewModel.onIntent(ExportUiIntent.ConfirmFinalDelivery) },
                    onCancel = { viewModel.onIntent(ExportUiIntent.CancelFinalDelivery) }
                )
            }

            state.deliveryResult?.let { result ->
                DeliveryResultSection(
                    result = result,
                    scopeSummary = state.summaryText
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImportSection(
                    state = state,
                    onSelectFile = {
                        val testUri = (context as? MainActivity)?.consumeImportDocumentForTesting()
                        if (testUri != null) {
                            viewModel.onIntent(ExportUiIntent.ImportDocumentSelected(testUri))
                        } else {
                            importPickerLauncher.launch(arrayOf("application/json"))
                        }
                    },
                    onConfirmImport = { viewModel.onIntent(ExportUiIntent.ConfirmImportApply) },
                    onCancelImport = { viewModel.onIntent(ExportUiIntent.CancelImportReview) }
                )
                state.generalValidationMessage?.let { message ->
                    ValidationMessage(message = message, testTag = "export-general-validation")
                }
                state.statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("export-status-message")
                    )
                }
                if (state.isReviewingWarnings) {
                    WarningReviewSection(
                        warningMessages = state.warningMessages,
                        isBusy = state.isBusy,
                        onContinue = { viewModel.onIntent(ExportUiIntent.ContinueWarningReview) },
                        onCancel = { viewModel.onIntent(ExportUiIntent.CancelWarningReview) }
                    )
                } else if (state.stagedExport == null) {
                    Button(
                        onClick = { viewModel.onIntent(ExportUiIntent.ConfirmExportStart) },
                        enabled = !state.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export-confirm-action")
                            .semantics { contentDescription = state.confirmActionLabel }
                    ) {
                        Text(text = state.confirmActionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportSection(
    state: ExportUiState,
    onSelectFile: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit
) {
    ExportSection(
        title = "Import",
        testTag = "import-section"
    ) {
        Text(
            text = "Choose an exported JSON file to validate locally before anything is written. Android grants access only to the file you choose here.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("import-copy")
        )
        Button(
            onClick = onSelectFile,
            enabled = !state.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("import-select-file")
        ) {
            Text("Choose import file")
        }
        state.importValidationMessage?.let { message ->
            ValidationMessage(message = message, testTag = "import-validation")
        }
        state.importStatusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("import-status-message")
            )
        }
        state.importReview?.let { review ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import-review-card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Import review", style = MaterialTheme.typography.titleMedium)
                    Text("File: ${review.fileName}", modifier = Modifier.testTag("import-review-file-name"))
                    Text("Schema: ${review.schemaVersion}", modifier = Modifier.testTag("import-review-schema"))
                    Text(
                        text = "Create ${review.summary.ingredientsToCreate} ingredients, ${review.summary.compoundsToCreate} compounds, and ${review.summary.eventCatalogEntriesToCreate} saved values.",
                        modifier = Modifier.testTag("import-review-create-summary")
                    )
                    Text(
                        text = "Reuse ${review.summary.ingredientsToReuse} ingredients, update ${review.summary.compoundsToUpdate} compounds, and create ${review.summary.ingestionsToCreate + review.summary.exposuresToCreate + review.summary.outbreaksToCreate} events.",
                        modifier = Modifier.testTag("import-review-reuse-summary")
                    )
                    Text(
                        text = "Reuse ${review.summary.eventCatalogEntriesToReuse} saved values and keep compound updates explicit before apply.",
                        modifier = Modifier.testTag("import-review-catalog-summary")
                    )
                    Text(
                        text = "Nothing is written until you confirm import.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("import-review-privacy-note")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancelImport,
                            enabled = !state.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import-cancel-review")
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = onConfirmImport,
                            enabled = !state.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import-confirm-apply")
                        ) {
                            Text("Confirm import")
                        }
                    }
                }
            }
        }
        state.importResult?.let { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import-result-card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Import completed", style = MaterialTheme.typography.titleMedium)
                    Text("File: ${result.fileName}", modifier = Modifier.testTag("import-result-file-name"))
                    Text(
                        text = "Created ${result.summary.ingestionsToCreate + result.summary.exposuresToCreate + result.summary.outbreaksToCreate} events from the validated dataset.",
                        modifier = Modifier.testTag("import-result-summary")
                    )
                    Text(
                        text = "Ingredients: ${result.summary.ingredientsToCreate} new / ${result.summary.ingredientsToReuse} reused. Compounds: ${result.summary.compoundsToCreate} new / ${result.summary.compoundsToUpdate} updated. Saved values: ${result.summary.eventCatalogEntriesToCreate} new / ${result.summary.eventCatalogEntriesToReuse} reused.",
                        modifier = Modifier.testTag("import-result-breakdown")
                    )
                }
            }
        }
    }
}

@Composable
private fun FinalConfirmationSection(
    state: ExportUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("export-final-confirmation")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Final confirmation", style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.summaryText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("export-final-scope-summary")
            )
            Text(
                text = "Destination: ${state.destinationOptions.firstOrNull { it.isSelected }?.label.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("export-final-destination")
            )
            Text(
                text = "Nothing is saved outside the app until you confirm this destination.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("export-final-privacy-note")
            )
            Text(
                text = "File: ${state.stagedExport?.artifact?.fileName.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("export-final-file-name")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export-final-cancel")
                ) {
                    Text(text = "Cancel")
                }
                Button(
                    onClick = onConfirm,
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export-final-confirm")
                ) {
                    Text(text = "Confirm save")
                }
            }
        }
    }
}

@Composable
private fun DeliveryResultSection(
    result: com.example.app.features.export.domain.ExportDeliveryResult,
    scopeSummary: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("export-result-summary")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Export saved", style = MaterialTheme.typography.titleMedium)
            Text(text = scopeSummary, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Destination: ${result.locationLabel}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("export-result-destination")
            )
            Text(
                text = "File: ${result.fileName}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("export-result-file-name")
            )
            Text(
                text = "Location: ${result.locationUri}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("export-result-location")
            )
        }
    }
}

@Composable
private fun WarningReviewSection(
    warningMessages: List<String>,
    isBusy: Boolean,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("export-warning-review")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Review warnings", style = MaterialTheme.typography.titleMedium)
            warningMessages.forEachIndexed { index, message ->
                Text(
                    text = "• $message",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("export-warning-message-$index")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export-warning-cancel")
                ) {
                    Text(text = "Cancel")
                }
                Button(
                    onClick = onContinue,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export-warning-continue")
                ) {
                    Text(text = "Continue export")
                }
            }
        }
    }
}

@Composable
private fun ExportSection(
    title: String,
    testTag: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ExportChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    testTag: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ValidationMessage(message: String, testTag: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag(testTag)
    )
}
