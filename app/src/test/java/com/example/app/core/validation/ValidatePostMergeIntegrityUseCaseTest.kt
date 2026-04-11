package com.example.app.core.validation

import com.example.app.core.common.result.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatePostMergeIntegrityUseCaseTest {

    private val useCase = ValidatePostMergeIntegrityUseCase()

    @Test
    fun returns_success_when_all_checks_pass() {
        val result = useCase(
            listOf(
                PostMergeIntegrityCheck(isValid = true, failureMessage = "unused"),
                PostMergeIntegrityCheck(isValid = true, failureMessage = "unused")
            )
        )

        assertTrue(result is AppResult.Success<Unit>)
    }

    @Test
    fun returns_first_integrity_error_when_any_check_fails() {
        val result = useCase(
            listOf(
                PostMergeIntegrityCheck(isValid = true, failureMessage = "unused"),
                PostMergeIntegrityCheck(isValid = false, failureMessage = "Rollback ingredient merge and reload the preview."),
                PostMergeIntegrityCheck(isValid = false, failureMessage = "unused second failure")
            )
        ) as AppResult.Error

        assertEquals("Rollback ingredient merge and reload the preview.", result.error.message)
    }
}
