package io.github.mateovich.allergotracker.features.export.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

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
