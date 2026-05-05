package io.github.mateovich.allergotracker.features.records.ui

import android.view.View
import android.widget.DatePicker
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.features.exposure.domain.DeleteExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.ExposureEvent
import io.github.mateovich.allergotracker.features.exposure.domain.ExposureRepository
import io.github.mateovich.allergotracker.features.exposure.domain.GetExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.UpdateExposureEventCommand
import io.github.mateovich.allergotracker.features.exposure.domain.UpdateExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.ui.EditExposureScreen
import io.github.mateovich.allergotracker.features.exposure.ui.EditExposureViewModel
import io.github.mateovich.allergotracker.features.compounds.domain.ObserveReusableCompoundsUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompound
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompoundRepository
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionDefaultsUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.DeleteIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.ingestion.domain.GetIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionEvent
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionRepository
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRepository
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventCommand
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.ui.CreateIngestionScreen
import io.github.mateovich.allergotracker.features.ingestion.ui.CreateIngestionViewModel
import io.github.mateovich.allergotracker.features.ingestion.ui.EditIngestionScreen
import io.github.mateovich.allergotracker.features.ingestion.ui.EditIngestionViewModel
import io.github.mateovich.allergotracker.features.outbreak.domain.DeleteOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.GetOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.OutbreakEvent
import io.github.mateovich.allergotracker.features.outbreak.domain.OutbreakRepository
import io.github.mateovich.allergotracker.features.outbreak.domain.UpdateOutbreakEventCommand
import io.github.mateovich.allergotracker.features.outbreak.domain.UpdateOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.ui.EditOutbreakScreen
import io.github.mateovich.allergotracker.features.outbreak.ui.EditOutbreakViewModel
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordDetailUseCase
import io.github.mateovich.allergotracker.features.records.domain.RecordDetail
import io.github.mateovich.allergotracker.features.records.domain.RecordDetailSnapshot
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordCountUseCase
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordsUseCase
import io.github.mateovich.allergotracker.features.records.domain.RecordEventType
import io.github.mateovich.allergotracker.features.records.domain.RecordSummary
import io.github.mateovich.allergotracker.features.records.domain.RecordSummaryComparator
import io.github.mateovich.allergotracker.features.records.domain.RecordsFilter
import io.github.mateovich.allergotracker.features.records.domain.RecordsRepository
import io.github.mateovich.allergotracker.features.records.domain.recordIntensityLabel
import io.github.mateovich.allergotracker.features.records.ui.RecordDetailScreen
import io.github.mateovich.allergotracker.features.records.ui.RecordDetailViewModel
import io.github.mateovich.allergotracker.features.records.ui.RecordDetailViewModelFactory
import io.github.mateovich.allergotracker.navigation.AppRoutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clicking_record_row_navigates_to_matching_detail_route() {
        val repository = object : RecordsRepository {
            override fun observeRecordCount() = flowOf(1)

            override fun observeRecords(filters: Flow<RecordsFilter>) = flowOf(
                listOf(
                    RecordSummary(
                        id = "ingestion-7",
                        sortKey = 7L,
                        typeLabel = "Ingestion",
                        title = "Tea",
                        detailLabel = "2 Light snack",
                        occurredAtEpochMs = 1_700_000_000_000L,
                        createdAtEpochMs = 1_700_000_000_000L,
                        sourceId = 7L,
                        eventType = RecordEventType.INGESTION,
                        intensityLevel = 2
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

        assertEquals(AppRoutes.recordDetail(RecordEventType.INGESTION, 7L), navigatedRoute)
    }

    @Test
    fun records_detail_flow_preserves_active_filters_after_back_navigation() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            ingestion = IngestionEvent(7L, "Tea", 2, fixedNow - 60_000L, "with honey"),
            exposure = ExposureEvent(5L, "Pollen", fixedNow - 120_000L, "outside")
        )

        composeRule.setContent {
            RecordsDetailJourneyHost(
                recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow),
                detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow)
            )
        }

        composeRule.onNodeWithTag("records-filter-action").performClick()
        composeRule.onNodeWithTag("records-filter-type-ingestion").performClick()
        composeRule.onNodeWithTag("records-filter-apply").performClick()
        composeRule.onNodeWithTag("record-item-ingestion-7").performClick()

        composeRule.onNodeWithTag("record-detail-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("record-detail-field-0").assertIsDisplayed()
        composeRule.onNodeWithTag("record-detail-back").performClick()

        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Active filter: Types: Ingestion").assertIsDisplayed()
        composeRule.onNodeWithText("Tea").assertIsDisplayed()
        composeRule.onAllNodesWithText("Pollen").assertCountEquals(0)
    }

    @Test
    fun unavailable_record_detail_shows_clear_message_and_returns_to_records() {
        val fixedNow = 1_700_000_000_000L
        val timeline = MutableStateFlow(
            listOf(
                RecordSummary(
                    id = "ingestion-7",
                    sortKey = 7L,
                    typeLabel = "Ingestion",
                    title = "Tea",
                    detailLabel = "2 Light snack",
                    occurredAtEpochMs = fixedNow,
                    createdAtEpochMs = fixedNow,
                    sourceId = 7L,
                    eventType = RecordEventType.INGESTION,
                    intensityLevel = 2
                )
            )
        )
        val recordsRepository = object : RecordsRepository {
            override fun observeRecordCount() = timeline.map { it.size }

            override fun observeRecords(filters: Flow<RecordsFilter>) = timeline

            override fun observeRecordDetail(recordId: Long, eventType: RecordEventType): Flow<AppResult<RecordDetail>> {
                timeline.value = emptyList()
                return flowOf(AppResult.Error(DomainError.PersistenceError("This record is no longer available.")))
            }
        }

        composeRule.setContent {
            RecordsDetailJourneyHost(
                recordsFactory = recordsFactoryFor(recordsRepository, fixedNow),
                detailFactoryProvider = detailFactoryProviderFor(recordsRepository, fixedNow)
            )
        }

        composeRule.onNodeWithTag("record-item-ingestion-7").performClick()
        composeRule.onNodeWithText("This record is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithTag("record-detail-unavailable-confirm").performClick()

        composeRule.onNodeWithTag("records-empty-state").assertIsDisplayed()
        composeRule.onAllNodesWithText("Tea").assertCountEquals(0)
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
                    createdAtEpochMs = fixedNow - 60_000L,
                    sourceId = 7L,
                    eventType = RecordEventType.INGESTION,
                    intensityLevel = 2
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

            override fun observeRecords(filters: Flow<RecordsFilter>): Flow<List<RecordSummary>> = timeline

            override fun observeRecordDetail(recordId: Long, eventType: RecordEventType): Flow<AppResult<RecordDetail>> {
                return timeline.map { records ->
                    val record = records.firstOrNull { it.sourceId == recordId && it.eventType == eventType }
                    if (record == null) {
                        AppResult.Error(DomainError.PersistenceError("This record is no longer available."))
                    } else {
                        AppResult.Success(
                            RecordDetail.Ingestion(
                                recordId = record.sourceId,
                                occurredAtEpochMs = record.occurredAtEpochMs,
                                itemName = currentEvent.itemName,
                                quantityLabel = record.detailLabel,
                                reusableEntityKind = currentEvent.reusableEntityKind,
                                reusableEntityId = currentEvent.reusableEntityId,
                                notes = currentEvent.notes
                            )
                        )
                    }
                }
            }
        }
        val ingestionRepository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> {
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
                        createdAtEpochMs = fixedNow,
                        sourceId = 7L,
                        eventType = RecordEventType.INGESTION,
                        intensityLevel = 2
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
                detailFactoryProvider = detailFactoryProviderFor(recordsRepository, fixedNow),
                editFactory = editFactory
            )
        }

        composeRule.onNodeWithTag("record-item-ingestion-7").performClick()
        composeRule.onNodeWithTag("record-detail-edit").performClick()
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
            IngestionEditJourneyHost(
                recordsFactory = recordsFactory,
                detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow),
                editFactory = editFactory
            )
        }

        composeRule.onNodeWithTag("record-item-ingestion-7").performClick()
        composeRule.onNodeWithTag("record-detail-delete").performClick()
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
            ExposureEditJourneyHost(
                recordsFactory = recordsFactory,
                detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow),
                editFactory = editFactory
            )
        }

        composeRule.onNodeWithTag("record-item-exposure-5").performClick()
        composeRule.onNodeWithTag("record-detail-delete").performClick()
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
            OutbreakEditJourneyHost(
                recordsFactory = recordsFactory,
                detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow),
                editFactory = editFactory
            )
        }

        composeRule.onNodeWithTag("record-item-outbreak-9").performClick()
        composeRule.onNodeWithTag("record-detail-delete").performClick()
        composeRule.onNodeWithText("Delete outbreak record \"Skin flare at Arm\"? This cannot be undone.").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-outbreak-delete-confirm").performClick()

        composeRule.onNodeWithTag("records-empty-state").assertIsDisplayed()
        composeRule.onAllNodesWithText("Skin flare").assertCountEquals(0)
    }

    @Test
    fun ingestion_edit_form_shows_shared_offline_notice() {
        val fixedNow = 1_700_000_000_000L

        assertEditFormShowsOfflineNotice(
            composeRule = composeRule,
            host = {
                val store = ReactiveRecordStore(
                    ingestion = IngestionEvent(7L, "Tea", 2, fixedNow - 60_000L, "with honey")
                )
                IngestionEditJourneyHost(
                    recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow),
                    detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow),
                    editFactory = object : ViewModelProvider.Factory {
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
                )
            },
            recordTag = "record-item-ingestion-7",
            formTag = "edit-ingestion-form-screen"
        )
    }

    @Test
    fun exposure_edit_form_shows_shared_offline_notice() {
        val fixedNow = 1_700_000_000_000L

        assertEditFormShowsOfflineNotice(
            composeRule = composeRule,
            host = {
                val store = ReactiveRecordStore(
                    exposure = ExposureEvent(5L, "Pollen", fixedNow - 120_000L, "outside")
                )
                ExposureEditJourneyHost(
                    recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow),
                    detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow),
                    editFactory = object : ViewModelProvider.Factory {
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
                )
            },
            recordTag = "record-item-exposure-5",
            formTag = "edit-exposure-form-screen"
        )
    }

    @Test
    fun exposure_edit_form_keeps_picker_controls_without_redundant_datetime_input() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            exposure = ExposureEvent(5L, "Pollen", fixedNow - 120_000L, "outside")
        )

        composeRule.setContent {
            ExposureEditJourneyHost(
                recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow),
                detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow),
                editFactory = object : ViewModelProvider.Factory {
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
            )
        }

        composeRule.onNodeWithTag("record-item-exposure-5").performClick()
        composeRule.onNodeWithTag("record-detail-edit").performClick()
        composeRule.onNodeWithTag("edit-exposure-occurred-at-date-picker").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("edit-exposure-occurred-at-label").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-exposure-occurred-at-time-picker").assertIsDisplayed()
        composeRule.onAllNodesWithTag("edit-exposure-occurred-at-input").assertCountEquals(0)
    }

    @Test
    fun exposure_delete_cancel_from_detail_stays_closed_until_explicit_retry() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            exposure = ExposureEvent(5L, "Pollen", fixedNow - 120_000L, "outside")
        )

        composeRule.setContent {
            ExposureEditJourneyHost(
                recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow),
                detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow),
                editFactory = object : ViewModelProvider.Factory {
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
            )
        }

        composeRule.onNodeWithTag("record-item-exposure-5").performClick()
        composeRule.onNodeWithTag("record-detail-delete").performClick()
        composeRule.onNodeWithTag("edit-exposure-delete-cancel").performClick()

        composeRule.onNodeWithTag("edit-exposure-form-screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("edit-exposure-delete-confirm").assertCountEquals(0)

        composeRule.onNodeWithTag("edit-exposure-delete").performClick()
        composeRule.onNodeWithTag("edit-exposure-delete-confirm").assertIsDisplayed()
    }

    @Test
    fun ingestion_delete_cancel_from_detail_stays_closed_until_explicit_retry() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            ingestion = IngestionEvent(7L, "Tea", 2, fixedNow - 60_000L, "with honey")
        )

        composeRule.setContent {
            IngestionEditJourneyHost(
                recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow),
                detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow),
                editFactory = object : ViewModelProvider.Factory {
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
            )
        }

        composeRule.onNodeWithTag("record-item-ingestion-7").performClick()
        composeRule.onNodeWithTag("record-detail-delete").performClick()
        composeRule.onNodeWithTag("edit-ingestion-delete-cancel").performClick()

        composeRule.onNodeWithTag("edit-ingestion-form-screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("edit-ingestion-delete-confirm").assertCountEquals(0)

        composeRule.onNodeWithTag("edit-ingestion-delete").performClick()
        composeRule.onNodeWithTag("edit-ingestion-delete-confirm").assertIsDisplayed()
    }

    @Test
    fun outbreak_delete_cancel_from_detail_stays_closed_until_explicit_retry() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            outbreak = OutbreakEvent(9L, "Skin flare", "Arm", fixedNow - 180_000L, "after workout")
        )

        composeRule.setContent {
            OutbreakEditJourneyHost(
                recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow),
                detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow),
                editFactory = object : ViewModelProvider.Factory {
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
            )
        }

        composeRule.onNodeWithTag("record-item-outbreak-9").performClick()
        waitForTag(composeRule, "record-detail-edit")
        composeRule.onNodeWithTag("record-detail-edit").performClick()
        ensureDeleteDialogVisible(
            composeRule = composeRule,
            formTag = "edit-outbreak-form-screen",
            deleteButtonTag = "edit-outbreak-delete",
            dialogActionTag = "edit-outbreak-delete-cancel"
        )
        composeRule.onNodeWithTag("edit-outbreak-delete-cancel").performClick()

        composeRule.onNodeWithTag("edit-outbreak-form-screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("edit-outbreak-delete-confirm").assertCountEquals(0)

        composeRule.onNodeWithTag("edit-outbreak-delete").performClick()
        waitForTag(composeRule, "edit-outbreak-delete-confirm")
        composeRule.onNodeWithTag("edit-outbreak-delete-confirm").assertIsDisplayed()
    }

    @Test
    fun outbreak_edit_form_shows_shared_offline_notice() {
        val fixedNow = 1_700_000_000_000L

        assertEditFormShowsOfflineNotice(
            composeRule = composeRule,
            host = {
                val store = ReactiveRecordStore(
                    outbreak = OutbreakEvent(9L, "Skin flare", "Arm", fixedNow - 180_000L, "after workout")
                )
                OutbreakEditJourneyHost(
                    recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow),
                    detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow),
                    editFactory = object : ViewModelProvider.Factory {
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
                )
            },
            recordTag = "record-item-outbreak-9",
            formTag = "edit-outbreak-form-screen"
        )
    }

    @Test
    fun outbreak_edit_form_keeps_picker_controls_without_redundant_datetime_input() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            outbreak = OutbreakEvent(9L, "Skin flare", "Arm", fixedNow - 180_000L, "after workout")
        )

        composeRule.setContent {
            OutbreakEditJourneyHost(
                recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow),
                detailFactoryProvider = detailFactoryProviderFor(store.detailRepository(), fixedNow),
                editFactory = object : ViewModelProvider.Factory {
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
            )
        }

        composeRule.onNodeWithTag("record-item-outbreak-9").performClick()
        composeRule.onNodeWithTag("record-detail-edit").performClick()
        composeRule.onNodeWithTag("edit-outbreak-occurred-at-date-picker").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("edit-outbreak-occurred-at-label").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-outbreak-occurred-at-time-picker").assertIsDisplayed()
        composeRule.onAllNodesWithTag("edit-outbreak-occurred-at-input").assertCountEquals(0)
    }

    @Test
    fun add_menu_opens_from_fab_and_dismisses_on_outside_tap() {
        val factory = recordsFactoryFor(
            recordsRepository = object : RecordsRepository {
                override fun observeRecordCount() = flowOf(0)

                override fun observeRecords(filters: Flow<RecordsFilter>) = flowOf(emptyList<RecordSummary>())
            },
            fixedNow = 1_700_000_000_000L
        )

        composeRule.setContent {
            RecordsScreen(
                onNavigateToRoute = {},
                viewModelFactory = factory
            )
        }

        composeRule.onNodeWithTag("records-add-fab").performClick()
        composeRule.onNodeWithTag("records-add-menu", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ingestion").assertIsDisplayed()

        composeRule.onNodeWithTag("records-add-menu-scrim").performClick()

        composeRule.onAllNodesWithText("Ingestion").assertCountEquals(0)
    }

    @Test
    fun filter_dialog_applies_combination_and_shows_active_indicators() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            ingestion = IngestionEvent(7L, "Tea", 2, fixedNow - 60_000L, "with honey"),
            exposure = ExposureEvent(5L, "Pollen", fixedNow - 120_000L, "outside")
        )

        composeRule.setContent {
            RecordsScreen(
                onNavigateToRoute = {},
                viewModelFactory = recordsFactoryFor(store.recordsRepository(), fixedNow)
            )
        }

        composeRule.onNodeWithTag("records-filter-action").performClick()
        composeRule.onNodeWithTag("records-filter-type-ingestion").performClick()
        composeRule.onNodeWithTag("records-filter-intensity-2").performClick()
        composeRule.onNodeWithTag("records-filter-apply").performClick()

        composeRule.onNodeWithTag("records-active-filters").assertIsDisplayed()
        composeRule.onNodeWithText("Active filters").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Active filter: Types: Ingestion").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Active filter: Intensity: 2").assertIsDisplayed()
        composeRule.onNodeWithText("Tea").assertIsDisplayed()
        composeRule.onAllNodesWithText("Pollen").assertCountEquals(0)
        composeRule.onNodeWithTag("records-clear-filters").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Clear filters").assertIsDisplayed()
    }

    @Test
    fun filtered_empty_state_offers_clear_action_and_restores_full_list() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            ingestion = IngestionEvent(7L, "Tea", 2, fixedNow - 60_000L, "with honey"),
            exposure = ExposureEvent(5L, "Pollen", fixedNow - 120_000L, "outside")
        )

        composeRule.setContent {
            RecordsScreen(
                onNavigateToRoute = {},
                viewModelFactory = recordsFactoryFor(store.recordsRepository(), fixedNow)
            )
        }

        composeRule.onNodeWithTag("records-filter-action").performClick()
        composeRule.onNodeWithTag("records-filter-intensity-5").performClick()
        composeRule.onNodeWithTag("records-filter-apply").performClick()

        composeRule.onNodeWithTag("records-empty-state").assertIsDisplayed()
        composeRule.onNodeWithText("No records match these filters").assertIsDisplayed()
        composeRule.onNodeWithTag("records-empty-state-action").assertIsDisplayed()
        composeRule.onAllNodesWithText("Clear filters").assertCountEquals(2)
        composeRule.onNodeWithTag("records-empty-state-action").performClick()

        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onNodeWithText("Tea").assertIsDisplayed()
        composeRule.onNodeWithText("Pollen").assertIsDisplayed()
    }

    @Test
    fun filter_dialog_applies_date_range_in_real_ui_flow() {
        val fixedNow = 1_700_000_000_000L
        val zoneId = ZoneId.systemDefault()
        val includedDate = LocalDate.of(2026, 3, 20)
        val store = ReactiveRecordStore(
            ingestion = IngestionEvent(
                7L,
                "Tea",
                2,
                includedDate.atStartOfDay(zoneId).plusHours(9).toInstant().toEpochMilli(),
                "with honey"
            ),
            exposure = ExposureEvent(
                5L,
                "Pollen",
                includedDate.minusDays(1).atStartOfDay(zoneId).plusHours(8).toInstant().toEpochMilli(),
                "outside"
            )
        )

        composeRule.setContent {
            RecordsScreen(
                onNavigateToRoute = {},
                viewModelFactory = recordsFactoryFor(store.recordsRepository(), fixedNow)
            )
        }

        composeRule.onNodeWithTag("records-filter-action").performClick()
        composeRule.onNodeWithTag("records-filter-start-date-picker").performClick()
        setDatePicker(2026, 3, 20)
        composeRule.onNodeWithTag("records-filter-end-date-picker").performClick()
        setDatePicker(2026, 3, 20)
        composeRule.onNodeWithTag("records-filter-apply").performClick()

        composeRule.onNodeWithText("Tea").assertIsDisplayed()
        composeRule.onAllNodesWithText("Pollen").assertCountEquals(0)
        composeRule.onNodeWithText("2026-03-20 to 2026-03-20").assertIsDisplayed()
    }

    @Test
    fun filter_dialog_applies_date_range_selected_with_native_pickers() {
        val fixedNow = 1_700_000_000_000L
        val zoneId = ZoneId.systemDefault()
        val includedDate = LocalDate.of(2026, 3, 20)
        val store = ReactiveRecordStore(
            ingestion = IngestionEvent(
                7L,
                "Tea",
                2,
                includedDate.atStartOfDay(zoneId).plusHours(9).toInstant().toEpochMilli(),
                "with honey"
            ),
            exposure = ExposureEvent(
                5L,
                "Pollen",
                includedDate.minusDays(1).atStartOfDay(zoneId).plusHours(8).toInstant().toEpochMilli(),
                "outside"
            )
        )

        composeRule.setContent {
            RecordsScreen(
                onNavigateToRoute = {},
                viewModelFactory = recordsFactoryFor(store.recordsRepository(), fixedNow)
            )
        }

        composeRule.onNodeWithTag("records-filter-action").performClick()
        composeRule.onNodeWithTag("records-filter-start-date-picker").performClick()
        setDatePicker(2026, 3, 20)
        composeRule.onNodeWithTag("records-filter-end-date-picker").performClick()
        setDatePicker(2026, 3, 20)
        composeRule.onNodeWithTag("records-filter-apply").performClick()

        composeRule.onNodeWithText("Tea").assertIsDisplayed()
        composeRule.onAllNodesWithText("Pollen").assertCountEquals(0)
        composeRule.onNodeWithText("2026-03-20 to 2026-03-20").assertIsDisplayed()
    }

    @Test
    fun ingestion_create_flow_returns_to_records_with_new_row_in_chronological_position() {
        val fixedNow = 1_700_000_000_000L
        val store = ReactiveRecordStore(
            exposure = ExposureEvent(5L, "Pollen", fixedNow - 120_000L, "outside")
        )

        composeRule.setContent {
            CreateIngestionJourneyHost(
                recordsFactory = recordsFactoryFor(store.recordsRepository(), fixedNow),
                createFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return CreateIngestionViewModel(
                            createIngestionDefaultsUseCase = CreateIngestionDefaultsUseCase(CurrentTimeProvider { fixedNow }),
                            createIngestionEventUseCase = CreateIngestionEventUseCase(
                                repository = store.ingestionRepository(),
                                currentTimeProvider = CurrentTimeProvider { fixedNow }
                            ),
                            observeReusableIngredientsUseCase = ObserveReusableIngredientsUseCase(
                                object : ReusableIngredientRepository {
                                    override suspend fun createIngredient(command: io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
                                        error("unused")
                                    }

                                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = emptyFlow()
                                }
                            ),
                            observeReusableCompoundsUseCase = ObserveReusableCompoundsUseCase(
                                object : ReusableCompoundRepository {
                                    override suspend fun createCompound(command: io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundCommand): AppResult<ReusableCompound> {
                                        error("unused")
                                    }

                                    override fun observeCompounds(): Flow<List<ReusableCompound>> = emptyFlow()
                                }
                            ),
                            createReusableIngredientUseCase = CreateReusableIngredientUseCase(
                                object : ReusableIngredientRepository {
                                    override suspend fun createIngredient(command: io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
                                        return AppResult.Success(
                                            ReusableIngredient(
                                                id = 99L,
                                                name = command.name,
                                                notes = command.notes,
                                                createdAtEpochMs = System.currentTimeMillis(),
                                                updatedAtEpochMs = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = emptyFlow()
                                }
                            )
                        ) as T
                    }
                }
            )
        }

        composeRule.onNodeWithTag("records-add-fab").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()
        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("ingestion-reusable-search").performTextReplacement("Tea")
        composeRule.onNodeWithTag("ingestion-quantity-2").performClick()
        composeRule.onNodeWithTag("ingestion-save").performClick()

        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onAllNodesWithText("Tea").assertCountEquals(1)
        composeRule.onAllNodesWithText("Pollen").assertCountEquals(1)
        composeRule.onNodeWithTag("record-item-ingestion-1").assertIsDisplayed()
        composeRule.onNodeWithTag("record-item-exposure-5").assertIsDisplayed()

        assertNodeAppearsAbove("record-item-ingestion-1", "record-item-exposure-5")

        val orderedIds = runBlocking {
            store.recordsRepository().observeRecords(flowOf(RecordsFilter())).first().map { it.id }
        }
        assertEquals(listOf("ingestion-1", "exposure-5"), orderedIds)
    }

    @Test
    fun equal_visible_timestamps_render_last_saved_record_above_older_cross_type_row() {
        val sharedOccurredAt = 1_700_000_000_000L
        val repository = object : RecordsRepository {
            override fun observeRecordCount() = flowOf(2)

            override fun observeRecords(filters: Flow<RecordsFilter>) = flowOf(
                listOf(
                    RecordSummary(
                        id = "ingestion-10",
                        sortKey = 10L,
                        typeLabel = "Ingestion",
                        title = "Tea",
                        detailLabel = "2 Light snack",
                        occurredAtEpochMs = sharedOccurredAt,
                        createdAtEpochMs = 100L,
                        sourceId = 10L,
                        eventType = RecordEventType.INGESTION,
                        intensityLevel = 2
                    ),
                    RecordSummary(
                        id = "exposure-1",
                        sortKey = 1L,
                        typeLabel = "Exposure",
                        title = "Pollen",
                        detailLabel = "Exposure recorded",
                        occurredAtEpochMs = sharedOccurredAt,
                        createdAtEpochMs = 200L,
                        sourceId = 1L,
                        eventType = RecordEventType.EXPOSURE,
                        intensityLevel = null
                    )
                ).sortedWith(RecordSummaryComparator)
            )
        }

        composeRule.setContent {
            RecordsScreen(
                onNavigateToRoute = {},
                viewModelFactory = recordsFactoryFor(repository, fixedNow = sharedOccurredAt)
            )
        }

        composeRule.onNodeWithTag("record-item-exposure-1").assertIsDisplayed()
        composeRule.onNodeWithTag("record-item-ingestion-10").assertIsDisplayed()

        assertNodeAppearsAbove("record-item-exposure-1", "record-item-ingestion-10")
    }

    @Test
    fun empty_records_state_keeps_creation_entrypoint_visible() {
        val factory = recordsFactoryFor(
            recordsRepository = object : RecordsRepository {
                override fun observeRecordCount() = flowOf(0)

                override fun observeRecords(filters: Flow<RecordsFilter>) = flowOf(emptyList<RecordSummary>())
            },
            fixedNow = 1_700_000_000_000L
        )

        composeRule.setContent {
            RecordsScreen(
                onNavigateToRoute = {},
                viewModelFactory = factory
            )
        }

        composeRule.onNodeWithTag("records-empty-state").assertIsDisplayed()
        composeRule.onNodeWithTag("records-add-fab").assertIsDisplayed()
    }
}

