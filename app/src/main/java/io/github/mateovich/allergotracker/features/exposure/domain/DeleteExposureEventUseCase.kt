package io.github.mateovich.allergotracker.features.exposure.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

class DeleteExposureEventUseCase(
    private val repository: ExposureRepository
) {
    suspend operator fun invoke(id: Long): AppResult<Unit> {
        return repository.deleteExposure(id)
    }
}
