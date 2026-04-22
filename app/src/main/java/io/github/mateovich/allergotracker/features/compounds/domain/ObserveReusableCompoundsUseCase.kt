package io.github.mateovich.allergotracker.features.compounds.domain

import kotlinx.coroutines.flow.Flow

class ObserveReusableCompoundsUseCase(
    private val repository: ReusableCompoundRepository
) {
    operator fun invoke(): Flow<List<ReusableCompound>> = repository.observeCompounds()
}
