package com.example.app.core.validation

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult

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
