package io.github.mateovich.allergotracker.features.exposure.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

interface ExposureRepository {
    suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent>
    suspend fun getExposure(id: Long): AppResult<ExposureEvent>
    suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent>
    suspend fun deleteExposure(id: Long): AppResult<Unit>
}
