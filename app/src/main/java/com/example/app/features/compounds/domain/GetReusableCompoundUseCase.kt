package com.example.app.features.compounds.domain

import com.example.app.core.common.result.AppResult

class GetReusableCompoundUseCase(
    private val repository: ReusableCompoundRepository
) {
    suspend operator fun invoke(id: Long): AppResult<ReusableCompound> {
        return repository.getCompound(id)
    }
}
