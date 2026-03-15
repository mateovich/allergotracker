package com.example.app.features.ingestion.domain

import com.example.app.core.common.result.AppResult

interface IngestionRepository {
    suspend fun createIngestion(command: CreateIngestionEventCommand): AppResult<IngestionEvent>
    suspend fun getIngestion(id: Long): AppResult<IngestionEvent>
    suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent>
    suspend fun deleteIngestion(id: Long): AppResult<Unit>
}