private fun assertEditFormShowsOfflineNotice(
    composeRule: AndroidComposeTestRule<*, *>,
    host: @Composable () -> Unit,
    recordTag: String,
    formTag: String
) {
    composeRule.setContent { host() }

    composeRule.onNodeWithTag(recordTag).performClick()
    composeRule.onNodeWithTag("record-detail-edit").performClick()
    composeRule.onNodeWithTag(formTag).assertIsDisplayed()
    composeRule.onNodeWithTag("local-only-notice").assertIsDisplayed()
    composeRule.onNodeWithText("Works offline. Saved only on this device.").assertIsDisplayed()
}

private fun RecordsScreenTest.assertNodeAppearsAbove(upperTag: String, lowerTag: String) {
    val upperTop = composeRule.onNodeWithTag(upperTag).fetchSemanticsNode().boundsInRoot.top
    val lowerTop = composeRule.onNodeWithTag(lowerTag).fetchSemanticsNode().boundsInRoot.top
    assertTrue("Expected $upperTag above $lowerTag but got $upperTop >= $lowerTop", upperTop < lowerTop)
}

@Composable
private fun IngestionEditJourneyHost(
    recordsFactory: ViewModelProvider.Factory,
    detailFactoryProvider: (Long, RecordEventType) -> ViewModelProvider.Factory,
    editFactory: ViewModelProvider.Factory
) {
    var currentRoute by remember { mutableStateOf(AppRoutes.RECORDS) }

    when (currentRoute) {
        AppRoutes.RECORDS -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )

        AppRoutes.recordDetail(RecordEventType.INGESTION, 7L) -> RecordDetailScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = detailFactoryProvider(7L, RecordEventType.INGESTION)
        )

        AppRoutes.editIngestion(7L), AppRoutes.editIngestion(7L, openDelete = true) -> EditIngestionScreen(
            onNavigateToRoute = { currentRoute = it },
            openDeleteOnStart = currentRoute == AppRoutes.editIngestion(7L, openDelete = true),
            viewModelFactory = editFactory
        )

        else -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )
    }
}

