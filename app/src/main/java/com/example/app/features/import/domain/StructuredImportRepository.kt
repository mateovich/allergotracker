package com.example.app.features.import.domain

import com.example.app.core.common.result.AppResult
import com.example.app.core.export.schema.VersionedExportEnvelopeDto

interface StructuredImportRepository {
    suspend fun review(document: ImportDocumentContent, envelope: VersionedExportEnvelopeDto): AppResult<StructuredImportReview>

    suspend fun apply(review: StructuredImportReview): AppResult<StructuredImportApplyResult>
}
