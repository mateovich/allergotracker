package com.example.app.features.records.domain

import kotlinx.coroutines.flow.Flow

class ObserveRecordsUseCase(
    private val repository: RecordsRepository
) {
    operator fun invoke(): Flow<List<RecordSummary>> = repository.observeRecords()
}
