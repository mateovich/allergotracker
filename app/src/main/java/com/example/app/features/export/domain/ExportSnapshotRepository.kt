package com.example.app.features.export.domain

import com.example.app.core.common.result.AppResult

interface ExportSnapshotRepository {
    suspend fun getSnapshot(scope: ExportScope): AppResult<ExportSnapshot>
}
