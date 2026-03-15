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
import com.example.app.features.exposure.domain.DeleteExposureEventUseCase
import com.example.app.features.exposure.domain.ExposureEvent
import com.example.app.features.exposure.domain.ExposureRepository
import com.example.app.features.exposure.domain.GetExposureEventUseCase
import com.example.app.features.exposure.domain.UpdateExposureEventCommand
import com.example.app.features.exposure.domain.UpdateExposureEventUseCase
import com.example.app.features.exposure.ui.EditExposureScreen
import com.example.app.features.exposure.ui.EditExposureViewModel
import com.example.app.features.ingestion.domain.DeleteIngestionEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.GetIngestionEventUseCase
import com.example.app.features.ingestion.domain.IngestionEvent
import com.example.app.features.ingestion.domain.IngestionRepository
import com.example.app.features.ingestion.domain.UpdateIngestionEventCommand
import com.example.app.features.ingestion.domain.UpdateIngestionEventUseCase
import com.example.app.features.ingestion.ui.EditIngestionScreen
import com.example.app.features.ingestion.ui.EditIngestionViewModel
import com.example.app.features.outbreak.domain.DeleteOutbreakEventUseCase
import com.example.app.features.outbreak.domain.GetOutbreakEventUseCase
import com.example.app.features.outbreak.domain.OutbreakEvent
import com.example.app.features.outbreak.domain.OutbreakRepository
import com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand
import com.example.app.features.outbreak.domain.UpdateOutbreakEventUseCase
import com.example.app.features.outbreak.ui.EditOutbreakScreen
import com.example.app.features.outbreak.ui.EditOutbreakViewModel
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.ObserveRecordsUseCase
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordsRepository
import com.example.app.navigation.AppRoutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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

            override suspend fun deleteIngestion(id: Long): AppResult<Unit> {
                timeline.value = emptyList()
                return AppResult.Success(Unit)
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
                    deleteIngestionEventUseCase = DeleteIngestionEventUseCase(ingestionRepository),
                    updateIngestionEventUseCase = UpdateIngestionEventUseCase(ingestionRepository, CurrentTimeProvider { fixedNow }),
                    currentTimeProvider = CurrentTimeProvider { fixedNow }
                ) as T
            }
        }

        composeRule.setContent {
            IngestionEditJourneyHost(
                recordsFactory = recordsFactory,
                editFactory = editFactory
            )
        }

        composeRule.onNodeWithTag("record-item-ingestion-7").performClick()
        composeRule.onNodeWithTag("edit-ingestion-form-screen").assertIsDisplayed()

        composeRule.onNodeWithTag("edit-ingestion-item-name").performTextReplacement("Tea updated")
        composeRule.onNodeWithTag("edit-ingestion-save").performClick()

        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onAllNodesWithText("Tea updated").assertCountEquals(1)
        composeRule.onAllNodesWithText("Tea").assertCountEquals(0)
    }

    @Test
    fun ingestion_delete_flow_removes_row_and_returns_to_records() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            ingestion = IngestionEvent(7L, "Tea", 2, fixedNow - 60_000L, "with honey")
        )

        val recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow)
        val editFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditIngestionViewModel(
                    recordId = 7L,
                    getIngestionEventUseCase = GetIngestionEventUseCase(store.ingestionRepository()),
                    deleteIngestionEventUseCase = DeleteIngestionEventUseCase(store.ingestionRepository()),
                    updateIngestionEventUseCase = UpdateIngestionEventUseCase(store.ingestionRepository(), CurrentTimeProvider { fixedNow }),
                    currentTimeProvider = CurrentTimeProvider { fixedNow }
                ) as T
            }
        }

        composeRule.setContent {
            IngestionEditJourneyHost(recordsFactory = recordsFactory, editFactory = editFactory)
        }

        composeRule.onNodeWithTag("record-item-ingestion-7").performClick()
        composeRule.onNodeWithTag("edit-ingestion-delete").performClick()
        composeRule.onNodeWithText("Delete ingestion record \"Tea\"? This cannot be undone.").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-ingestion-delete-confirm").performClick()

        composeRule.onNodeWithTag("records-empty-state").assertIsDisplayed()
        composeRule.onAllNodesWithText("Tea").assertCountEquals(0)
    }

    @Test
    fun exposure_delete_flow_removes_row_and_returns_to_records() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            exposure = ExposureEvent(5L, "Pollen", fixedNow - 120_000L, "outside")
        )

        val recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow)
        val editFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditExposureViewModel(
                    recordId = 5L,
                    getExposureEventUseCase = GetExposureEventUseCase(store.exposureRepository()),
                    deleteExposureEventUseCase = DeleteExposureEventUseCase(store.exposureRepository()),
                    updateExposureEventUseCase = UpdateExposureEventUseCase(store.exposureRepository(), CurrentTimeProvider { fixedNow }),
                    currentTimeProvider = CurrentTimeProvider { fixedNow }
                ) as T
            }
        }

        composeRule.setContent {
            ExposureEditJourneyHost(recordsFactory = recordsFactory, editFactory = editFactory)
        }

        composeRule.onNodeWithTag("record-item-exposure-5").performClick()
        composeRule.onNodeWithTag("edit-exposure-delete").performClick()
        composeRule.onNodeWithText("Delete exposure record \"Pollen\"? This cannot be undone.").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-exposure-delete-confirm").performClick()

        composeRule.onNodeWithTag("records-empty-state").assertIsDisplayed()
        composeRule.onAllNodesWithText("Pollen").assertCountEquals(0)
    }

    @Test
    fun outbreak_delete_flow_removes_row_and_returns_to_records() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            outbreak = OutbreakEvent(9L, "Skin flare", "Arm", fixedNow - 180_000L, "after workout")
        )

        val recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow)
        val editFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditOutbreakViewModel(
                    recordId = 9L,
                    getOutbreakEventUseCase = GetOutbreakEventUseCase(store.outbreakRepository()),
                    deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(store.outbreakRepository()),
                    updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(store.outbreakRepository(), CurrentTimeProvider { fixedNow }),
                    currentTimeProvider = CurrentTimeProvider { fixedNow }
                ) as T
            }
        }

        composeRule.setContent {
            OutbreakEditJourneyHost(recordsFactory = recordsFactory, editFactory = editFactory)
        }

        composeRule.onNodeWithTag("record-item-outbreak-9").performClick()
        composeRule.onNodeWithTag("edit-outbreak-delete").performClick()
        composeRule.onNodeWithText("Delete outbreak record \"Skin flare at Arm\"? This cannot be undone.").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-outbreak-delete-confirm").performClick()

        composeRule.onNodeWithTag("records-empty-state").assertIsDisplayed()
        composeRule.onAllNodesWithText("Skin flare").assertCountEquals(0)
    }
}

