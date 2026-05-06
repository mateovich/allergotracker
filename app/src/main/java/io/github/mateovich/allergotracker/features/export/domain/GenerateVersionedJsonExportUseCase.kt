package io.github.mateovich.allergotracker.features.export.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.core.export.schema.EXPORT_SCHEMA_VERSION
import io.github.mateovich.allergotracker.core.export.schema.EventCatalogReferenceDto
import io.github.mateovich.allergotracker.core.export.schema.ExposureExportDto
import io.github.mateovich.allergotracker.core.export.schema.IngestionExportDto
import io.github.mateovich.allergotracker.core.export.schema.NormalizedBaseIngredientContributionExportDto
import io.github.mateovich.allergotracker.core.export.schema.NormalizedBaseIngredientsExportDto
import io.github.mateovich.allergotracker.core.export.schema.NormalizedSourceIngestionExportDto
import io.github.mateovich.allergotracker.core.export.schema.OutbreakExportDto
import io.github.mateovich.allergotracker.core.export.schema.ReusableCompoundComponentExportDto
import io.github.mateovich.allergotracker.core.export.schema.ReusableCompoundExportDto
import io.github.mateovich.allergotracker.core.export.schema.ReusableEntityReferenceDto
import io.github.mateovich.allergotracker.core.export.schema.ReusableEventCatalogExportDto
import io.github.mateovich.allergotracker.core.export.schema.ReusableIngredientExportDto
import io.github.mateovich.allergotracker.core.export.schema.VersionedExportDataDto
import io.github.mateovich.allergotracker.core.export.schema.VersionedExportEnvelopeDto
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import java.time.Instant
import java.time.format.DateTimeFormatter

open class GenerateVersionedJsonExportUseCase(
    private val snapshotRepository: ExportSnapshotRepository,
    private val writer: ExportArtifactWriter,
    private val serializer: VersionedJsonExportSerializer,
    private val currentTimeProvider: CurrentTimeProvider
) {
    private val normalizedIngredientBaseExportBuilder = NormalizedIngredientBaseExportBuilder()

    open suspend fun review(configuration: ExportConfiguration): ExportPreflightReview {
        return assess(configuration)
    }

    open suspend operator fun invoke(
        configuration: ExportConfiguration,
        warningDecision: ExportWarningDecision = ExportWarningDecision.RequireCleanPass,
        preparedExport: PreparedExport? = null
    ): AppResult<GeneratedJsonExport> {
        val review = assess(configuration)
        review.result.blockingIssues.firstOrNull()?.let { issue ->
            return AppResult.Error(issue.error)
        }
        if (
            warningDecision == ExportWarningDecision.AcceptWarnings &&
            review.result.requiresExplicitWarningAcceptance() &&
            preparedExport == null
        ) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Review export warnings before continuing.",
                    fieldErrors = mapOf(PREFLIGHT_FIELD to "Review export warnings before continuing.")
                )
            )
        }
        if (review.result.requiresExplicitWarningAcceptance() && warningDecision != ExportWarningDecision.AcceptWarnings) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Review export warnings before continuing.",
                    fieldErrors = mapOf(PREFLIGHT_FIELD to "Review export warnings before continuing.")
                )
            )
        }
        if (warningDecision == ExportWarningDecision.AcceptWarnings && preparedExport?.configuration != configuration) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Export options changed. Review warnings before continuing.",
                    fieldErrors = mapOf(PREFLIGHT_FIELD to "Export options changed. Review warnings before continuing.")
                )
            )
        }
        if (warningDecision == ExportWarningDecision.AcceptWarnings && preparedExport != null && review.preparedExport != preparedExport) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Export data changed. Review warnings before continuing.",
                    fieldErrors = mapOf(PREFLIGHT_FIELD to "Export data changed. Review warnings before continuing.")
                )
            )
        }
        val exportData = review.preparedExport
            ?: return AppResult.Error(DomainError.ExportError("Could not prepare the export. Try again."))
        val exportedAt = currentTimeProvider.nowEpochMillis().toUtcIsoString()
        val envelope = VersionedExportEnvelopeDto(
            schemaVersion = EXPORT_SCHEMA_VERSION,
            exportedAt = exportedAt,
            data = exportData.snapshot.toDto(exportData.normalizedBaseIngredients)
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

    private suspend fun assess(configuration: ExportConfiguration): ExportPreflightReview {
        val snapshot = when (val result = snapshotRepository.getSnapshot(configuration.scope)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> {
                return ExportPreflightReview(
                    result = ExportPreflightResult(
                        issues = listOf(ExportPreflightIssue.Blocking(result.error))
                    ),
                    preparedExport = null
                )
            }
        }
        val normalizedBaseIngredients = when (val result = normalizedIngredientBaseExportBuilder.build(snapshot)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> {
                return ExportPreflightReview(
                    result = ExportPreflightResult(
                        issues = listOf(ExportPreflightIssue.Blocking(result.error))
                    ),
                    preparedExport = null
                )
            }
        }
        return ExportPreflightReview(
            result = buildResult(configuration),
            preparedExport = PreparedExport(
                configuration = configuration,
                snapshot = snapshot,
                normalizedBaseIngredients = normalizedBaseIngredients
            )
        )
    }

    private fun buildResult(configuration: ExportConfiguration): ExportPreflightResult {
        return ExportPreflightResult(issues = buildWarnings(configuration))
    }

    private fun buildWarnings(configuration: ExportConfiguration): List<ExportPreflightIssue.Warning> {
        return when (configuration.scope) {
            ExportScope.AllRecords -> emptyList()
            is ExportScope.EventTypeRecords -> {
                listOf(
                    ExportPreflightIssue.Warning(
                        message = "This export includes only ${configuration.scope.eventType.label.lowercase()} records. Continue only if you intentionally want a partial dataset."
                    )
                )
            }
        }
    }

    private companion object {
        const val PREFLIGHT_FIELD = "preflight"
    }
}

