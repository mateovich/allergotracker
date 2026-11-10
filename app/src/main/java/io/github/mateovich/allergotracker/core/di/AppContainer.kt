package io.github.mateovich.allergotracker.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.core.database.AppDatabase
import io.github.mateovich.allergotracker.core.database.HomeDao
import io.github.mateovich.allergotracker.core.distribution.AppDistributionFactory
import io.github.mateovich.allergotracker.core.database.migrations.Migration_1_2
import io.github.mateovich.allergotracker.core.database.migrations.Migration_2_3
import io.github.mateovich.allergotracker.core.database.migrations.Migration_3_4
import io.github.mateovich.allergotracker.core.database.migrations.Migration_4_5
import io.github.mateovich.allergotracker.core.database.migrations.Migration_5_6
import io.github.mateovich.allergotracker.core.database.migrations.Migration_6_7
import io.github.mateovich.allergotracker.core.database.migrations.Migration_7_8
import io.github.mateovich.allergotracker.core.database.migrations.Migration_8_9
import io.github.mateovich.allergotracker.core.database.migrations.Migration_9_10
import io.github.mateovich.allergotracker.core.database.migrations.Migration_10_11
import io.github.mateovich.allergotracker.core.database.migrations.Migration_11_12
import io.github.mateovich.allergotracker.core.database.migrations.Migration_12_13
import io.github.mateovich.allergotracker.core.database.migrations.Migration_13_14
import io.github.mateovich.allergotracker.core.database.migrations.Migration_14_15
import io.github.mateovich.allergotracker.features.compounds.data.RoomReusableCompoundRepository
import io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.CompoundChildEdge
import io.github.mateovich.allergotracker.features.compounds.domain.ReplaceIngredientWithCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.CompoundCompositionIntegrity
import io.github.mateovich.allergotracker.features.compounds.domain.GetReusableCompoundMergePreviewUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.GetReusableCompoundRetirementImpactUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.GetReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.MergeReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ObserveReusableCompoundsUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.RetireReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.UpdateReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.ui.CreateCompoundViewModelFactory
import io.github.mateovich.allergotracker.features.compounds.ui.EditCompoundViewModelFactory
import io.github.mateovich.allergotracker.features.eventcatalog.data.RoomReusableEventCatalogRepository
import io.github.mateovich.allergotracker.features.eventcatalog.domain.GetReusableEventCatalogEntryUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.GetReusableEventCatalogRetirementImpactUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.domain.RetireReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.UpdateReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.ui.EditReusableEventCatalogViewModelFactory
import io.github.mateovich.allergotracker.features.export.domain.DeliverStagedExportUseCase
import io.github.mateovich.allergotracker.features.export.domain.ValidateExportConfigurationUseCase
import io.github.mateovich.allergotracker.features.export.domain.GenerateVersionedJsonExportUseCase
import io.github.mateovich.allergotracker.features.export.data.DownloadsExportDeliveryRepository
import io.github.mateovich.allergotracker.features.export.data.ExportSnapshotDataAccess
import io.github.mateovich.allergotracker.features.export.data.KotlinxVersionedJsonExportSerializer
import io.github.mateovich.allergotracker.features.export.data.LocalJsonExportArtifactWriter
import io.github.mateovich.allergotracker.features.export.data.RoomExportSnapshotRepository
import io.github.mateovich.allergotracker.features.export.ui.ExportViewModelFactory
import io.github.mateovich.allergotracker.features.import.data.AndroidImportDocumentReader
import io.github.mateovich.allergotracker.features.import.data.KotlinxVersionedJsonImportParser
import io.github.mateovich.allergotracker.features.import.data.RoomStructuredImportRepository
import io.github.mateovich.allergotracker.features.import.domain.ApplyStructuredImportUseCase
import io.github.mateovich.allergotracker.features.import.domain.ReviewStructuredImportUseCase
import io.github.mateovich.allergotracker.features.exposure.data.RoomExposureRepository
import io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureDefaultsUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.DeleteExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.GetExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.UpdateExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.ui.CreateExposureViewModelFactory
import io.github.mateovich.allergotracker.features.exposure.ui.EditExposureViewModelFactory
import io.github.mateovich.allergotracker.features.ingredients.data.RoomReusableIngredientRepository
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientMergePreviewUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientRetirementImpactUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.MergeReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.RetireReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.UpdateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.ui.CreateIngredientViewModelFactory
import io.github.mateovich.allergotracker.features.ingredients.ui.EditIngredientViewModelFactory
import io.github.mateovich.allergotracker.features.ingestion.data.RoomIngestionRepository
import io.github.mateovich.allergotracker.features.ingestion.data.IngestionEventEntity
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionDefaultsUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.ingestion.domain.DeleteIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.GetIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.ui.CreateIngestionViewModelFactory
import io.github.mateovich.allergotracker.features.ingestion.ui.EditIngestionViewModelFactory
import io.github.mateovich.allergotracker.features.home.data.HomeRepositoryImpl
import io.github.mateovich.allergotracker.features.home.domain.GetHomeMessageUseCase
import io.github.mateovich.allergotracker.features.home.domain.HomeRepository
import io.github.mateovich.allergotracker.features.outbreak.data.RoomOutbreakRepository
import io.github.mateovich.allergotracker.features.outbreak.domain.CreateOutbreakDefaultsUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.CreateOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.DeleteOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.GetOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.UpdateOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.ui.CreateOutbreakViewModelFactory
import io.github.mateovich.allergotracker.features.outbreak.ui.EditOutbreakViewModelFactory
import io.github.mateovich.allergotracker.features.records.data.RoomRecordsRepository
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordCountUseCase
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordDetailUseCase
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordsUseCase
import io.github.mateovich.allergotracker.features.records.domain.RecordsRepository
import io.github.mateovich.allergotracker.features.records.domain.RecordEventType
import io.github.mateovich.allergotracker.features.records.ui.RecordDetailViewModelFactory
import io.github.mateovich.allergotracker.features.records.ui.RecordsViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Application composition root. Core event flows are wired only to Room-backed repositories.
 */
