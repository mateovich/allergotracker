package io.github.mateovich.allergotracker.features.home.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

interface HomeRepository {
    fun loadWelcomeMessage(): AppResult<String>
}
