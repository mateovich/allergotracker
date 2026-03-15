package com.example.app.features.exposure.domain

import com.example.app.core.common.result.AppResult

interface ExposureRepository {
    suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent>
    suspend fun getExposure(id: Long): AppResult<ExposureEvent>
    suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent>
}
