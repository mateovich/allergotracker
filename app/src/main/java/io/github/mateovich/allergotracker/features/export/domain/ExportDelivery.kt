package io.github.mateovich.allergotracker.features.export.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

sealed interface ExportDestination {
    data object DeviceDownloads : ExportDestination
}

data class ExportDeliveryResult(
    val destination: ExportDestination,
    val fileName: String,
    val locationLabel: String,
    val locationUri: String
)

interface ExportDeliveryRepository {
    suspend fun deliver(stagedExport: GeneratedJsonExport, destination: ExportDestination): AppResult<ExportDeliveryResult>
}
