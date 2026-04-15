package com.example.app.features.export.domain

import com.example.app.core.common.result.AppResult

class DeliverStagedExportUseCase(
    private val repository: ExportDeliveryRepository
) {
    suspend operator fun invoke(
        stagedExport: GeneratedJsonExport,
        destination: ExportDestination
    ): AppResult<ExportDeliveryResult> {
        return repository.deliver(stagedExport = stagedExport, destination = destination)
    }
}