@Composable
private fun CreateIngestionJourneyHost(
    recordsFactory: ViewModelProvider.Factory,
    createFactory: ViewModelProvider.Factory
) {
    var currentRoute by remember { mutableStateOf(AppRoutes.RECORDS) }

    when (currentRoute) {
        AppRoutes.RECORDS -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )

        AppRoutes.CREATE_INGESTION -> CreateIngestionScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = createFactory
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
    detailFactoryProvider: (Long, RecordEventType) -> ViewModelProvider.Factory,
    editFactory: ViewModelProvider.Factory
) {
    var currentRoute by remember { mutableStateOf(AppRoutes.RECORDS) }

    when (currentRoute) {
        AppRoutes.RECORDS -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )

        AppRoutes.recordDetail(RecordEventType.EXPOSURE, 5L) -> RecordDetailScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = detailFactoryProvider(5L, RecordEventType.EXPOSURE)
        )

        AppRoutes.editExposure(5L), AppRoutes.editExposure(5L, openDelete = true) -> EditExposureScreen(
            onNavigateToRoute = { currentRoute = it },
            openDeleteOnStart = currentRoute == AppRoutes.editExposure(5L, openDelete = true),
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
    detailFactoryProvider: (Long, RecordEventType) -> ViewModelProvider.Factory,
    editFactory: ViewModelProvider.Factory
) {
    var currentRoute by remember { mutableStateOf(AppRoutes.RECORDS) }

    when (currentRoute) {
        AppRoutes.RECORDS -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )

        AppRoutes.recordDetail(RecordEventType.OUTBREAK, 9L) -> RecordDetailScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = detailFactoryProvider(9L, RecordEventType.OUTBREAK)
        )

        AppRoutes.editOutbreak(9L), AppRoutes.editOutbreak(9L, openDelete = true) -> EditOutbreakScreen(
            onNavigateToRoute = { currentRoute = it },
            openDeleteOnStart = currentRoute == AppRoutes.editOutbreak(9L, openDelete = true),
            viewModelFactory = editFactory
        )

        else -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )
    }
}

