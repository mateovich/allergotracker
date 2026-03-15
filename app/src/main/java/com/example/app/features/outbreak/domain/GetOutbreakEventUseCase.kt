package com.example.app.features.outbreak.domain

import com.example.app.core.common.result.AppResult

class GetOutbreakEventUseCase(
    private val repository: OutbreakRepository
) {
    suspend operator fun invoke(id: Long): AppResult<OutbreakEvent> = repository.getOutbreak(id)
}
