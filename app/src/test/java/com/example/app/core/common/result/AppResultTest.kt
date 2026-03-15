package com.example.app.core.common.result

import com.example.app.core.common.error.DomainError
import org.junit.Assert.assertEquals
import org.junit.Test

class AppResultTest {

    @Test
    fun fold_returns_success_branch_value() {
        val result: AppResult<String> = AppResult.Success("ok")

        val folded = result.fold(
            onSuccess = { "value:$it" },
            onError = { "error:${it.message}" }
        )

        assertEquals("value:ok", folded)
    }

    @Test
    fun fold_returns_error_branch_value() {
        val result: AppResult<String> = AppResult.Error(DomainError.ValidationError("bad"))

        val folded = result.fold(
            onSuccess = { "value:$it" },
            onError = { "error:${it.message}" }
        )

        assertEquals("error:bad", folded)
    }
}
