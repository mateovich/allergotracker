package io.github.mateovich.allergotracker.features.compounds.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

class GetReusableCompoundUseCase(
    private val repository: ReusableCompoundRepository
) {
    suspend operator fun invoke(id: Long): AppResult<ReusableCompound> {
        return repository.getCompound(id)
    }
}
