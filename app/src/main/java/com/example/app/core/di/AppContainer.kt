package com.example.app.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.example.app.core.database.AppDatabase
import com.example.app.core.database.HomeDao
import com.example.app.core.database.migrations.Migration_1_2
import com.example.app.core.database.migrations.Migration_2_3
import com.example.app.core.database.migrations.Migration_3_4
import com.example.app.core.database.migrations.Migration_4_5
import com.example.app.core.database.migrations.Migration_5_6
import com.example.app.core.database.migrations.Migration_6_7
import com.example.app.core.database.migrations.Migration_7_8
import com.example.app.core.database.migrations.Migration_8_9
import com.example.app.core.database.migrations.Migration_9_10
import com.example.app.core.database.migrations.Migration_10_11
import com.example.app.core.database.migrations.Migration_11_12
import com.example.app.core.database.migrations.Migration_12_13
import com.example.app.features.compounds.data.RoomReusableCompoundRepository
import com.example.app.features.compounds.domain.CreateReusableCompoundUseCase
import com.example.app.features.compounds.domain.GetReusableCompoundRetirementImpactUseCase
import com.example.app.features.compounds.domain.GetReusableCompoundUseCase
import com.example.app.features.compounds.domain.ObserveReusableCompoundsUseCase
import com.example.app.features.compounds.domain.RetireReusableCompoundUseCase
import com.example.app.features.compounds.domain.UpdateReusableCompoundUseCase
import com.example.app.features.compounds.ui.CreateCompoundViewModelFactory
import com.example.app.features.compounds.ui.EditCompoundViewModelFactory
import com.example.app.features.eventcatalog.data.RoomReusableEventCatalogRepository
import com.example.app.features.eventcatalog.domain.GetReusableEventCatalogEntryUseCase
import com.example.app.features.eventcatalog.domain.GetReusableEventCatalogRetirementImpactUseCase
import com.example.app.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.RetireReusableEventCatalogUseCase
import com.example.app.features.eventcatalog.domain.UpdateReusableEventCatalogUseCase
import com.example.app.features.eventcatalog.ui.EditReusableEventCatalogViewModelFactory
import com.example.app.features.exposure.data.RoomExposureRepository
import com.example.app.features.exposure.domain.CreateExposureDefaultsUseCase
import com.example.app.features.exposure.domain.CreateExposureEventUseCase
import com.example.app.features.exposure.domain.DeleteExposureEventUseCase
import com.example.app.features.exposure.domain.GetExposureEventUseCase
import com.example.app.features.exposure.domain.UpdateExposureEventUseCase
import com.example.app.features.exposure.ui.CreateExposureViewModelFactory
import com.example.app.features.exposure.ui.EditExposureViewModelFactory
import com.example.app.features.ingredients.data.RoomReusableIngredientRepository
import com.example.app.features.ingredients.domain.CreateReusableIngredientUseCase
import com.example.app.features.ingredients.domain.GetReusableIngredientRetirementImpactUseCase
import com.example.app.features.ingredients.domain.GetReusableIngredientUseCase
import com.example.app.features.ingredients.domain.ObserveReusableIngredientsUseCase
import com.example.app.features.ingredients.domain.RetireReusableIngredientUseCase
import com.example.app.features.ingredients.domain.UpdateReusableIngredientUseCase
import com.example.app.features.ingredients.ui.CreateIngredientViewModelFactory
import com.example.app.features.ingredients.ui.EditIngredientViewModelFactory
import com.example.app.features.ingestion.data.RoomIngestionRepository
import com.example.app.features.ingestion.domain.CreateIngestionDefaultsUseCase
import com.example.app.features.ingestion.domain.CreateIngestionEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.DeleteIngestionEventUseCase
import com.example.app.features.ingestion.domain.GetIngestionEventUseCase
import com.example.app.features.ingestion.domain.ReusableEntityKind
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
import com.example.app.features.records.domain.ObserveRecordDetailUseCase
import com.example.app.features.records.domain.ObserveRecordsUseCase
import com.example.app.features.records.domain.RecordsRepository
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.ui.RecordDetailViewModelFactory
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
    ).addMigrations(Migration_1_2, Migration_2_3, Migration_3_4, Migration_4_5, Migration_5_6, Migration_6_7, Migration_7_8, Migration_8_9, Migration_9_10, Migration_10_11, Migration_11_12, Migration_12_13).build()

    private val homeDao: HomeDao = appDatabase.homeDao()
    private val ingestionEventDao = appDatabase.ingestionEventDao()
    private val exposureEventDao = appDatabase.exposureEventDao()
    private val outbreakEventDao = appDatabase.outbreakEventDao()
    private val reusableEventCatalogDao = appDatabase.reusableEventCatalogDao()
    private val reusableIngredientDao = appDatabase.reusableIngredientDao()
    private val reusableCompoundDao = appDatabase.reusableCompoundDao()
    private val homeRepository: HomeRepository = HomeRepositoryImpl(homeDao)
    private val getHomeMessageUseCase = GetHomeMessageUseCase(homeRepository)
    private val recordsRepository: RecordsRepository = RoomRecordsRepository(ingestionEventDao, exposureEventDao, outbreakEventDao)
    private val reusableEventCatalogRepository = RoomReusableEventCatalogRepository(reusableEventCatalogDao, systemCurrentTimeProvider::nowEpochMillis)
    private val ingestionRepository = RoomIngestionRepository(
        ingestionEventDao = ingestionEventDao,
        currentTimeProvider = systemCurrentTimeProvider,
        reusableEntityExists = { kind, id ->
            when (kind) {
                ReusableEntityKind.INGREDIENT -> reusableIngredientDao.existsById(id)
                ReusableEntityKind.COMPOUND -> reusableCompoundDao.existsById(id)
            }
        },
        touchReusableEntity = { kind, id ->
            val updatedAt = systemCurrentTimeProvider.nowEpochMillis()
            when (kind) {
                ReusableEntityKind.INGREDIENT -> reusableIngredientDao.touchById(id, updatedAt)
                ReusableEntityKind.COMPOUND -> reusableCompoundDao.touchById(id, updatedAt)
            }
        }
    )
    private val exposureRepository = RoomExposureRepository(exposureEventDao, systemCurrentTimeProvider, reusableEventCatalogRepository)
    private val outbreakRepository = RoomOutbreakRepository(outbreakEventDao, systemCurrentTimeProvider, reusableEventCatalogRepository)
    private val reusableIngredientRepository = RoomReusableIngredientRepository(reusableIngredientDao, systemCurrentTimeProvider::nowEpochMillis)
    private val reusableCompoundRepository = RoomReusableCompoundRepository(
        compoundDao = reusableCompoundDao,
        ingredientExists = reusableIngredientDao::existsById,
        compoundExists = reusableCompoundDao::existsById,
        nowEpochMillis = systemCurrentTimeProvider::nowEpochMillis
    )
    private val createIngestionDefaultsUseCase = CreateIngestionDefaultsUseCase(systemCurrentTimeProvider)
    private val createIngestionEventUseCase = CreateIngestionEventUseCase(
        repository = ingestionRepository,
        currentTimeProvider = systemCurrentTimeProvider,
        reusableEntityExists = { kind, id ->
            when (kind) {
                ReusableEntityKind.INGREDIENT -> reusableIngredientDao.existsById(id)
                ReusableEntityKind.COMPOUND -> reusableCompoundDao.existsById(id)
            }
        }
    )
    private val getIngestionEventUseCase = GetIngestionEventUseCase(ingestionRepository)
    private val deleteIngestionEventUseCase = DeleteIngestionEventUseCase(ingestionRepository)
    private val updateIngestionEventUseCase = UpdateIngestionEventUseCase(
        ingestionRepository,
        systemCurrentTimeProvider,
        reusableEntityExists = { kind, id ->
            when (kind) {
                ReusableEntityKind.INGREDIENT -> reusableIngredientDao.existsById(id)
                ReusableEntityKind.COMPOUND -> reusableCompoundDao.existsById(id)
            }
        }
    )
    private val createExposureDefaultsUseCase = CreateExposureDefaultsUseCase(systemCurrentTimeProvider)
    private val createExposureEventUseCase = CreateExposureEventUseCase(
        repository = exposureRepository,
        currentTimeProvider = systemCurrentTimeProvider,
        exposureCatalogExists = { catalogId ->
            reusableEventCatalogRepository.existsCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, catalogId)
        },
        getExposureCatalogEntry = { catalogId ->
            reusableEventCatalogRepository.getCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, catalogId)
        }
    )
    private val getExposureEventUseCase = GetExposureEventUseCase(exposureRepository)
    private val deleteExposureEventUseCase = DeleteExposureEventUseCase(exposureRepository)
    private val updateExposureEventUseCase = UpdateExposureEventUseCase(
        exposureRepository,
        systemCurrentTimeProvider,
        exposureCatalogExists = { catalogId ->
            reusableEventCatalogRepository.existsCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, catalogId)
        },
        getExposureCatalogEntry = { catalogId ->
            reusableEventCatalogRepository.getCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, catalogId)
        }
    )
    private val createOutbreakDefaultsUseCase = CreateOutbreakDefaultsUseCase(systemCurrentTimeProvider)
    private val createOutbreakEventUseCase = CreateOutbreakEventUseCase(
        repository = outbreakRepository,
        currentTimeProvider = systemCurrentTimeProvider,
        outbreakDescriptorCatalogExists = { catalogId ->
            reusableEventCatalogRepository.existsCatalogEntry(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, catalogId)
        },
        bodyLocationCatalogExists = { catalogId ->
            reusableEventCatalogRepository.existsCatalogEntry(ReusableEventCatalogKind.BODY_LOCATION, catalogId)
        },
        getOutbreakDescriptorCatalogEntry = { catalogId ->
            reusableEventCatalogRepository.getCatalogEntry(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, catalogId)
        },
        getBodyLocationCatalogEntry = { catalogId ->
            reusableEventCatalogRepository.getCatalogEntry(ReusableEventCatalogKind.BODY_LOCATION, catalogId)
        }
    )
    private val getOutbreakEventUseCase = GetOutbreakEventUseCase(outbreakRepository)
    private val deleteOutbreakEventUseCase = DeleteOutbreakEventUseCase(outbreakRepository)
    private val updateOutbreakEventUseCase = UpdateOutbreakEventUseCase(
        outbreakRepository,
        systemCurrentTimeProvider,
        outbreakDescriptorCatalogExists = { catalogId ->
            reusableEventCatalogRepository.existsCatalogEntry(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, catalogId)
        },
        bodyLocationCatalogExists = { catalogId ->
            reusableEventCatalogRepository.existsCatalogEntry(ReusableEventCatalogKind.BODY_LOCATION, catalogId)
        },
        getOutbreakDescriptorCatalogEntry = { catalogId ->
            reusableEventCatalogRepository.getCatalogEntry(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR, catalogId)
        },
        getBodyLocationCatalogEntry = { catalogId ->
            reusableEventCatalogRepository.getCatalogEntry(ReusableEventCatalogKind.BODY_LOCATION, catalogId)
        }
    )
    private val createReusableIngredientUseCase = CreateReusableIngredientUseCase(reusableIngredientRepository)
    private val createReusableCompoundUseCase = CreateReusableCompoundUseCase(reusableCompoundRepository)
    private val getReusableIngredientUseCase = GetReusableIngredientUseCase(reusableIngredientRepository)
    private val getReusableCompoundUseCase = GetReusableCompoundUseCase(reusableCompoundRepository)
    private val getReusableEventCatalogEntryUseCase = GetReusableEventCatalogEntryUseCase(reusableEventCatalogRepository)
    private val getReusableIngredientRetirementImpactUseCase = GetReusableIngredientRetirementImpactUseCase(
        repository = reusableIngredientRepository,
        countIngestionReferences = { ingredientId ->
            ingestionEventDao.countReusableEntityReferences(ReusableEntityKind.INGREDIENT.storageValue, ingredientId)
        },
        countCompoundReferences = { ingredientId ->
            reusableCompoundDao.countIngredientComponentReferences(ingredientId)
        }
    )
    private val getReusableCompoundRetirementImpactUseCase = GetReusableCompoundRetirementImpactUseCase(
        repository = reusableCompoundRepository,
        countIngestionReferences = { compoundId ->
            ingestionEventDao.countReusableEntityReferences(ReusableEntityKind.COMPOUND.storageValue, compoundId)
        },
        countChildCompoundReferences = { compoundId ->
            reusableCompoundDao.countChildCompoundReferences(compoundId)
        }
    )
    private val getReusableEventCatalogRetirementImpactUseCase = GetReusableEventCatalogRetirementImpactUseCase(
        repository = reusableEventCatalogRepository,
        countReferences = { kind, catalogId ->
            when (kind) {
                ReusableEventCatalogKind.EXPOSURE_TYPE -> exposureEventDao.countCatalogReferences(catalogId)
                ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> outbreakEventDao.countOutbreakDescriptorCatalogReferences(catalogId)
                ReusableEventCatalogKind.BODY_LOCATION -> outbreakEventDao.countBodyLocationCatalogReferences(catalogId)
            }
        }
    )
    private val updateReusableIngredientUseCase = UpdateReusableIngredientUseCase(
        repository = reusableIngredientRepository,
        syncReferences = { ingredientId, name ->
            ingestionEventDao.updateReusableEntityName(ReusableEntityKind.INGREDIENT.storageValue, ingredientId, name)
        },
        countReferences = { ingredientId ->
            ingestionEventDao.countReusableEntityReferences(ReusableEntityKind.INGREDIENT.storageValue, ingredientId)
        },
        runAtomically = { block ->
            appDatabase.withTransaction { block() }
        }
    )
    private val retireReusableIngredientUseCase = RetireReusableIngredientUseCase(
        repository = reusableIngredientRepository,
        getImpact = getReusableIngredientRetirementImpactUseCase,
        detachIngestionReferences = { ingredientId ->
            ingestionEventDao.clearReusableEntityReferences(ReusableEntityKind.INGREDIENT.storageValue, ingredientId)
        },
        runAtomically = { block ->
            appDatabase.withTransaction { block() }
        }
    )
    private val updateReusableCompoundUseCase = UpdateReusableCompoundUseCase(
        repository = reusableCompoundRepository,
        ingredientExists = { ingredientId -> reusableIngredientDao.existsById(ingredientId) },
        compoundExists = { reusableCompoundDao.existsById(it) },
        syncReferences = { compoundId, name ->
            ingestionEventDao.updateReusableEntityName(ReusableEntityKind.COMPOUND.storageValue, compoundId, name)
        },
        countReferences = { compoundId ->
            ingestionEventDao.countReusableEntityReferences(ReusableEntityKind.COMPOUND.storageValue, compoundId)
        },
        runAtomically = { block ->
            appDatabase.withTransaction { block() }
        }
    )
    private val retireReusableCompoundUseCase = RetireReusableCompoundUseCase(
        repository = reusableCompoundRepository,
        getImpact = getReusableCompoundRetirementImpactUseCase,
        detachIngestionReferences = { compoundId ->
            ingestionEventDao.clearReusableEntityReferences(ReusableEntityKind.COMPOUND.storageValue, compoundId)
        },
        runAtomically = { block ->
            appDatabase.withTransaction { block() }
        }
    )
    private val updateReusableEventCatalogUseCase = UpdateReusableEventCatalogUseCase(
        repository = reusableEventCatalogRepository,
        syncReferences = { kind, catalogId, name ->
            when (kind) {
                ReusableEventCatalogKind.EXPOSURE_TYPE -> exposureEventDao.updateCatalogName(catalogId, name)
                ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> outbreakEventDao.updateOutbreakDescriptorCatalogName(catalogId, name)
                ReusableEventCatalogKind.BODY_LOCATION -> outbreakEventDao.updateBodyLocationCatalogName(catalogId, name)
            }
        },
        countReferences = { kind, catalogId ->
            when (kind) {
                ReusableEventCatalogKind.EXPOSURE_TYPE -> exposureEventDao.countCatalogReferences(catalogId)
                ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> outbreakEventDao.countOutbreakDescriptorCatalogReferences(catalogId)
                ReusableEventCatalogKind.BODY_LOCATION -> outbreakEventDao.countBodyLocationCatalogReferences(catalogId)
            }
        },
        runAtomically = { block ->
            appDatabase.withTransaction { block() }
        }
    )
    private val retireReusableEventCatalogUseCase = RetireReusableEventCatalogUseCase(
        repository = reusableEventCatalogRepository,
        getImpact = getReusableEventCatalogRetirementImpactUseCase,
        runAtomically = { block ->
            appDatabase.withTransaction { block() }
        }
    )
    private val observeReusableIngredientsUseCase = ObserveReusableIngredientsUseCase(reusableIngredientRepository)
    private val observeReusableCompoundsUseCase = ObserveReusableCompoundsUseCase(reusableCompoundRepository)
    private val observeReusableEventCatalogUseCase = ObserveReusableEventCatalogUseCase(reusableEventCatalogRepository)
    private val observeRecordCountUseCase = ObserveRecordCountUseCase(recordsRepository)
    private val observeRecordsUseCase = ObserveRecordsUseCase(recordsRepository)
    private val observeRecordDetailUseCase = ObserveRecordDetailUseCase(recordsRepository)

    val recordsViewModelFactory: RecordsViewModelFactory =
        RecordsViewModelFactory(systemCurrentTimeProvider, observeRecordCountUseCase, observeRecordsUseCase)
    val createIngestionViewModelFactory: CreateIngestionViewModelFactory =
        CreateIngestionViewModelFactory(
            createIngestionDefaultsUseCase,
            createIngestionEventUseCase,
            observeReusableIngredientsUseCase,
            observeReusableCompoundsUseCase,
            createReusableIngredientUseCase
        )
    val createExposureViewModelFactory: CreateExposureViewModelFactory =
        CreateExposureViewModelFactory(
            createExposureDefaultsUseCase,
            createExposureEventUseCase,
            observeReusableEventCatalogUseCase
        )
    val createOutbreakViewModelFactory: CreateOutbreakViewModelFactory =
        CreateOutbreakViewModelFactory(
            createOutbreakDefaultsUseCase,
            createOutbreakEventUseCase,
            observeReusableEventCatalogUseCase
        )
    val createIngredientViewModelFactory: CreateIngredientViewModelFactory =
        CreateIngredientViewModelFactory(createReusableIngredientUseCase)
    val createCompoundViewModelFactory: CreateCompoundViewModelFactory =
        CreateCompoundViewModelFactory(
            createReusableCompoundUseCase,
            observeReusableIngredientsUseCase,
            observeReusableCompoundsUseCase
        )

    fun createEditIngredientViewModelFactory(reusableId: Long): EditIngredientViewModelFactory {
        return EditIngredientViewModelFactory(
            reusableId,
            getReusableIngredientUseCase,
            updateReusableIngredientUseCase,
            getReusableIngredientRetirementImpactUseCase,
            retireReusableIngredientUseCase
        )
    }

    fun createEditCompoundViewModelFactory(reusableId: Long): EditCompoundViewModelFactory {
        return EditCompoundViewModelFactory(
            reusableId,
            getReusableCompoundUseCase,
            updateReusableCompoundUseCase,
            observeReusableIngredientsUseCase,
            observeReusableCompoundsUseCase,
            getReusableCompoundRetirementImpactUseCase,
            retireReusableCompoundUseCase
        )
    }

    fun createEditReusableEventCatalogViewModelFactory(
        kind: ReusableEventCatalogKind,
        reusableId: Long
    ): EditReusableEventCatalogViewModelFactory {
        return EditReusableEventCatalogViewModelFactory(
            reusableId,
            kind,
            getReusableEventCatalogEntryUseCase,
            updateReusableEventCatalogUseCase,
            getReusableEventCatalogRetirementImpactUseCase,
            retireReusableEventCatalogUseCase
        )
    }

    fun createRecordDetailViewModelFactory(recordId: Long, eventType: RecordEventType): RecordDetailViewModelFactory {
        return RecordDetailViewModelFactory(recordId, eventType, observeRecordDetailUseCase)
    }

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
            systemCurrentTimeProvider,
            observeReusableEventCatalogUseCase
        )
    }

    fun createEditOutbreakViewModelFactory(recordId: Long): EditOutbreakViewModelFactory {
        return EditOutbreakViewModelFactory(
            recordId,
            getOutbreakEventUseCase,
            deleteOutbreakEventUseCase,
            updateOutbreakEventUseCase,
            systemCurrentTimeProvider,
            observeReusableEventCatalogUseCase
        )
    }

    fun clearAllDataForTesting() {
        runBlocking {
            appDatabase.clearAllTables()
        }
    }
}
