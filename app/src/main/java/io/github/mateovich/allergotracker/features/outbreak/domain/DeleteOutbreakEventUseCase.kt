package io.github.mateovich.allergotracker.features.outbreak.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

class DeleteOutbreakEventUseCase(
    private val repository: OutbreakRepository
) {
    suspend operator fun invoke(id: Long): AppResult<Unit> {
        return repository.deleteOutbreak(id)
    }
}
