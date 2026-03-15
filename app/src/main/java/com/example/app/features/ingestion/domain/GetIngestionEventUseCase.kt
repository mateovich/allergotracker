package com.example.app.features.ingestion.domain

import com.example.app.core.common.result.AppResult

class GetIngestionEventUseCase(
    private val repository: IngestionRepository
) {
    suspend operator fun invoke(id: Long): AppResult<IngestionEvent> = repository.getIngestion(id)
}
