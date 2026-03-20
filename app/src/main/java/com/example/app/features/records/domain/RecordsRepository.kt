package com.example.app.features.records.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface RecordsRepository {
    fun observeRecordCount(): Flow<Int>
    fun observeRecords(filters: Flow<RecordsFilter> = flowOf(RecordsFilter())): Flow<List<RecordSummary>>
}
