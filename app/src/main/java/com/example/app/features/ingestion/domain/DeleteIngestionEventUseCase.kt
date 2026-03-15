package com.example.app.features.ingestion.domain

import com.example.app.core.common.result.AppResult

class DeleteIngestionEventUseCase(
    private val repository: IngestionRepository
) {
    suspend operator fun invoke(id: Long): AppResult<Unit> {
        return repository.deleteIngestion(id)
    }
}
