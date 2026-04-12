package com.example.app.features.export.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.core.export.schema.EXPORT_SCHEMA_VERSION
import com.example.app.core.export.schema.ExposureExportDto
import com.example.app.core.export.schema.IngestionExportDto
import com.example.app.core.export.schema.OutbreakExportDto
import com.example.app.core.export.schema.ReusableCompoundComponentExportDto
import com.example.app.core.export.schema.ReusableCompoundExportDto
import com.example.app.core.export.schema.ReusableEventCatalogExportDto
import com.example.app.core.export.schema.ReusableIngredientExportDto
import com.example.app.core.export.schema.VersionedExportDataDto
import com.example.app.core.export.schema.VersionedExportEnvelopeDto
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import java.time.Instant
import java.time.format.DateTimeFormatter

open class GenerateVersionedJsonExportUseCase(
    private val snapshotRepository: ExportSnapshotRepository,
    private val writer: ExportArtifactWriter,
    private val serializer: VersionedJsonExportSerializer,
    private val currentTimeProvider: CurrentTimeProvider
) {
    open suspend operator fun invoke(configuration: ExportConfiguration): AppResult<GeneratedJsonExport> {
        val snapshot = when (val result = snapshotRepository.getSnapshot(configuration.scope)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return AppResult.Error(result.error)
        }
        val exportedAt = currentTimeProvider.nowEpochMillis().toUtcIsoString()
        val envelope = VersionedExportEnvelopeDto(
            schemaVersion = EXPORT_SCHEMA_VERSION,
            exportedAt = exportedAt,
            data = snapshot.toDto()
        )
        val serialized = when (val result = serializer.serialize(envelope)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return AppResult.Error(result.error)
        }
        val artifact = when (val result = writer.writeJson(buildFileName(exportedAt), serialized)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return AppResult.Error(result.error)
        }
        return AppResult.Success(
            GeneratedJsonExport(
                configuration = configuration,
                schemaVersion = EXPORT_SCHEMA_VERSION,
                exportedAt = exportedAt,
                artifact = artifact
            )
        )
    }

    private fun buildFileName(exportedAt: String): String {
        val safeTimestamp = exportedAt.replace("-", "").replace(":", "").replace(".", "").replace("+", "_")
        return "app-export-v${EXPORT_SCHEMA_VERSION.replace('.', '_')}-$safeTimestamp.json"
    }
}

interface VersionedJsonExportSerializer {
    fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String>
}

private fun ExportSnapshot.toDto(): VersionedExportDataDto {
    return VersionedExportDataDto(
        ingestions = ingestions.map { record ->
            IngestionExportDto(
                id = record.id,
                itemName = record.itemName,
                quantityLevel = record.quantityLevel,
                occurredAt = record.occurredAtEpochMs.toUtcIsoString(),
                notes = record.notes,
                reusableEntityKind = record.reusableEntityKind,
                reusableEntityId = record.reusableEntityId,
                createdAt = record.createdAtEpochMs.toUtcIsoString()
            )
        },
        exposures = exposures.map { record ->
            ExposureExportDto(
                id = record.id,
                exposureType = record.exposureType,
                occurredAt = record.occurredAtEpochMs.toUtcIsoString(),
                notes = record.notes,
                intensityLevel = record.intensityLevel,
                exposureTypeCatalogId = record.exposureTypeCatalogId,
                createdAt = record.createdAtEpochMs.toUtcIsoString()
            )
        },
        outbreaks = outbreaks.map { record ->
            OutbreakExportDto(
                id = record.id,
                outbreakDescriptor = record.outbreakDescriptor,
                bodyLocation = record.bodyLocation,
                occurredAt = record.occurredAtEpochMs.toUtcIsoString(),
                notes = record.notes,
                intensityLevel = record.intensityLevel,
                outbreakDescriptorCatalogId = record.outbreakDescriptorCatalogId,
                bodyLocationCatalogId = record.bodyLocationCatalogId,
                createdAt = record.createdAtEpochMs.toUtcIsoString()
            )
        },
        reusableIngredients = reusableIngredients.map { record ->
            ReusableIngredientExportDto(
                id = record.id,
                name = record.name,
                notes = record.notes,
                createdAt = record.createdAtEpochMs.toUtcIsoString(),
                updatedAt = record.updatedAtEpochMs.toUtcIsoString()
            )
        },
        reusableCompounds = reusableCompounds.map { record ->
            ReusableCompoundExportDto(
                id = record.id,
                name = record.name,
                notes = record.notes,
                compositionStatus = record.compositionStatus,
                components = record.components.map { component ->
                    ReusableCompoundComponentExportDto(
                        id = component.id,
                        position = component.position,
                        kind = component.kind,
                        ingredientId = component.ingredientId,
                        childCompoundId = component.childCompoundId,
                        proportionBasisPoints = component.proportionBasisPoints
                    )
                },
                createdAt = record.createdAtEpochMs.toUtcIsoString(),
                updatedAt = record.updatedAtEpochMs.toUtcIsoString()
            )
        },
        reusableEventCatalog = reusableEventCatalogEntries.map { record ->
            ReusableEventCatalogExportDto(
                id = record.id,
                kind = record.kind,
                name = record.name,
                usageCount = record.usageCount,
                createdAt = record.createdAtEpochMs.toUtcIsoString(),
                updatedAt = record.updatedAtEpochMs.toUtcIsoString(),
                lastUsedAt = record.lastUsedAtEpochMs.toUtcIsoString()
            )
        }
    )
}

internal fun Long.toUtcIsoString(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(this))

internal fun Throwable.toExportError(defaultMessage: String): DomainError.ExportError {
    return DomainError.ExportError(defaultMessage)
}
