package com.example.app.features.home.domain

import com.example.app.core.common.result.AppResult

class GetHomeMessageUseCase(
    private val repository: HomeRepository
) {
    operator fun invoke(): AppResult<String> = repository.loadWelcomeMessage()
}
