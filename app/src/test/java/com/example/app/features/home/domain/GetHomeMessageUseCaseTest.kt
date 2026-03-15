package com.example.app.features.home.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import org.junit.Assert.assertTrue
import org.junit.Test

class GetHomeMessageUseCaseTest {

    @Test
    fun use_case_returns_repository_result_without_throwing() {
        val useCase = GetHomeMessageUseCase(
            repository = object : HomeRepository {
                override fun loadWelcomeMessage(): AppResult<String> {
                    return AppResult.Error(DomainError.PersistenceError("fail"))
                }
            }
        )

        val result = useCase()

        assertTrue(result is AppResult.Error)
    }
}
