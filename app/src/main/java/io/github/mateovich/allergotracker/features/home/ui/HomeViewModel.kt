package io.github.mateovich.allergotracker.features.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.home.domain.GetHomeMessageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getHomeMessageUseCase: GetHomeMessageUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState(isLoading = true))
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        onIntent(HomeUiIntent.Load)
    }

    fun onIntent(intent: HomeUiIntent) {
        when (intent) {
            HomeUiIntent.Load -> loadData()
        }
    }

    private fun loadData() {
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)

        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = when (val result = getHomeMessageUseCase()) {
                is AppResult.Success -> HomeUiState(
                    title = result.value,
                    isLoading = false,
                    error = null
                )

                is AppResult.Error -> HomeUiState(
                    title = "Unable to load placeholder content",
                    isLoading = false,
                    error = result.error
                )
            }
        }
    }
}