private fun waitForTag(
    composeRule: AndroidComposeTestRule<*, *>,
    tag: String
) {
    composeRule.waitUntil(timeoutMillis = 15_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun ensureDeleteDialogVisible(
    composeRule: AndroidComposeTestRule<*, *>,
    formTag: String,
    deleteButtonTag: String,
    dialogActionTag: String
) {
    waitForTag(composeRule, formTag)
    if (composeRule.onAllNodesWithTag(dialogActionTag).fetchSemanticsNodes().isEmpty()) {
        composeRule.onNodeWithTag(deleteButtonTag).performScrollTo().performClick()
        composeRule.waitForIdle()
        waitForTag(composeRule, dialogActionTag)
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

private fun setDatePicker(year: Int, month: Int, day: Int) {
    onView(isAssignableFrom(DatePicker::class.java))
        .perform(setDatePickerValue(year, month, day))
    onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click())
}

private fun setDatePickerValue(year: Int, month: Int, day: Int): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)

        override fun getDescription(): String = "set date picker value"

        override fun perform(uiController: UiController, view: View) {
            (view as DatePicker).updateDate(year, month - 1, day)
            uiController.loopMainThreadUntilIdle()
        }
    }
}

private fun detailFactoryProviderFor(
    recordsRepository: RecordsRepository,
    fixedNow: Long
): (Long, RecordEventType) -> ViewModelProvider.Factory {
    return { recordId, eventType ->
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RecordDetailViewModel(
                    recordId = recordId,
                    eventType = eventType,
                    observeRecordDetailUseCase = ObserveRecordDetailUseCase(recordsRepository)
                ) as T
            }
        }
    }
}

