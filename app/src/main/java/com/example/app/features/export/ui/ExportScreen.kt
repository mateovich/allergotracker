package com.example.app.features.export.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.core.designsystem.AppScreenScaffold
import com.example.app.core.designsystem.LocalOnlyNotice
import com.example.app.features.export.domain.ExportConfiguration
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ExportScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onExportStartRequested: (ExportConfiguration) -> Unit = {},
    viewModel: ExportViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ExportUiEffect.ExportStartRequested -> onExportStartRequested(effect.configuration)
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.generalValidationMessage?.let { message ->
                    ValidationMessage(message = message, testTag = "export-general-validation")
                }
                state.requestMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("export-request-message")
                    )
                }
                if (state.isReviewingWarnings) {
                    WarningReviewSection(
                        warningMessages = state.warningMessages,
                        isBusy = state.isConfirming,
                        onContinue = { viewModel.onIntent(ExportUiIntent.ContinueWarningReview) },
                        onCancel = { viewModel.onIntent(ExportUiIntent.CancelWarningReview) }
                    )
                } else {
                    Button(
                        onClick = { viewModel.onIntent(ExportUiIntent.ConfirmExportStart) },
                        enabled = !state.isConfirming,
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
