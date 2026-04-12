package com.example.app.features.export.data

import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.data.ReusableCompoundWithComponents
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.export.domain.ExportExposureRecord
import com.example.app.features.export.domain.ExportIngestionRecord
import com.example.app.features.export.domain.ExportOutbreakRecord
import com.example.app.features.export.domain.ExportReusableCompoundComponentRecord
import com.example.app.features.export.domain.ExportReusableCompoundRecord
import com.example.app.features.export.domain.ExportReusableEventCatalogRecord
import com.example.app.features.export.domain.ExportReusableIngredientRecord
import com.example.app.features.export.domain.ExportScope
import com.example.app.features.export.domain.ExportSnapshot
import com.example.app.features.export.domain.ExportSnapshotRepository
import com.example.app.features.export.domain.toExportError
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.records.domain.RecordEventType

class RoomExportSnapshotRepository(
    private val dataAccess: ExportSnapshotDataAccess
) : ExportSnapshotRepository {
    override suspend fun getSnapshot(scope: ExportScope): AppResult<ExportSnapshot> {
        return try {
            AppResult.Success(
                dataAccess.readSnapshot {
                    val ingestions = if (scope.includes(RecordEventType.INGESTION)) {
                        dataAccess.getIngestionEvents().map { entity ->
                            ExportIngestionRecord(
                                id = entity.id,
                                itemName = entity.itemName,
                                quantityLevel = entity.quantityLevel,
                                occurredAtEpochMs = entity.occurredAtEpochMs,
                                notes = entity.notes,
                                reusableEntityKind = entity.reusableEntityKind,
                                reusableEntityId = entity.reusableEntityId,
                                createdAtEpochMs = entity.createdAtEpochMs
                            )
                        }
                    } else {
                        emptyList()
                    }
                    val exposures = if (scope.includes(RecordEventType.EXPOSURE)) {
                        dataAccess.getExposureEvents().map { entity ->
                            ExportExposureRecord(
                                id = entity.id,
                                exposureType = entity.exposureType,
                                occurredAtEpochMs = entity.occurredAtEpochMs,
                                notes = entity.notes,
                                intensityLevel = entity.intensityLevel,
                                exposureTypeCatalogId = entity.exposureTypeCatalogId,
                                createdAtEpochMs = entity.createdAtEpochMs
                            )
                        }
                    } else {
                        emptyList()
                    }
                    val outbreaks = if (scope.includes(RecordEventType.OUTBREAK)) {
                        dataAccess.getOutbreakEvents().map { entity ->
                            ExportOutbreakRecord(
                                id = entity.id,
                                outbreakDescriptor = entity.outbreakDescriptor,
                                bodyLocation = entity.bodyLocation,
                                occurredAtEpochMs = entity.occurredAtEpochMs,
                                notes = entity.notes,
                                intensityLevel = entity.intensityLevel,
                                outbreakDescriptorCatalogId = entity.outbreakDescriptorCatalogId,
                                bodyLocationCatalogId = entity.bodyLocationCatalogId,
                                createdAtEpochMs = entity.createdAtEpochMs
                            )
                        }
                    } else {
                        emptyList()
                    }
                    val reusableIngredients = dataAccess.getReusableIngredients()
                    val reusableCompounds = dataAccess.getReusableCompounds()
                    val reusableEventCatalogEntries = dataAccess.getReusableEventCatalogEntries()
                    val scopedReusableData = scope.toScopedReusableData(
                        ingestions = ingestions,
                        exposures = exposures,
                        outbreaks = outbreaks,
                        reusableIngredients = reusableIngredients,
                        reusableCompounds = reusableCompounds,
                        reusableEventCatalogEntries = reusableEventCatalogEntries
                    )
                    ExportSnapshot(
                        ingestions = ingestions,
                        exposures = exposures,
                        outbreaks = outbreaks,
                        reusableIngredients = scopedReusableData.reusableIngredients.map { entity ->
                            ExportReusableIngredientRecord(
                                id = entity.id,
                                name = entity.name,
                                notes = entity.notes,
                                createdAtEpochMs = entity.createdAtEpochMs,
                                updatedAtEpochMs = entity.updatedAtEpochMs
                            )
                        },
                        reusableCompounds = scopedReusableData.reusableCompounds.map { compound ->
                            ExportReusableCompoundRecord(
                                id = compound.compound.id,
                                name = compound.compound.name,
                                notes = compound.compound.notes,
                                compositionStatus = compound.compound.compositionStatus,
                                components = compound.components
                                    .sortedWith(compareBy({ it.position }, { it.id }))
                                    .map { component ->
                                        ExportReusableCompoundComponentRecord(
                                            id = component.id,
                                            position = component.position,
                                            kind = component.componentKind,
                                            ingredientId = component.ingredientId,
                                            childCompoundId = component.childCompoundId,
                                            proportionBasisPoints = component.proportionBasisPoints
                                        )
                                    },
                                createdAtEpochMs = compound.compound.createdAtEpochMs,
                                updatedAtEpochMs = compound.compound.updatedAtEpochMs
                            )
                        },
                        reusableEventCatalogEntries = scopedReusableData.reusableEventCatalogEntries.map { entity ->
                            ExportReusableEventCatalogRecord(
                                id = entity.id,
                                kind = entity.kind,
                                name = entity.name,
                                usageCount = entity.usageCount,
                                createdAtEpochMs = entity.createdAtEpochMs,
                                updatedAtEpochMs = entity.updatedAtEpochMs,
                                lastUsedAtEpochMs = entity.lastUsedAtEpochMs
                            )
                        }
                    )
                }
            )
        } catch (error: Exception) {
            AppResult.Error(error.toExportError("Could not build the export snapshot. Try again."))
        }
    }
}

