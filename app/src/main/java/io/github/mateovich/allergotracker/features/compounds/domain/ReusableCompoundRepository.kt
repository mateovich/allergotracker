package io.github.mateovich.allergotracker.features.compounds.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

interface ReusableCompoundRepository {
    suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound>
    suspend fun getCompound(id: Long): AppResult<ReusableCompound> =
        AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    suspend fun updateCompound(command: UpdateReusableCompoundCommand): AppResult<ReusableCompound> =
        AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    suspend fun deleteCompound(id: Long): AppResult<Unit> =
        AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    fun observeCompounds(): Flow<List<ReusableCompound>>
}
