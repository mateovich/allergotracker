package com.example.app.features.records.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.core.designsystem.AppScreenScaffold
import com.example.app.core.designsystem.EmptyStateMessage
import com.example.app.core.designsystem.PrimaryAddFab
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RecordsScreen(
    onNavigateToRoute: (String) -> Unit = {},
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: RecordsViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(RecordsUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is RecordsUiEffect.NavigateToCreateForm -> onNavigateToRoute(effect.route)
                is RecordsUiEffect.NavigateToEditForm -> onNavigateToRoute(effect.route)
            }
        }
    }

    AppScreenScaffold(
        title = state.title,
        floatingActionButton = {
            Box(modifier = Modifier.wrapContentSize(Alignment.BottomEnd)) {
                PrimaryAddFab(
                    contentDescription = state.addActionLabel,
                    onClick = { viewModel.onIntent(RecordsUiIntent.OpenAddEntryPoint) }
                )
                DropdownMenu(
                    expanded = state.isQuickPickerVisible,
                    onDismissRequest = {
                        viewModel.onIntent(RecordsUiIntent.DismissAddEntryPoint)
                    },
                    modifier = Modifier.semantics {
                        contentDescription = state.quickPickerLabel
                    }
                ) {
                    EventType.entries.forEach { eventType ->
                        DropdownMenuItem(
                            text = { Text(text = eventType.label) },
                            onClick = {
                                viewModel.onIntent(RecordsUiIntent.SelectEventType(eventType))
                            },
                            modifier = Modifier.semantics {
                                contentDescription = eventType.label
                            }
                        )
                    }
                }
            }
        }
    ) { contentModifier ->
        if (!state.hasRecords) {
            EmptyStateMessage(
                title = state.emptyStateTitle,
                message = state.emptyStateMessage,
                modifier = contentModifier
            )
        } else {
            LazyColumn(
                modifier = contentModifier
                    .fillMaxWidth()
                    .testTag("records-list")
            ) {
                items(state.records, key = { it.id }) { record ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = record.isEditable) {
                                viewModel.onIntent(RecordsUiIntent.OpenRecord(record.id))
                            }
                            .testTag("record-item-${record.id}")
                    ) {
                        Text(text = record.typeLabel, style = MaterialTheme.typography.labelMedium)
                        Text(text = record.title, style = MaterialTheme.typography.titleMedium)
                        if (record.detailLabel.isNotBlank()) {
                            Text(text = record.detailLabel)
                        }
                        Text(text = record.occurredAtLabel)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
