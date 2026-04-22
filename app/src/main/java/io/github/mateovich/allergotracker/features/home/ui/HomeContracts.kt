package io.github.mateovich.allergotracker.features.home.ui

import io.github.mateovich.allergotracker.core.common.error.DomainError

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
