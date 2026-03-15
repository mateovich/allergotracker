package com.example.app.features.exposure.domain

import com.example.app.core.common.result.AppResult

class GetExposureEventUseCase(
    private val repository: ExposureRepository
) {
    suspend operator fun invoke(id: Long): AppResult<ExposureEvent> = repository.getExposure(id)
}
