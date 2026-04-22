package io.github.mateovich.allergotracker.features.records.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import io.github.mateovich.allergotracker.core.designsystem.AppScreenScaffold
import io.github.mateovich.allergotracker.core.designsystem.LocalOnlyNotice
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RecordDetailScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: RecordDetailViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(RecordDetailUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                RecordDetailUiEffect.NavigateToRecords -> onNavigateToRoute(io.github.mateovich.allergotracker.navigation.AppRoutes.RECORDS)
                is RecordDetailUiEffect.NavigateToRoute -> onNavigateToRoute(effect.route)
            }
        }
    }

    AppScreenScaffold(
        title = state.screenTitle,
        topBarActions = {
            TextButton(
                onClick = { viewModel.onIntent(RecordDetailUiIntent.NavigateBack) },
                modifier = Modifier.testTag("record-detail-back")
            ) {
                Text(text = "Back")
            }
        }
    ) { contentModifier ->
        if (state.isLoading) {
            Column(
                modifier = contentModifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("record-detail-loading"))
            }
            return@AppScreenScaffold
        }

        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("record-detail-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalOnlyNotice()
            Text(text = state.typeLabel, style = MaterialTheme.typography.labelLarge)
            Text(text = state.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = state.occurredAtLabel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("record-detail-occurred-at")
            )

            Button(
                onClick = { viewModel.onIntent(RecordDetailUiIntent.OpenEdit) },
                modifier = Modifier.fillMaxWidth().testTag("record-detail-edit")
            ) {
                Text(text = state.editActionLabel)
            }
            OutlinedButton(
                onClick = { viewModel.onIntent(RecordDetailUiIntent.OpenDelete) },
                modifier = Modifier.fillMaxWidth().testTag("record-detail-delete")
            ) {
                Text(text = state.deleteActionLabel, color = MaterialTheme.colorScheme.error)
            }
            Text(
                text = state.deleteHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.fields.forEachIndexed { index, field ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("record-detail-field-$index"),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = field.label, style = MaterialTheme.typography.labelMedium)
                        Text(text = field.value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (state.isUnavailableDialogVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(RecordDetailUiIntent.AcknowledgeUnavailable) },
            title = { Text("Record unavailable") },
            text = { Text(state.unavailableMessage) },
            confirmButton = {
                Button(
                    onClick = { viewModel.onIntent(RecordDetailUiIntent.AcknowledgeUnavailable) },
                    modifier = Modifier.testTag("record-detail-unavailable-confirm")
                ) {
                    Text("Back to Records")
                }
            }
        )
    }
}
