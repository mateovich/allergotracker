package com.example.app.features.import.domain

import com.example.app.core.common.result.AppResult

class ApplyStructuredImportUseCase(
    private val repository: StructuredImportRepository
) {
    suspend operator fun invoke(review: StructuredImportReview): AppResult<StructuredImportApplyResult> {
        return repository.apply(review)
    }
}
