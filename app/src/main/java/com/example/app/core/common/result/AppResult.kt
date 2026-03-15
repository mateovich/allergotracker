package com.example.app.core.common.result

import com.example.app.core.common.error.DomainError

sealed class AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>()
    data class Error(val error: DomainError) : AppResult<Nothing>()

    inline fun <R> fold(onSuccess: (T) -> R, onError: (DomainError) -> R): R {
        return when (this) {
            is Success -> onSuccess(value)
            is Error -> onError(error)
        }
    }
}
