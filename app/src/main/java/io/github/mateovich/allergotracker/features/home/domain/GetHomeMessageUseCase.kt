package io.github.mateovich.allergotracker.features.home.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

class GetHomeMessageUseCase(
    private val repository: HomeRepository
) {
    operator fun invoke(): AppResult<String> = repository.loadWelcomeMessage()
}
