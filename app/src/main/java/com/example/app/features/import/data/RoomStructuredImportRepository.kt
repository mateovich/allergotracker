package com.example.app.features.import.data

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.core.export.schema.EXPORT_SCHEMA_VERSION
import com.example.app.core.export.schema.EventCatalogReferenceDto
import com.example.app.core.export.schema.ExposureExportDto
import com.example.app.core.export.schema.IngestionExportDto
import com.example.app.core.export.schema.OutbreakExportDto
import com.example.app.core.export.schema.ReusableCompoundExportDto
import com.example.app.core.export.schema.ReusableEntityReferenceDto
import com.example.app.core.export.schema.ReusableEventCatalogExportDto
import com.example.app.core.export.schema.ReusableIngredientExportDto
import com.example.app.core.export.schema.VersionedExportEnvelopeDto
import com.example.app.features.compounds.data.ReusableCompoundDao
import com.example.app.features.compounds.domain.CompoundComponentKind
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
import com.example.app.features.compounds.domain.CreateReusableCompoundComponentCommand
import com.example.app.features.compounds.domain.CreateReusableCompoundUseCase
import com.example.app.features.compounds.domain.UpdateReusableCompoundCommand
import com.example.app.features.compounds.domain.UpdateReusableCompoundComponentCommand
import com.example.app.features.compounds.domain.UpdateReusableCompoundUseCase
import com.example.app.features.eventcatalog.data.ReusableEventCatalogDao
import com.example.app.features.eventcatalog.data.ReusableEventCatalogEntity
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.canonicalizeReusableEventCatalogName
import com.example.app.features.eventcatalog.domain.normalizeReusableEventCatalogName
import com.example.app.features.import.domain.ImportDocumentContent
import com.example.app.features.import.domain.PreparedStructuredImport
import com.example.app.features.import.domain.StructuredImportApplyResult
import com.example.app.features.import.domain.StructuredImportRepository
import com.example.app.features.import.domain.StructuredImportReview
import com.example.app.features.import.domain.StructuredImportSummary
import com.example.app.features.ingredients.data.ReusableIngredientDao
import com.example.app.features.ingredients.domain.CreateReusableIngredientCommand
import com.example.app.features.ingredients.domain.CreateReusableIngredientUseCase
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CreateIngestionEventUseCase
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.outbreak.domain.CreateOutbreakEventCommand
import com.example.app.features.outbreak.domain.CreateOutbreakEventUseCase
import com.example.app.features.exposure.domain.CreateExposureEventCommand
import com.example.app.features.exposure.domain.CreateExposureEventUseCase
import com.example.app.features.import.domain.StructuredImportSummary as Summary
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class RoomStructuredImportRepository(
    private val reusableIngredientDao: ReusableIngredientDao,
    private val reusableCompoundDao: ReusableCompoundDao,
    private val reusableEventCatalogDao: ReusableEventCatalogDao,
    private val createReusableIngredientUseCase: CreateReusableIngredientUseCase,
    private val createReusableCompoundUseCase: CreateReusableCompoundUseCase,
    private val updateReusableCompoundUseCase: UpdateReusableCompoundUseCase,
    private val createIngestionEventUseCase: CreateIngestionEventUseCase,
    private val createExposureEventUseCase: CreateExposureEventUseCase,
    private val createOutbreakEventUseCase: CreateOutbreakEventUseCase,
    private val nowEpochMillis: () -> Long,
    private val runAtomically: suspend (suspend () -> AppResult<StructuredImportApplyResult>) -> AppResult<StructuredImportApplyResult>
) : StructuredImportRepository {
    override suspend fun review(document: ImportDocumentContent, envelope: VersionedExportEnvelopeDto): AppResult<StructuredImportReview> {
        if (envelope.schemaVersion != EXPORT_SCHEMA_VERSION) {
            return AppResult.Error(
                DomainError.ImportError(
                    "Unsupported schema version ${envelope.schemaVersion}. This app can import only $EXPORT_SCHEMA_VERSION files."
                )
            )
        }

        val validationError = validateEnvelope(envelope)
        if (validationError != null) {
            return AppResult.Error(validationError)
        }

        val ingredientNames = envelope.data.reusableIngredients.map { it.name.normalizeKey() }.toSet()
        val compoundNames = envelope.data.reusableCompounds.map { it.name.normalizeKey() }.toSet()
        val existingIngredientNames = reusableIngredientDao.observeAll().snapshot().associateBy { it.normalizedName }
        val existingCompoundNames = reusableCompoundDao.observeAll().snapshot().associateBy { it.compound.normalizedName }
        val existingCatalogNames = ReusableEventCatalogKind.entries.flatMap { kind ->
            reusableEventCatalogDao.observeByKind(kind.storageValue).snapshot().map { kind.storageValue to it.normalizedName }
        }.toSet()

        val summary = Summary(
            ingredientsToCreate = ingredientNames.count { it !in existingIngredientNames },
            ingredientsToReuse = ingredientNames.count { it in existingIngredientNames },
            compoundsToCreate = compoundNames.count { it !in existingCompoundNames },
            compoundsToUpdate = compoundNames.count { it in existingCompoundNames },
            eventCatalogEntriesToCreate = envelope.data.reusableEventCatalog.count { dto ->
                val kind = ReusableEventCatalogKind.fromStorageValue(dto.kind) ?: return@count true
                kind.storageValue to normalizeReusableEventCatalogName(dto.name) !in existingCatalogNames
            },
            eventCatalogEntriesToReuse = envelope.data.reusableEventCatalog.count { dto ->
                val kind = ReusableEventCatalogKind.fromStorageValue(dto.kind) ?: return@count false
                (kind.storageValue to normalizeReusableEventCatalogName(dto.name)) in existingCatalogNames
            },
            ingestionsToCreate = envelope.data.ingestions.size,
            exposuresToCreate = envelope.data.exposures.size,
            outbreaksToCreate = envelope.data.outbreaks.size
        )

        return AppResult.Success(
            StructuredImportReview(
                fileName = document.fileName,
                schemaVersion = envelope.schemaVersion,
                summary = summary,
                preparedImport = PreparedStructuredImport(document = document, envelope = envelope)
            )
        )
    }

    override suspend fun apply(review: StructuredImportReview): AppResult<StructuredImportApplyResult> {
        return try {
            runAtomically {
                val envelope = review.preparedImport.envelope
                val ingredientIdMap = linkedMapOf<Long, Long>()
                val compoundIdMap = linkedMapOf<Long, Long>()
                val catalogIdMap = linkedMapOf<CatalogKey, LocalCatalogValue>()

                for (ingredient in envelope.data.reusableIngredients) {
                    when (
                        val result = createReusableIngredientUseCase(
                            CreateReusableIngredientCommand(name = ingredient.name, notes = ingredient.notes)
                        )
                    ) {
                        is AppResult.Success -> ingredientIdMap[ingredient.id] = result.value.id
                        is AppResult.Error -> throw StructuredImportRollback(result.error)
                    }
                }

                for (entry in envelope.data.reusableEventCatalog) {
                    val kind = ReusableEventCatalogKind.fromStorageValue(entry.kind)
                        ?: throw StructuredImportRollback(DomainError.ImportError("Unsupported event catalog kind ${entry.kind}."))
                    val local = when (val result = upsertCatalogEntry(kind, entry)) {
                        is AppResult.Success -> result.value
                        is AppResult.Error -> throw StructuredImportRollback(result.error)
                    }
                    catalogIdMap[CatalogKey(kind, entry.id)] = local
                }

                val remainingCompounds = envelope.data.reusableCompounds.toMutableList()
                while (remainingCompounds.isNotEmpty()) {
                    var progressed = false
                    val iterator = remainingCompounds.iterator()
                    while (iterator.hasNext()) {
                        val compound = iterator.next()
                        if (!compound.components.all { componentRefResolved(it.componentRef, ingredientIdMap, compoundIdMap) }) {
                            continue
                        }
                        when (val result = upsertCompound(compound, ingredientIdMap, compoundIdMap)) {
                            is AppResult.Success -> {
                                compoundIdMap[compound.id] = result.value
                                iterator.remove()
                                progressed = true
                            }

                            is AppResult.Error -> throw StructuredImportRollback(result.error)
                        }
                    }
                    if (!progressed) {
                        throw StructuredImportRollback(
                            DomainError.ImportError(
                                "Compound references are recursive or unresolved. Fix the imported compound graph and try again."
                            )
                        )
                    }
                }

                for (ingestion in envelope.data.ingestions) {
                    val mapped = ingestion.reusableEntityRef?.toMappedReusableEntity(ingredientIdMap, compoundIdMap)
                        ?: MappedReusableEntity(null, null)
                    when (
                        val result = createIngestionEventUseCase(
                            CreateIngestionEventCommand(
                                itemName = ingestion.itemName,
                                quantityLevel = ingestion.quantityLevel,
                                occurredAtEpochMs = ingestion.occurredAt.toEpochMillis(),
                                notes = ingestion.notes,
                                reusableEntityKind = mapped.kind,
                                reusableEntityId = mapped.id
                            )
                        )
                    ) {
                        is AppResult.Success -> Unit
                        is AppResult.Error -> throw StructuredImportRollback(result.error)
                    }
                }

                for (exposure in envelope.data.exposures) {
                    val mappedCatalog = exposure.exposureTypeCatalogRef?.toMappedCatalog(catalogIdMap)
                    when (
                        val result = createExposureEventUseCase(
                            CreateExposureEventCommand(
                                exposureType = mappedCatalog?.name ?: exposure.exposureType,
                                occurredAtEpochMs = exposure.occurredAt.toEpochMillis(),
                                notes = exposure.notes,
                                intensityLevel = exposure.intensityLevel,
                                exposureTypeCatalogId = mappedCatalog?.id
                            )
                        )
                    ) {
                        is AppResult.Success -> Unit
                        is AppResult.Error -> throw StructuredImportRollback(result.error)
                    }
                }

                for (outbreak in envelope.data.outbreaks) {
                    val descriptor = outbreak.outbreakDescriptorCatalogRef?.toMappedCatalog(catalogIdMap)
                    val bodyLocation = outbreak.bodyLocationCatalogRef?.toMappedCatalog(catalogIdMap)
                    when (
                        val result = createOutbreakEventUseCase(
                            CreateOutbreakEventCommand(
                                outbreakDescriptor = descriptor?.name ?: outbreak.outbreakDescriptor,
                                bodyLocation = bodyLocation?.name ?: outbreak.bodyLocation,
                                occurredAtEpochMs = outbreak.occurredAt.toEpochMillis(),
                                notes = outbreak.notes,
                                intensityLevel = outbreak.intensityLevel,
                                outbreakDescriptorCatalogId = descriptor?.id,
                                bodyLocationCatalogId = bodyLocation?.id
                            )
                        )
                    ) {
                        is AppResult.Success -> Unit
                        is AppResult.Error -> throw StructuredImportRollback(result.error)
                    }
                }

                AppResult.Success(
                    StructuredImportApplyResult(
                        fileName = review.fileName,
                        summary = review.summary
                    )
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (rollback: StructuredImportRollback) {
            AppResult.Error(rollback.error)
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't import the validated dataset locally. Try again."))
        }
    }

    private suspend fun upsertCompound(
        dto: ReusableCompoundExportDto,
        ingredientIdMap: Map<Long, Long>,
        compoundIdMap: Map<Long, Long>
    ): AppResult<Long> {
        val normalizedName = dto.name.normalizeKey()
        val existing = reusableCompoundDao.findByNormalizedName(normalizedName)
        val mappedComponents = dto.components.sortedBy { it.position }.map { component ->
            val mappedId = when (component.componentRef.kind) {
                ReusableEntityKind.INGREDIENT.storageValue -> ingredientIdMap[component.componentRef.id]
                ReusableEntityKind.COMPOUND.storageValue -> compoundIdMap[component.componentRef.id]
                else -> null
            }
            if (mappedId == null) {
                return AppResult.Error(
                    DomainError.ImportError("Compound ${dto.name} references a reusable item that could not be mapped locally.")
                )
            }
            MappedComponent(
                kind = if (component.componentRef.kind == ReusableEntityKind.INGREDIENT.storageValue) {
                    CompoundComponentKind.INGREDIENT
                } else {
                    CompoundComponentKind.COMPOUND
                },
                reusableEntityId = mappedId,
                proportionBasisPoints = component.proportionBasisPoints
            )
        }

        return if (existing == null) {
            when (
                val result = createReusableCompoundUseCase(
                    CreateReusableCompoundCommand(
                        name = dto.name,
                        notes = dto.notes,
                        components = mappedComponents.map { component ->
                            CreateReusableCompoundComponentCommand(
                                kind = component.kind,
                                reusableEntityId = component.reusableEntityId,
                                proportionBasisPoints = component.proportionBasisPoints
                            )
                        }
                    )
                )
            ) {
                is AppResult.Success -> AppResult.Success(result.value.id)
                is AppResult.Error -> result
            }
        } else {
            when (
                val result = updateReusableCompoundUseCase(
                    UpdateReusableCompoundCommand(
                        id = existing.id,
                        name = dto.name,
                        notes = dto.notes,
                        components = mappedComponents.map { component ->
                            UpdateReusableCompoundComponentCommand(
                                kind = component.kind,
                                reusableEntityId = component.reusableEntityId,
                                proportionBasisPoints = component.proportionBasisPoints
                            )
                        }
                    )
                )
            ) {
                is AppResult.Success -> AppResult.Success(result.value.id)
                is AppResult.Error -> result
            }
        }
    }

    private suspend fun upsertCatalogEntry(
        kind: ReusableEventCatalogKind,
        dto: ReusableEventCatalogExportDto
    ): AppResult<LocalCatalogValue> {
        return try {
            val normalizedName = normalizeReusableEventCatalogName(dto.name)
            val existing = reusableEventCatalogDao.findByNormalizedName(kind.storageValue, normalizedName)
            if (existing != null) {
                val canonicalName = canonicalizeReusableEventCatalogName(dto.name)
                if (existing.name != canonicalName) {
                    reusableEventCatalogDao.update(existing.copy(name = canonicalName, updatedAtEpochMs = nowEpochMillis()))
                }
                return AppResult.Success(LocalCatalogValue(id = existing.id, name = canonicalName))
            }
            val now = nowEpochMillis()
            val canonicalName = canonicalizeReusableEventCatalogName(dto.name)
            val id = reusableEventCatalogDao.insert(
                ReusableEventCatalogEntity(
                    kind = kind.storageValue,
                    name = canonicalName,
                    normalizedName = normalizedName,
                    usageCount = 0,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    lastUsedAtEpochMs = now
                )
            )
            AppResult.Success(LocalCatalogValue(id = id, name = canonicalName))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't import saved values locally. Try again."))
        }
    }

    private suspend fun validateEnvelope(envelope: VersionedExportEnvelopeDto): DomainError? {
        duplicateIds(envelope.data.reusableIngredients.map(ReusableIngredientExportDto::id), "reusable ingredient")?.let { message ->
            return DomainError.ImportError(message)
        }
        duplicateIds(envelope.data.reusableCompounds.map(ReusableCompoundExportDto::id), "reusable compound")?.let { message ->
            return DomainError.ImportError(message)
        }
        duplicateCatalogIds(envelope.data.reusableEventCatalog)?.let { message ->
            return DomainError.ImportError(message)
        }

        val timestampError = validateContractTimestamps(envelope)
        if (timestampError != null) {
            return timestampError
        }

        val ingredientsById = envelope.data.reusableIngredients.associateBy { it.id }
        val compoundsById = envelope.data.reusableCompounds.associateBy { it.id }
        val catalogByKey = mutableMapOf<CatalogKey, ReusableEventCatalogExportDto>()
        for (dto in envelope.data.reusableEventCatalog) {
            val key = CatalogKey.fromStorageValue(dto.kind, dto.id)
                ?: return DomainError.ImportError("Unsupported event catalog kind ${dto.kind}.")
            catalogByKey[key] = dto
        }

        duplicateNames(envelope.data.reusableIngredients.map(ReusableIngredientExportDto::name))?.let { message ->
            return DomainError.ImportError(message)
        }
        duplicateNames(envelope.data.reusableCompounds.map(ReusableCompoundExportDto::name))?.let { message ->
            return DomainError.ImportError(message)
        }
        duplicateCatalogNames(envelope.data.reusableEventCatalog)?.let { message ->
            return DomainError.ImportError(message)
        }

        for (compound in envelope.data.reusableCompounds) {
            for (component in compound.components) {
                when (component.componentRef.kind) {
                    ReusableEntityKind.INGREDIENT.storageValue -> if (component.componentRef.id !in ingredientsById) {
                        return DomainError.ImportError("Compound ${compound.name} references an ingredient that is not present in the import file.")
                    }

                    ReusableEntityKind.COMPOUND.storageValue -> if (component.componentRef.id !in compoundsById) {
                        return DomainError.ImportError("Compound ${compound.name} references a child compound that is not present in the import file.")
                    }

                    else -> return DomainError.ImportError("Compound ${compound.name} uses unsupported reusable kind ${component.componentRef.kind}.")
                }
            }
        }

        for (ingestion in envelope.data.ingestions) {
            val ref = ingestion.reusableEntityRef ?: continue
            when (ref.kind) {
                ReusableEntityKind.INGREDIENT.storageValue -> if (ref.id !in ingredientsById) {
                    return DomainError.ImportError("Ingestion ${ingestion.itemName} references an ingredient that is not present in the import file.")
                }

                ReusableEntityKind.COMPOUND.storageValue -> if (ref.id !in compoundsById) {
                    return DomainError.ImportError("Ingestion ${ingestion.itemName} references a compound that is not present in the import file.")
                }

                else -> return DomainError.ImportError("Ingestion ${ingestion.itemName} uses unsupported reusable kind ${ref.kind}.")
            }
        }

        validateExposureCatalogReference(envelope.data.exposures, catalogByKey)?.let { return it }
        validateOutbreakCatalogReference(envelope.data.outbreaks, catalogByKey)?.let { return it }
        return null
    }

    private fun validateExposureCatalogReference(
        exposures: List<ExposureExportDto>,
        catalogByKey: Map<CatalogKey, ReusableEventCatalogExportDto>
    ): DomainError? {
        for (exposure in exposures) {
            val ref = exposure.exposureTypeCatalogRef ?: continue
            val key = CatalogKey.fromStorageValue(ref.kind, ref.id)
                ?: return DomainError.ImportError("Exposure ${exposure.exposureType} uses unsupported catalog kind ${ref.kind}.")
            if (key !in catalogByKey) {
                return DomainError.ImportError("Exposure ${exposure.exposureType} references a saved value that is not present in the import file.")
            }
        }
        return null
    }

    private fun validateOutbreakCatalogReference(
        outbreaks: List<OutbreakExportDto>,
        catalogByKey: Map<CatalogKey, ReusableEventCatalogExportDto>
    ): DomainError? {
        for (outbreak in outbreaks) {
            val descriptor = outbreak.outbreakDescriptorCatalogRef
            val descriptorKey = descriptor?.let { CatalogKey.fromStorageValue(it.kind, it.id) }
            if (descriptor != null && descriptorKey == null) {
                return DomainError.ImportError("Outbreak ${outbreak.outbreakDescriptor} uses unsupported descriptor catalog kind ${descriptor.kind}.")
            }
            if (descriptorKey != null && descriptorKey !in catalogByKey) {
                return DomainError.ImportError("Outbreak ${outbreak.outbreakDescriptor} references a saved descriptor that is not present in the import file.")
            }
            val bodyLocation = outbreak.bodyLocationCatalogRef
            val bodyLocationKey = bodyLocation?.let { CatalogKey.fromStorageValue(it.kind, it.id) }
            if (bodyLocation != null && bodyLocationKey == null) {
                return DomainError.ImportError("Outbreak ${outbreak.outbreakDescriptor} uses unsupported body-location catalog kind ${bodyLocation.kind}.")
            }
            if (bodyLocationKey != null && bodyLocationKey !in catalogByKey) {
                return DomainError.ImportError("Outbreak ${outbreak.outbreakDescriptor} references a saved body location that is not present in the import file.")
            }
        }
        return null
    }
}

private data class CatalogKey(val kind: ReusableEventCatalogKind, val importId: Long) {
    companion object {
        fun fromStorageValue(kind: String, importId: Long): CatalogKey? {
            val resolvedKind = ReusableEventCatalogKind.fromStorageValue(kind) ?: return null
            return CatalogKey(resolvedKind, importId)
        }
    }
}

private data class LocalCatalogValue(
    val id: Long,
    val name: String
)

private data class MappedComponent(
    val kind: CompoundComponentKind,
    val reusableEntityId: Long,
    val proportionBasisPoints: Int?
)

private data class MappedReusableEntity(
    val kind: ReusableEntityKind?,
    val id: Long?
)

private class StructuredImportRollback(
    val error: DomainError
) : RuntimeException()

private suspend fun <T> kotlinx.coroutines.flow.Flow<List<T>>.snapshot(): List<T> = first()

private fun String.normalizeKey(): String = trim().replace(Regex("\\s+"), " ").lowercase()

private fun duplicateNames(names: List<String>): String? {
    val duplicate = names.map { it.normalizeKey() }.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
    return duplicate?.let { "Import file contains duplicate reusable names. Rename duplicates before retrying." }
}

private fun duplicateIds(ids: List<Long>, itemLabel: String): String? {
    val duplicate = ids.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
    return duplicate?.let { "Import file contains duplicate $itemLabel IDs. Regenerate or fix the source file before retrying." }
}

private fun duplicateCatalogNames(entries: List<ReusableEventCatalogExportDto>): String? {
    val duplicate = entries
        .map { it.kind to normalizeReusableEventCatalogName(it.name) }
        .groupingBy { it }
        .eachCount()
        .entries
        .firstOrNull { it.value > 1 }
    return duplicate?.let { "Import file contains duplicate saved values for the same catalog kind. Rename duplicates before retrying." }
}

private fun duplicateCatalogIds(entries: List<ReusableEventCatalogExportDto>): String? {
    val duplicate = entries
        .map { it.kind to it.id }
        .groupingBy { it }
        .eachCount()
        .entries
        .firstOrNull { it.value > 1 }
    return duplicate?.let { "Import file contains duplicate saved-value IDs for the same catalog kind. Regenerate or fix the source file before retrying." }
}

private fun validateContractTimestamps(envelope: VersionedExportEnvelopeDto): DomainError? {
    parseTimestamp(envelope.exportedAt, "exported_at")?.let { return it }

    envelope.data.ingestions.forEach { ingestion ->
        parseTimestamp(ingestion.occurredAt, "ingestion occurred_at for ${ingestion.itemName}")?.let { return it }
        parseTimestamp(ingestion.createdAt, "ingestion created_at for ${ingestion.itemName}")?.let { return it }
    }
    envelope.data.exposures.forEach { exposure ->
        parseTimestamp(exposure.occurredAt, "exposure occurred_at for ${exposure.exposureType}")?.let { return it }
        parseTimestamp(exposure.createdAt, "exposure created_at for ${exposure.exposureType}")?.let { return it }
    }
    envelope.data.outbreaks.forEach { outbreak ->
        parseTimestamp(outbreak.occurredAt, "outbreak occurred_at for ${outbreak.outbreakDescriptor}")?.let { return it }
        parseTimestamp(outbreak.createdAt, "outbreak created_at for ${outbreak.outbreakDescriptor}")?.let { return it }
    }
    envelope.data.reusableIngredients.forEach { ingredient ->
        parseTimestamp(ingredient.createdAt, "reusable ingredient created_at for ${ingredient.name}")?.let { return it }
        parseTimestamp(ingredient.updatedAt, "reusable ingredient updated_at for ${ingredient.name}")?.let { return it }
    }
    envelope.data.reusableCompounds.forEach { compound ->
        parseTimestamp(compound.createdAt, "reusable compound created_at for ${compound.name}")?.let { return it }
        parseTimestamp(compound.updatedAt, "reusable compound updated_at for ${compound.name}")?.let { return it }
    }
    envelope.data.reusableEventCatalog.forEach { entry ->
        parseTimestamp(entry.createdAt, "saved value created_at for ${entry.name}")?.let { return it }
        parseTimestamp(entry.updatedAt, "saved value updated_at for ${entry.name}")?.let { return it }
        parseTimestamp(entry.lastUsedAt, "saved value last_used_at for ${entry.name}")?.let { return it }
    }
    envelope.data.normalizedBaseIngredients.forEach { normalized ->
        parseTimestamp(
            normalized.sourceEvent.occurredAt,
            "normalized_base_ingredients source_event.occurred_at for ${normalized.sourceEvent.itemName}"
        )?.let { return it }
    }

    return null
}

private fun parseTimestamp(value: String, label: String): DomainError? {
    return try {
        Instant.parse(value)
        null
    } catch (_: Exception) {
        DomainError.ImportError("Import file has invalid timestamp '$label'. Use ISO-8601 UTC values from the export contract.")
    }
}

private fun componentRefResolved(
    componentRef: ReusableEntityReferenceDto,
    ingredientIdMap: Map<Long, Long>,
    compoundIdMap: Map<Long, Long>
): Boolean {
    return when (componentRef.kind) {
        ReusableEntityKind.INGREDIENT.storageValue -> componentRef.id in ingredientIdMap
        ReusableEntityKind.COMPOUND.storageValue -> componentRef.id in compoundIdMap
        else -> false
    }
}

private fun ReusableEntityReferenceDto.toMappedReusableEntity(
    ingredientIdMap: Map<Long, Long>,
    compoundIdMap: Map<Long, Long>
): MappedReusableEntity? {
    return when (kind) {
        ReusableEntityKind.INGREDIENT.storageValue -> ingredientIdMap[id]?.let { MappedReusableEntity(ReusableEntityKind.INGREDIENT, it) }
        ReusableEntityKind.COMPOUND.storageValue -> compoundIdMap[id]?.let { MappedReusableEntity(ReusableEntityKind.COMPOUND, it) }
        else -> null
    }
}

private fun EventCatalogReferenceDto.toMappedCatalog(catalogIdMap: Map<CatalogKey, LocalCatalogValue>): LocalCatalogValue? {
    val key = CatalogKey.fromStorageValue(kind, id) ?: return null
    return catalogIdMap[key]
}

private fun String.toEpochMillis(): Long = Instant.parse(this).toEpochMilli()
