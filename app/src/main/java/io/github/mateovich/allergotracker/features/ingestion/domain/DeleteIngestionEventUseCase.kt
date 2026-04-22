package io.github.mateovich.allergotracker.features.ingestion.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

class DeleteIngestionEventUseCase(
    private val repository: IngestionRepository
) {
    suspend operator fun invoke(id: Long): AppResult<Unit> {
        return repository.deleteIngestion(id)
    }
}
