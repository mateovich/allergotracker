package com.example.app.features.home.domain

import com.example.app.core.common.result.AppResult

interface HomeRepository {
    fun loadWelcomeMessage(): AppResult<String>
}