interface VersionedJsonExportSerializer {
    fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String>
}

private fun ExportSnapshot.toDto(
    normalizedBaseIngredients: List<NormalizedBaseIngredientsRecord>
): VersionedExportDataDto {
    return VersionedExportDataDto(
        ingestions = ingestions.map { record ->
            IngestionExportDto(
                id = record.id,
                itemName = record.itemName,
                quantityLevel = record.quantityLevel,
                occurredAt = record.occurredAtEpochMs.toUtcIsoString(),
                notes = record.notes,
                reusableEntityRef = record.reusableEntityRef?.toDto(),
                createdAt = record.createdAtEpochMs.toUtcIsoString()
            )
        },
        exposures = exposures.map { record ->
            ExposureExportDto(
                id = record.id,
                exposureType = record.exposureType,
                occurredAt = record.occurredAtEpochMs.toUtcIsoString(),
                place = record.place,
                notes = record.notes,
                durationMinutes = record.durationMinutes,
                intensityLevel = record.intensityLevel,
                exposureTypeCatalogRef = record.exposureTypeCatalogRef?.toDto(),
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
                durationMinutes = record.durationMinutes,
                intensityLevel = record.intensityLevel,
                outbreakDescriptorCatalogRef = record.outbreakDescriptorCatalogRef?.toDto(),
                bodyLocationCatalogRef = record.bodyLocationCatalogRef?.toDto(),
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
                        componentRef = component.componentRef.toDto(),
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
        },
        normalizedBaseIngredients = normalizedBaseIngredients.map { record ->
            NormalizedBaseIngredientsExportDto(
                sourceEvent = NormalizedSourceIngestionExportDto(
                    kind = record.sourceEvent.kind,
                    id = record.sourceEvent.id,
                    itemName = record.sourceEvent.itemName,
                    quantityLevel = record.sourceEvent.quantityLevel,
                    occurredAt = record.sourceEvent.occurredAtEpochMs.toUtcIsoString(),
                    reusableEntityRef = record.sourceEvent.reusableEntityRef.toDto()
                ),
                derivationKind = record.derivationKind,
                baseIngredients = record.baseIngredients.map { ingredient ->
                    NormalizedBaseIngredientContributionExportDto(
                        reusableIngredientRef = ingredient.reusableIngredientRef.toDto(),
                        ingredientName = ingredient.ingredientName,
                        contributionBasisPoints = ingredient.contributionBasisPoints,
                        derivedQuantityLevelBasisPoints = ingredient.derivedQuantityLevelBasisPoints
                    )
                }
            )
        }
    )
}

internal fun Long.toUtcIsoString(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(this))

private fun ExportReusableEntityReference.toDto(): ReusableEntityReferenceDto {
    return ReusableEntityReferenceDto(
        kind = kind,
        id = id
    )
}

private fun ExportEventCatalogReference.toDto(): EventCatalogReferenceDto {
    return EventCatalogReferenceDto(
        kind = kind,
        id = id
    )
}

internal fun Throwable.toExportError(defaultMessage: String): DomainError.ExportError {
    return DomainError.ExportError(defaultMessage)
}

private val io.github.mateovich.allergotracker.features.records.domain.RecordEventType.label: String
    get() = when (this) {
        io.github.mateovich.allergotracker.features.records.domain.RecordEventType.INGESTION -> "Ingestion"
        io.github.mateovich.allergotracker.features.records.domain.RecordEventType.EXPOSURE -> "Exposure"
        io.github.mateovich.allergotracker.features.records.domain.RecordEventType.OUTBREAK -> "Outbreak"
    }
