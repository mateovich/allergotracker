package io.github.mateovich.allergotracker.features.export.data

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundWithComponents
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.export.domain.ExportExposureRecord
import io.github.mateovich.allergotracker.features.export.domain.ExportEventCatalogReference
import io.github.mateovich.allergotracker.features.export.domain.ExportIngestionRecord
import io.github.mateovich.allergotracker.features.export.domain.ExportOutbreakRecord
import io.github.mateovich.allergotracker.features.export.domain.ExportReusableCompoundComponentRecord
import io.github.mateovich.allergotracker.features.export.domain.ExportReusableCompoundRecord
import io.github.mateovich.allergotracker.features.export.domain.ExportReusableEntityReference
import io.github.mateovich.allergotracker.features.export.domain.ExportReusableEventCatalogRecord
import io.github.mateovich.allergotracker.features.export.domain.ExportReusableIngredientRecord
import io.github.mateovich.allergotracker.features.export.domain.ExportScope
import io.github.mateovich.allergotracker.features.export.domain.ExportSnapshot
import io.github.mateovich.allergotracker.features.export.domain.ExportSnapshotRepository
import io.github.mateovich.allergotracker.features.export.domain.toExportError
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.records.domain.RecordEventType

class RoomExportSnapshotRepository(
    private val dataAccess: ExportSnapshotDataAccess
) : ExportSnapshotRepository {
    override suspend fun getSnapshot(scope: ExportScope): AppResult<ExportSnapshot> {
        return try {
            AppResult.Success(
                dataAccess.readSnapshot {
                    val ingestionEntities = if (scope.includes(RecordEventType.INGESTION)) dataAccess.getIngestionEvents() else emptyList()
                    val exposureEntities = if (scope.includes(RecordEventType.EXPOSURE)) dataAccess.getExposureEvents() else emptyList()
                    val outbreakEntities = if (scope.includes(RecordEventType.OUTBREAK)) dataAccess.getOutbreakEvents() else emptyList()
                    val reusableIngredients = dataAccess.getReusableIngredients().sortedBy { it.id }
                    val reusableCompounds = dataAccess.getReusableCompounds().sortedBy { it.compound.id }
                    val reusableEventCatalogEntries = dataAccess.getReusableEventCatalogEntries().sortedWith(compareBy({ it.kind }, { it.id }))
                    val scopedReusableData = scope.toScopedReusableData(
                        ingestions = ingestionEntities,
                        exposures = exposureEntities,
                        outbreaks = outbreakEntities,
                        reusableIngredients = reusableIngredients,
                        reusableCompounds = reusableCompounds,
                        reusableEventCatalogEntries = reusableEventCatalogEntries
                    )
                    val reusableIngredientIds = scopedReusableData.reusableIngredients.map { it.id }.toSet()
                    val reusableCompoundIds = scopedReusableData.reusableCompounds.map { it.compound.id }.toSet()
                    val reusableEventCatalogEntriesById = scopedReusableData.reusableEventCatalogEntries.associateBy { it.id }
                    ExportSnapshot(
                        ingestions = ingestionEntities.map { entity ->
                            ExportIngestionRecord(
                                id = entity.id,
                                itemName = entity.itemName,
                                quantityLevel = entity.quantityLevel,
                                occurredAtEpochMs = entity.occurredAtEpochMs,
                                notes = entity.notes,
                                reusableEntityRef = entity.toReusableEntityReferenceOrNull(
                                    reusableIngredientIds = reusableIngredientIds,
                                    reusableCompoundIds = reusableCompoundIds
                                ),
                                createdAtEpochMs = entity.createdAtEpochMs
                            )
                        },
                        exposures = exposureEntities.map { entity ->
                            ExportExposureRecord(
                                id = entity.id,
                                exposureType = entity.exposureType,
                                occurredAtEpochMs = entity.occurredAtEpochMs,
                                notes = entity.notes,
                                intensityLevel = entity.intensityLevel,
                                exposureTypeCatalogRef = entity.toCatalogReferenceOrNull(
                                    expectedKind = ReusableEventCatalogKind.EXPOSURE_TYPE.storageValue,
                                    catalogEntriesById = reusableEventCatalogEntriesById,
                                    ownerDescription = "exposure record ${entity.id}",
                                    fieldLabel = "exposure_type_catalog_id"
                                ),
                                createdAtEpochMs = entity.createdAtEpochMs,
                                place = entity.place
                            )
                        },
                        outbreaks = outbreakEntities.map { entity ->
                            ExportOutbreakRecord(
                                id = entity.id,
                                outbreakDescriptor = entity.outbreakDescriptor,
                                bodyLocation = entity.bodyLocation,
                                occurredAtEpochMs = entity.occurredAtEpochMs,
                                notes = entity.notes,
                                intensityLevel = entity.intensityLevel,
                                outbreakDescriptorCatalogRef = entity.outbreakDescriptorCatalogId.toCatalogReferenceOrNull(
                                    expectedKind = ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR.storageValue,
                                    catalogEntriesById = reusableEventCatalogEntriesById,
                                    ownerDescription = "outbreak record ${entity.id}",
                                    fieldLabel = "outbreak_descriptor_catalog_id"
                                ),
                                bodyLocationCatalogRef = entity.bodyLocationCatalogId.toCatalogReferenceOrNull(
                                    expectedKind = ReusableEventCatalogKind.BODY_LOCATION.storageValue,
                                    catalogEntriesById = reusableEventCatalogEntriesById,
                                    ownerDescription = "outbreak record ${entity.id}",
                                    fieldLabel = "body_location_catalog_id"
                                ),
                                createdAtEpochMs = entity.createdAtEpochMs
                            )
                        },
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
                                            componentRef = component.toComponentReference(
                                                compoundId = compound.compound.id,
                                                reusableIngredientIds = reusableIngredientIds,
                                                reusableCompoundIds = reusableCompoundIds
                                            ),
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
        } catch (error: MissingExportReferenceException) {
            AppResult.Error(DomainError.ExportError(error.message ?: "Could not build the export snapshot. Try again."))
        } catch (error: Exception) {
            AppResult.Error(error.toExportError("Could not build the export snapshot. Try again."))
        }
    }
}

private class MissingExportReferenceException(message: String) : IllegalStateException(message)

private data class ScopedReusableData(
    val reusableIngredients: List<io.github.mateovich.allergotracker.features.ingredients.data.ReusableIngredientEntity>,
    val reusableCompounds: List<ReusableCompoundWithComponents>,
    val reusableEventCatalogEntries: List<io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogEntity>
)

interface ExportSnapshotDataAccess {
    suspend fun <T> readSnapshot(block: suspend () -> T): T
    suspend fun getIngestionEvents() = emptyList<io.github.mateovich.allergotracker.features.ingestion.data.IngestionEventEntity>()
    suspend fun getExposureEvents() = emptyList<io.github.mateovich.allergotracker.features.exposure.data.ExposureEventEntity>()
    suspend fun getOutbreakEvents() = emptyList<io.github.mateovich.allergotracker.features.outbreak.data.OutbreakEventEntity>()
    suspend fun getReusableIngredients() = emptyList<io.github.mateovich.allergotracker.features.ingredients.data.ReusableIngredientEntity>()
    suspend fun getReusableCompounds(): List<ReusableCompoundWithComponents> = emptyList()
    suspend fun getReusableEventCatalogEntries() = emptyList<io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogEntity>()
}

private fun ExportScope.toScopedReusableData(
    ingestions: List<io.github.mateovich.allergotracker.features.ingestion.data.IngestionEventEntity>,
    exposures: List<io.github.mateovich.allergotracker.features.exposure.data.ExposureEventEntity>,
    outbreaks: List<io.github.mateovich.allergotracker.features.outbreak.data.OutbreakEventEntity>,
    reusableIngredients: List<io.github.mateovich.allergotracker.features.ingredients.data.ReusableIngredientEntity>,
    reusableCompounds: List<ReusableCompoundWithComponents>,
    reusableEventCatalogEntries: List<io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogEntity>
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
                    reusableEventCatalogEntries = reusableEventCatalogEntries.filter { it.id in selectedCatalogIds }
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

private fun io.github.mateovich.allergotracker.features.ingestion.data.IngestionEventEntity.toReusableEntityReferenceOrNull(
    reusableIngredientIds: Set<Long>,
    reusableCompoundIds: Set<Long>
): ExportReusableEntityReference? {
    val kind = reusableEntityKind
    val id = reusableEntityId
    if (kind == null && id == null) return null
    if (kind == null || id == null) {
        throw MissingExportReferenceException(
            "Export blocked: ingestion record ${this.id} has an incomplete reusable entity reference; " +
                "both reusable_entity_kind and reusable_entity_id are required. " +
                "Edit that ingestion and set both reusable reference fields before exporting."
        )
    }
    return when (kind) {
        ReusableEntityKind.INGREDIENT.storageValue -> {
            if (id !in reusableIngredientIds) {
                throw MissingExportReferenceException(
                    "Export blocked: ingestion record ${this.id} references missing reusable ingredient $id in reusable_entity_id. " +
                        "Edit that ingestion or restore the reusable ingredient before exporting."
                )
            }
            ExportReusableEntityReference(kind = kind, id = id)
        }

        ReusableEntityKind.COMPOUND.storageValue -> {
            if (id !in reusableCompoundIds) {
                throw MissingExportReferenceException(
                    "Export blocked: ingestion record ${this.id} references missing reusable compound $id in reusable_entity_id. " +
                        "Edit that ingestion or restore the reusable compound before exporting."
                )
            }
            ExportReusableEntityReference(kind = kind, id = id)
        }

        else -> {
            throw MissingExportReferenceException(
                "Export blocked: ingestion record ${this.id} references unsupported reusable_entity_kind '$kind'. " +
                    "Edit that ingestion and choose a supported reusable reference before exporting."
            )
        }
    }
}

private fun io.github.mateovich.allergotracker.features.exposure.data.ExposureEventEntity.toCatalogReferenceOrNull(
    expectedKind: String,
    catalogEntriesById: Map<Long, io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogEntity>,
    ownerDescription: String,
    fieldLabel: String
): ExportEventCatalogReference? {
    return exposureTypeCatalogId.toCatalogReferenceOrNull(expectedKind, catalogEntriesById, ownerDescription, fieldLabel)
}

private fun Long?.toCatalogReferenceOrNull(
    expectedKind: String,
    catalogEntriesById: Map<Long, io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogEntity>,
    ownerDescription: String,
    fieldLabel: String
): ExportEventCatalogReference? {
    val id = this ?: return null
    val entry = catalogEntriesById[id]
        ?: throw MissingExportReferenceException(
            "Export blocked: $ownerDescription references missing catalog entry $id in $fieldLabel; expected '$expectedKind'. " +
                "Edit the ${ownerDescription.substringBefore(" record")} saved value before exporting."
        )
    if (entry.kind != expectedKind) {
        throw MissingExportReferenceException(
            "Export blocked: $ownerDescription references catalog entry $id in $fieldLabel; " +
                "expected '$expectedKind', actual '${entry.kind}'. " +
                "Edit the ${ownerDescription.substringBefore(" record")} saved value before exporting."
        )
    }
    return ExportEventCatalogReference(kind = entry.kind, id = entry.id)
}

private fun io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundComponentEntity.toComponentReference(
    compoundId: Long,
    reusableIngredientIds: Set<Long>,
    reusableCompoundIds: Set<Long>
): ExportReusableEntityReference {
    return when (componentKind) {
        ReusableEntityKind.INGREDIENT.storageValue -> {
            val ingredientId = ingredientId
                ?: throw MissingExportReferenceException(
                    "Export blocked: compound $compoundId component $id is missing ingredient_id. " +
                        "Edit that compound component before exporting."
                )
            if (ingredientId !in reusableIngredientIds) {
                throw MissingExportReferenceException(
                    "Export blocked: compound $compoundId component $id references missing reusable ingredient $ingredientId in ingredient_id. " +
                        "Edit that compound component or restore the reusable ingredient before exporting."
                )
            }
            ExportReusableEntityReference(kind = componentKind, id = ingredientId)
        }

        ReusableEntityKind.COMPOUND.storageValue -> {
            val childCompoundId = childCompoundId
                ?: throw MissingExportReferenceException(
                    "Export blocked: compound $compoundId component $id is missing child_compound_id. " +
                        "Edit that compound component before exporting."
                )
            if (childCompoundId !in reusableCompoundIds) {
                throw MissingExportReferenceException(
                    "Export blocked: compound $compoundId component $id references missing child compound $childCompoundId in child_compound_id. " +
                        "Edit that compound component or restore the child compound before exporting."
                )
            }
            ExportReusableEntityReference(kind = componentKind, id = childCompoundId)
        }

        else -> {
            throw MissingExportReferenceException(
                "Export blocked: compound $compoundId component $id uses unsupported componentKind '$componentKind'. " +
                    "Edit that compound component before exporting."
            )
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
