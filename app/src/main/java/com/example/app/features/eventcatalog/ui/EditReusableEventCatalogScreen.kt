package com.example.app.features.eventcatalog.ui

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
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogRetirementImpact
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditReusableEventCatalogScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onNavigateBackWithSelection: (EditedEventCatalogSelectionResult) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EditReusableEventCatalogViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(EditReusableEventCatalogUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is EditReusableEventCatalogUiEffect.NavigateBackWithSelection -> onNavigateBackWithSelection(effect.result)
                EditReusableEventCatalogUiEffect.NavigateBackAfterRetirement -> onNavigateBack()
            }
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        if (state.isLoading) {
            Column(modifier = contentModifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("edit-event-catalog-loading"))
            }
            return@AppScreenScaffold
        }

        if (state.name.isBlank() && state.formMessage.isNotBlank()) {
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag("edit-event-catalog-load-error"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = state.formMessage, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { viewModel.onIntent(EditReusableEventCatalogUiIntent.Load) },
                    modifier = Modifier.testTag("edit-event-catalog-retry")
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
                .testTag("edit-event-catalog-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalOnlyNotice()
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.onIntent(EditReusableEventCatalogUiIntent.UpdateName(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-event-catalog-name"),
                label = { Text(state.nameLabel) },
                supportingText = { state.nameError?.let { Text(it) } },
                isError = state.nameError != null,
                singleLine = true
            )
            if (state.formMessage.isNotBlank()) {
                Text(
                    text = state.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("edit-event-catalog-form-message")
                )
            }
            Button(
                onClick = { viewModel.onIntent(EditReusableEventCatalogUiIntent.Save) },
                enabled = state.canSave && !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("edit-event-catalog-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save changes")
            }
            Button(
                onClick = { viewModel.onIntent(EditReusableEventCatalogUiIntent.RequestRetirementPreview) },
                enabled = !state.isSaving && !state.isRetiring,
                modifier = Modifier.fillMaxWidth().testTag("edit-event-catalog-retire")
            ) {
                Text("Retire saved value")
            }
        }

        if (state.isRetirementDialogVisible) {
            state.retirementImpact?.let { impact ->
            val message = buildEventCatalogRetirementMessage(state.kind, impact)
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(EditReusableEventCatalogUiIntent.DismissRetirementDialog) },
                title = { Text("Retire saved value?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(message, modifier = Modifier.testTag("edit-event-catalog-retire-message"))
                        if (state.formMessage.isNotBlank()) {
                            Text(
                                text = state.formMessage,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("edit-event-catalog-retire-error")
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!impact.isBlocked) {
                        TextButton(
                            onClick = { viewModel.onIntent(EditReusableEventCatalogUiIntent.ConfirmRetirement) },
                            enabled = !state.isRetiring,
                            modifier = Modifier.testTag("edit-event-catalog-retire-confirm")
                        ) {
                            Text(if (state.isRetiring) "Retiring..." else "Confirm")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.onIntent(EditReusableEventCatalogUiIntent.DismissRetirementDialog) },
                        modifier = Modifier.testTag("edit-event-catalog-retire-dismiss")
                    ) {
                        Text(if (impact.isBlocked) "Close" else "Cancel")
                    }
                }
            )
            }
        }
    }
}

private fun buildEventCatalogRetirementMessage(
    kind: ReusableEventCatalogKind,
    impact: ReusableEventCatalogRetirementImpact
): String {
    val referenceArea = when (kind) {
        ReusableEventCatalogKind.EXPOSURE_TYPE -> "Exposure records"
        ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> "Outbreak records"
        ReusableEventCatalogKind.BODY_LOCATION -> "Outbreak body-location fields"
    }
    return buildString {
        appendLine("Current references:")
        appendLine("- $referenceArea: ${impact.activeReferenceCount}")
        appendLine()
        append(
            if (impact.isBlocked) {
                "Safe path: replace or clear those references before trying retirement again."
            } else {
                "Safe path: no active references remain, so this saved value can be removed from the reusable catalog."
            }
        )
    }.trimEnd()
}
