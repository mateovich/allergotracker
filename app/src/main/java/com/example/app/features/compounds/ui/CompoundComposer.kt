package com.example.app.features.compounds.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
fun CompoundComposer(
    prefix: String,
    componentSearchQuery: String,
    filteredComponents: List<CompoundComponentCatalogOptionUiModel>,
    components: List<CompoundComponentRowUiModel>,
    selectedPreset: CompoundCompositionPreset?,
    totalPercentLabel: String,
    componentsError: String?,
    totalError: String?,
    onSearchQueryChange: (String) -> Unit,
    onAddIngredient: (Long) -> Unit,
    onAddCompound: (Long) -> Unit,
    onApplyPreset: (CompoundCompositionPreset) -> Unit,
    onProportionTextChange: (Int, String) -> Unit,
    onProportionSliderChange: (Int, Int) -> Unit,
    onRemoveComponent: (Int) -> Unit
) {
    OutlinedTextField(
        value = componentSearchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$prefix-component-search"),
        label = { Text("Search saved ingredients or compounds") },
        singleLine = true
    )

    if (filteredComponents.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filteredComponents.forEach { option ->
                AssistChip(
                    onClick = {
                        when (option.kind) {
                            "ingredient" -> onAddIngredient(option.id)
                            else -> onAddCompound(option.id)
                        }
                    },
                    label = { Text("${option.name} - ${option.supportingLabel}") },
                    modifier = Modifier.testTag("$prefix-component-option-${option.kind}-${option.id}")
                )
            }
        }
    } else {
        Text(
            text = "No saved ingredients or compounds match. Create reusable items first if needed.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("$prefix-component-empty")
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Components", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("$prefix-preset-actions")
        ) {
            CompoundCompositionPreset.entries.forEach { preset ->
                val isSelected = selectedPreset == preset
                FilterChip(
                    selected = isSelected,
                    onClick = { onApplyPreset(preset) },
                    label = { Text(preset.label) },
                    modifier = Modifier
                        .testTag("$prefix-preset-${preset.wireValue}")
                        .semantics {
                            contentDescription = "Apply ${preset.label.lowercase()} preset"
                            stateDescription = buildString {
                                append(if (isSelected) "Selected" else "Not selected")
                                append(". ")
                                append(totalPercentLabel)
                            }
                        }
                )
            }
        }
        components.forEachIndexed { index, component ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$prefix-component-row-$index"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${component.name} (${component.kind})",
                        modifier = Modifier.testTag("$prefix-component-label-$index")
                    )
                    Text(
                        text = component.proportionBasisPoints?.toPercentLabel() ?: "--",
                        modifier = Modifier.testTag("$prefix-component-value-$index")
                    )
                }
                Slider(
                    value = (component.proportionBasisPoints ?: 0) / 100f,
                    onValueChange = { value -> onProportionSliderChange(index, (value * 100).toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("$prefix-component-slider-$index")
                        .semantics {
                            contentDescription = "${component.name} proportion slider"
                            stateDescription = component.proportionBasisPoints?.toPercentLabel() ?: "0%"
                        }
                )
                OutlinedTextField(
                    value = component.proportionPercent,
                    onValueChange = { onProportionTextChange(index, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("$prefix-component-proportion-$index"),
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
                    onClick = { onRemoveComponent(index) },
                    modifier = Modifier.testTag("$prefix-component-remove-$index")
                ) {
                    Text("Remove component")
                }
            }
        }
        Text(
            text = totalPercentLabel,
            modifier = Modifier.testTag("$prefix-total-label")
        )
        componentsError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("$prefix-components-error")
            )
        }
        totalError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("$prefix-total-error")
            )
        }
    }
}
