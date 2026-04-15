package com.example.app.features.export.domain

import com.example.app.core.common.result.AppResult

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
