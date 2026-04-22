package io.github.mateovich.allergotracker.features.import.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

interface ImportDocumentReader {
    suspend fun readDocument(documentUri: String): AppResult<ImportDocumentContent>
}
