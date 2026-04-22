package io.github.mateovich.allergotracker.features.exposure.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

class GetExposureEventUseCase(
    private val repository: ExposureRepository
) {
    suspend operator fun invoke(id: Long): AppResult<ExposureEvent> = repository.getExposure(id)
}
