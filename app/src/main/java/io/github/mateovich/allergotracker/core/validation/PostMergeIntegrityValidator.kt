package io.github.mateovich.allergotracker.core.validation

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult

data class PostMergeIntegrityCheck(
    val isValid: Boolean,
    val failureMessage: String
)

class ValidatePostMergeIntegrityUseCase {
    operator fun invoke(checks: List<PostMergeIntegrityCheck>): AppResult<Unit> {
        val failedCheck = checks.firstOrNull { check -> !check.isValid }
            ?: return AppResult.Success(Unit)
        return AppResult.Error(DomainError.IntegrityError(failedCheck.failureMessage))
    }
}