private data class ScopedReusableData(
    val reusableIngredients: List<com.example.app.features.ingredients.data.ReusableIngredientEntity>,
    val reusableCompounds: List<ReusableCompoundWithComponents>,
    val reusableEventCatalogEntries: List<com.example.app.features.eventcatalog.data.ReusableEventCatalogEntity>
)

interface ExportSnapshotDataAccess {
    suspend fun <T> readSnapshot(block: suspend () -> T): T
    suspend fun getIngestionEvents() = emptyList<com.example.app.features.ingestion.data.IngestionEventEntity>()
    suspend fun getExposureEvents() = emptyList<com.example.app.features.exposure.data.ExposureEventEntity>()
    suspend fun getOutbreakEvents() = emptyList<com.example.app.features.outbreak.data.OutbreakEventEntity>()
    suspend fun getReusableIngredients() = emptyList<com.example.app.features.ingredients.data.ReusableIngredientEntity>()
    suspend fun getReusableCompounds(): List<ReusableCompoundWithComponents> = emptyList()
    suspend fun getReusableEventCatalogEntries() = emptyList<com.example.app.features.eventcatalog.data.ReusableEventCatalogEntity>()
}

private fun ExportScope.toScopedReusableData(
    ingestions: List<ExportIngestionRecord>,
    exposures: List<ExportExposureRecord>,
    outbreaks: List<ExportOutbreakRecord>,
    reusableIngredients: List<com.example.app.features.ingredients.data.ReusableIngredientEntity>,
    reusableCompounds: List<ReusableCompoundWithComponents>,
    reusableEventCatalogEntries: List<com.example.app.features.eventcatalog.data.ReusableEventCatalogEntity>
): ScopedReusableData {
    return when (this) {
        ExportScope.AllRecords -> ScopedReusableData(
            reusableIngredients = reusableIngredients,
            reusableCompounds = reusableCompounds,
            reusableEventCatalogEntries = reusableEventCatalogEntries
        )

        is ExportScope.EventTypeRecords -> when (eventType) {
            RecordEventType.INGESTION -> {
                val directlyReferencedIngredientIds = ingestions
                    .filter { it.reusableEntityKind == ReusableEntityKind.INGREDIENT.storageValue }
                    .mapNotNull { it.reusableEntityId }
                    .toSet()
                val directlyReferencedCompoundIds = ingestions
                    .filter { it.reusableEntityKind == ReusableEntityKind.COMPOUND.storageValue }
                    .mapNotNull { it.reusableEntityId }
                    .toSet()
                val includedCompoundIds = reusableCompounds.collectCompoundClosureIds(directlyReferencedCompoundIds)
                val compoundIngredientIds = reusableCompounds
                    .filter { it.compound.id in includedCompoundIds }
                    .flatMap { compound -> compound.components.mapNotNull { it.ingredientId } }
                    .toSet()
                ScopedReusableData(
                    reusableIngredients = reusableIngredients.filter { it.id in directlyReferencedIngredientIds || it.id in compoundIngredientIds },
                    reusableCompounds = reusableCompounds.filter { it.compound.id in includedCompoundIds },
                    reusableEventCatalogEntries = emptyList()
                )
            }

            RecordEventType.EXPOSURE -> {
                val selectedCatalogIds = exposures.mapNotNull { it.exposureTypeCatalogId }.toSet()
                ScopedReusableData(
                    reusableIngredients = emptyList(),
                    reusableCompounds = emptyList(),
                    reusableEventCatalogEntries = reusableEventCatalogEntries.filter {
                        it.kind == ReusableEventCatalogKind.EXPOSURE_TYPE.storageValue && it.id in selectedCatalogIds
                    }
                )
            }

            RecordEventType.OUTBREAK -> {
                val selectedCatalogIds = outbreaks
                    .flatMap { listOfNotNull(it.outbreakDescriptorCatalogId, it.bodyLocationCatalogId) }
                    .toSet()
                ScopedReusableData(
                    reusableIngredients = emptyList(),
                    reusableCompounds = emptyList(),
                    reusableEventCatalogEntries = reusableEventCatalogEntries.filter { it.id in selectedCatalogIds }
                )
            }
        }
    }
}

private fun List<ReusableCompoundWithComponents>.collectCompoundClosureIds(seedIds: Set<Long>): Set<Long> {
    if (seedIds.isEmpty()) return emptySet()
    val compoundsById = associateBy { it.compound.id }
    val includedIds = seedIds.toMutableSet()
    val pendingIds = ArrayDeque(seedIds)
    while (pendingIds.isNotEmpty()) {
        val compoundId = pendingIds.removeFirst()
        val compound = compoundsById[compoundId] ?: continue
        compound.components.mapNotNull { it.childCompoundId }.forEach { childCompoundId ->
            if (includedIds.add(childCompoundId)) {
                pendingIds.addLast(childCompoundId)
            }
        }
    }
    return includedIds
}

private fun ExportScope.includes(eventType: RecordEventType): Boolean {
    return when (this) {
        ExportScope.AllRecords -> true
        is ExportScope.EventTypeRecords -> this.eventType == eventType
    }
}
