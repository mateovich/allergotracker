package io.github.mateovich.allergotracker.features.export.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

interface ExportSnapshotRepository {
    suspend fun getSnapshot(scope: ExportScope): AppResult<ExportSnapshot>
}
