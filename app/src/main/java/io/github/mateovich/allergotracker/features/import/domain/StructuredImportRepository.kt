package io.github.mateovich.allergotracker.features.import.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.core.export.schema.VersionedExportEnvelopeDto

interface StructuredImportRepository {
    suspend fun review(document: ImportDocumentContent, envelope: VersionedExportEnvelopeDto): AppResult<StructuredImportReview>

    suspend fun apply(review: StructuredImportReview): AppResult<StructuredImportApplyResult>
}
