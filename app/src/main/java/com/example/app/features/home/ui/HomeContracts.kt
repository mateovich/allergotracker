package com.example.app.features.home.ui

import com.example.app.core.common.error.DomainError

data class HomeUiState(
    val title: String = "",
    val isLoading: Boolean = false,
    val error: DomainError? = null
)

sealed interface HomeUiIntent {
    data object Load : HomeUiIntent
}

sealed interface HomeUiEffect {
    data class ShowError(val error: DomainError) : HomeUiEffect
}
