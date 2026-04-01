package com.example.app.features.compounds.domain

import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

interface ReusableCompoundRepository {
    suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound>
    suspend fun getCompound(id: Long): AppResult<ReusableCompound> =
        AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    suspend fun updateCompound(command: UpdateReusableCompoundCommand): AppResult<ReusableCompound> =
        AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    fun observeCompounds(): Flow<List<ReusableCompound>>
}