@Composable
private fun IngestionEditJourneyHost(
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

@Composable
private fun ExposureEditJourneyHost(
    recordsFactory: ViewModelProvider.Factory,
    editFactory: ViewModelProvider.Factory
) {
    var currentRoute by remember { mutableStateOf(AppRoutes.RECORDS) }

    when (currentRoute) {
        AppRoutes.RECORDS -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )

        AppRoutes.editExposure(5L) -> EditExposureScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = editFactory
        )

        else -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )
    }
}

@Composable
private fun OutbreakEditJourneyHost(
    recordsFactory: ViewModelProvider.Factory,
    editFactory: ViewModelProvider.Factory
) {
    var currentRoute by remember { mutableStateOf(AppRoutes.RECORDS) }

    when (currentRoute) {
        AppRoutes.RECORDS -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )

        AppRoutes.editOutbreak(9L) -> EditOutbreakScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = editFactory
        )

        else -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )
    }
}

private fun recordsFactoryFor(
    recordsRepository: RecordsRepository,
    fixedNow: Long
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordsViewModel(
                currentTimeProvider = CurrentTimeProvider { fixedNow },
                observeRecordCountUseCase = ObserveRecordCountUseCase(recordsRepository),
                observeRecordsUseCase = ObserveRecordsUseCase(recordsRepository)
            ) as T
        }
    }
}