@Composable
private fun RecordsDetailJourneyHost(
    recordsFactory: ViewModelProvider.Factory,
    detailFactoryProvider: (Long, RecordEventType) -> ViewModelProvider.Factory
) {
    var currentRoute by remember { mutableStateOf(AppRoutes.RECORDS) }

    when (currentRoute) {
        AppRoutes.RECORDS -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )

        AppRoutes.recordDetail(RecordEventType.INGESTION, 7L) -> RecordDetailScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = detailFactoryProvider(7L, RecordEventType.INGESTION)
        )

        AppRoutes.recordDetail(RecordEventType.EXPOSURE, 5L) -> RecordDetailScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = detailFactoryProvider(5L, RecordEventType.EXPOSURE)
        )

        AppRoutes.recordDetail(RecordEventType.OUTBREAK, 9L) -> RecordDetailScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = detailFactoryProvider(9L, RecordEventType.OUTBREAK)
        )

        else -> RecordsScreen(
            onNavigateToRoute = { currentRoute = it },
            viewModelFactory = recordsFactory
        )
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
        override fun observeRecordCount(): Flow<Int> = observeRecords(flowOf(RecordsFilter())).map { it.size }

        override fun observeRecords(filters: Flow<RecordsFilter>): Flow<List<RecordSummary>> {
            return combine(
                combine(ingestionState, exposureState, outbreakState) { ingestionEvent, exposureEvent, outbreakEvent ->
                    listOfNotNull(
                        ingestionEvent?.toRecordSummary(),
                        exposureEvent?.toRecordSummary(),
                        outbreakEvent?.toRecordSummary()
                    ).sortedWith(RecordSummaryComparator)
                },
                filters
            ) { records, activeFilters ->
                records.filter { record ->
                    (activeFilters.eventTypes.isEmpty() || record.eventType in activeFilters.eventTypes) &&
                        (activeFilters.startDate == null || !Instant.ofEpochMilli(record.occurredAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate().isBefore(activeFilters.startDate)) &&
                        (activeFilters.endDate == null || !Instant.ofEpochMilli(record.occurredAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate().isAfter(activeFilters.endDate)) &&
                        (activeFilters.intensityLevels.isEmpty() || activeFilters.intensityLevels.any { it.level == record.intensityLevel })
                }
            }
        }

        override fun observeRecordDetail(recordId: Long, eventType: RecordEventType): Flow<AppResult<RecordDetail>> {
            return combine(ingestionState, exposureState, outbreakState) { ingestionEvent, exposureEvent, outbreakEvent ->
                when (eventType) {
                    RecordEventType.INGESTION -> ingestionEvent?.takeIf { it.id == recordId }?.let {
                        AppResult.Success(
                            RecordDetail.Ingestion(
                                recordId = it.id,
                                occurredAtEpochMs = it.occurredAtEpochMs,
                                itemName = it.itemName,
                                quantityLabel = "${it.quantityLevel} Light snack",
                                reusableEntityKind = it.reusableEntityKind,
                                reusableEntityId = it.reusableEntityId,
                                notes = it.notes
                            )
                        )
                    }

                    RecordEventType.EXPOSURE -> exposureEvent?.takeIf { it.id == recordId }?.let {
                        AppResult.Success(
                            RecordDetail.Exposure(
                                recordId = it.id,
                                occurredAtEpochMs = it.occurredAtEpochMs,
                                exposureType = it.exposureType,
                                intensityLabel = recordIntensityLabel(it.intensityLevel),
                                notes = it.notes
                            )
                        )
                    }

                    RecordEventType.OUTBREAK -> outbreakEvent?.takeIf { it.id == recordId }?.let {
                        AppResult.Success(
                            RecordDetail.Outbreak(
                                recordId = it.id,
                                occurredAtEpochMs = it.occurredAtEpochMs,
                                outbreakDescriptor = it.outbreakDescriptor,
                                bodyLocation = it.bodyLocation,
                                intensityLabel = recordIntensityLabel(it.intensityLevel),
                                notes = it.notes
                            )
                        )
                    }
                } ?: AppResult.Error(DomainError.PersistenceError("This record is no longer available."))
            }
        }
    }

    fun detailRepository(): RecordsRepository = recordsRepository()

    fun ingestionRepository(): IngestionRepository = object : IngestionRepository {
        override suspend fun createIngestion(command: CreateIngestionEventCommand): AppResult<IngestionEvent> {
            val created = IngestionEvent(
                id = nextIngestionId(),
                itemName = command.itemName.trim(),
                quantityLevel = command.quantityLevel ?: return AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.ValidationError("Choose a quantity.")),
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes?.trim()?.takeIf { it.isNotEmpty() }
            )
            ingestionState.value = created
            return AppResult.Success(created)
        }

        override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
            return ingestionState.value?.takeIf { it.id == id }?.let { AppResult.Success(it) }
                ?: AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Missing ingestion"))
        }

        override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
            val current = ingestionState.value ?: return AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Missing ingestion"))
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
        override suspend fun createExposure(command: io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")

        override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
            return exposureState.value?.takeIf { it.id == id }?.let { AppResult.Success(it) }
                ?: AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Missing exposure"))
        }

        override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
            val current = exposureState.value ?: return AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Missing exposure"))
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
        override suspend fun createOutbreak(command: io.github.mateovich.allergotracker.features.outbreak.domain.CreateOutbreakEventCommand): AppResult<OutbreakEvent> = error("unused")

        override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> {
            return outbreakState.value?.takeIf { it.id == id }?.let { AppResult.Success(it) }
                ?: AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Missing outbreak"))
        }

        override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
            val current = outbreakState.value ?: return AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Missing outbreak"))
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

    private fun nextIngestionId(): Long {
        return (ingestionState.value?.id ?: 0L) + 1L
    }
}

