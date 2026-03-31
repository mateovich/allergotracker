package com.example.app.features.compounds.domain

import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

interface ReusableCompoundRepository {
    suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound>
    fun observeCompounds(): Flow<List<ReusableCompound>>
}
