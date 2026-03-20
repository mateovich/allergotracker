package com.example.app.features.records.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ObserveRecordsUseCase(
    private val repository: RecordsRepository
) {
    operator fun invoke(filters: Flow<RecordsFilter> = flowOf(RecordsFilter())): Flow<List<RecordSummary>> {
        return repository.observeRecords(filters)
    }
}
