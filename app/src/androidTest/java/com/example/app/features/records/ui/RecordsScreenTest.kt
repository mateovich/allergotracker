package com.example.app.features.records.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.GetIngestionEventUseCase
import com.example.app.features.ingestion.domain.IngestionEvent
import com.example.app.features.ingestion.domain.IngestionRepository
import com.example.app.features.ingestion.domain.UpdateIngestionEventCommand
import com.example.app.features.ingestion.domain.UpdateIngestionEventUseCase
import com.example.app.features.ingestion.ui.EditIngestionScreen
import com.example.app.features.ingestion.ui.EditIngestionViewModel
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.ObserveRecordsUseCase
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordsRepository
import com.example.app.navigation.AppRoutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecordsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clicking_record_row_navigates_to_matching_edit_route() {
        val repository = object : RecordsRepository {
            override fun observeRecordCount() = flowOf(1)

            override fun observeRecords() = flowOf(
                listOf(
                    RecordSummary(
                        id = "ingestion-7",
                        sortKey = 7L,
                        typeLabel = "Ingestion",
                        title = "Tea",
                        detailLabel = "2 Light snack",
                        occurredAtEpochMs = 1_700_000_000_000L,
                        sourceId = 7L,
                        eventType = RecordEventType.INGESTION
                    )
                )
            )
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RecordsViewModel(
                    currentTimeProvider = CurrentTimeProvider { 1_700_000_000_000L },
                    observeRecordCountUseCase = ObserveRecordCountUseCase(repository),
                    observeRecordsUseCase = ObserveRecordsUseCase(repository)
                ) as T
            }
        }
        var navigatedRoute = ""

        composeRule.setContent {
            RecordsScreen(
                onNavigateToRoute = { navigatedRoute = it },
                viewModelFactory = factory
            )
        }

        composeRule.onNodeWithTag("record-item-ingestion-7").performClick()

        assertEquals(AppRoutes.editIngestion(7L), navigatedRoute)
    }

    @Test
    fun ingestion_edit_flow_blocks_invalid_save_then_updates_existing_record_without_duplicate() {
        val fixedNow = 1_700_000_000_000L
        val timeline = MutableStateFlow(
            listOf(
                RecordSummary(
                    id = "ingestion-7",
                    sortKey = 7L,
                    typeLabel = "Ingestion",
                    title = "Tea",
                    detailLabel = "2 Light snack",
                    occurredAtEpochMs = fixedNow - 60_000L,
                    sourceId = 7L,
                    eventType = RecordEventType.INGESTION
                )
            )
        )
        var currentEvent = IngestionEvent(
            id = 7L,
            itemName = "Tea",
            quantityLevel = 2,
            occurredAtEpochMs = fixedNow - 60_000L,
            notes = "with honey"
        )

        val recordsRepository = object : RecordsRepository {
            override fun observeRecordCount(): Flow<Int> = timeline.map { it.size }

            override fun observeRecords(): Flow<List<RecordSummary>> = timeline
        }
        val ingestionRepository = object : IngestionRepository {
            override suspend fun createIngestion(command: com.example.app.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> {
                error("unused")
            }

            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> = AppResult.Success(currentEvent)

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                currentEvent = currentEvent.copy(
                    itemName = command.itemName,
                    quantityLevel = command.quantityLevel ?: currentEvent.quantityLevel,
                    occurredAtEpochMs = command.occurredAtEpochMs,
                    notes = command.notes
                )
                timeline.value = listOf(
                    RecordSummary(
                        id = "ingestion-7",
                        sortKey = 7L,
                        typeLabel = "Ingestion",
                        title = currentEvent.itemName,
                        detailLabel = "2 Light snack",
                        occurredAtEpochMs = currentEvent.occurredAtEpochMs,
                        sourceId = 7L,
                        eventType = RecordEventType.INGESTION
                    )
                )
                return AppResult.Success(currentEvent)
            }
        }
        val recordsFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RecordsViewModel(
                    currentTimeProvider = CurrentTimeProvider { fixedNow },
                    observeRecordCountUseCase = ObserveRecordCountUseCase(recordsRepository),
                    observeRecordsUseCase = ObserveRecordsUseCase(recordsRepository)
                ) as T
            }
        }
        val editFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditIngestionViewModel(
                    recordId = 7L,
                    getIngestionEventUseCase = GetIngestionEventUseCase(ingestionRepository),
                    updateIngestionEventUseCase = UpdateIngestionEventUseCase(ingestionRepository, CurrentTimeProvider { fixedNow }),
                    currentTimeProvider = CurrentTimeProvider { fixedNow }
                ) as T
            }
        }

        composeRule.setContent {
            EditJourneyHost(
                recordsFactory = recordsFactory,
                editFactory = editFactory
            )
        }

        composeRule.onNodeWithTag("record-item-ingestion-7").performClick()
        composeRule.onNodeWithTag("edit-ingestion-form-screen").assertIsDisplayed()

        composeRule.onNodeWithTag("edit-ingestion-item-name").performTextReplacement("   ")
        composeRule.onNodeWithTag("edit-ingestion-save").performClick()
        composeRule.onNodeWithText("Enter what you ingested.").assertIsDisplayed()

        composeRule.onNodeWithTag("edit-ingestion-item-name").performTextReplacement("Tea updated")
        composeRule.onNodeWithTag("edit-ingestion-save").performClick()

        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onAllNodesWithText("Tea updated").assertCountEquals(1)
        composeRule.onAllNodesWithText("Tea").assertCountEquals(0)
    }
}

@Composable
private fun EditJourneyHost(
    recordsFactory: ViewModelProvider.Factory,
    editFactory: ViewModelProvider.Factory
) {
    var currentRoute by remember { mutableStateOf(AppRoutes.RECORDS) }

    when (currentRoute) {
        AppRoutes.RECORDS -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )

        AppRoutes.editIngestion(7L) -> EditIngestionScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = editFactory
        )

        else -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )
    }
}
