package io.github.mateovich.allergotracker.features.import.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

class ApplyStructuredImportUseCase(
    private val repository: StructuredImportRepository
) {
    suspend operator fun invoke(review: StructuredImportReview): AppResult<StructuredImportApplyResult> {
        return repository.apply(review)
    }
}