private fun IngestionEvent.toRecordSummary(): RecordSummary {
    val quantityLabel = IngestionQuantityOption.fromLevel(quantityLevel)?.label.orEmpty()
    return RecordSummary(
        id = "ingestion-$id",
        sortKey = id,
        typeLabel = "Ingestion",
        title = itemName,
        detailLabel = quantityLabel,
        occurredAtEpochMs = occurredAtEpochMs,
        createdAtEpochMs = occurredAtEpochMs,
        sourceId = id,
        eventType = RecordEventType.INGESTION,
        intensityLevel = quantityLevel,
        detailSnapshot = RecordDetailSnapshot.Ingestion(
            quantityLabel = quantityLabel,
            reusableEntityKind = reusableEntityKind,
            reusableEntityId = reusableEntityId,
            notes = notes
        )
    )
}

private fun ExposureEvent.toRecordSummary(): RecordSummary {
    return RecordSummary(
        id = "exposure-$id",
        sortKey = id,
        typeLabel = "Exposure",
        title = exposureType,
        detailLabel = listOfNotNull(
            recordIntensityLabel(intensityLevel),
            notes?.takeIf { it.isNotBlank() }
        ).joinToString(" - ").ifBlank { "Exposure recorded" },
        occurredAtEpochMs = occurredAtEpochMs,
        createdAtEpochMs = occurredAtEpochMs,
        sourceId = id,
        eventType = RecordEventType.EXPOSURE,
        intensityLevel = intensityLevel,
        detailSnapshot = RecordDetailSnapshot.Exposure(
            intensityLabel = recordIntensityLabel(intensityLevel),
            notes = notes
        )
    )
}

private fun OutbreakEvent.toRecordSummary(): RecordSummary {
    return RecordSummary(
        id = "outbreak-$id",
        sortKey = id,
        typeLabel = "Outbreak",
        title = outbreakDescriptor,
        detailLabel = listOfNotNull(
            recordIntensityLabel(intensityLevel),
            bodyLocation.takeIf { it.isNotBlank() },
            notes?.takeIf { it.isNotBlank() }
        ).joinToString(" - "),
        occurredAtEpochMs = occurredAtEpochMs,
        createdAtEpochMs = occurredAtEpochMs,
        sourceId = id,
        eventType = RecordEventType.OUTBREAK,
        intensityLevel = intensityLevel,
        detailSnapshot = RecordDetailSnapshot.Outbreak(
            bodyLocation = bodyLocation,
            intensityLabel = recordIntensityLabel(intensityLevel),
            notes = notes
        )
    )
}
