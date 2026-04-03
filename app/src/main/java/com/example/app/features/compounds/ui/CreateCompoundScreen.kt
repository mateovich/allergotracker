package com.example.app.features.compounds.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CreateCompoundScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onNavigateBackWithSelection: (com.example.app.features.ingestion.ui.IngestionReusableSelectionResult) -> Unit,
    viewModel: CreateCompoundViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CreateCompoundUiEffect.NavigateBackWithSelection -> onNavigateBackWithSelection(effect.result)
            }
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("create-compound-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalOnlyNotice()
            Text(text = state.helperMessage, style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.onIntent(CreateCompoundUiIntent.UpdateName(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compound-name"),
                label = { Text("Compound name") },
                supportingText = { state.nameError?.let { Text(it) } },
                isError = state.nameError != null,
                singleLine = true
            )

            OutlinedTextField(
                value = state.componentSearchQuery,
                onValueChange = { viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentSearchQuery(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compound-component-search"),
                label = { Text("Search saved ingredients or compounds") },
                singleLine = true
            )

            if (state.filteredComponents.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.filteredComponents.forEach { option ->
                        AssistChip(
                            onClick = {
                                when (option.kind) {
                                    "ingredient" -> viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(option.id))
                                    else -> viewModel.onIntent(CreateCompoundUiIntent.AddCompoundComponent(option.id))
                                }
                            },
                            label = { Text("${option.name} - ${option.supportingLabel}") },
                            modifier = Modifier.testTag("compound-component-option-${option.kind}-${option.id}")
                        )
                    }
                }
            } else {
                Text(
                    text = "No saved ingredients or compounds match. Create reusable items first if needed.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("compound-component-empty")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Components", style = MaterialTheme.typography.titleSmall)
                state.components.forEachIndexed { index, component ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("compound-component-row-$index"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${component.name} (${component.kind})",
                            modifier = Modifier.testTag("compound-component-label-$index")
                        )
                        OutlinedTextField(
                            value = component.proportionPercent,
                            onValueChange = {
                                viewModel.onIntent(
                                    CreateCompoundUiIntent.UpdateComponentProportion(index = index, value = it)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("compound-component-proportion-$index"),
                            label = { Text("Proportion (%)") },
                            supportingText = {
                                if (component.targetError != null || component.proportionError != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        component.targetError?.let { Text(it) }
                                        component.proportionError?.let { Text(it) }
                                    }
                                }
                            },
                            isError = component.proportionError != null || component.targetError != null,
                            singleLine = true
                        )
                        TextButton(
                            onClick = { viewModel.onIntent(CreateCompoundUiIntent.RemoveComponent(index)) },
                            modifier = Modifier.testTag("compound-component-remove-$index")
                        ) {
                            Text("Remove component")
                        }
                    }
                }
                Text(
                    text = state.totalPercentLabel,
                    modifier = Modifier.testTag("compound-total-label")
                )
                state.componentsError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("compound-components-error")
                    )
                }
                state.totalError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("compound-total-error")
                    )
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(CreateCompoundUiIntent.UpdateNotes(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compound-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )

            if (state.formMessage.isNotBlank()) {
                Text(
                    text = state.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("compound-form-message")
                )
            }

            Button(
                onClick = { viewModel.onIntent(CreateCompoundUiIntent.Save) },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compound-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save compound")
            }
        }
    }
}
