package com.example.app.core.di

import android.content.Context
import androidx.room.Room
import com.example.app.core.database.AppDatabase
import com.example.app.core.database.HomeDao
import com.example.app.core.database.migrations.Migration_1_2
import com.example.app.core.database.migrations.Migration_2_3
import com.example.app.core.database.migrations.Migration_3_4
import com.example.app.core.database.migrations.Migration_4_5
import com.example.app.features.exposure.data.RoomExposureRepository
import com.example.app.features.exposure.domain.CreateExposureDefaultsUseCase
import com.example.app.features.exposure.domain.CreateExposureEventUseCase
import com.example.app.features.exposure.domain.DeleteExposureEventUseCase
import com.example.app.features.exposure.domain.GetExposureEventUseCase
import com.example.app.features.exposure.domain.UpdateExposureEventUseCase
import com.example.app.features.exposure.ui.CreateExposureViewModelFactory
import com.example.app.features.exposure.ui.EditExposureViewModelFactory
import com.example.app.features.ingestion.data.RoomIngestionRepository
import com.example.app.features.ingestion.domain.CreateIngestionDefaultsUseCase
import com.example.app.features.ingestion.domain.CreateIngestionEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.DeleteIngestionEventUseCase
import com.example.app.features.ingestion.domain.GetIngestionEventUseCase
import com.example.app.features.ingestion.domain.UpdateIngestionEventUseCase
import com.example.app.features.ingestion.ui.CreateIngestionViewModelFactory
import com.example.app.features.ingestion.ui.EditIngestionViewModelFactory
import com.example.app.features.home.data.HomeRepositoryImpl
import com.example.app.features.home.domain.GetHomeMessageUseCase
import com.example.app.features.home.domain.HomeRepository
import com.example.app.features.outbreak.data.RoomOutbreakRepository
import com.example.app.features.outbreak.domain.CreateOutbreakDefaultsUseCase
import com.example.app.features.outbreak.domain.CreateOutbreakEventUseCase
import com.example.app.features.outbreak.domain.DeleteOutbreakEventUseCase
import com.example.app.features.outbreak.domain.GetOutbreakEventUseCase
import com.example.app.features.outbreak.domain.UpdateOutbreakEventUseCase
import com.example.app.features.outbreak.ui.CreateOutbreakViewModelFactory
import com.example.app.features.outbreak.ui.EditOutbreakViewModelFactory
import com.example.app.features.records.data.RoomRecordsRepository
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.ObserveRecordsUseCase
import com.example.app.features.records.domain.RecordsRepository
import com.example.app.features.records.ui.RecordsViewModelFactory
import kotlinx.coroutines.runBlocking

/**
 * Application composition root. Core event flows are wired only to Room-backed repositories.
 */
class AppContainer(appContext: Context) {
    private val systemCurrentTimeProvider = CurrentTimeProvider { System.currentTimeMillis() }
    private val appDatabase: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "app_database"
    ).addMigrations(Migration_1_2, Migration_2_3, Migration_3_4, Migration_4_5).build()

    private val homeDao: HomeDao = appDatabase.homeDao()
    private val ingestionEventDao = appDatabase.ingestionEventDao()
    private val exposureEventDao = appDatabase.exposureEventDao()
    private val outbreakEventDao = appDatabase.outbreakEventDao()
    private val homeRepository: HomeRepository = HomeRepositoryImpl(homeDao)
    private val getHomeMessageUseCase = GetHomeMessageUseCase(homeRepository)
    private val recordsRepository: RecordsRepository = RoomRecordsRepository(ingestionEventDao, exposureEventDao, outbreakEventDao)
    private val ingestionRepository = RoomIngestionRepository(ingestionEventDao, systemCurrentTimeProvider)
    private val exposureRepository = RoomExposureRepository(exposureEventDao, systemCurrentTimeProvider)
    private val outbreakRepository = RoomOutbreakRepository(outbreakEventDao, systemCurrentTimeProvider)
    private val createIngestionDefaultsUseCase = CreateIngestionDefaultsUseCase(systemCurrentTimeProvider)
    private val createIngestionEventUseCase = CreateIngestionEventUseCase(
        repository = ingestionRepository,
        currentTimeProvider = systemCurrentTimeProvider
    )
    private val getIngestionEventUseCase = GetIngestionEventUseCase(ingestionRepository)
    private val deleteIngestionEventUseCase = DeleteIngestionEventUseCase(ingestionRepository)
    private val updateIngestionEventUseCase = UpdateIngestionEventUseCase(ingestionRepository, systemCurrentTimeProvider)
    private val createExposureDefaultsUseCase = CreateExposureDefaultsUseCase(systemCurrentTimeProvider)
    private val createExposureEventUseCase = CreateExposureEventUseCase(
        repository = exposureRepository,
        currentTimeProvider = systemCurrentTimeProvider
    )
    private val getExposureEventUseCase = GetExposureEventUseCase(exposureRepository)
    private val deleteExposureEventUseCase = DeleteExposureEventUseCase(exposureRepository)
    private val updateExposureEventUseCase = UpdateExposureEventUseCase(exposureRepository, systemCurrentTimeProvider)
    private val createOutbreakDefaultsUseCase = CreateOutbreakDefaultsUseCase(systemCurrentTimeProvider)
    private val createOutbreakEventUseCase = CreateOutbreakEventUseCase(
        repository = outbreakRepository,
        currentTimeProvider = systemCurrentTimeProvider
    )
    private val getOutbreakEventUseCase = GetOutbreakEventUseCase(outbreakRepository)
    private val deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(outbreakRepository)
    private val updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(outbreakRepository, systemCurrentTimeProvider)
    private val observeRecordCountUseCase = ObserveRecordCountUseCase(recordsRepository)
    private val observeRecordsUseCase = ObserveRecordsUseCase(recordsRepository)

    val recordsViewModelFactory: RecordsViewModelFactory =
        RecordsViewModelFactory(systemCurrentTimeProvider, observeRecordCountUseCase, observeRecordsUseCase)
    val createIngestionViewModelFactory: CreateIngestionViewModelFactory =
        CreateIngestionViewModelFactory(createIngestionDefaultsUseCase, createIngestionEventUseCase)
    val createExposureViewModelFactory: CreateExposureViewModelFactory =
        CreateExposureViewModelFactory(createExposureDefaultsUseCase, createExposureEventUseCase)
    val createOutbreakViewModelFactory: CreateOutbreakViewModelFactory =
        CreateOutbreakViewModelFactory(createOutbreakDefaultsUseCase, createOutbreakEventUseCase)

    fun createEditIngestionViewModelFactory(recordId: Long): EditIngestionViewModelFactory {
        return EditIngestionViewModelFactory(
            recordId,
            getIngestionEventUseCase,
            deleteIngestionEventUseCase,
            updateIngestionEventUseCase,
            systemCurrentTimeProvider
        )
    }

    fun createEditExposureViewModelFactory(recordId: Long): EditExposureViewModelFactory {
        return EditExposureViewModelFactory(
            recordId,
            getExposureEventUseCase,
            deleteExposureEventUseCase,
            updateExposureEventUseCase,
            systemCurrentTimeProvider
        )
    }

    fun createEditOutbreakViewModelFactory(recordId: Long): EditOutbreakViewModelFactory {
        return EditOutbreakViewModelFactory(
            recordId,
            getOutbreakEventUseCase,
            deleteOutbreakEventUseCase,
            updateOutbreakEventUseCase,
            systemCurrentTimeProvider
        )
    }

    fun clearAllDataForTesting() {
        runBlocking {
            appDatabase.clearAllTables()
        }
    }
}
