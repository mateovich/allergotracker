package io.github.mateovich.allergotracker.features.export.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

interface ExportArtifactWriter {
    suspend fun writeJson(fileName: String, content: String): AppResult<ExportArtifact>
}
