package com.example.app.features.import.domain

import com.example.app.core.export.schema.VersionedExportEnvelopeDto

data class ImportDocumentContent(
    val documentUri: String,
    val fileName: String,
    val rawJson: String
)

data class StructuredImportReview(
    val fileName: String,
    val schemaVersion: String,
    val summary: StructuredImportSummary,
    val preparedImport: PreparedStructuredImport
)

data class StructuredImportSummary(
    val ingredientsToCreate: Int,
    val ingredientsToReuse: Int,
    val compoundsToCreate: Int,
    val compoundsToUpdate: Int,
    val eventCatalogEntriesToCreate: Int,
    val eventCatalogEntriesToReuse: Int,
    val ingestionsToCreate: Int,
    val exposuresToCreate: Int,
    val outbreaksToCreate: Int,
    val rejectedCount: Int = 0
)

data class PreparedStructuredImport(
    val document: ImportDocumentContent,
    val envelope: VersionedExportEnvelopeDto
)

data class StructuredImportApplyResult(
    val fileName: String,
    val summary: StructuredImportSummary
)
