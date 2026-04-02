package com.example.app.features.compounds.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.core.designsystem.AppScreenScaffold
import com.example.app.core.designsystem.LocalOnlyNotice
import com.example.app.features.compounds.domain.ReusableCompoundRetirementImpact
import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditCompoundScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onNavigateBackWithSelection: (IngestionReusableSelectionResult) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EditCompoundViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is EditCompoundUiEffect.NavigateBackWithSelection -> onNavigateBackWithSelection(effect.result)
                EditCompoundUiEffect.NavigateBackAfterRetirement -> onNavigateBack()
            }
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        if (state.isLoading) {
            Column(modifier = contentModifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("edit-compound-loading"))
            }
            return@AppScreenScaffold
        }

        if (state.name.isBlank() && state.formMessage.isNotBlank()) {
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag("edit-compound-load-error"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = state.formMessage, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { viewModel.onIntent(EditCompoundUiIntent.Load) },
                    modifier = Modifier.testTag("edit-compound-retry")
                ) {
                    Text("Retry")
                }
            }
            return@AppScreenScaffold
        }

        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
                .testTag("edit-compound-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalOnlyNotice()
            Text(text = state.helperMessage, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.onIntent(EditCompoundUiIntent.UpdateName(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-compound-name"),
                label = { Text("Compound name") },
                supportingText = { state.nameError?.let { Text(it) } },
                isError = state.nameError != null,
                singleLine = true
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(EditCompoundUiIntent.UpdateNotes(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-compound-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )
            if (state.formMessage.isNotBlank()) {
                Text(
                    text = state.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("edit-compound-form-message")
                )
            }
            Button(
                onClick = { viewModel.onIntent(EditCompoundUiIntent.Save) },
                enabled = state.canSave && !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("edit-compound-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save changes")
            }
            Button(
                onClick = { viewModel.onIntent(EditCompoundUiIntent.RequestRetirementPreview) },
                enabled = !state.isSaving && !state.isRetiring,
                modifier = Modifier.fillMaxWidth().testTag("edit-compound-retire")
            ) {
                Text("Retire compound")
            }
        }

        if (state.isRetirementDialogVisible) {
            state.retirementImpact?.let { impact ->
            val message = buildCompoundRetirementMessage(impact)
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(EditCompoundUiIntent.DismissRetirementDialog) },
                title = { Text("Retire compound?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(message, modifier = Modifier.testTag("edit-compound-retire-message"))
                        if (state.formMessage.isNotBlank()) {
                            Text(
                                text = state.formMessage,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("edit-compound-retire-error")
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!impact.isBlocked) {
                        TextButton(
                            onClick = { viewModel.onIntent(EditCompoundUiIntent.ConfirmRetirement) },
                            enabled = !state.isRetiring,
                            modifier = Modifier.testTag("edit-compound-retire-confirm")
                        ) {
                            Text(if (state.isRetiring) "Retiring..." else "Confirm")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.onIntent(EditCompoundUiIntent.DismissRetirementDialog) },
                        modifier = Modifier.testTag("edit-compound-retire-dismiss")
                    ) {
                        Text(if (impact.isBlocked) "Close" else "Cancel")
                    }
                }
            )
            }
        }
    }
}

private fun buildCompoundRetirementMessage(impact: ReusableCompoundRetirementImpact): String {
    return buildString {
        appendLine("Current references:")
        appendLine("- Ingestion records: ${impact.ingestionReferenceCount}")
        appendLine("- Nested compounds: ${impact.childCompoundReferenceCount}")
        appendLine()
        append(
            if (impact.isBlocked) {
                "Safe path: update the linked nested compounds first, then retry retirement."
            } else if (impact.requiresDetachment) {
                "Safe path: retirement will detach the linked ingestion records and keep their current text labels for manual review."
            } else {
                "Safe path: no active references remain, so this compound can be removed from the reusable catalog."
            }
        )
    }.trimEnd()
}