class AppContainer(appContext: Context) {
    private val appDistribution = AppDistributionFactory.fromBuildConfig()
    private val systemCurrentTimeProvider = CurrentTimeProvider { System.currentTimeMillis() }
    private val appDatabase: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "app_database"
    ).addMigrations(Migration_1_2, Migration_2_3, Migration_3_4, Migration_4_5, Migration_5_6, Migration_6_7, Migration_7_8, Migration_8_9, Migration_9_10, Migration_10_11, Migration_11_12, Migration_12_13, Migration_13_14, Migration_14_15).build()

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
    private val getReusableIngredientMergePreviewUseCase = GetReusableIngredientMergePreviewUseCase(
        repository = reusableIngredientRepository,
        countIngestionReferences = { ingredientId ->
            ingestionEventDao.countReusableEntityReferences(ReusableEntityKind.INGREDIENT.storageValue, ingredientId)
        },
        countCompoundReferences = { ingredientId ->
            reusableCompoundDao.countIngredientComponentReferences(ingredientId)
        },
        countConflictingCompounds = { sourceIngredientId, destinationIngredientId ->
            reusableCompoundDao.countIngredientMergeConflictingCompounds(sourceIngredientId, destinationIngredientId)
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
    private val getReusableCompoundMergePreviewUseCase = GetReusableCompoundMergePreviewUseCase(
        repository = reusableCompoundRepository,
        countIngestionReferences = { compoundId ->
            ingestionEventDao.countReusableEntityReferences(ReusableEntityKind.COMPOUND.storageValue, compoundId)
        },
        countParentCompoundReferences = { compoundId ->
            reusableCompoundDao.countChildCompoundReferences(compoundId)
        },
        countConflictingParentCompounds = { sourceCompoundId, destinationCompoundId ->
            reusableCompoundDao.countCompoundMergeConflictingParents(sourceCompoundId, destinationCompoundId)
        },
        countRecursiveParentCompounds = { sourceCompoundId, destinationCompoundId ->
            CompoundCompositionIntegrity.countRecursiveMergeParents(
                sourceCompoundId = sourceCompoundId,
                destinationCompoundId = destinationCompoundId,
                impactedParentCompoundIds = reusableCompoundDao.findParentCompoundIdsReferencingChild(sourceCompoundId),
                childEdges = reusableCompoundDao.findAllChildCompoundEdges().map { edge ->
                    CompoundChildEdge(
                        parentCompoundId = edge.parentCompoundId,
                        childCompoundId = edge.childCompoundId
                    )
                }
            )
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
    private val mergeReusableIngredientUseCase = MergeReusableIngredientUseCase(
        getPreview = getReusableIngredientMergePreviewUseCase,
        getIngredient = reusableIngredientRepository::getIngredient,
        deleteIngredient = reusableIngredientRepository::deleteIngredient,
        reassignIngestionReferences = { sourceIngredientId, destinationIngredientId, destinationName ->
            ingestionEventDao.reassignReusableEntityReferences(
                ReusableEntityKind.INGREDIENT.storageValue,
                sourceIngredientId,
                destinationIngredientId,
                destinationName
            )
        },
        replaceCompoundReferences = { sourceIngredientId, destinationIngredientId ->
            reusableCompoundDao.replaceIngredientComponentReferences(sourceIngredientId, destinationIngredientId)
        },
        countIngestionReferences = { ingredientId ->
            ingestionEventDao.countReusableEntityReferences(ReusableEntityKind.INGREDIENT.storageValue, ingredientId)
        },
        countCompoundReferences = reusableCompoundDao::countIngredientComponentReferences,
        findAffectedCompoundIds = reusableCompoundDao::findCompoundIdsReferencingIngredient,
        hasInvalidCompoundState = reusableCompoundRepository::hasDuplicateComponents,
        validatePostMergeIntegrity = io.github.mateovich.allergotracker.core.validation.ValidatePostMergeIntegrityUseCase(),
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
    private val mergeReusableCompoundUseCase = MergeReusableCompoundUseCase(
        getPreview = getReusableCompoundMergePreviewUseCase,
        getCompound = reusableCompoundRepository::getCompound,
        deleteCompound = reusableCompoundRepository::deleteCompound,
        reassignIngestionReferences = { sourceCompoundId, destinationCompoundId, destinationName ->
            ingestionEventDao.reassignReusableEntityReferences(
                ReusableEntityKind.COMPOUND.storageValue,
                sourceCompoundId,
                destinationCompoundId,
                destinationName
            )
        },
        replaceParentCompoundReferences = { sourceCompoundId, destinationCompoundId ->
            reusableCompoundDao.replaceChildCompoundReferences(sourceCompoundId, destinationCompoundId)
        },
        countIngestionReferences = { compoundId ->
            ingestionEventDao.countReusableEntityReferences(ReusableEntityKind.COMPOUND.storageValue, compoundId)
        },
        countParentCompoundReferences = reusableCompoundDao::countChildCompoundReferences,
        findAffectedParentCompoundIds = reusableCompoundDao::findParentCompoundIdsReferencingChild,
        hasInvalidParentCompoundState = { compoundIds ->
            reusableCompoundRepository.hasDuplicateComponents(compoundIds) ||
                reusableCompoundRepository.hasRecursiveComposition(compoundIds)
        },
        validatePostMergeIntegrity = io.github.mateovich.allergotracker.core.validation.ValidatePostMergeIntegrityUseCase(),
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
    private val validateExportConfigurationUseCase = ValidateExportConfigurationUseCase()
    private val exportSnapshotRepository = RoomExportSnapshotRepository(
        object : ExportSnapshotDataAccess {
            override suspend fun <T> readSnapshot(block: suspend () -> T): T {
                return appDatabase.withTransaction { block() }
            }

            override suspend fun getIngestionEvents() = ingestionEventDao.observeIngestionEvents().first().sortedWith(compareBy({ it.occurredAtEpochMs }, { it.id }))
            override suspend fun getExposureEvents() = exposureEventDao.observeExposureEvents().first().sortedWith(compareBy({ it.occurredAtEpochMs }, { it.id }))
            override suspend fun getOutbreakEvents() = outbreakEventDao.observeOutbreakEvents().first().sortedWith(compareBy({ it.occurredAtEpochMs }, { it.id }))
            override suspend fun getReusableIngredients() = reusableIngredientDao.observeAll().first()
            override suspend fun getReusableCompounds() = reusableCompoundDao.observeAll().first()
            override suspend fun getReusableEventCatalogEntries() =
                reusableEventCatalogDao.observeByKind(ReusableEventCatalogKind.EXPOSURE_TYPE.storageValue).first() +
                    reusableEventCatalogDao.observeByKind(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR.storageValue).first() +
                    reusableEventCatalogDao.observeByKind(ReusableEventCatalogKind.BODY_LOCATION.storageValue).first()
        }
    )
    private val generateVersionedJsonExportUseCase = GenerateVersionedJsonExportUseCase(
        snapshotRepository = exportSnapshotRepository,
        writer = LocalJsonExportArtifactWriter(appContext.cacheDir.resolve("exports")),
        serializer = KotlinxVersionedJsonExportSerializer(),
        currentTimeProvider = systemCurrentTimeProvider
    )
    private val deliverStagedExportUseCase = DeliverStagedExportUseCase(
        repository = DownloadsExportDeliveryRepository(appContext)
    )
    private val structuredImportRepository = RoomStructuredImportRepository(
        reusableIngredientDao = reusableIngredientDao,
        reusableCompoundDao = reusableCompoundDao,
        reusableEventCatalogDao = reusableEventCatalogDao,
        createReusableIngredientUseCase = createReusableIngredientUseCase,
        createReusableCompoundUseCase = createReusableCompoundUseCase,
        updateReusableCompoundUseCase = updateReusableCompoundUseCase,
        createIngestionEventUseCase = createIngestionEventUseCase,
        createExposureEventUseCase = createExposureEventUseCase,
        createOutbreakEventUseCase = createOutbreakEventUseCase,
        nowEpochMillis = systemCurrentTimeProvider::nowEpochMillis,
        runAtomically = { block -> appDatabase.withTransaction { block() } }
    )
    private val reviewStructuredImportUseCase = ReviewStructuredImportUseCase(
        documentReader = AndroidImportDocumentReader(appContext.contentResolver),
        parser = KotlinxVersionedJsonImportParser(),
        repository = structuredImportRepository
    )
    private val applyStructuredImportUseCase = ApplyStructuredImportUseCase(structuredImportRepository)

    val recordsViewModelFactory: RecordsViewModelFactory =
        RecordsViewModelFactory(systemCurrentTimeProvider, observeRecordCountUseCase, observeRecordsUseCase)
    val exportViewModelFactory: ExportViewModelFactory =
        ExportViewModelFactory(
            appDistribution,
            observeRecordCountUseCase,
            validateExportConfigurationUseCase,
            generateVersionedJsonExportUseCase,
            deliverStagedExportUseCase,
            reviewStructuredImportUseCase,
            applyStructuredImportUseCase
        )
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
    private val replaceIngredientWithCompoundUseCase = ReplaceIngredientWithCompoundUseCase(
        createCompound = createReusableCompoundUseCase::invoke,
        getIngredientName = { ingredientId ->
            when (val result = reusableIngredientRepository.getIngredient(ingredientId)) {
                is AppResult.Success -> AppResult.Success(result.value.name)
                is AppResult.Error -> result
            }
        },
        deleteIngredient = reusableIngredientRepository::deleteIngredient,
        countIngestionReferences = { ingredientId ->
            ingestionEventDao.countReusableEntityReferences(ReusableEntityKind.INGREDIENT.storageValue, ingredientId)
        },
        countComponentReferences = reusableCompoundDao::countIngredientComponentReferences,
        findParentCompoundIds = reusableCompoundDao::findCompoundIdsReferencingIngredient,
        findChildCompoundEdges = {
            reusableCompoundDao.findAllChildCompoundEdges().map { edge ->
                CompoundChildEdge(edge.parentCompoundId, edge.childCompoundId)
            }
        },
        reassignIngestionReferences = { ingredientId, compoundId, compoundName ->
            ingestionEventDao.convertReusableEntityReferences(
                ReusableEntityKind.INGREDIENT.storageValue,
                ingredientId,
                ReusableEntityKind.COMPOUND.storageValue,
                compoundId,
                compoundName
            )
        },
        replaceComponentReferences = reusableCompoundDao::replaceIngredientComponentsWithCompound,
        runAtomically = { block ->
            appDatabase.withTransaction { block() }
        }
    )
    val createIngredientViewModelFactory: CreateIngredientViewModelFactory =
        CreateIngredientViewModelFactory(createReusableIngredientUseCase)
    fun createCreateCompoundViewModelFactory(
        initialName: String = "",
        initialSeedIngredient: io.github.mateovich.allergotracker.features.ingestion.ui.IngestionReusableSelectionResult? = null
    ): CreateCompoundViewModelFactory =
        CreateCompoundViewModelFactory(
            createReusableCompoundUseCase,
            createReusableIngredientUseCase,
            observeReusableIngredientsUseCase,
            observeReusableCompoundsUseCase,
            replaceIngredientWithCompoundUseCase,
            initialName,
            initialSeedIngredient
        )

    fun createEditIngredientViewModelFactory(reusableId: Long): EditIngredientViewModelFactory {
        return EditIngredientViewModelFactory(
            reusableId,
            getReusableIngredientUseCase,
            updateReusableIngredientUseCase,
            observeReusableIngredientsUseCase,
            getReusableIngredientMergePreviewUseCase,
            mergeReusableIngredientUseCase,
            getReusableIngredientRetirementImpactUseCase,
            retireReusableIngredientUseCase
        )
    }

    fun createEditCompoundViewModelFactory(reusableId: Long): EditCompoundViewModelFactory {
        return EditCompoundViewModelFactory(
            reusableId,
            getReusableCompoundUseCase,
            updateReusableCompoundUseCase,
            createReusableIngredientUseCase,
            observeReusableIngredientsUseCase,
            observeReusableCompoundsUseCase,
            getReusableCompoundMergePreviewUseCase,
            mergeReusableCompoundUseCase,
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
            systemCurrentTimeProvider,
            reusableEntityExists = { kind, id ->
                when (kind) {
                    ReusableEntityKind.INGREDIENT -> reusableIngredientDao.existsById(id)
                    ReusableEntityKind.COMPOUND -> reusableCompoundDao.existsById(id)
                }
            },
            observeReusableIngredientsUseCase = observeReusableIngredientsUseCase,
            observeReusableCompoundsUseCase = observeReusableCompoundsUseCase,
            createReusableIngredientUseCase = createReusableIngredientUseCase
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

    fun insertManualIngestionForTesting(
        itemName: String,
        quantityLevel: Int,
        occurredAtEpochMs: Long,
        notes: String? = null
    ): Long {
        return runBlocking {
            ingestionEventDao.insert(
                IngestionEventEntity(
                    itemName = itemName,
                    quantityLevel = quantityLevel,
                    occurredAtEpochMs = occurredAtEpochMs,
                    notes = notes,
                    reusableEntityKind = null,
                    reusableEntityId = null,
                    createdAtEpochMs = occurredAtEpochMs
                )
            )
        }
    }
}
