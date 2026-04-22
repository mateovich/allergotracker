package com.example.app.features.import.domain

import com.example.app.core.common.result.AppResult

interface ImportDocumentReader {
    suspend fun readDocument(documentUri: String): AppResult<ImportDocumentContent>
}
