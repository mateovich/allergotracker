package io.github.mateovich.allergotracker.features.outbreak.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

class GetOutbreakEventUseCase(
    private val repository: OutbreakRepository
) {
    suspend operator fun invoke(id: Long): AppResult<OutbreakEvent> = repository.getOutbreak(id)
}
