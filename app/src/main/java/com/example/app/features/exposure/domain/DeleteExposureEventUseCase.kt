package com.example.app.features.exposure.domain

import com.example.app.core.common.result.AppResult

class DeleteExposureEventUseCase(
    private val repository: ExposureRepository
) {
    suspend operator fun invoke(id: Long): AppResult<Unit> {
        return repository.deleteExposure(id)
    }
}