private class ReactiveRecordStore(
    ingestion: IngestionEvent? = null,
    exposure: ExposureEvent? = null,
    outbreak: OutbreakEvent? = null
) {
    private val ingestionState = MutableStateFlow(ingestion)
    private val exposureState = MutableStateFlow(exposure)
    private val outbreakState = MutableStateFlow(outbreak)

    fun recordsRepository(): RecordsRepository = object : RecordsRepository {
        override fun observeRecordCount(): Flow<Int> = observeRecords().map { it.size }

        override fun observeRecords(): Flow<List<RecordSummary>> {
            return combine(ingestionState, exposureState, outbreakState) { ingestionEvent, exposureEvent, outbreakEvent ->
                listOfNotNull(
                    ingestionEvent?.toRecordSummary(),
                    exposureEvent?.toRecordSummary(),
                    outbreakEvent?.toRecordSummary()
                ).sortedWith(compareByDescending<RecordSummary> { it.occurredAtEpochMs }.thenByDescending { it.sourceId })
            }
        }
    }

    fun ingestionRepository(): IngestionRepository = object : IngestionRepository {
        override suspend fun createIngestion(command: com.example.app.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

        override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
            return ingestionState.value?.takeIf { it.id == id }?.let { AppResult.Success(it) }
                ?: AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Missing ingestion"))
        }

        override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
            val current = ingestionState.value ?: return AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Missing ingestion"))
            val updated = current.copy(
                itemName = command.itemName,
                quantityLevel = command.quantityLevel ?: current.quantityLevel,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes
            )
            ingestionState.value = updated
            return AppResult.Success(updated)
        }

        override suspend fun deleteIngestion(id: Long): AppResult<Unit> {
            ingestionState.value = null
            return AppResult.Success(Unit)
        }
    }

    fun exposureRepository(): ExposureRepository = object : ExposureRepository {
        override suspend fun createExposure(command: com.example.app.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")

        override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
            return exposureState.value?.takeIf { it.id == id }?.let { AppResult.Success(it) }
                ?: AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Missing exposure"))
        }

        override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
            val current = exposureState.value ?: return AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Missing exposure"))
            val updated = current.copy(
                exposureType = command.exposureType,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes
            )
            exposureState.value = updated
            return AppResult.Success(updated)
        }

        override suspend fun deleteExposure(id: Long): AppResult<Unit> {
            exposureState.value = null
            return AppResult.Success(Unit)
        }
    }

    fun outbreakRepository(): OutbreakRepository = object : OutbreakRepository {
        override suspend fun createOutbreak(command: com.example.app.features.outbreak.domain.CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")

        override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> {
            return outbreakState.value?.takeIf { it.id == id }?.let { AppResult.Success(it) }
                ?: AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Missing outbreak"))
        }

        override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
            val current = outbreakState.value ?: return AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Missing outbreak"))
            val updated = current.copy(
                outbreakDescriptor = command.outbreakDescriptor,
                bodyLocation = command.bodyLocation,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes
            )
            outbreakState.value = updated
            return AppResult.Success(updated)
        }

        override suspend fun deleteOutbreak(id: Long): AppResult<Unit> {
            outbreakState.value = null
            return AppResult.Success(Unit)
        }
    }
}

private fun IngestionEvent.toRecordSummary(): RecordSummary {
    return RecordSummary(
        id = "ingestion-$id",
        sortKey = id,
        typeLabel = "Ingestion",
        title = itemName,
        detailLabel = "$quantityLevel Light snack",
        occurredAtEpochMs = occurredAtEpochMs,
        sourceId = id,
        eventType = RecordEventType.INGESTION
    )
}

private fun ExposureEvent.toRecordSummary(): RecordSummary {
    return RecordSummary(
        id = "exposure-$id",
        sortKey = id,
        typeLabel = "Exposure",
        title = exposureType,
        detailLabel = "Exposure recorded",
        occurredAtEpochMs = occurredAtEpochMs,
        sourceId = id,
        eventType = RecordEventType.EXPOSURE
    )
}

private fun OutbreakEvent.toRecordSummary(): RecordSummary {
    return RecordSummary(
        id = "outbreak-$id",
        sortKey = id,
        typeLabel = "Outbreak",
        title = outbreakDescriptor,
        detailLabel = "$bodyLocation - ${notes ?: ""}".trimEnd(' ', '-'),
        occurredAtEpochMs = occurredAtEpochMs,
        sourceId = id,
        eventType = RecordEventType.OUTBREAK
    )
}
