package com.example.app.features.export.domain

import com.example.app.core.common.result.AppResult

interface ExportArtifactWriter {
    suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact>
}
