package com.example.app.features.outbreak.domain

import com.example.app.core.common.result.AppResult

class DeleteOutbreakEventUseCase(
    private val repository: OutbreakRepository
) {
    suspend operator fun invoke(id: Long): AppResult<Unit> {
        return repository.deleteOutbreak(id)
    }
}
