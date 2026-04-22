package io.github.mateovich.allergotracker.features.records.domain

import kotlinx.coroutines.flow.Flow

class ObserveRecordCountUseCase(
    private val repository: RecordsRepository
) {
    operator fun invoke(): Flow<Int> = repository.observeRecordCount()
}
